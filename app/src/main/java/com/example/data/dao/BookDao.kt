package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY addedDate DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE isWishlist = 0 ORDER BY addedDate DESC")
    fun getLibraryBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE isWishlist = 1 ORDER BY addedDate DESC")
    fun getWishlistBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    fun getBookById(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getBookByIdDirect(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getBookByCloudId(cloudId: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<BookEntity>)

    @Update
    suspend fun updateBook(book: BookEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: Long)

    @Query("DELETE FROM books")
    suspend fun deleteAllBooks()

    @Query("UPDATE books SET isSynced = :synced")
    suspend fun setAllSynced(synced: Boolean)

    @Query("SELECT * FROM books WHERE title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%' OR isbn LIKE '%' || :query || '%' ORDER BY addedDate DESC")
    fun searchBooks(query: String): Flow<List<BookEntity>>
}
