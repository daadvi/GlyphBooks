package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.data.dao.BookDao
import com.example.data.model.BookEntity
import com.example.sync.CloudSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class BookRepository(
    private val context: Context,
    private val bookDao: BookDao,
    val cloudSyncManager: CloudSyncManager
) {
    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()
    val libraryBooks: Flow<List<BookEntity>> = bookDao.getLibraryBooks()
    val wishlistBooks: Flow<List<BookEntity>> = bookDao.getWishlistBooks()

    fun getBookById(id: Long): Flow<BookEntity?> = bookDao.getBookById(id)

    fun search(query: String): Flow<List<BookEntity>> = bookDao.searchBooks(query)

    suspend fun saveBook(book: BookEntity): Long = withContext(Dispatchers.IO) {
        val id = bookDao.insertBook(book)
        if (cloudSyncManager.syncInfo.value.autoSyncEnabled) {
            // Trigger background sync state update
            bookDao.setAllSynced(true)
        }
        id
    }

    suspend fun updateBook(book: BookEntity) = withContext(Dispatchers.IO) {
        bookDao.updateBook(book)
    }

    suspend fun deleteBook(book: BookEntity) = withContext(Dispatchers.IO) {
        // Also remove local cover file if it exists
        if (book.coverUri.isNotBlank() && book.coverUri.startsWith("file://")) {
            try {
                val file = File(Uri.parse(book.coverUri).path ?: "")
                if (file.exists()) file.delete()
            } catch (_: Exception) {}
        }
        bookDao.deleteBook(book)
    }

    suspend fun saveCoverBitmapLocally(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
        val filename = "cover_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
        val destFile = File(coversDir, filename)
        FileOutputStream(destFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        "file://${destFile.absolutePath}"
    }

    suspend fun populateSampleIfEmpty() = withContext(Dispatchers.IO) {
        // We can add a couple starter curated books if database is fresh so the user has an immediate interactive experience
    }
}
