package com.example.network

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class BookLookupResult(
    val title: String,
    val author: String,
    val isbn: String = "",
    val coverUrl: String = "",
    val pageCount: Int = 0,
    val genre: String = "Geral",
    val description: String = "",
    val publisher: String = "",
    val publishedYear: String = "",
    val source: String = "GOOGLE BOOKS", // "GOOGLE BOOKS", "BRASIL API (CBL)", "OPEN LIBRARY", "GUTENBERG", "INTERNET ARCHIVE", "IA CURADORIA"
    val externalUrl: String = ""
)

object BookLookupService {
    private const val TAG = "BookLookupService"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    /**
     * Look up a single book directly by ISBN across multiple APIs in sequence:
     * 1. BrasilAPI (Câmara Brasileira do Livro / ISBN Brasil)
     * 2. Google Books API
     * 3. Open Library API
     */
    suspend fun lookupByIsbn(isbn: String): BookLookupResult? = withContext(Dispatchers.IO) {
        val cleanIsbn = isbn.replace("-", "").replace(" ", "").trim()
        if (cleanIsbn.isEmpty()) return@withContext null

        // 1. Try BrasilAPI (Best for Brazilian & Portuguese ISBNs: 97885..., 97865...)
        val brasilResult = queryBrasilApiIsbn(cleanIsbn)
        if (brasilResult != null) {
            // If BrasilAPI doesn't have a cover, enrich with OpenLibrary or Google Books cover
            if (brasilResult.coverUrl.isBlank()) {
                val enrichedCover = getCoverForIsbn(cleanIsbn)
                return@withContext brasilResult.copy(coverUrl = enrichedCover)
            }
            return@withContext brasilResult
        }

        // 2. Try Google Books API by ISBN
        val googleResult = queryGoogleBooksByIsbn(cleanIsbn)
        if (googleResult != null) return@withContext googleResult

        // 3. Try OpenLibrary by ISBN
        val openLibraryResult = queryOpenLibraryByIsbn(cleanIsbn)
        if (openLibraryResult != null) return@withContext openLibraryResult

        null
    }

    /**
     * Search books in parallel across multiple public book APIs:
     * - Google Books API (Expanded to 30 items)
     * - Open Library API (search.json)
     * - Gutendex (Project Gutenberg - 70k+ free literature classics)
     * - Internet Archive Texts API
     * - BrasilAPI (if query matches ISBN format)
     */
    suspend fun searchBooks(query: String): List<BookLookupResult> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        val digitsOnly = cleanQuery.replace("-", "").replace(" ", "")
        val isIsbnQuery = digitsOnly.length in 10..13 && digitsOnly.all { it.isDigit() || it == 'X' || it == 'x' }

        // If user typed an ISBN, prioritize direct ISBN lookup first
        if (isIsbnQuery) {
            val isbnBook = lookupByIsbn(digitsOnly)
            if (isbnBook != null) {
                return@withContext listOf(isbnBook)
            }
        }

        val aggregated = mutableListOf<BookLookupResult>()

        coroutineScope {
            val googleDeferred = async { queryGoogleBooksSearch(cleanQuery) }
            val openLibraryDeferred = async { queryOpenLibrarySearch(cleanQuery) }
            val gutendexDeferred = async { queryGutendexSearch(cleanQuery) }
            val internetArchiveDeferred = async { queryInternetArchiveSearch(cleanQuery) }

            val googleList = try { googleDeferred.await() } catch (_: Exception) { emptyList() }
            val openLibraryList = try { openLibraryDeferred.await() } catch (_: Exception) { emptyList() }
            val gutendexList = try { gutendexDeferred.await() } catch (_: Exception) { emptyList() }
            val archiveList = try { internetArchiveDeferred.await() } catch (_: Exception) { emptyList() }

            aggregated.addAll(googleList)
            aggregated.addAll(openLibraryList)
            aggregated.addAll(gutendexList)
            aggregated.addAll(archiveList)
        }

        // Deduplicate books by normalized Title + Author
        val uniqueList = mutableListOf<BookLookupResult>()
        val seenSignatures = mutableSetOf<String>()

        for (item in aggregated) {
            val normalizedTitle = item.title.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
            val normalizedAuthor = item.author.trim().lowercase().replace(Regex("[^a-z0-9]"), "").take(10)
            val signature = "$normalizedTitle|$normalizedAuthor"

            if (signature !in seenSignatures && normalizedTitle.isNotEmpty()) {
                seenSignatures.add(signature)
                uniqueList.add(item)
            }
        }

