package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.example.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.BookEntity
import com.example.data.model.UserTasteProfile
import com.example.sync.CloudSyncInfo
import com.example.ui.components.NothingBadge
import com.example.ui.components.NothingButton
import com.example.ui.components.NothingCard
import com.example.ui.components.NothingSectionHeader
import com.example.ui.components.NothingTextField
import com.example.ui.components.SegmentedDotBar
import com.example.ui.theme.CmfOrange
import com.example.ui.theme.MatrixAmber
import com.example.ui.theme.MatrixGreen
import com.example.ui.theme.NothingRed

@Composable
fun HomeScreen(
    libraryBooks: List<BookEntity>,
    syncInfo: CloudSyncInfo,
    tasteProfile: UserTasteProfile,
    onBookClick: (BookEntity) -> Unit,
    onOpenScanner: () -> Unit,
    onOpenAdd: () -> Unit,
    onOpenCloudSync: () -> Unit,
    onOpenExplore: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("TODOS") } // TODOS, LENDO, CONCLUÍDOS, QUERO LER
    var isGridView by remember { mutableStateOf(false) }

    val filteredBooks = remember(libraryBooks, searchQuery, selectedFilter) {
        var list = libraryBooks
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) ||
                it.author.lowercase().contains(q) ||
                it.isbn.lowercase().contains(q) ||
                it.genre.lowercase().contains(q)
            }
        }
        when (selectedFilter) {
            "LENDO" -> list.filter { it.status == "READING" }
            "CONCLUÍDOS" -> list.filter { it.status == "COMPLETED" }
            "QUERO LER" -> list.filter { it.status == "OWNED" }
            else -> list
        }
    }

    // Reading stats calculation
    val readingCount = libraryBooks.count { it.status == "READING" }
    val completedCount = libraryBooks.count { it.status == "COMPLETED" }
    val totalPagesRead = libraryBooks.sumOf { it.currentPage }
    val totalPagesGoal = libraryBooks.sumOf { it.pageCount }.coerceAtLeast(1)
    val globalProgress = (totalPagesRead.toFloat() / totalPagesGoal.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp)
    ) {
        // CMF Nothing Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.glyph_book_icon_2d_1790767468377),
                    contentDescription = "GlyphBook Ícone",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .border(1.dp, CmfOrange.copy(alpha = 0.5f), RoundedCornerShape(9.dp))
                )
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(NothingRed)
                        )
                        Text(
                            text = "GLYPH // BOOKS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = "SCANNER & BIBLIOTECA",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cloud Status Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onOpenCloudSync)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("top_cloud_pill"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MatrixGreen)
                    )
                    Text(
                        text = syncInfo.vaultId,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Add button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(CmfOrange)
                        .clickable(onClick = onOpenAdd)
                        .testTag("top_add_manual_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Adicionar Livro",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // CMF Telemetry Console Card (Stats widget inspired by user image 2 & 3)
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
                        text = "METAS DE LEITURA // HUD",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$totalPagesRead / $totalPagesGoal PÁGINAS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MatrixGreen
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                SegmentedDotBar(
                    progress = globalProgress,
                    totalSegments = 22,
                    activeColor = MatrixGreen,
                    height = 8.dp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Stats Counters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatPill(number = "${libraryBooks.size}", label = "LIVROS FÍSICOS")
                    StatPill(number = "$readingCount", label = "LENDO AGORA", color = CmfOrange)
                    StatPill(number = "$completedCount", label = "CONCLUÍDOS", color = MatrixGreen)
                }
            }
        }

        // Literary Taste Insight Banner
        if (tasteProfile.topGenres.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            NothingCard(
                onClick = onOpenExplore,
                borderColor = CmfOrange.copy(alpha = 0.5f),
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = CmfOrange, modifier = Modifier.size(16.dp))
                        Column {
                            Text(
                                text = "SEU GOSTO LITERÁRIO:",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = CmfOrange
                            )
                            val firstGenre = tasteProfile.topGenres.first()
                            Text(
                                text = "${firstGenre.genre} (${firstGenre.bookCount} livros • ${String.format("%.1f", firstGenre.averageRating)}★)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                    NothingBadge(text = "VER RECOMENDAÇÕES", color = CmfOrange, borderColor = CmfOrange.copy(alpha = 0.5f))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Bar
        NothingTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = "Buscar na biblioteca",
            placeholder = "Título, autor, gênero ou ISBN...",
            leadingIcon = Icons.Filled.Search,
            testTag = "input_search_library"
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filters and View Toggle Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("TODOS", "LENDO", "CONCLUÍDOS").forEach { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CmfOrange,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("filter_$filter")
                    )
                }
            }

            IconButton(
                onClick = { isGridView = !isGridView },
                modifier = Modifier.size(36.dp).testTag("toggle_view_mode")
            ) {
                Icon(
                    imageVector = if (isGridView) Icons.Filled.ViewList else Icons.Filled.GridView,
                    contentDescription = "Alternar Visualização",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Books Display List or Grid
        if (filteredBooks.isEmpty()) {
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
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.MenuBook,
                            contentDescription = null,
                            tint = CmfOrange,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (searchQuery.isNotBlank()) "NENHUM LIVRO ENCONTRADO" else "BIBLIOTECA VAZIA",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (searchQuery.isNotBlank()) "Tente buscar com outros termos."
                        else "Use a câmera para escanear a capa de qualquer livro físico e catalogar automaticamente.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NothingButton(
                            text = "Escanear Livro",
                            icon = Icons.Filled.PhotoCamera,
                            onClick = onOpenScanner,
                            testTag = "empty_library_scan_btn"
                        )
                    }
                }
            }
        } else if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredBooks, key = { it.id }) { book ->
                    BookGridCard(book = book, onClick = { onBookClick(book) })
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredBooks, key = { it.id }) { book ->
                    BookListCard(book = book, onClick = { onBookClick(book) })
                }
            }
        }
    }
}

