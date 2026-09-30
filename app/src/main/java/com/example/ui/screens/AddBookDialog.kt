package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.BookEntity
import com.example.network.BookLookupResult
import com.example.network.BookLookupService
import com.example.ui.components.NothingBadge
import com.example.ui.components.NothingButton
import com.example.ui.components.NothingCard
import com.example.ui.components.NothingTextField
import com.example.ui.theme.CmfOrange
import com.example.ui.theme.MatrixAmber
import com.example.ui.theme.MatrixCyan
import com.example.ui.theme.MatrixGreen
import com.example.ui.theme.NothingRed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBookDialog(
    initialIsWishlist: Boolean = false,
    onDismiss: () -> Unit,
    onBookAdded: (BookEntity) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Busca por ISBN/Rede, 1: Digitação Manual

    // Form fields
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var isbn by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("Literatura") }
    var pageCount by remember { mutableStateOf("") }
    var coverUrl by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isWishlist by remember { mutableStateOf(initialIsWishlist) }

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<BookLookupResult>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CATÁLOGO // NOVO LIVRO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isWishlist) "Adicionar à Lista de Desejos" else "Adicionar à Biblioteca",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tabs: Busca Automática vs Entrada Manual
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = CmfOrange
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "BUSCA POR ISBN / TÍTULO",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "ENTRADA MANUAL",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                // Tab 0: Network Search by ISBN or Title
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        NothingTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = "ISBN (10 ou 13 dígitos) ou Nome do Livro",
                            placeholder = "Ex: 9780132350884 ou Sapiens",
                            leadingIcon = Icons.Filled.Search,
                            modifier = Modifier.weight(1f),
                            testTag = "input_search_isbn_query"
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    NothingButton(
                        text = "Consultar Catálogo Mundial",
                        onClick = {
                            if (searchQuery.isNotBlank()) {
                                coroutineScope.launch {
                                    isSearching = true
                                    searchError = null
                                    val cleanQuery = searchQuery.trim()

                                    // If clean query looks like ISBN (digits or hyphens), try ISBN directly first
                                    val digitsOnly = cleanQuery.replace("-", "")
                                    var result: BookLookupResult? = null
                                    if (digitsOnly.length in 10..13 && digitsOnly.all { it.isDigit() || it == 'X' || it == 'x' }) {
                                        result = BookLookupService.lookupByIsbn(digitsOnly)
                                    }

                                    if (result != null) {
                                        searchResults = listOf(result)
                                    } else {
                                        // Search by text query
                                        val list = BookLookupService.searchBooks(cleanQuery)
                                        searchResults = list
                                        if (list.isEmpty()) {
                                            searchError = "Nenhum livro encontrado para '$cleanQuery'. Tente a Entrada Manual."
                                        }
                                    }
                                    isSearching = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "btn_search_catalog"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isSearching) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = CmfOrange, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Consultando 5 APIs (Google Books, BrasilAPI, OpenLibrary, Gutenberg, Internet Archive)...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (searchError != null) {
                        Text(
                            text = searchError!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NothingRed,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else if (searchResults.isNotEmpty()) {
                        Text(
                            text = "RESULTADOS ENCONTRADOS (TOQUE PARA SELECIONAR):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        LazyColumn(
                            modifier = Modifier.height(260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(searchResults) { book ->
                                NothingCard(
                                    onClick = {
                                        title = book.title
                                        author = book.author
                                        isbn = book.isbn
                                        genre = book.genre
                                        pageCount = if (book.pageCount > 0) book.pageCount.toString() else "300"
                                        coverUrl = book.coverUrl
                                        notes = book.description
                                        selectedTab = 1 // Switch to edit & confirm
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        if (book.coverUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = book.coverUrl,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .width(45.dp)
                                                    .height(65.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .width(45.dp)
                                                    .height(65.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(MaterialTheme.colorScheme.surface),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Filled.Book, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = book.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = book.author,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                            if (book.isbn.isNotBlank()) {
                                                Text(
                                                    text = "ISBN: ${book.isbn}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            NothingBadge(
                                                text = book.source,
                                                color = when (book.source) {
                                                    "BRASIL API (CBL)" -> MatrixGreen
                                                    "GOOGLE BOOKS" -> MatrixCyan
                                                    "OPEN LIBRARY" -> MatrixAmber
                                                    "GUTENBERG" -> CmfOrange
                                                    else -> Color(0xFFB0BEC5)
                                                }
                                            )
                                            NothingBadge(text = "SELECIONAR")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Tab 1: Manual Input & Confirmation
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    NothingTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = "Nome do Livro (Obrigatório)",
                        placeholder = "Ex: O Hobbit",
                        testTag = "input_manual_title"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    NothingTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = "Autor(es)",
                        placeholder = "Ex: J.R.R. Tolkien",
                        testTag = "input_manual_author"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NothingTextField(
                            value = isbn,
                            onValueChange = { isbn = it },
                            label = "ISBN",
                            placeholder = "Ex: 97885...",
                            modifier = Modifier.weight(1.3f),
                            testTag = "input_manual_isbn"
                        )

                        NothingTextField(
                            value = pageCount,
                            onValueChange = { pageCount = it },
                            label = "Páginas",
                            placeholder = "Ex: 310",
                            modifier = Modifier.weight(1f),
                            testTag = "input_manual_pages"
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    NothingTextField(
                        value = genre,
                        onValueChange = { genre = it },
                        label = "Gênero / Categoria",
                        placeholder = "Ex: Ficção Científica, Clássico...",
                        testTag = "input_manual_genre"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    NothingTextField(
                        value = coverUrl,
                        onValueChange = { coverUrl = it },
                        label = "Link da Foto/Capa (Opcional)",
                        placeholder = "https://...",
                        testTag = "input_manual_cover"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Destination toggle (Library vs Wishlist)
                    Text(
                        text = "CLASSIFICAÇÃO DO REGISTRO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !isWishlist,
                            onClick = { isWishlist = false },
                            label = { Text("TENHO (BIBLIOTECA)") },
                            modifier = Modifier.weight(1f).testTag("chip_dest_library")
                        )
                        FilterChip(
                            selected = isWishlist,
                            onClick = { isWishlist = true },
                            label = { Text("QUERO (LISTA DE DESEJOS)") },
                            modifier = Modifier.weight(1f).testTag("chip_dest_wishlist")
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    NothingButton(
                        text = if (isWishlist) "Salvar na Lista de Desejos" else "Adicionar à Biblioteca",
                        onClick = {
                            if (title.isNotBlank()) {
                                val pages = pageCount.toIntOrNull() ?: 200
                                val book = BookEntity(
                                    title = title.trim(),
                                    author = author.trim().ifBlank { "Autor Não Informado" },
                                    isbn = isbn.trim(),
                                    coverUri = coverUrl.trim(),
                                    status = if (isWishlist) "WISHLIST" else "OWNED",
                                    isWishlist = isWishlist,
                                    genre = genre.trim().ifBlank { "Geral" },
                                    pageCount = pages,
                                    notes = notes.trim(),
                                    isSynced = true
                                )
                                onBookAdded(book)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "confirm_manual_add_btn"
                    )
                }
            }
        }
    }
}