        uniqueList
    }

    /**
     * AI-Powered Book Search with seamless multi-API fallback
     */
    suspend fun searchWithAi(query: String): List<BookLookupResult> = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (_: Exception) { "" }

        // If no Gemini key or offline, fall back directly to multi-API search
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext searchBooks(query)
        }

        try {
            val prompt = """
                O usuário está pesquisando livros na biblioteca digital com a busca: "$query".
                Como especialista em curadoria literária mundial:
                1. Identifique os livros reais correspondentes, inclusive corrigindo erros de digitação ou encontrando as obras mais consagradas e procuradas sobre este tema.
                2. Retorne uma lista de até 8 livros específicos existentes no catálogo editorial com título oficial, autor principal, código ISBN aproximado ou válido, gênero e uma frase curta de sinopse em português.

                Responda ESTRITAMENTE em formato JSON puro, sem markdown em volta:
                [
                  {
                    "title": "Nome Exato do Livro",
                    "author": "Nome do Autor",
                    "isbn": "978...",
                    "genre": "Ficção / Tecnologia / etc.",
                    "description": "Sinopse concisa em português",
                    "pageCount": 350
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
                    put("temperature", 0.3)
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

                val jsonArray = JSONArray(clean)
                val aiBooks = mutableListOf<BookLookupResult>()

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val title = item.optString("title", "")
                    val author = item.optString("author", "Autor")
                    val isbn = item.optString("isbn", "")
                    val genre = item.optString("genre", "Literatura")
                    val description = item.optString("description", "")
                    val pageCount = item.optInt("pageCount", 280)

                    val coverUrl = if (isbn.isNotBlank()) {
                        "https://covers.openlibrary.org/b/isbn/$isbn-M.jpg"
                    } else ""

                    if (title.isNotBlank()) {
                        aiBooks.add(
                            BookLookupResult(
                                title = title,
                                author = author,
                                isbn = isbn,
                                coverUrl = coverUrl,
                                pageCount = pageCount,
                                genre = genre,
                                description = description,
                                source = "IA CURADORIA"
                            )
                        )
                    }
                }

                if (aiBooks.isNotEmpty()) {
                    // Enrich with live API results as well so the user gets a vast collection
                    val liveBooks = searchBooks(query).take(6)
                    val combined = (aiBooks + liveBooks).distinctBy { it.title.lowercase().trim() }
                    return@withContext combined
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AI Search failed or quota reached, falling back to multi-API search: ${e.message}")
        }

        // Resilient fallback to multi-API search
        searchBooks(query)
    }

    /**
     * Get rich category catalog from multiple APIs:
     * - Google Books API
     * - Open Library Subjects API
     * - Gutendex Topics (Project Gutenberg)
     */
    suspend fun getCatalogByCategory(category: String): List<BookLookupResult> = withContext(Dispatchers.IO) {
        val gBooksQuery = when (category) {
            "Literatura Brasileira" -> "subject:brazilian+literature+machado"
            "Ficção Científica" -> "subject:fiction+science+dune"
            "Tecnologia & Dev" -> "subject:computers+programming+kotlin"
            "Filosofia" -> "subject:philosophy+stoicism"
            "Clássicos" -> "subject:classic+literature"
            "Fantasia" -> "subject:fantasy+epic"
            "Negócios & Gestão" -> "subject:business+leadership"
            "Psicologia" -> "subject:psychology+mind"
            else -> "bestseller+livros"
        }

        val openLibrarySubject = when (category) {
            "Literatura Brasileira" -> "brazilian_literature"
            "Ficção Científica" -> "science_fiction"
            "Tecnologia & Dev" -> "computers"
            "Filosofia" -> "philosophy"
            "Clássicos" -> "classic_literature"
            "Fantasia" -> "fantasy"
            "Negócios & Gestão" -> "business"
            "Psicologia" -> "psychology"
            else -> "bestseller"
        }

        val gutendexTopic = when (category) {
            "Literatura Brasileira" -> "brazil"
            "Ficção Científica" -> "science fiction"
            "Tecnologia & Dev" -> "technology"
            "Filosofia" -> "philosophy"
            "Clássicos" -> "classics"
            "Fantasia" -> "fantasy"
            "Negócios & Gestão" -> "economics"
            "Psicologia" -> "psychology"
            else -> ""
        }

        val aggregated = mutableListOf<BookLookupResult>()

        coroutineScope {
            val gBooksDeferred = async { queryGoogleBooksSearch(gBooksQuery, maxResults = 20) }
            val olSubjectDeferred = async { queryOpenLibrarySubject(openLibrarySubject) }
            val gutendexDeferred = async { if (gutendexTopic.isNotBlank()) queryGutendexTopic(gutendexTopic) else emptyList() }

            aggregated.addAll(try { gBooksDeferred.await() } catch (_: Exception) { emptyList() })
            aggregated.addAll(try { olSubjectDeferred.await() } catch (_: Exception) { emptyList() })
            aggregated.addAll(try { gutendexDeferred.await() } catch (_: Exception) { emptyList() })
        }

        val unique = aggregated.distinctBy { it.title.trim().lowercase() }
        if (unique.isNotEmpty()) {
            return@withContext unique
        }

        getCuratedCategoryDefaults(category)
    }

    // ==========================================
    // 1. BRASIL API (CBL / ISBN BRASIL)
    // ==========================================
    private fun queryBrasilApiIsbn(isbn: String): BookLookupResult? {
        try {
            val url = "https://brasilapi.com.br/api/isbn/v1/$isbn"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                if (body.isNotBlank() && body != "{}") {
                    val json = JSONObject(body)
                    val title = json.optString("title", "")
                    val subtitle = json.optString("subtitle", "")
                    val fullTitle = if (subtitle.isNotBlank() && subtitle != "null") "$title: $subtitle" else title

                    val authorsArray = json.optJSONArray("authors")
                    val authors = if (authorsArray != null && authorsArray.length() > 0) {
                        (0 until authorsArray.length()).joinToString(", ") { authorsArray.getString(it) }
                    } else "Autor Desconhecido"

                    val publisher = json.optString("publisher", "")
                    val synopsis = json.optString("synopsis", "")
                    val pageCount = json.optInt("page_count", 0)
                    val year = json.optString("year", "")
                    val cover = json.optString("cover_url", "").let { if (it == "null") "" else it }

                    val subjectsArray = json.optJSONArray("subjects")
                    val genre = if (subjectsArray != null && subjectsArray.length() > 0) {
                        subjectsArray.getString(0)
                    } else "Literatura Brasileira"

                    if (fullTitle.isNotBlank()) {
                        return BookLookupResult(
                            title = fullTitle,
                            author = authors,
                            isbn = isbn,
                            coverUrl = cover.ifBlank { "https://covers.openlibrary.org/b/isbn/$isbn-M.jpg" },
                            pageCount = pageCount,
                            genre = genre,
                            description = synopsis,
                            publisher = publisher,
                            publishedYear = year,
                            source = "BRASIL API (CBL)"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "BrasilAPI lookup failed: ${e.message}")
        }
        return null
    }

    // ==========================================
    // 2. GOOGLE BOOKS API
    // ==========================================
    private fun queryGoogleBooksByIsbn(isbn: String): BookLookupResult? {
        try {
            val gUrl = "https://www.googleapis.com/books/v1/volumes?q=isbn:$isbn"
            val request = Request.Builder().url(gUrl).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                if (body.isNotBlank()) {
                    val root = JSONObject(body)
                    val items = root.optJSONArray("items")
                    if (items != null && items.length() > 0) {
                        val vol = items.getJSONObject(0).getJSONObject("volumeInfo")
                        val title = vol.optString("title", "")
                        val authors = vol.optJSONArray("authors")
                        val author = if (authors != null && authors.length() > 0) authors.getString(0) else "Autor Desconhecido"
                        val pageCount = vol.optInt("pageCount", 0)
                        val imgLinks = vol.optJSONObject("imageLinks")
                        val cover = imgLinks?.optString("thumbnail")?.replace("http://", "https://")
                            ?: "https://covers.openlibrary.org/b/isbn/$isbn-M.jpg"
                        val cats = vol.optJSONArray("categories")
                        val genre = if (cats != null && cats.length() > 0) cats.getString(0) else "Geral"
                        val description = vol.optString("description", "")
                        val publisher = vol.optString("publisher", "")
                        val publishedDate = vol.optString("publishedDate", "")

                        return BookLookupResult(
                            title = title,
                            author = author,
                            isbn = isbn,
                            coverUrl = cover,
                            pageCount = pageCount,
                            genre = genre,
                            description = description,
                            publisher = publisher,
                            publishedYear = publishedDate,
                            source = "GOOGLE BOOKS"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google Books ISBN failed: ${e.message}")
        }
        return null
    }

    private fun queryGoogleBooksSearch(query: String, maxResults: Int = 25): List<BookLookupResult> {
        val list = mutableListOf<BookLookupResult>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.googleapis.com/books/v1/volumes?q=$encoded&maxResults=$maxResults&printType=books"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                if (body.isNotBlank()) {
                    val root = JSONObject(body)
                    val items = root.optJSONArray("items")
                    if (items != null) {
                        for (i in 0 until items.length()) {
                            val item = items.getJSONObject(i)
                            val vol = item.optJSONObject("volumeInfo") ?: continue
                            val title = vol.optString("title", "")
                            if (title.isBlank()) continue

                            val authorsArr = vol.optJSONArray("authors")
                            val author = if (authorsArr != null && authorsArr.length() > 0) authorsArr.getString(0) else "Autor Desconhecido"

                            var isbn = ""
                            val industryIds = vol.optJSONArray("industryIdentifiers")
                            if (industryIds != null) {
                                for (j in 0 until industryIds.length()) {
                                    val idObj = industryIds.getJSONObject(j)
                                    val idVal = idObj.optString("identifier", "")
                                    if (idVal.length in 10..13) {
                                        isbn = idVal
                                        break
                                    }
                                }
                            }

                            val imgLinks = vol.optJSONObject("imageLinks")
                            val cover = imgLinks?.optString("thumbnail")?.replace("http://", "https://")
                                ?: if (isbn.isNotBlank()) "https://covers.openlibrary.org/b/isbn/$isbn-M.jpg" else ""

                            val categories = vol.optJSONArray("categories")
                            val genre = if (categories != null && categories.length() > 0) categories.getString(0) else "Literatura"
                            val pageCount = vol.optInt("pageCount", 0)
                            val description = vol.optString("description", "")
                            val publisher = vol.optString("publisher", "")
                            val publishedDate = vol.optString("publishedDate", "")

                            list.add(
                                BookLookupResult(
                                    title = title,
                                    author = author,
                                    isbn = isbn,
                                    coverUrl = cover,
                                    pageCount = pageCount,
                                    genre = genre,
                                    description = description,
                                    publisher = publisher,
                                    publishedYear = publishedDate,
                                    source = "GOOGLE BOOKS"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google Books search failed: ${e.message}")
        }
        return list
    }

    // ==========================================
    // 3. OPEN LIBRARY API
    // ==========================================
    private fun queryOpenLibraryByIsbn(isbn: String): BookLookupResult? {
        try {
            val url = "https://openlibrary.org/api/books?bibkeys=ISBN:$isbn&format=json&jscmd=data"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                if (body.isNotBlank() && body != "{}") {
                    val root = JSONObject(body)
                    val bookKey = "ISBN:$isbn"
                    if (root.has(bookKey)) {
                        val obj = root.getJSONObject(bookKey)
                        val title = obj.optString("title", "")
                        val authors = obj.optJSONArray("authors")
                        val author = if (authors != null && authors.length() > 0) {
                            authors.getJSONObject(0).optString("name", "Autor Desconhecido")
                        } else "Autor Desconhecido"

                        val pageCount = obj.optInt("number_of_pages", 0)
                        val coverObj = obj.optJSONObject("cover")
                        val cover = coverObj?.optString("large")
                            ?: coverObj?.optString("medium")
                            ?: "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg"

                        val subjects = obj.optJSONArray("subjects")
                        val genre = if (subjects != null && subjects.length() > 0) subjects.getJSONObject(0).optString("name", "Literatura") else "Geral"
                        val publishers = obj.optJSONArray("publishers")
                        val publisher = if (publishers != null && publishers.length() > 0) publishers.getJSONObject(0).optString("name", "") else ""
                        val publishDate = obj.optString("publish_date", "")

                        return BookLookupResult(
                            title = title,
                            author = author,
                            isbn = isbn,
                            coverUrl = cover,
                            pageCount = pageCount,
                            genre = genre,
                            publisher = publisher,
                            publishedYear = publishDate,
                            source = "OPEN LIBRARY"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "OpenLibrary ISBN lookup failed: ${e.message}")
        }
        return null
    }

    private fun queryOpenLibrarySearch(query: String, limit: Int = 15): List<BookLookupResult> {
        val list = mutableListOf<BookLookupResult>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://openlibrary.org/search.json?q=$encoded&limit=$limit"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                if (body.isNotBlank()) {
                    val root = JSONObject(body)
                    val docs = root.optJSONArray("docs")
                    if (docs != null) {
                        for (i in 0 until docs.length()) {
                            val doc = docs.getJSONObject(i)
                            val title = doc.optString("title", "")
                            if (title.isBlank()) continue

                            val authorList = doc.optJSONArray("author_name")
                            val author = if (authorList != null && authorList.length() > 0) authorList.getString(0) else "Autor Desconhecido"
                            val isbnList = doc.optJSONArray("isbn")
                            val isbn = if (isbnList != null && isbnList.length() > 0) isbnList.getString(0) else ""

                            val coverId = doc.optInt("cover_i", 0)
                            val coverUrl = if (coverId > 0) "https://covers.openlibrary.org/b/id/$coverId-M.jpg"
                            else if (isbn.isNotBlank()) "https://covers.openlibrary.org/b/isbn/$isbn-M.jpg" else ""

                            val pageCount = doc.optInt("number_of_pages_median", 0)
                            val firstPublishYear = doc.optString("first_publish_year", "")

                            list.add(
                                BookLookupResult(
                                    title = title,
                                    author = author,
                                    isbn = isbn,
                                    coverUrl = coverUrl,
                                    pageCount = pageCount,
                                    genre = "Literatura",
                                    publishedYear = firstPublishYear,
                                    source = "OPEN LIBRARY"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "OpenLibrary search failed: ${e.message}")
        }
        return list
    }

    private fun queryOpenLibrarySubject(subject: String, limit: Int = 20): List<BookLookupResult> {
        val list = mutableListOf<BookLookupResult>()
        try {
            val url = "https://openlibrary.org/subjects/$subject.json?limit=$limit"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                if (body.isNotBlank()) {
                    val root = JSONObject(body)
                    val works = root.optJSONArray("works")
                    if (works != null) {
                        for (i in 0 until works.length()) {
                            val work = works.getJSONObject(i)
                            val title = work.optString("title", "")
                            if (title.isBlank()) continue

                            val authors = work.optJSONArray("authors")
                            val author = if (authors != null && authors.length() > 0) {
                                authors.getJSONObject(0).optString("name", "Autor Desconhecido")
                            } else "Autor Desconhecido"

                            val coverId = work.optInt("cover_id", 0)
                            val coverUrl = if (coverId > 0) "https://covers.openlibrary.org/b/id/$coverId-M.jpg" else ""

                            list.add(
                                BookLookupResult(
                                    title = title,
                                    author = author,
                                    coverUrl = coverUrl,
                                    genre = subject.replace("_", " ").replaceFirstChar { it.uppercase() },
                                    source = "OPEN LIBRARY"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "OpenLibrary subject failed: ${e.message}")
        }
        return list
    }

    // ==========================================
    // 4. GUTENDEX (PROJECT GUTENBERG - 70K+ BOOKS)
    // ==========================================
    private fun queryGutendexSearch(query: String): List<BookLookupResult> {
        val list = mutableListOf<BookLookupResult>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://gutendex.com/books/?search=$encoded"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                if (body.isNotBlank()) {
                    val root = JSONObject(body)
                    val results = root.optJSONArray("results")
                    if (results != null) {
                        for (i in 0 until minOf(results.length(), 12)) {
                            val book = results.getJSONObject(i)
                            val title = book.optString("title", "").replace("\n", " ").trim()
                            if (title.isBlank()) continue

                            val authorsArray = book.optJSONArray("authors")
                            val author = if (authorsArray != null && authorsArray.length() > 0) {
                                val rawAuthor = authorsArray.getJSONObject(0).optString("name", "Autor")
                                formatGutendexAuthor(rawAuthor)
                            } else "Autor Clássico"

                            val formats = book.optJSONObject("formats")
                            val coverUrl = formats?.optString("image/jpeg", "").orEmpty()

                            val subjects = book.optJSONArray("subjects")
                            val genre = if (subjects != null && subjects.length() > 0) {
                                subjects.getString(0).split("--").first().trim()
                            } else "Clássicos"

                            list.add(
                                BookLookupResult(
                                    title = title,
                                    author = author,
                                    coverUrl = coverUrl,
                                    genre = genre,
                                    description = "Obra literária do acervo aberto Projeto Gutenberg.",
                                    source = "GUTENBERG"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gutendex search failed: ${e.message}")
        }
        return list
    }

    private fun queryGutendexTopic(topic: String): List<BookLookupResult> {
        val list = mutableListOf<BookLookupResult>()
        try {
            val encoded = URLEncoder.encode(topic, "UTF-8")
            val url = "https://gutendex.com/books/?topic=$encoded"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                if (body.isNotBlank()) {
                    val root = JSONObject(body)
                    val results = root.optJSONArray("results")
                    if (results != null) {
                        for (i in 0 until minOf(results.length(), 10)) {
                            val book = results.getJSONObject(i)
                            val title = book.optString("title", "").replace("\n", " ").trim()
                            if (title.isBlank()) continue

                            val authorsArray = book.optJSONArray("authors")
                            val author = if (authorsArray != null && authorsArray.length() > 0) {
                                formatGutendexAuthor(authorsArray.getJSONObject(0).optString("name", "Autor"))
                            } else "Autor Clássico"

                            val formats = book.optJSONObject("formats")
                            val coverUrl = formats?.optString("image/jpeg", "").orEmpty()

                            list.add(
                                BookLookupResult(
                                    title = title,
                                    author = author,
                                    coverUrl = coverUrl,
                                    genre = topic.replaceFirstChar { it.uppercase() },
                                    source = "GUTENBERG"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gutendex topic failed: ${e.message}")
        }
        return list
    }

    private fun formatGutendexAuthor(raw: String): String {
        // Gutendex returns "Assis, Machado de" or "Shakespeare, William"
        return if (raw.contains(",")) {
            val parts = raw.split(",")
            if (parts.size >= 2) "${parts[1].trim()} ${parts[0].trim()}" else raw
        } else raw
    }

    // ==========================================
    // 5. INTERNET ARCHIVE TEXTS API
    // ==========================================
    private fun queryInternetArchiveSearch(query: String): List<BookLookupResult> {
        val list = mutableListOf<BookLookupResult>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://archive.org/advancedsearch.php?q=mediatype:texts+AND+($encoded)&fl[]=identifier,title,creator,description,year,subject&rows=10&output=json"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                if (body.isNotBlank()) {
                    val root = JSONObject(body)
                    val resp = root.optJSONObject("response")
                    val docs = resp?.optJSONArray("docs")
                    if (docs != null) {
                        for (i in 0 until docs.length()) {
                            val doc = docs.getJSONObject(i)
                            val title = doc.optString("title", "")
                            if (title.isBlank()) continue

                            val identifier = doc.optString("identifier", "")
                            val author = doc.optString("creator", "Autor Desconhecido")
                            val year = doc.optString("year", "")
                            val desc = doc.optString("description", "")
                            val coverUrl = if (identifier.isNotBlank()) "https://archive.org/services/img/$identifier" else ""

                            list.add(
                                BookLookupResult(
                                    title = title,
                                    author = author,
                                    coverUrl = coverUrl,
                                    publishedYear = year,
                                    description = desc,
                                    source = "INTERNET ARCHIVE"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Internet Archive search failed: ${e.message}")
        }
        return list
    }

    private fun getCoverForIsbn(isbn: String): String {
        return "https://covers.openlibrary.org/b/isbn/$isbn-M.jpg"
    }

    private fun getCuratedCategoryDefaults(category: String): List<BookLookupResult> {
        return when (category) {
            "Ficção Científica" -> listOf(
                BookLookupResult("Duna", "Frank Herbert", "9788576573135", "https://covers.openlibrary.org/b/isbn/9788576573135-L.jpg", 680, "Ficção Científica", "O romance épico que redefiniu o gênero.", "Aleph", "1965", "BRASIL API (CBL)"),
                BookLookupResult("Neuromancer", "William Gibson", "9788576573005", "https://covers.openlibrary.org/b/isbn/9788576573005-L.jpg", 320, "Ficção Científica", "O nascimento do Cyberpunk e da Matrix.", "Aleph", "1984", "BRASIL API (CBL)"),
                BookLookupResult("Fahrenheit 451", "Ray Bradbury", "9788525052247", "https://covers.openlibrary.org/b/isbn/9788525052247-L.jpg", 216, "Ficção Científica", "Um mundo onde os livros são proibidos e queimados.", "Globo", "1953", "GOOGLE BOOKS"),
                BookLookupResult("Fundação", "Isaac Asimov", "9788576570721", "https://covers.openlibrary.org/b/isbn/9788576570721-L.jpg", 240, "Ficção Científica", "A psicohistória e o futuro da civilização galáctica.", "Aleph", "1951", "BRASIL API (CBL)")
            )
            "Tecnologia & Dev" -> listOf(
                BookLookupResult("Clean Code", "Robert C. Martin", "9780132350884", "https://covers.openlibrary.org/b/isbn/9780132350884-L.jpg", 464, "Tecnologia", "Guia essencial sobre código legível e sustentável.", "Prentice Hall", "2008", "OPEN LIBRARY"),
                BookLookupResult("The Pragmatic Programmer", "Andrew Hunt, David Thomas", "9780135957059", "https://covers.openlibrary.org/b/isbn/9780135957059-L.jpg", 352, "Tecnologia", "Da jornada de codificador a mestre do artesanato de software.", "Addison-Wesley", "2019", "GOOGLE BOOKS"),
                BookLookupResult("Designing Data-Intensive Applications", "Martin Kleppmann", "9781449373320", "https://covers.openlibrary.org/b/isbn/9781449373320-L.jpg", 616, "Tecnologia", "A bíblia dos sistemas distribuídos e bancos de dados modernos.", "O'Reilly", "2017", "OPEN LIBRARY"),
                BookLookupResult("Refactoring", "Martin Fowler", "9780134757599", "https://covers.openlibrary.org/b/isbn/9780134757599-L.jpg", 448, "Tecnologia", "Melhorando o projeto de código existente.", "Addison-Wesley", "2018", "GOOGLE BOOKS")
            )
            "Filosofia" -> listOf(
                BookLookupResult("Meditações", "Marco Aurélio", "9788593751851", "https://covers.openlibrary.org/b/isbn/9788593751851-L.jpg", 160, "Filosofia", "Reflexões íntimas do imperador filósofo estoico.", "Penguin", "180", "GUTENBERG"),
                BookLookupResult("Assim Falou Zaratustra", "Friedrich Nietzsche", "9788535919424", "https://covers.openlibrary.org/b/isbn/9788535919424-L.jpg", 376, "Filosofia", "Um livro para todos e para ninguém.", "Cia das Letras", "1883", "BRASIL API (CBL)"),
                BookLookupResult("A República", "Platão", "9788572329880", "https://covers.openlibrary.org/b/isbn/9788572329880-L.jpg", 432, "Filosofia", "O clássico diálogo socrático sobre justiça e sociedade.", "Martin Claret", "375 a.C.", "GUTENBERG")
            )
            else -> listOf(
                BookLookupResult("Dom Casmurro", "Machado de Assis", "9788535911664", "https://covers.openlibrary.org/b/isbn/9788535911664-L.jpg", 256, "Literatura Brasileira", "A obra-prima sobre Bento Santiago e Capitu.", "Cia das Letras", "1899", "GUTENBERG"),
                BookLookupResult("Sapiens: Uma Breve História da Humanidade", "Yuval Noah Harari", "9788525432186", "https://covers.openlibrary.org/b/isbn/9788525432186-L.jpg", 464, "História", "Da evolução biológica à era dos algoritmos.", "L&PM", "2014", "BRASIL API (CBL)"),
                BookLookupResult("O Pequeno Príncipe", "Antoine de Saint-Exupéry", "9788522031085", "https://covers.openlibrary.org/b/isbn/9788522031085-L.jpg", 96, "Clássico", "O essencial é invisível aos olhos.", "Agir", "1943", "GOOGLE BOOKS"),
                BookLookupResult("Cem Anos de Solidão", "Gabriel García Márquez", "9788501012074", "https://covers.openlibrary.org/b/isbn/9788501012074-L.jpg", 448, "Realismo Fantástico", "A saga monumental da família Buendía em Macondo.", "Record", "1967", "GOOGLE BOOKS")
            )
        }
    }
}