@Composable
private fun StatPill(
    number: String,
    label: String,
    color: Color = Color.Unspecified
) {
    Column {
        Text(
            text = number,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BookListCard(
    book: BookEntity,
    onClick: () -> Unit
) {
    NothingCard(
        onClick = onClick,
        borderColor = MaterialTheme.colorScheme.outline
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover Image
            Box(
                modifier = Modifier
                    .width(62.dp)
                    .height(90.dp)
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
                        Icons.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Info & Reading Progress
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = book.genre.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (book.rating > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = MatrixAmber, modifier = Modifier.size(12.dp))
                            Text(text = " ${book.rating}", style = MaterialTheme.typography.labelSmall, color = MatrixAmber)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(8.dp))

                val total = if (book.pageCount > 0) book.pageCount else 100
                val pct = ((book.currentPage.toFloat() / total.toFloat()) * 100).toInt().coerceIn(0, 100)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (book.status == "COMPLETED") "CONCLUÍDO" else "${book.currentPage}/$total pág",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (book.status == "COMPLETED") MatrixGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (book.status == "COMPLETED") MatrixGreen else CmfOrange
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                SegmentedDotBar(
                    progress = book.readingProgressPercent,
                    totalSegments = 16,
                    activeColor = if (book.status == "COMPLETED") MatrixGreen else CmfOrange,
                    height = 5.dp
                )
            }
        }
    }
}

@Composable
private fun BookGridCard(
    book: BookEntity,
    onClick: () -> Unit
) {
    NothingCard(
        onClick = onClick,
        borderColor = MaterialTheme.colorScheme.outline
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
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
                        Icons.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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

            Spacer(modifier = Modifier.height(6.dp))

            SegmentedDotBar(
                progress = book.readingProgressPercent,
                totalSegments = 10,
                activeColor = if (book.status == "COMPLETED") MatrixGreen else CmfOrange,
                height = 4.dp
            )
        }
    }
}
