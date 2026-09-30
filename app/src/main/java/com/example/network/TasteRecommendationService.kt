package com.example.network

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.BookEntity
import com.example.data.model.UserTasteProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RecommendedBook(
    val title: String,
    val author: String,
    val isbn: String = "",
    val coverUrl: String = "",
    val genre: String = "Geral",
    val recommendationReason: String = "",
    val description: String = "",
    val pageCount: Int = 0,
    val matchScorePercent: Int = 95
)

object TasteRecommendationService {
    private const val TAG = "TasteRecommendation"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun getRecommendations(
        tasteProfile: UserTasteProfile,
        ownedBooks: List<BookEntity>
    ): List<RecommendedBook> = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (_: Exception) { "" }
        val ownedTitles = ownedBooks.map { it.title.trim().lowercase() }.toSet()

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val genresInfo = tasteProfile.topGenres.take(3).joinToString(", ") {
                    "${it.genre} (${it.bookCount} livros, média ${String.format("%.1f", it.averageRating)}★)"
                }
                val topRated = ownedBooks.filter { it.rating >= 4 }.joinToString(", ") {
                    "'${it.title}' por ${it.author} (${it.rating}★, ${it.genre})"
                }
                val lowRated = ownedBooks.filter { it.rating in 1..2 }.joinToString(", ") {
                    "'${it.title}' (${it.rating}★)"
                }

                val prompt = """
                    Você é o algoritmo de recomendação do app GlyphBook.
                    O usuário possui uma biblioteca pessoal e você deve analisar rigorosamente o perfil de gostos dele:

                    1. Quantidade de livros por gênero e notas médias dadas:
                       $genresInfo
                    2. Livros mais bem avaliados pelo usuário no app (4 ou 5 estrelas):
                       ${topRated.ifBlank { "Ainda não avaliou com estrelas, use a quantidade de livros por gênero." }}
                    3. Livros com baixa avaliação (evitar similares):
                       ${lowRated.ifBlank { "Nenhum mal avaliado." }}
                    4. Livros que o usuário JÁ TEM (NÃO recomende nenhum destes):
                       ${ownedTitles.take(15).joinToString("; ")}

                    Com base estritamente nestes gostos e nas avaliações dadas, selecione 5 livros reais extraordinários para ele.
                    Para cada livro, explique de forma personalizada o motivo da recomendação citando as preferências dele.

                    Responda ESTRITAMENTE em formato JSON puro, sem markdown em volta:
                    [
                      {
                        "title": "Nome do Livro",
                        "author": "Nome do Autor",
                        "isbn": "978...",
                        "genre": "Gênero do Livro",
                        "reason": "Porque você avaliou '...' com 5 estrelas e adora o gênero ...",
                        "description": "Breve sinopse em português",
                        "pageCount": 320,
                        "matchScore": 98
                      }
                    ]
                """.trimIndent()

