package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.ui.components.NothingTextField
import com.example.ui.components.SegmentedDotBar
import com.example.ui.theme.CmfOrange
import com.example.ui.theme.MatrixAmber
import com.example.ui.theme.MatrixGreen
import com.example.ui.theme.NothingRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    book: BookEntity,
    onBack: () -> Unit,
    onUpdateBook: (BookEntity) -> Unit,
    onDeleteBook: (BookEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(book.currentPage) }
    var currentRating by remember { mutableIntStateOf(book.rating) }
    var currentStatus by remember { mutableStateOf(book.status) }
    var notesText by remember { mutableStateOf(book.notes) }

    val formattedDate = remember(book.addedDate) {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(book.addedDate))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(NothingRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DETALHES // REGISTRO",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.testTag("detail_delete_btn")
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Excluir Livro", tint = NothingRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Cover and Core Details Hero
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Book Cover
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(175.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (book.coverUri.isNotBlank()) {
                        AsyncImage(
                            model = book.coverUri,
                            contentDescription = "Capa de ${book.title}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "SEM FOTO",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Title, Author, Genre, ISBN
                Column(modifier = Modifier.weight(1f)) {
                    NothingBadge(
                        text = if (book.isWishlist) "LISTA DE DESEJOS" else book.status.uppercase(),
                        color = if (book.isWishlist) MatrixAmber else CmfOrange,
                        borderColor = if (book.isWishlist) MatrixAmber.copy(alpha = 0.5f) else CmfOrange.copy(alpha = 0.5f)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (book.isbn.isNotBlank()) {
                        Text(
                            text = "ISBN: ${book.isbn}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "GÊNERO: ${book.genre.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // If Wishlist: Big CTA to convert to Library
            if (book.isWishlist) {
                NothingCard(
                    backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    borderColor = CmfOrange
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "ESTE LIVRO ESTÁ NA SUA LISTA DE DESEJOS",
                            style = MaterialTheme.typography.labelSmall,
                            color = CmfOrange
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Adquiriu este livro físico recentemente?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        NothingButton(
                            text = "Mover para Minha Biblioteca",
                            icon = Icons.Filled.BookmarkAdded,
                            onClick = {
                                val updated = book.copy(
                                    isWishlist = false,
                                    status = "OWNED",
                                    isSynced = true
                                )
                                onUpdateBook(updated)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "move_to_library_btn"
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Reading Progress Section (for owned books)
            if (!book.isWishlist) {
                NothingCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PROGRESSO DE LEITURA",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val totalPages = if (book.pageCount > 0) book.pageCount else 100
                            val pct = ((currentPage.toFloat() / totalPages.toFloat()) * 100).toInt().coerceIn(0, 100)
                            Text(
                                text = "$currentPage / $totalPages PÁGINAS ($pct%)",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (pct >= 100) MatrixGreen else CmfOrange
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val totalPages = if (book.pageCount > 0) book.pageCount else 100
                        val progressFloat = (currentPage.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f)
                        SegmentedDotBar(
                            progress = progressFloat,
                            totalSegments = 24,
                            activeColor = if (progressFloat >= 1f) MatrixGreen else CmfOrange,
                            height = 8.dp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Controls: -1, Slider, +1
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (currentPage > 0) {
                                        currentPage--
                                        val newStatus = if (currentPage == 0) "OWNED" else "READING"
                                        currentStatus = newStatus
                                        onUpdateBook(book.copy(currentPage = currentPage, status = newStatus))
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = "Diminuir página")
                            }

                            Slider(
                                value = currentPage.toFloat(),
                                onValueChange = {
                                    currentPage = it.toInt()
                                },
                                onValueChangeFinished = {
                                    val newStatus = if (currentPage >= totalPages) "COMPLETED" else if (currentPage > 0) "READING" else "OWNED"
                                    currentStatus = newStatus
                                    onUpdateBook(book.copy(currentPage = currentPage, status = newStatus))
                                },
                                valueRange = 0f..totalPages.toFloat(),
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = CmfOrange,
                                    activeTrackColor = CmfOrange
                                )
                            )

                            IconButton(
                                onClick = {
                                    if (currentPage < totalPages) {
                                        currentPage++
                                        val newStatus = if (currentPage >= totalPages) "COMPLETED" else "READING"
                                        currentStatus = newStatus
                                        onUpdateBook(book.copy(currentPage = currentPage, status = newStatus))
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Aumentar página")
                            }
                        }

                        // Status Chips
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = currentStatus == "OWNED",
                                onClick = {
                                    currentStatus = "OWNED"
                                    onUpdateBook(book.copy(status = "OWNED"))
                                },
                                label = { Text("Quero Ler", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = currentStatus == "READING",
                                onClick = {
                                    currentStatus = "READING"
                                    onUpdateBook(book.copy(status = "READING"))
                                },
                                label = { Text("Lendo", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = currentStatus == "COMPLETED",
                                onClick = {
                                    currentStatus = "COMPLETED"
                                    currentPage = totalPages
                                    onUpdateBook(book.copy(status = "COMPLETED", currentPage = totalPages))
                                },
                                label = { Text("Concluído", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Star Rating
            NothingCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "AVALIAÇÃO PESSOAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 1..5) {
                            Icon(
                                imageVector = if (i <= currentRating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "$i estrelas",
                                tint = if (i <= currentRating) MatrixAmber else MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable {
                                        currentRating = if (currentRating == i) 0 else i
                                        onUpdateBook(book.copy(rating = currentRating))
                                    }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Notes and Review section
            NothingCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "NOTAS & RESENHA",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    NothingTextField(
                        value = notesText,
                        onValueChange = {
                            notesText = it
                            onUpdateBook(book.copy(notes = it))
                        },
                        label = "Anotações do Leitor",
                        placeholder = "Escreva seus pensamentos, trechos marcantes ou resenha sobre o livro...",
                        singleLine = false,
                        testTag = "input_book_notes"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Technical Cloud & Device Metadata HUD
            NothingCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MatrixGreen))
                            Text(text = "SINCRONIZAÇÃO EM NUVEM", style = MaterialTheme.typography.labelSmall, color = MatrixGreen)
                        }
                        Text(text = "ID: ${book.cloudId.take(8)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Catalogado em: $formattedDate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Excluir Livro?") },
            text = { Text("Tem certeza que deseja remover '${book.title}' da sua biblioteca?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteBook(book)
                        onBack()
                    }
                ) {
                    Text("Excluir", color = NothingRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
