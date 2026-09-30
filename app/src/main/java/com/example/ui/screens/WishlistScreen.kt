package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.BookEntity
import com.example.ui.components.NothingBadge
import com.example.ui.components.NothingButton
import com.example.ui.components.NothingCard
import com.example.ui.components.NothingSectionHeader
import com.example.ui.components.NothingTextField
import com.example.ui.theme.CmfOrange
import com.example.ui.theme.MatrixAmber
import com.example.ui.theme.NothingRed

@Composable
fun WishlistScreen(
    wishlistBooks: List<BookEntity>,
    onBookClick: (BookEntity) -> Unit,
    onMoveToLibrary: (BookEntity) -> Unit,
    onDeleteBook: (BookEntity) -> Unit,
    onOpenAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredList = remember(wishlistBooks, searchQuery) {
        if (searchQuery.isBlank()) wishlistBooks
        else {
            val q = searchQuery.trim().lowercase()
            wishlistBooks.filter {
                it.title.lowercase().contains(q) ||
                it.author.lowercase().contains(q) ||
                it.isbn.lowercase().contains(q) ||
                it.genre.lowercase().contains(q)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp)
    ) {
        // Futuristic Section Header
        NothingSectionHeader(
            tag = "INDEX.WISH",
            title = "Lista de Desejos",
            trailingText = "${wishlistBooks.size} LIVROS DESEJADOS"
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar
        NothingTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = "Filtrar por nome, autor ou ISBN",
            placeholder = "Pesquisar desejos...",
            leadingIcon = Icons.Filled.Search,
            testTag = "input_search_wishlist"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Add Quick Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NothingBadge(
                text = "● FILA DE AQUISIÇÃO",
                color = MatrixAmber,
                borderColor = MatrixAmber.copy(alpha = 0.4f)
            )

            NothingButton(
                text = "+ Novo Desejo",
                onClick = onOpenAdd,
                modifier = Modifier.height(36.dp),
                testTag = "btn_add_wishlist_item"
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.BookmarkBorder,
                            contentDescription = null,
                            tint = MatrixAmber,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "SUA LISTA DE DESEJOS ESTÁ VAZIA",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Escaneie capas de livros que viu em livrarias ou adicione títulos por ISBN para lembrar depois.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    NothingButton(
                        text = "Adicionar Livro Desejado",
                        onClick = onOpenAdd,
                        testTag = "btn_empty_wishlist_add"
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 110.dp)
            ) {
                items(filteredList, key = { it.id }) { book ->
                    WishlistBookCard(
                        book = book,
                        onClick = { onBookClick(book) },
                        onMoveToLibrary = { onMoveToLibrary(book) },
                        onDelete = { onDeleteBook(book) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WishlistBookCard(
    book: BookEntity,
    onClick: () -> Unit,
    onMoveToLibrary: () -> Unit,
    onDelete: () -> Unit
) {
    NothingCard(
        onClick = onClick,
        borderColor = MaterialTheme.colorScheme.outline
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cover Thumbnail
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(88.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (book.coverUri.isNotBlank()) {
                        AsyncImage(
                            model = book.coverUri,
                            contentDescription = book.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Filled.BookmarkBorder,
                            contentDescription = null,
                            tint = MatrixAmber,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Title & Author
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NothingBadge(text = book.genre.uppercase())
                        if (book.isbn.isNotBlank()) {
                            NothingBadge(
                                text = "ISBN: ${book.isbn.take(8)}...",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Remover desejo",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action: Mover para Biblioteca
            NothingButton(
                text = "✓ Comprei! Mover para Biblioteca",
                icon = Icons.Filled.BookmarkAdded,
                onClick = onMoveToLibrary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                testTag = "wishlist_move_to_library_${book.id}"
            )
        }
    }
}
