package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String,
    val isbn: String = "",
    val coverUri: String = "",
    val status: String = "OWNED", // OWNED, READING, COMPLETED, WISHLIST
    val isWishlist: Boolean = false,
    val rating: Int = 0, // 0 to 5
    val notes: String = "",
    val genre: String = "Geral",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val addedDate: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val cloudId: String = UUID.randomUUID().toString()
) {
    val readingProgressPercent: Float
        get() = if (pageCount > 0) {
            (currentPage.toFloat() / pageCount.toFloat()).coerceIn(0f, 1f)
        } else if (status == "COMPLETED") {
            1f
        } else {
            0f
        }
}