                val jsonBody = JSONObject().apply {
                    val contents = JSONArray().apply {
                        val contentObj = JSONObject().apply {
                            val parts = JSONArray().apply {
                                put(JSONObject().apply { put("text", prompt) })
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

                val requestUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(requestUrl)
                    .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseString = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    val rootJson = JSONObject(responseString)
                    val candidates = rootJson.optJSONArray("candidates")
                    val candidate = candidates?.optJSONObject(0)
                    val content = candidate?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val text = parts?.optJSONObject(0)?.optString("text", "") ?: ""

                    val clean = text.trim()
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()

                    val array = JSONArray(clean)
                    val recommendations = mutableListOf<RecommendedBook>()

                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val title = obj.optString("title", "")
                        val author = obj.optString("author", "Autor")
                        val isbn = obj.optString("isbn", "")
                        val genre = obj.optString("genre", tasteProfile.primaryGenre)
                        val reason = obj.optString("reason", "Alinhado com seus livros mais bem avaliados.")
                        val desc = obj.optString("description", "")
                        val pages = obj.optInt("pageCount", 300)
                        val match = obj.optInt("matchScore", 95).coerceIn(80, 99)

                        if (title.isNotBlank() && !ownedTitles.contains(title.lowercase())) {
                            val cover = if (isbn.isNotBlank()) {
                                "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg"
                            } else ""

                            recommendations.add(
                                RecommendedBook(
                                    title = title,
                                    author = author,
                                    isbn = isbn,
                                    coverUrl = cover,
                                    genre = genre,
                                    recommendationReason = reason,
                                    description = desc,
                                    pageCount = pages,
                                    matchScorePercent = match
                                )
                            )
                        }
                    }

                    if (recommendations.isNotEmpty()) {
                        return@withContext recommendations
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini recommendation failed, falling back: ${e.message}")
            }
        }

        // Algorithmic Fallback based on top genre and ratings
        fallbackRecommendations(tasteProfile, ownedTitles)
    }

    private fun fallbackRecommendations(
        tasteProfile: UserTasteProfile,
        ownedTitles: Set<String>
    ): List<RecommendedBook> {
        val topGenre = tasteProfile.topGenres.firstOrNull()?.genre ?: "Ficção Científica"
        val topRatedBook = tasteProfile.favoriteBooks.firstOrNull()
        val reasonBase = if (topRatedBook != null && topRatedBook.rating > 0) {
            "Porque você avaliou '${topRatedBook.title}' com ${topRatedBook.rating}★"
        } else {
            "Baseado no gênero que você mais tem: $topGenre"
        }

        val pool = when {
            topGenre.contains("Ficção", ignoreCase = true) || topGenre.contains("Sci", ignoreCase = true) -> listOf(
                RecommendedBook(
                    title = "O Fim da Infância",
                    author = "Arthur C. Clarke",
                    isbn = "9788576570943",
                    coverUrl = "https://covers.openlibrary.org/b/isbn/9788576570943-L.jpg",
                    genre = "Ficção Científica",
                    recommendationReason = "$reasonBase e seu alto interesse por Sci-Fi.",
                    description = "A chegada de naves extraterrestres que transformam o destino da Terra.",
                    pageCount = 320,
                    matchScorePercent = 98
                ),
                RecommendedBook(
                    title = "Flores para Algernon",
                    author = "Daniel Keyes",
                    isbn = "9788576574163",
                    coverUrl = "https://covers.openlibrary.org/b/isbn/9788576574163-L.jpg",
                    genre = "Ficção Científica",
                    recommendationReason = "$reasonBase e seu apreço por reflexões profundas.",
                    description = "Um clássico emocionante sobre inteligência, empatia e humanidade.",
                    pageCount = 288,
                    matchScorePercent = 96
                ),
                RecommendedBook(
                    title = "O Homem do Castelo Alto",
                    author = "Philip K. Dick",
                    isbn = "9788576574446",
                    coverUrl = "https://covers.openlibrary.org/b/isbn/9788576574446-L.jpg",
                    genre = "Ficção Científica",
                    recommendationReason = "$reasonBase e temas distópicos.",
                    description = "Uma realidade alternativa onde as forças do Eixo venceram a Segunda Guerra.",
                    pageCount = 288,
                    matchScorePercent = 92
                )
            )
            topGenre.contains("Tecnologia", ignoreCase = true) || topGenre.contains("Dev", ignoreCase = true) -> listOf(
                RecommendedBook(
                    title = "Arquitetura Limpa",
                    author = "Robert C. Martin",
                    isbn = "9788550804606",
                    coverUrl = "https://covers.openlibrary.org/b/isbn/9788550804606-L.jpg",
                    genre = "Tecnologia",
                    recommendationReason = "$reasonBase e sua biblioteca focada em excelência de software.",
                    description = "O guia do artesão para estrutura e design de sistemas.",
                    pageCount = 432,
                    matchScorePercent = 99
                ),
                RecommendedBook(
                    title = "O Programador Pragmático",
                    author = "David Thomas, Andrew Hunt",
                    isbn = "9788577807000",
                    coverUrl = "https://covers.openlibrary.org/b/isbn/9788577807000-L.jpg",
                    genre = "Tecnologia",
                    recommendationReason = "$reasonBase e seus livros de programação bem avaliados.",
                    description = "Conselhos atemporais para se tornar um desenvolvedor de alto nível.",
                    pageCount = 352,
                    matchScorePercent = 97
                ),
                RecommendedBook(
                    title = "Microsserviços Prontos Para a Produção",
                    author = "Susan J. Fowler",
                    isbn = "9788575225882",
                    coverUrl = "https://covers.openlibrary.org/b/isbn/9788575225882-L.jpg",
                    genre = "Tecnologia",
                    recommendationReason = "$reasonBase e seu interesse em arquitetura de dados.",
                    description = "Construindo sistemas padronizados em uma organização distribuída.",
                    pageCount = 240,
                    matchScorePercent = 91
                )
            )
            else -> listOf(
                RecommendedBook(
                    title = "A Coragem de Não Agradar",
                    author = "Ichiro Kishimi, Fumitake Koga",
                    isbn = "9788543106571",
                    coverUrl = "https://covers.openlibrary.org/b/isbn/9788543106571-L.jpg",
                    genre = "Filosofia / Psicologia",
                    recommendationReason = "$reasonBase e suas leituras reflexivas.",
                    description = "Como a psicologia adleriana pode libertar você e mudar sua vida.",
                    pageCount = 272,
                    matchScorePercent = 95
                ),
                RecommendedBook(
                    title = "Sobre a Brevidade da Vida",
                    author = "Sêneca",
                    isbn = "9788582850985",
                    coverUrl = "https://covers.openlibrary.org/b/isbn/9788582850985-L.jpg",
                    genre = "Filosofia",
                    recommendationReason = "$reasonBase e seu interesse em obras filosóficas.",
                    description = "A obra-prima estoica sobre o tempo e o viver pleno.",
                    pageCount = 112,
                    matchScorePercent = 94
                )
            )
        }

        return pool.filter { !ownedTitles.contains(it.title.lowercase()) }
    }
}
