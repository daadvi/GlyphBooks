package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.BookEntity
import com.example.ui.components.NothingDockBar
import com.example.ui.components.ScreenDestination
import com.example.ui.screens.AddBookDialog
import com.example.ui.screens.BookDetailScreen
import com.example.ui.screens.CameraScanScreen
import com.example.ui.screens.CloudSyncScreen
import com.example.ui.screens.ExploreCatalogScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.WishlistScreen
import com.example.ui.theme.GlyphBookTheme
import com.example.ui.viewmodel.BookViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GlyphBookTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp(
    viewModel: BookViewModel = viewModel()
) {
    val libraryBooks by viewModel.libraryBooks.collectAsState()
    val wishlistBooks by viewModel.wishlistBooks.collectAsState()
    val allBooks by viewModel.allBooks.collectAsState()
    val syncInfo by viewModel.cloudSyncManager.syncInfo.collectAsState()
    val tasteProfile by viewModel.userTasteProfile.collectAsState()
    val recommendations by viewModel.recommendations.collectAsState()
    val isLoadingRecommendations by viewModel.isLoadingRecommendations.collectAsState()

    var currentScreen by remember { mutableStateOf(ScreenDestination.LIBRARY) }
    var showCameraScan by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var addDialogInitialWishlist by remember { mutableStateOf(false) }
    var selectedBook by remember { mutableStateOf<BookEntity?>(null) }

    // Back button handling
    BackHandler(enabled = selectedBook != null || showCameraScan || showAddDialog || currentScreen != ScreenDestination.LIBRARY) {
        when {
            selectedBook != null -> selectedBook = null
            showCameraScan -> showCameraScan = false
            showAddDialog -> showAddDialog = false
            currentScreen != ScreenDestination.LIBRARY -> currentScreen = ScreenDestination.LIBRARY
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (showCameraScan) {
            CameraScanScreen(
                onBookScannedAndSaved = { newBook ->
                    viewModel.addBook(newBook)
                },
                onClose = { showCameraScan = false },
                onSaveBitmapCover = { bitmap ->
                    viewModel.saveCoverBitmap(bitmap)
                }
            )
        } else if (selectedBook != null) {
            BookDetailScreen(
                book = selectedBook!!,
                onBack = { selectedBook = null },
                onUpdateBook = { updated ->
                    viewModel.updateBook(updated)
                    selectedBook = updated
                },
                onDeleteBook = { toDelete ->
                    viewModel.deleteBook(toDelete)
                    selectedBook = null
                }
            )
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets.statusBars,
                bottomBar = {
                    NothingDockBar(
                        currentScreen = currentScreen,
                        onNavigate = { currentScreen = it },
                        onOpenScanner = { showCameraScan = true }
                    )
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentScreen) {
                        ScreenDestination.LIBRARY -> {
                            HomeScreen(
                                libraryBooks = libraryBooks,
                                syncInfo = syncInfo,
                                tasteProfile = tasteProfile,
                                onBookClick = { selectedBook = it },
                                onOpenScanner = { showCameraScan = true },
                                onOpenAdd = {
                                    addDialogInitialWishlist = false
                                    showAddDialog = true
                                },
                                onOpenCloudSync = { currentScreen = ScreenDestination.CLOUD_SYNC },
                                onOpenExplore = { currentScreen = ScreenDestination.EXPLORE }
                            )
                        }
                        ScreenDestination.EXPLORE -> {
                            ExploreCatalogScreen(
                                tasteProfile = tasteProfile,
                                recommendations = recommendations,
                                isLoadingRecommendations = isLoadingRecommendations,
                                onRefreshRecommendations = { viewModel.refreshRecommendations() },
                                onAddBookToLibrary = { book ->
                                    viewModel.addBook(book)
                                },
                                onAddBookToWishlist = { book ->
                                    viewModel.addBook(book)
                                }
                            )
                        }
                        ScreenDestination.WISHLIST -> {
                            WishlistScreen(
                                wishlistBooks = wishlistBooks,
                                onBookClick = { selectedBook = it },
                                onMoveToLibrary = { book ->
                                    viewModel.moveWishlistToLibrary(book)
                                },
                                onDeleteBook = { book ->
                                    viewModel.deleteBook(book)
                                },
                                onOpenAdd = {
                                    addDialogInitialWishlist = true
                                    showAddDialog = true
                                }
                            )
                        }
                        ScreenDestination.CLOUD_SYNC -> {
                            CloudSyncScreen(
                                cloudSyncManager = viewModel.cloudSyncManager,
                                allBooks = allBooks,
                                onClearAllData = { viewModel.clearAllData() }
                            )
                        }
                    }
                }
            }

            if (showAddDialog) {
                AddBookDialog(
                    initialIsWishlist = addDialogInitialWishlist,
                    onDismiss = { showAddDialog = false },
                    onBookAdded = { newBook ->
                        viewModel.addBook(newBook)
                    }
                )
            }
        }
    }
}
