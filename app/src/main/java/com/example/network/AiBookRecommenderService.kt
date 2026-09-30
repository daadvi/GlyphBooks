package com.example.network

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
import java.util.concurrent.TimeUnit

data class AiPromptRecommendation(
    val title: String,
    val author: String,
    val isbn: String = "",
    val coverUrl: String = "",
    val genre: String = "Literatura",
    val matchReason: String = "",
    val description: String = "",
    val pageCount: Int = 300,
    val matchPercent: Int = 98
)

object AiBookRecommenderService {
    private const val TAG = "AiBookRecommender"
    private val MODELS = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash")

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Recommends books based on any natural language query/description from the user.
     * E.g. "livro que o personagem é heroi", "historia com romance e viagem no tempo", etc.
     */
    suspend fun recommendBooksByPrompt(userPrompt: String): List<AiPromptRecommendation> = withContext(Dispatchers.IO) {
        val cleanPrompt = userPrompt.trim()
        if (cleanPrompt.isBlank()) return@withContext emptyList()

        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (_: Exception) { "" }

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val systemPrompt = """
                    Você é o Curador Literário por IA do aplicativo GlyphBook.
                    O usuário está buscando recomendações digitando uma descrição livre em linguagem natural: "$cleanPrompt".
                    (Exemplos: "livro que o personagem é heroi", "ficção com viagem no tempo", "romance gótico em mansão").

                    Sua missão:
                    1. Compreenda perfeitamente o desejo do leitor (arquétipo do personagem, enredo, clima, gênero, tom).
                    2. Selecione de 4 a 6 livros reais, aclamados e extraordinários que atendam com máxima precisão a essa descrição.
                    3. Para cada livro, escreva um "matchReason" em português explicando especificamente por que este livro atende à busca do usuário (ex: se pediu herói, explique o papel heroico do protagonista).
                    4. Retorne título oficial exato, autor, isbn aproximado se conhecido, gênero, sinopse cativante e páginas.

                    Responda ESTRITAMENTE em formato JSON puro, sem markdown em volta, no schema:
                    [
                      {
                        "title": "Título Oficial do Livro",
                        "author": "Nome do Autor",
                        "isbn": "978...",
                        "genre": "Ficção / Fantasia / etc.",
                        "matchReason": "Explicação personalizada de como o livro atende à busca...",
                        "description": "Sinopse concisa em português",
                        "pageCount": 350,
                        "matchPercent": 98
                      }
                    ]
                """.trimIndent()

                val jsonBody = JSONObject().apply {
                    val contents = JSONArray().apply {
                        val contentObj = JSONObject().apply {
                            val parts = JSONArray().apply {
                                put(JSONObject().apply { put("text", systemPrompt) })
                            }
                            put("parts", parts)
                        }
                        put(contentObj)
                    }
                    put("contents", contents)
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.35)
                        put("responseMimeType", "application/json")
                    })
                }

                val bodyBytes = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

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

                            val cleanText = text.trim()
                                .removePrefix("```json")
                                .removePrefix("```")
                                .removeSuffix("```")
                                .trim()

                            val array = JSONArray(cleanText)
                            val list = mutableListOf<AiPromptRecommendation>()

                            for (i in 0 until array.length()) {
                                val item = array.getJSONObject(i)
                                val title = item.optString("title", "")
                                val author = item.optString("author", "Autor")
                                val isbn = item.optString("isbn", "")
                                val genre = item.optString("genre", "Literatura")
                                val reason = item.optString("matchReason", "Recomendado com base na sua descrição.")
                                val desc = item.optString("description", "")
                                val pages = item.optInt("pageCount", 320)
                                val match = item.optInt("matchPercent", 96).coerceIn(85, 99)

                                val coverUrl = if (isbn.isNotBlank()) {
                                    "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg"
                                } else ""

                                if (title.isNotBlank()) {
                                    list.add(
                                        AiPromptRecommendation(
                                            title = title,
                                            author = author,
                                            isbn = isbn,
                                            coverUrl = coverUrl,
                                            genre = genre,
                                            matchReason = reason,
                                            description = desc,
                                            pageCount = pages,
                                            matchPercent = match
                                        )
                                    )
                                }
                            }

                            if (list.isNotEmpty()) {
                                return@withContext enrichCoversWithApis(list)
                            }
                        }
                    } catch (mErr: Exception) {
                        Log.w(TAG, "Model $model prompt recommendation failed: ${mErr.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "AI prompt recommendation error: ${e.message}", e)
            }
        }

        // Smart, rich heuristic fallback in case Gemini API is not configured or offline
        getSmartFallbackRecommendations(cleanPrompt)
    }

    private suspend fun enrichCoversWithApis(
        list: List<AiPromptRecommendation>
    ): List<AiPromptRecommendation> = withContext(Dispatchers.IO) {
        list.map { rec ->
            if (rec.coverUrl.isBlank() || rec.pageCount == 0) {
                try {
                    val search = BookLookupService.searchBooks(rec.title).firstOrNull()
                    if (search != null) {
                        rec.copy(
                            coverUrl = search.coverUrl.ifBlank { rec.coverUrl },
                            isbn = search.isbn.ifBlank { rec.isbn },
                            pageCount = if (search.pageCount > 0) search.pageCount else rec.pageCount,
                            genre = search.genre.ifBlank { rec.genre }
                        )
                    } else rec
                } catch (_: Exception) {
                    rec
                }
            } else rec
        }
    }

    private suspend fun getSmartFallbackRecommendations(query: String): List<AiPromptRecommendation> = withContext(Dispatchers.IO) {
        val q = query.lowercase().trim()

        val results = when {
            // Hero archetype: "livro que o personagem é heroi" / "heroi" / "heroico"
            q.contains("heroi") || q.contains("herói") || q.contains("protagonista") || q.contains("salvar") -> {
                listOf(
                    AiPromptRecommendation(
                        title = "O Ladrão de Raios",
                        author = "Rick Riordan",
                        isbn = "9788598078396",
                        coverUrl = "https://covers.openlibrary.org/b/isbn/9788598078396-L.jpg",
                        genre = "Fantasia & Aventura",
                        matchReason = "Percy Jackson descobre ser um semideus e assume a jornada clássica do herói mitológico para salvar sua mãe e impedir uma guerra no Olimpo.",
                        description = "Um garoto de doze anos descobre que seu verdadeiro pai é Poseidon e precisa provar sua inocência enfrentando monstros mitológicos modernos.",
                        pageCount = 400,
                        matchPercent = 99
                    ),
                    AiPromptRecommendation(
                        title = "O Hobbit",
                        author = "J.R.R. Tolkien",
                        isbn = "9788595084742",
                        coverUrl = "https://covers.openlibrary.org/b/isbn/9788595084742-L.jpg",
                        genre = "Fantasia Clássica",
                        matchReason = "Bilbo Bolseiro personifica o herói relutante que supera todos os medos para ajudar seus companheiros diante do dragão Smaug.",
                        description = "A jornada extraordinária de Bilbo através da Terra Média com treze anões e o mago Gandalf.",
                        pageCount = 336,
                        matchPercent = 97
                    ),
                    AiPromptRecommendation(
                        title = "Mistborn: O Império Final",
                        author = "Brandon Sanderson",
                        isbn = "9788544100868",
                        coverUrl = "https://covers.openlibrary.org/b/isbn/9788544100868-L.jpg",
                        genre = "Fantasia Épica",
                        matchReason = "Kelsier e Vin formam uma dupla heroica que lidera uma rebelião impossível contra um deus-imperador imortal.",
                        description = "Em um mundo onde as cinzas caem e o Senhor Soberano reina há mil anos, ladrões com poderes alomânticos tentam o impensável.",
                        pageCount = 608,
                        matchPercent = 96
                    ),
                    AiPromptRecommendation(
                        title = "Duna",
                        author = "Frank Herbert",
                        isbn = "9788576573135",
                        coverUrl = "https://covers.openlibrary.org/b/isbn/9788576573135-L.jpg",
                        genre = "Ficção Científica",
                        matchReason = "Paul Atreides é o herói profetizado (Muad'Dib) que une o povo Fremen nas areias implacáveis de Arrakis.",
                        description = "A luta colossal pelo controle da especiaria mélânge no planeta desértico de Arrakis.",
                        pageCount = 680,
                        matchPercent = 95
                    )
                )
            }

            // Time travel / Sci-fi: "viagem no tempo", "futuro", "tempo"
            q.contains("tempo") || q.contains("viaja") || q.contains("futuro") -> {
                listOf(
                    AiPromptRecommendation(
                        title = "A Máquina do Tempo",
                        author = "H.G. Wells",
                        isbn = "9788525410948",
                        coverUrl = "https://covers.openlibrary.org/b/isbn/9788525410948-L.jpg",
                        genre = "Ficção Científica Clássica",
                        matchReason = "O clássico supremo sobre um inventor britânico que viaja até o ano 802.701 d.C.",
                        description = "Uma expedição fascinante rumo ao fim dos tempos e a divisão da humanidade em Eloi e Morlocks.",
                        pageCount = 144,
                        matchPercent = 98
                    ),
                    AiPromptRecommendation(
                        title = "Matéria Escura",
                        author = "Blake Crouch",
                        isbn = "9788551001226",
                        coverUrl = "https://covers.openlibrary.org/b/isbn/9788551001226-L.jpg",
                        genre = "Thriller Sci-Fi",
                        matchReason = "Um físico genial é jogado em realidades paralelas e luta através de dimensões temporais para voltar à sua família.",
                        description = "Você é feliz com a vida que escolheu? Uma jornada eletrizante pelo multiverso quântico.",
                        pageCount = 352,
                        matchPercent = 97
                    ),
                    AiPromptRecommendation(
                        title = "11/22/63",
                        author = "Stephen King",
                        isbn = "9788581051512",
                        coverUrl = "https://covers.openlibrary.org/b/isbn/9788581051512-L.jpg",
                        genre = "Ficção & Suspense",
                        matchReason = "Um professor viaja aos anos 1950 através de um portal para tentar impedir o assassinato de John F. Kennedy.",
                        description = "Uma viagem épica aos Estados Unidos dos anos 60 com consequências imprevisíveis para o continuum do tempo.",
                        pageCount = 736,
                        matchPercent = 95
                    )
                )
            }

            // Mystery / Detective: "detetive", "crime", "investigação", "misterio", "mistério"
            q.contains("detetive") || q.contains("crime") || q.contains("misterio") || q.contains("mistério") || q.contains("assassinato") -> {
                listOf(
                    AiPromptRecommendation(
                        title = "Assassinato no Expresso do Oriente",
                        author = "Agatha Christie",
                        isbn = "9788525430267",
                        coverUrl = "https://covers.openlibrary.org/b/isbn/9788525430267-L.jpg",
                        genre = "Mistério & Suspense",
                        matchReason = "O detetive genial Hercule Poirot precisa solucionar um homicídio trancado dentro de um trem preso na neve.",
                        description = "Doze passageiros suspeitos, um homem morto com doze facadas e a mente afiada de Poirot.",
                        pageCount = 240,
                        matchPercent = 98
                    ),
                    AiPromptRecommendation(
                        title = "Um Estudo em Vermelho",
                        author = "Arthur Conan Doyle",
                        isbn = "9788537809624",
                        coverUrl = "https://covers.openlibrary.org/b/isbn/9788537809624-L.jpg",
                        genre = "Policial Clássico",
                        matchReason = "O caso de estreia que une Sherlock Holmes e o Dr. John Watson resolvendo um crime bizarro em Londres.",
                        description = "O primeiro enigma que apresentou ao mundo o método dedutivo de Sherlock Holmes.",
                        pageCount = 176,
                        matchPercent = 97
                    )
                )
            }

            // Romance
            q.contains("romance") || q.contains("amor") || q.contains("apaixonar") -> {
                listOf(
                    AiPromptRecommendation(
                        title = "Orgulho e Preconceito",
                        author = "Jane Austen",
                        isbn = "9788544001820",
                        coverUrl = "https://covers.openlibrary.org/b/isbn/9788544001820-L.jpg",
                        genre = "Romance Clássico",
                        matchReason = "O embate inesquecível entre Elizabeth Bennet e o misterioso Sr. Darcy na Inglaterra rural.",
                        description = "Uma crítica de costumes afiada e uma das histórias de amor mais celebradas de todos os tempos.",
                        pageCount = 424,
                        matchPercent = 98
                    ),
                    AiPromptRecommendation(
                        title = "Pessoas Normais",
                        author = "Sally Rooney",
                        isbn = "9788535932591",
                        coverUrl = "https://covers.openlibrary.org/b/isbn/9788535932591-L.jpg",
                        genre = "Romance Contemporâneo",
                        matchReason = "Uma história profunda sobre as conexões humanas, vulnerabilidade e a intensidade do primeiro amor.",
                        description = "A relação magnética e complexa entre Marianne e Connell da escola à universidade em Dublin.",
                        pageCount = 264,
                        matchPercent = 95
                    )
                )
            }

            // General search: query live APIs
            else -> {
                try {
                    val apiResults = BookLookupService.searchBooks(query).take(4)
                    apiResults.map { book ->
                        AiPromptRecommendation(
                            title = book.title,
                            author = book.author,
                            isbn = book.isbn,
                            coverUrl = book.coverUrl,
                            genre = book.genre,
                            matchReason = "Obra relevante identificada no acervo para o tema '$query'.",
                            description = book.description,
                            pageCount = if (book.pageCount > 0) book.pageCount else 280,
                            matchPercent = 94
                        )
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }

        enrichCoversWithApis(results)
    }
}
