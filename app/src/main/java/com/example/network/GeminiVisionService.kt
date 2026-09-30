package com.example.network

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class ExtractedBookData(
    val title: String,
    val author: String,
    val isbn: String = "",
    val genre: String = "Geral",
    val synopsis: String = "",
    val pageCount: Int = 0,
    val confidence: String = "HIGH",
    val rawNotes: String = ""
)

object GeminiVisionService {
    private const val TAG = "GeminiVisionService"
    private val MODELS = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash")

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        val maxDimension = 1200
        val scale = if (width > maxDimension || height > maxDimension) {
            maxDimension.toFloat() / maxOf(width, height)
        } else {
            1.0f
        }
        val scaled = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
        } else {
            this
        }
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Complete and resilient Book Identification Engine:
     * 1. Offline Barcode / EAN-13 scanning (ZXing) -> Instant 100% exact ISBN match
     * 2. Live Query across 5 Book APIs (BrasilAPI, Google Books, Open Library)
     * 3. Cover & Spine visual recognition via Gemini Vision (multi-model fallback)
     * 4. Multi-API metadata enrichment
     */
    suspend fun analyzeBookCover(bitmap: Bitmap): ExtractedBookData = withContext(Dispatchers.IO) {
        // STEP 1: Attempt offline optical barcode recognition (EAN-13 / ISBN)
        try {
            val detectedBarcode = BarcodeScannerService.scanBarcodeFromBitmap(bitmap)
            if (!detectedBarcode.isNullOrBlank()) {
                Log.i(TAG, "Barcode detected on book: $detectedBarcode. Looking up in APIs...")
                val apiBook = BookLookupService.lookupByIsbn(detectedBarcode)
                if (apiBook != null) {
                    Log.i(TAG, "Book found via Barcode + ${apiBook.source}: ${apiBook.title}")
                    return@withContext ExtractedBookData(
                        title = apiBook.title,
                        author = apiBook.author,
                        isbn = apiBook.isbn.ifBlank { detectedBarcode },
                        genre = apiBook.genre,
                        synopsis = apiBook.description,
                        pageCount = apiBook.pageCount,
                        confidence = "CÓDIGO DE BARRAS // ${apiBook.source}",
                        rawNotes = "Identificado com precisão de 100% pelo código de barras físico."
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Barcode check error: ${e.message}")
        }

        // STEP 2: Vision AI Analysis with Multi-Model Fallback
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val base64Image = bitmap.toBase64()
                val prompt = """
                    Você é um catalogador bibliotecário e especialista em design editorial.
                    Analise esta foto da capa ou lombada do livro físico anexada.
                    Identifique com precisão o livro real correspondente:
                    1. "title": Nome oficial e completo impresso na capa.
                    2. "author": Autor(es) principal(is).
                    3. "isbn": Código ISBN se visível na capa ou código de barras (caso contrário deixe "").
                    4. "genre": Gênero literário principal (Ficção Científica, Literatura Brasileira, Tecnologia, Filosofia, Fantasia, etc.).
                    5. "synopsis": Breve sinopse em português (1 a 2 frases).
                    6. "pageCount": Número de páginas aproximado desta edição (ou 0 se desconhecido).

                    Responda ESTRITAMENTE em formato JSON puro, sem markdown em volta:
                    {
                      "title": "Título Oficial",
                      "author": "Nome do Autor",
                      "isbn": "978...",
                      "genre": "Gênero",
                      "synopsis": "Sinopse em português",
                      "pageCount": 320
                    }
                """.trimIndent()

                val jsonBody = JSONObject().apply {
                    val contents = JSONArray().apply {
                        val contentObj = JSONObject().apply {
                            val parts = JSONArray().apply {
                                put(JSONObject().apply { put("text", prompt) })
                                put(JSONObject().apply {
                                    put("inlineData", JSONObject().apply {
                                        put("mimeType", "image/jpeg")
                                        put("data", base64Image)
                                    })
                                })
                            }
                            put("parts", parts)
                        }
                        put(contentObj)
                    }
                    put("contents", contents)
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.2)
                        put("responseMimeType", "application/json")
                    })
                }

                val bodyBytes = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

                // Try model sequence in case of quota or model unavailability
                for (model in MODELS) {
                    try {
                        val requestUrl = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                        val request = Request.Builder().url(requestUrl).post(bodyBytes).build()
                        val response = client.newCall(request).execute()
                        val responseString = response.body?.string().orEmpty()

                        if (response.isSuccessful && responseString.isNotBlank()) {
                            val rootJson = JSONObject(responseString)
                            val candidates = rootJson.optJSONArray("candidates")
                            val candidate = candidates?.optJSONObject(0)
                            val content = candidate?.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            val text = parts?.optJSONObject(0)?.optString("text", "") ?: ""

                            val parsed = parseGeminiResponse(text)
                            if (parsed.title.isNotBlank() && parsed.title != "Título Não Identificado") {
                                // Enrich with 5 APIs for highest data accuracy
                                val enriched = enrichWithLiveApis(parsed)
                                return@withContext enriched
                            }
                        } else {
                            Log.w(TAG, "Model $model returned code: ${response.code}")
                        }
                    } catch (modelErr: Exception) {
                        Log.w(TAG, "Model $model call failed: ${modelErr.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gemini vision processing failed", e)
            }
        }

        // STEP 3: Fallback - Safe Default with Guidance
        ExtractedBookData(
            title = "Livro Físico Escaneado",
            author = "Autor Desconhecido",
            genre = "Geral",
            synopsis = "Foto registrada. Caso a capa tenha código de barras, aponte a câmera diretamente para o verso para identificação instantânea.",
            confidence = "MANUAL_CHECK"
        )
    }

    private suspend fun enrichWithLiveApis(base: ExtractedBookData): ExtractedBookData = withContext(Dispatchers.IO) {
        try {
            if (base.isbn.isNotBlank()) {
                val apiBook = BookLookupService.lookupByIsbn(base.isbn)
                if (apiBook != null) {
                    return@withContext base.copy(
                        title = apiBook.title.ifBlank { base.title },
                        author = apiBook.author.ifBlank { base.author },
                        pageCount = if (apiBook.pageCount > 0) apiBook.pageCount else base.pageCount,
                        genre = apiBook.genre.ifBlank { base.genre },
                        synopsis = apiBook.description.ifBlank { base.synopsis },
                        confidence = "GEMINI IA + ${apiBook.source}"
                    )
                }
            }

            if (base.title.isNotBlank() && base.title != "Título Não Identificado") {
                val searchList = BookLookupService.searchBooks(base.title)
                val match = searchList.firstOrNull()
                if (match != null) {
                    return@withContext base.copy(
                        title = match.title.ifBlank { base.title },
                        author = match.author.ifBlank { base.author },
                        isbn = match.isbn.ifBlank { base.isbn },
                        pageCount = if (match.pageCount > 0) match.pageCount else base.pageCount,
                        genre = match.genre.ifBlank { base.genre },
                        synopsis = match.description.ifBlank { base.synopsis },
                        confidence = "GEMINI IA + ${match.source}"
                    )
                }
            }
        } catch (_: Exception) {}
        base
    }

    private fun parseGeminiResponse(rawText: String): ExtractedBookData {
        return try {
            val clean = rawText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val obj = JSONObject(clean)
            ExtractedBookData(
                title = obj.optString("title", "Título Não Identificado").ifBlank { "Título Não Identificado" },
                author = obj.optString("author", "Autor Desconhecido").ifBlank { "Autor Desconhecido" },
                isbn = obj.optString("isbn", ""),
                genre = obj.optString("genre", "Literatura").ifBlank { "Literatura" },
                synopsis = obj.optString("synopsis", ""),
                pageCount = obj.optInt("pageCount", 0),
                confidence = "IA GEMINI VISION",
                rawNotes = clean
            )
        } catch (e: Exception) {
            Log.w(TAG, "JSON parsing error on response: $rawText", e)
            ExtractedBookData(
                title = "Livro Escaneado",
                author = "Autor",
                confidence = "MEDIUM"
            )
        }
    }
}
