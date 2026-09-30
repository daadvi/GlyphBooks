package com.example.data.model

data class GenrePreference(
    val genre: String,
    val bookCount: Int,
    val averageRating: Float,
    val weightedScore: Float // bookCount * (averageRating.coerceAtLeast(2.5f))
)

data class UserTasteProfile(
    val totalBooks: Int,
    val totalRatedBooks: Int,
    val topGenres: List<GenrePreference>,
    val favoriteBooks: List<BookEntity>, // Books with rating >= 4
    val topAuthors: List<String>,
    val primaryGenre: String,
    val summaryText: String
) {
    companion object {
        fun calculateFromBooks(books: List<BookEntity>): UserTasteProfile {
            val validBooks = books.filter { !it.isWishlist }
            if (validBooks.isEmpty()) {
                return UserTasteProfile(
                    totalBooks = 0,
                    totalRatedBooks = 0,
                    topGenres = emptyList(),
                    favoriteBooks = emptyList(),
                    topAuthors = emptyList(),
                    primaryGenre = "Geral",
                    summaryText = "Adicione e avalie livros na sua biblioteca para a IA calibrar suas preferências literárias."
                )
            }

            // Group by genre
            val genreGroups = validBooks.groupBy {
                val g = it.genre.trim()
                if (g.isBlank() || g.equals("Geral", ignoreCase = true)) "Literatura Geral" else g
            }

            val genrePreferences = genreGroups.map { (genre, genreBooks) ->
                val ratedBooks = genreBooks.filter { it.rating > 0 }
                val avgRating = if (ratedBooks.isNotEmpty()) {
                    ratedBooks.map { it.rating }.average().toFloat()
                } else {
                    3.0f // default neutral expectation
                }

                // Weighted score prioritizes both quantity of books in that genre AND high user ratings
                val weightedScore = (genreBooks.size * 1.5f) + (avgRating * genreBooks.size * 2.0f)

                GenrePreference(
                    genre = genre,
                    bookCount = genreBooks.size,
                    averageRating = avgRating,
                    weightedScore = weightedScore
                )
            }.sortedByDescending { it.weightedScore }

            val topRatedBooks = validBooks.filter { it.rating >= 4 }.sortedByDescending { it.rating }
            val favoriteBooks = if (topRatedBooks.isNotEmpty()) topRatedBooks else validBooks.take(3)

            val authorScores = mutableMapOf<String, Float>()
            for (b in validBooks) {
                if (b.author.isNotBlank() && b.author != "Autor Desconhecido" && b.author != "Autor") {
                    val weight = if (b.rating > 0) b.rating.toFloat() else 3.0f
                    authorScores[b.author] = (authorScores[b.author] ?: 0f) + weight
                }
            }
            val topAuthors = authorScores.entries.sortedByDescending { it.value }.take(4).map { it.key }

            val primary = genrePreferences.firstOrNull()?.genre ?: "Literatura"
            val highRatedCount = validBooks.count { it.rating >= 4 }

            val summary = buildString {
                append("Gosto predominante: ")
                append(primary)
                if (genrePreferences.size > 1) {
                    append(" e ")
                    append(genrePreferences[1].genre)
                }
                append(". ")
                if (highRatedCount > 0) {
                    append("$highRatedCount livro(s) com alta avaliação (4-5★).")
                } else {
                    append("Avalie seus livros com estrelas para recomendações mais precisas.")
                }
            }

            return UserTasteProfile(
                totalBooks = validBooks.size,
                totalRatedBooks = validBooks.count { it.rating > 0 },
                topGenres = genrePreferences,
                favoriteBooks = favoriteBooks,
                topAuthors = topAuthors,
                primaryGenre = primary,
                summaryText = summary
            )
        }
    }
}
