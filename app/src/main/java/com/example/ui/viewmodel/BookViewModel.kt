package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.BookEntity
import com.example.data.model.UserTasteProfile
import com.example.data.repository.BookRepository
import com.example.network.RecommendedBook
import com.example.network.TasteRecommendationService
import com.example.sync.CloudSyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    val cloudSyncManager = CloudSyncManager(application, database.bookDao())
    val repository = BookRepository(application, database.bookDao(), cloudSyncManager)

    val libraryBooks: StateFlow<List<BookEntity>> = repository.libraryBooks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val wishlistBooks: StateFlow<List<BookEntity>> = repository.wishlistBooks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allBooks: StateFlow<List<BookEntity>> = repository.allBooks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // User Taste Profile dynamically derived from library books and ratings
    val userTasteProfile: StateFlow<UserTasteProfile> = libraryBooks
        .map { books -> UserTasteProfile.calculateFromBooks(books) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserTasteProfile.calculateFromBooks(emptyList())
        )

    // AI Taste Recommendations
    private val _recommendations = MutableStateFlow<List<RecommendedBook>>(emptyList())
    val recommendations: StateFlow<List<RecommendedBook>> = _recommendations.asStateFlow()

    private val _isLoadingRecommendations = MutableStateFlow(false)
    val isLoadingRecommendations: StateFlow<Boolean> = _isLoadingRecommendations.asStateFlow()

    init {
        // Guarantee that all data is zeroed on first access as requested by the user
        viewModelScope.launch {
            val prefs = application.getSharedPreferences("glyph_first_access_prefs", Context.MODE_PRIVATE)
            val isZeroed = prefs.getBoolean("zeroed_for_first_access", false)
            if (!isZeroed) {
                database.bookDao().deleteAllBooks()
                prefs.edit().putBoolean("zeroed_for_first_access", true).apply()
            }
            refreshRecommendations()
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            database.bookDao().deleteAllBooks()
            refreshRecommendations()
        }
    }

    fun syncWithEmail(email: String) {
        viewModelScope.launch {
            val currentBooks = allBooks.value
            cloudSyncManager.syncWithEmail(email, currentBooks)
        }
    }

    fun restoreFromEmail(email: String) {
        viewModelScope.launch {
            cloudSyncManager.restoreFromEmail(email)
            refreshRecommendations()
        }
    }

    fun refreshRecommendations() {
        viewModelScope.launch {
            _isLoadingRecommendations.value = true
            val profile = userTasteProfile.value
            val currentBooks = libraryBooks.value
            val results = TasteRecommendationService.getRecommendations(profile, currentBooks)
            _recommendations.value = results
            _isLoadingRecommendations.value = false
        }
    }

    fun addBook(book: BookEntity) {
        viewModelScope.launch {
            repository.saveBook(book)
            refreshRecommendations()
        }
    }

    fun updateBook(book: BookEntity) {
        viewModelScope.launch {
            repository.updateBook(book)
            refreshRecommendations()
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            repository.deleteBook(book)
            refreshRecommendations()
        }
    }

    fun moveWishlistToLibrary(book: BookEntity) {
        viewModelScope.launch {
            val updated = book.copy(
                isWishlist = false,
                status = "OWNED",
                isSynced = true
            )
            repository.updateBook(updated)
            refreshRecommendations()
        }
    }

    suspend fun saveCoverBitmap(bitmap: Bitmap): String {
        return repository.saveCoverBitmapLocally(bitmap)
    }
}
