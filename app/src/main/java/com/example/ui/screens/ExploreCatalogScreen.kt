package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.BookEntity
import com.example.data.model.UserTasteProfile
import com.example.network.AiBookRecommenderService
import com.example.network.AiPromptRecommendation
import com.example.network.BookLookupResult
import com.example.network.BookLookupService
import com.example.network.RecommendedBook
import com.example.ui.components.NothingBadge
import com.example.ui.components.NothingButton
import com.example.ui.components.NothingCard
import com.example.ui.components.NothingSectionHeader
import com.example.ui.components.NothingTextField
import com.example.ui.components.SegmentedDotBar
import com.example.ui.theme.CmfOrange
import com.example.ui.theme.MatrixAmber
import com.example.ui.theme.MatrixCyan
import com.example.ui.theme.MatrixGreen
import com.example.ui.theme.NothingRed
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreCatalogScreen(
    tasteProfile: UserTasteProfile,
    recommendations: List<RecommendedBook>,
    isLoadingRecommendations: Boolean,
    onRefreshRecommendations: () -> Unit,
    onAddBookToLibrary: (BookEntity) -> Unit,
    onAddBookToWishlist: (BookEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 0: Feed de Indicações, 1: Busca IA (Livre), 2: Meus Gostos // DNA, 3: Catálogo 5 APIs
    var activeTab by remember { mutableIntStateOf(0) }

    // Live search state for 5 APIs
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<BookLookupResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var lastSearchedQuery by remember { mutableStateOf("") }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Natural Language AI Recommender state
    var aiSearchPrompt by remember { mutableStateOf("") }
    var aiPromptResults by remember { mutableStateOf<List<AiPromptRecommendation>>(emptyList()) }
    var isAiPromptSearching by remember { mutableStateOf(false) }
    var lastExecutedAiPrompt by remember { mutableStateOf("") }

    // Category browsing state
    val categories = remember {
        listOf(
            "Destaques & Bestsellers",
            "Literatura Brasileira",
            "Ficção Científica",
            "Tecnologia & Dev",
            "Filosofia",
            "Clássicos",
            "Fantasia",
            "Negócios & Gestão",
            "Psicologia"
        )
    }
    var selectedCategory by remember { mutableStateOf(categories.first()) }
    var categoryBooks by remember { mutableStateOf<List<BookLookupResult>>(emptyList()) }
    var isLoadingCategory by remember { mutableStateOf(false) }

    val apiSources = remember {
        listOf("TODAS AS APIS", "GOOGLE BOOKS", "BRASIL API (CBL)", "OPEN LIBRARY", "GUTENBERG", "INTERNET ARCHIVE")
    }
    var selectedApiFilter by remember { mutableStateOf("TODAS AS APIS") }

    val displayedSearchResults = remember(searchResults, selectedApiFilter) {
        if (selectedApiFilter == "TODAS AS APIS") searchResults
        else searchResults.filter { it.source.contains(selectedApiFilter.take(8), ignoreCase = true) }
    }

    val displayedCategoryBooks = remember(categoryBooks, selectedApiFilter) {
        if (selectedApiFilter == "TODAS AS APIS") categoryBooks
        else categoryBooks.filter { it.source.contains(selectedApiFilter.take(8), ignoreCase = true) }
    }

    // Selected book for detailed preview sheet
    var previewBook by remember { mutableStateOf<BookLookupResult?>(null) }

    // Load category books when category changes
    LaunchedEffect(selectedCategory) {
        isLoadingCategory = true
        categoryBooks = BookLookupService.getCatalogByCategory(selectedCategory)
        isLoadingCategory = false
    }

    fun executeAiPromptSearch(prompt: String) {
        val q = prompt.trim()
        if (q.isBlank()) return
        aiSearchPrompt = q
        coroutineScope.launch {
            isAiPromptSearching = true
            lastExecutedAiPrompt = q
            val results = AiBookRecommenderService.recommendBooksByPrompt(q)
            aiPromptResults = results
            isAiPromptSearching = false
        }
    }

    fun performSearch(query: String) {
        val q = query.trim()
        if (q.isBlank()) {
            searchResults = emptyList()
            lastSearchedQuery = ""
            return
        }

        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            isSearching = true
            lastSearchedQuery = q
            val results = BookLookupService.searchWithAi(q)
            searchResults = results
            isSearching = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp)
    ) {
        // Section Header
        NothingSectionHeader(
            tag = "EXPLORE.AI",
            title = "Descoberta & Gostos",
            trailingText = "MOTOR IA CMF"
        )

        Spacer(modifier = Modifier.height(6.dp))

        // DOCK DE INDICAÇÃO DE LIVROS
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(26.dp)),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botão 1: FEED DE INDICAÇÕES
                ExploreDockPill(
                    title = "FEED",
                    subtitle = "Indicações",
                    icon = Icons.Filled.DynamicFeed,
                    isSelected = activeTab == 0,
                    badgeText = "HOT",
                    onClick = { activeTab = 0 },
                    testTag = "dock_btn_feed"
                )

                // Botão 2: BUSCA IA (Pesquise qualquer descrição livre)
                ExploreDockPill(
                    title = "BUSCA IA",
                    subtitle = "Recomendar",
                    icon = Icons.Filled.AutoAwesome,
                    isSelected = activeTab == 1,
                    badgeText = "IA",
                    onClick = { activeTab = 1 },
                    testTag = "dock_btn_ai_search"
                )

                // Botão 3: MEUS GOSTOS (DNA LITERÁRIO)
                ExploreDockPill(
                    title = "GOSTOS",
                    subtitle = "Meu DNA",
                    icon = Icons.Filled.Psychology,
                    isSelected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    testTag = "dock_btn_tastes"
                )

                // Botão 4: CATÁLOGO 5 APIS
                ExploreDockPill(
                    title = "5 APIS",
                    subtitle = "Catálogo",
                    icon = Icons.Filled.Public,
                    isSelected = activeTab == 3,
                    onClick = { activeTab = 3 },
                    testTag = "dock_btn_catalog"
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (activeTab) {
            0 -> {
                // ==========================================
                // TAB 0: FEED DE INDICAÇÃO DE LIVROS
                // ==========================================
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Feed Status Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(CmfOrange)
                                )
                                Text(
                                    text = "FEED // CURADORIA PERSONALIZADA",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = CmfOrange
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isLoadingRecommendations) {
                                    CircularProgressIndicator(color = CmfOrange, modifier = Modifier.size(16.dp))
                                } else {
                                    IconButton(
                                        onClick = onRefreshRecommendations,
                                        modifier = Modifier.size(28.dp).testTag("refresh_feed_btn")
                                    ) {
                                        Icon(
                                            Icons.Filled.Refresh,
                                            contentDescription = "Atualizar Feed",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Interactive Banner for AI Natural Language Search
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, CmfOrange.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { activeTab = 1 }
                                .testTag("feed_banner_goto_ai_search")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(CmfOrange.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = CmfOrange, modifier = Modifier.size(20.dp))
                                    }
                                    Column {
                                        Text(
                                            text = "QUER RECOMENDAÇÃO SOB MEDIDA?",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = CmfOrange
                                        )
                                        Text(
                                            text = "Descreva o que quiser: 'livro que o personagem é heroi', 'viagem no tempo'...",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                NothingBadge(text = "BUSCA IA", color = CmfOrange)
                            }
                        }
                    }

                    if (isLoadingRecommendations) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = CmfOrange, modifier = Modifier.size(40.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Curando as melhores indicações para o seu feed...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else if (recommendations.isEmpty()) {
                        item {
                            NothingCard {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Filled.DynamicFeed,
                                        contentDescription = null,
                                        tint = CmfOrange,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Seu Feed Literário Está Pronto!",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Conforme você lê, avalia livros com estrelas ou adiciona novas obras, o algoritmo aprende seus gêneros favoritos e abastece este feed.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    NothingButton(
                                        text = "Gerar Novo Feed",
                                        icon = Icons.Filled.AutoAwesome,
                                        onClick = onRefreshRecommendations,
                                        testTag = "btn_generate_feed_empty"
                                    )
                                }
                            }
                        }
                    } else {
                        // Hero Card - Destaque Principal do Feed
                        val hero = recommendations.first()
                        item {
                            FeedHeroBookCard(
                                book = hero,
                                onCardClick = { previewBook = hero.toLookupResult() },
                                onAddToLibrary = {
                                    onAddBookToLibrary(hero.toEntity(isWishlist = false))
                                    Toast.makeText(context, "'${hero.title}' adicionado à biblioteca!", Toast.LENGTH_SHORT).show()
                                },
                                onAddToWishlist = {
                                    onAddBookToWishlist(hero.toEntity(isWishlist = true))
                                    Toast.makeText(context, "'${hero.title}' salvo na Lista de Desejos!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        // Feed Timeline Posts
                        items(recommendations.drop(1)) { rec ->
                            FeedBookPostCard(
                                book = rec,
                                onCardClick = { previewBook = rec.toLookupResult() },
                                onAddToLibrary = {
                                    onAddBookToLibrary(rec.toEntity(isWishlist = false))
                                    Toast.makeText(context, "'${rec.title}' adicionado à biblioteca!", Toast.LENGTH_SHORT).show()
                                },
                                onAddToWishlist = {
                                    onAddBookToWishlist(rec.toEntity(isWishlist = true))
                                    Toast.makeText(context, "'${rec.title}' salvo na Lista de Desejos!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        // Feed Footer Navigation Shortcut
                        item {
                            NothingCard(backgroundColor = MaterialTheme.colorScheme.surfaceVariant) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "FIM DO FEED DE HOJE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Quer mais descobertas? Peça qualquer tema à IA ou explore as 5 APIs.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(
                                            selected = false,
                                            onClick = { activeTab = 1 },
                                            label = { Text("BUSCA IA", fontSize = 10.sp) },
                                            modifier = Modifier.testTag("feed_footer_goto_ai_search")
                                        )
                                        FilterChip(
                                            selected = false,
                                            onClick = { activeTab = 3 },
                                            label = { Text("5 APIS", fontSize = 10.sp) },
                                            modifier = Modifier.testTag("feed_footer_goto_catalog")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // ==========================================
                // TAB 1: BUSCA CONCEITUAL POR IA (O QUE VOCÊ QUISER)
                // ==========================================
                Column(modifier = Modifier.weight(1f)) {
                    // Search Bar
                    NothingTextField(
                        value = aiSearchPrompt,
                        onValueChange = { aiSearchPrompt = it },
                        label = "Descreva o que quiser para a IA recomendar",
                        placeholder = "Ex: livro que o personagem é heroi, ficção com viagem no tempo...",
                        leadingIcon = Icons.Filled.AutoAwesome,
                        trailingIcon = if (aiSearchPrompt.isNotBlank()) {
                            {
                                IconButton(onClick = {
                                    aiSearchPrompt = ""
                                    aiPromptResults = emptyList()
                                }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Limpar busca")
                                }
                            }
                        } else null,
                        testTag = "input_ai_semantic_search"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SUGESTÕES RÁPIDAS DE ENREDO & TEMA:",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        NothingButton(
                            text = "Recomendar",
                            icon = Icons.Filled.AutoAwesome,
                            onClick = { executeAiPromptSearch(aiSearchPrompt) },
                            modifier = Modifier.height(34.dp),
                            testTag = "btn_trigger_ai_recommend"
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Suggestion Chips (User can tap "Livro que o personagem é herói", etc.)
                    val promptChips = remember {
                        listOf(
                            "Livro que o personagem é herói",
                            "Ficção científica e viagem no tempo",
                            "Mistério com detetive sarcástico",
                            "Romance com reviravolta chocante",
                            "Fantasia com magia épica",
                            "Distopia com inteligência artificial"
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(promptChips) { chipText ->
                            FilterChip(
                                selected = aiSearchPrompt == chipText,
                                onClick = {
                                    aiSearchPrompt = chipText
                                    executeAiPromptSearch(chipText)
                                },
                                label = { Text(chipText, fontSize = 10.5.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CmfOrange,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.testTag("chip_prompt_${chipText.take(10)}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isAiPromptSearching) {
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
                                CircularProgressIndicator(color = CmfOrange, modifier = Modifier.size(44.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "IA ANALISANDO ENREDO & ARQUÉTIPOS",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = CmfOrange
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Consultando IA Gemini e acervo mundial para '$lastExecutedAiPrompt'...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else if (aiPromptResults.isEmpty() && aiSearchPrompt.isBlank()) {
                        // Empty Welcome / Instructions Card
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(bottom = 110.dp)
                        ) {
                            item {
                                NothingCard(
                                    borderColor = CmfOrange.copy(alpha = 0.6f),
                                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Filled.AutoAwesome,
                                            contentDescription = null,
                                            tint = CmfOrange,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "Recomendações Livres por IA",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Você pode descrever qualquer coisa que deseja encontrar em um livro:\n\n" +
                                                    "• 'Livro que o personagem é heroi'\n" +
                                                    "• 'História de amor proibido em uma guerra futura'\n" +
                                                    "• 'Livro curtinho para quem está com ressaca literária'\n" +
                                                    "• 'Suspense claustrofóbico em alto-mar'\n\n" +
                                                    "A IA entende o contexto e explica exatamente o porquê de cada indicação.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        NothingButton(
                                            text = "Testar: 'Livro que o personagem é herói'",
                                            icon = Icons.Filled.AutoAwesome,
                                            onClick = {
                                                aiSearchPrompt = "Livro que o personagem é herói"
                                                executeAiPromptSearch("Livro que o personagem é herói")
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            testTag = "test_prompt_hero"
                                        )
                                    }
                                }
                            }
                        }
                    } else if (aiPromptResults.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Nenhuma recomendação encontrada para '$lastExecutedAiPrompt'. Tente reformular a descrição.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        // Display AI Recommendations with Match Reasons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MatrixGreen))
                                Text(
                                    text = "${aiPromptResults.size} LIVROS INDICADOS PELA IA",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MatrixGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Para '$lastExecutedAiPrompt'",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(bottom = 110.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(aiPromptResults) { rec ->
                                AiPromptRecommendationCard(
                                    recommendation = rec,
                                    onCardClick = { previewBook = rec.toLookupResult() },
                                    onAddToLibrary = {
                                        onAddBookToLibrary(rec.toEntity(isWishlist = false))
                                        Toast.makeText(context, "'${rec.title}' adicionado à biblioteca!", Toast.LENGTH_SHORT).show()
                                    },
                                    onAddToWishlist = {
                                        onAddBookToWishlist(rec.toEntity(isWishlist = true))
                                        Toast.makeText(context, "'${rec.title}' salvo na Lista de Desejos!", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            2 -> {
                // ==========================================
                // TAB 2: MEUS GOSTOS // PERFIL DNA
                // ==========================================
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Item 1: Taste Profile DNA Card (Telemetry Console Style)
                    item {
                        NothingCard(
                            borderColor = CmfOrange,
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant
                        ) {
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Filled.Psychology, contentDescription = null, tint = CmfOrange, modifier = Modifier.size(20.dp))
                                        Text(
                                            text = "DNA LITERÁRIO // CALIBRADO POR IA",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CmfOrange
                                        )
                                    }

                                    IconButton(
                                        onClick = onRefreshRecommendations,
                                        modifier = Modifier.size(32.dp).testTag("refresh_taste_btn")
                                    ) {
                                        Icon(
                                            Icons.Filled.Refresh,
                                            contentDescription = "Recalibrar Gostos",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = tasteProfile.summaryText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Breakdown of Top Genres by Quantity and Star Rating
                                if (tasteProfile.topGenres.isNotEmpty()) {
                                    Text(
                                        text = "AFINIDADE POR GÊNERO & AVALIAÇÕES DADAS:",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    val maxScore = tasteProfile.topGenres.maxOfOrNull { it.weightedScore } ?: 1f
                                    tasteProfile.topGenres.take(4).forEach { pref ->
                                        val ratio = if (maxScore > 0) (pref.weightedScore / maxScore).coerceIn(0.15f, 1f) else 0.5f
                                        Column(modifier = Modifier.padding(bottom = 8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = pref.genre.uppercase(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = "${pref.bookCount} livros • ",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Icon(Icons.Filled.Star, contentDescription = null, tint = MatrixAmber, modifier = Modifier.size(12.dp))
                                                    Text(
                                                        text = " ${String.format("%.1f", pref.averageRating)}★",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MatrixAmber
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            SegmentedDotBar(
                                                progress = ratio,
                                                totalSegments = 20,
                                                activeColor = if (pref.averageRating >= 4.0f) MatrixGreen else CmfOrange,
                                                height = 6.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Direct shortcut to Feed inside the Taste section
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MatrixGreen))
                                Text(
                                    text = "LIVROS RECOMENDADOS PARA O SEU PERFIL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MatrixGreen
                                )
                            }

                            NothingButton(
                                text = "Abrir Feed",
                                icon = Icons.Filled.DynamicFeed,
                                onClick = { activeTab = 0 },
                                isPrimary = false,
                                modifier = Modifier.height(34.dp),
                                testTag = "open_feed_from_tastes_btn"
                            )
                        }
                    }

                    if (isLoadingRecommendations) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = CmfOrange, modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Analisando seus gêneros e avaliações por estrelas...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else if (recommendations.isEmpty()) {
                        item {
                            NothingCard {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Adicione livros e dê avaliações por estrelas",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "O sistema aprende instantaneamente quais gêneros você mais lê e suas obras favoritas (4-5★) para recomendar títulos perfeitos.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    NothingButton(
                                        text = "Gerar Recomendações",
                                        icon = Icons.Filled.AutoAwesome,
                                        onClick = onRefreshRecommendations,
                                        testTag = "btn_generate_recs_empty"
                                    )
                                }
                            }
                        }
                    } else {
                        items(recommendations) { rec ->
                            RecommendedBookCard(
                                book = rec,
                                onCardClick = { previewBook = rec.toLookupResult() },
                                onAddToLibrary = {
                                    onAddBookToLibrary(rec.toEntity(isWishlist = false))
                                    Toast.makeText(context, "'${rec.title}' adicionado à biblioteca!", Toast.LENGTH_SHORT).show()
                                },
                                onAddToWishlist = {
                                    onAddBookToWishlist(rec.toEntity(isWishlist = true))
                                    Toast.makeText(context, "'${rec.title}' adicionado aos desejos!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }

            else -> {
                // ==========================================
                // TAB 3: CATÁLOGO MUNDIAL & 5 APIS
                // ==========================================
                Column(modifier = Modifier.weight(1f)) {
                    // Search Input Field
                    NothingTextField(
                        value = searchQuery,
                        onValueChange = { newText ->
                            searchQuery = newText
                            searchJob?.cancel()
                            if (newText.trim().length >= 3) {
                                searchJob = coroutineScope.launch {
                                    delay(600)
                                    performSearch(newText)
                                }
                            } else if (newText.isBlank()) {
                                searchResults = emptyList()
                                lastSearchedQuery = ""
                            }
                        },
                        label = "Pesquisar nas 5 APIs (Google Books, BrasilAPI, OpenLibrary...)",
                        placeholder = "Ex: Clean Code, 1984, Dom Casmurro, 978...",
                        leadingIcon = Icons.Filled.Search,
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    searchResults = emptyList()
                                    lastSearchedQuery = ""
                                }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Limpar busca")
                                }
                            }
                        } else null,
                        testTag = "input_explore_search"
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Multi-API Telemetry Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
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
                                text = "5 APIS INTEGRADAS",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Bold,
                                color = MatrixGreen
                            )
                        }
                        Text(
                            text = "Google • BrasilAPI • OL • Guten • IA",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // API Source Filter Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(apiSources) { source ->
                            val isSelected = selectedApiFilter == source
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedApiFilter = source },
                                label = { Text(source, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MatrixCyan,
                                    selectedLabelColor = Color.Black
                                ),
                                modifier = Modifier.testTag("filter_api_$source")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Search Results or Category Browser
                    if (searchQuery.isNotBlank() || isSearching) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (isSearching) CmfOrange else MatrixGreen))
                                Text(
                                    text = if (isSearching) "CONSULTANDO 5 APIS DE LIVROS..." else "LIVROS ENCONTRADOS NAS APIS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSearching) CmfOrange else MatrixGreen
                                )
                            }

                            if (displayedSearchResults.isNotEmpty()) {
                                Text(
                                    text = "${displayedSearchResults.size} TÍTULOS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isSearching) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = CmfOrange, modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Buscando em tempo real em 5 APIs para '$lastSearchedQuery'...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else if (displayedSearchResults.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Nenhum livro retornado pelo filtro de API selecionado.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(bottom = 110.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(displayedSearchResults) { book ->
                                    CatalogBookCard(
                                        book = book,
                                        onCardClick = { previewBook = book },
                                        onAddToLibrary = {
                                            val entity = bookToEntity(book, isWishlist = false)
                                            onAddBookToLibrary(entity)
                                            Toast.makeText(context, "'${book.title}' adicionado à biblioteca!", Toast.LENGTH_SHORT).show()
                                        },
                                        onAddToWishlist = {
                                            val entity = bookToEntity(book, isWishlist = true)
                                            onAddBookToWishlist(entity)
                                            Toast.makeText(context, "'${book.title}' adicionado aos desejos!", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        // Category pills
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            items(categories) { cat ->
                                val isSelected = selectedCategory == cat
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CmfOrange,
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("cat_chip_$cat")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (isLoadingCategory) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = CmfOrange, modifier = Modifier.size(36.dp))
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(bottom = 110.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(displayedCategoryBooks) { book ->
                                    CatalogBookCard(
                                        book = book,
                                        onCardClick = { previewBook = book },
                                        onAddToLibrary = {
                                            val entity = bookToEntity(book, isWishlist = false)
                                            onAddBookToLibrary(entity)
                                            Toast.makeText(context, "'${book.title}' adicionado à biblioteca!", Toast.LENGTH_SHORT).show()
                                        },
                                        onAddToWishlist = {
                                            val entity = bookToEntity(book, isWishlist = true)
                                            onAddBookToWishlist(entity)
                                            Toast.makeText(context, "'${book.title}' adicionado aos desejos!", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet for Book Details Preview
    if (previewBook != null) {
        val b = previewBook!!
        ModalBottomSheet(
            onDismissRequest = { previewBook = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val badgeColor = when {
                        b.source.contains("BRASIL", ignoreCase = true) -> MatrixGreen
                        b.source.contains("GOOGLE", ignoreCase = true) -> MatrixCyan
                        b.source.contains("OPEN", ignoreCase = true) -> MatrixAmber
                        b.source.contains("GUTEN", ignoreCase = true) -> CmfOrange
                        b.source.contains("ARCHIVE", ignoreCase = true) -> Color(0xFF90A4AE)
                        else -> NothingRed
                    }
                    NothingBadge(
                        text = "FONTE: ${b.source}",
                        color = badgeColor,
                        borderColor = badgeColor.copy(alpha = 0.5f)
                    )
                    IconButton(onClick = { previewBook = null }) {
                        Icon(Icons.Filled.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(150.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (b.coverUrl.isNotBlank()) {
                            AsyncImage(
                                model = b.coverUrl,
                                contentDescription = b.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(b.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(b.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        NothingBadge(text = b.genre, color = MaterialTheme.colorScheme.primary)
                        if (b.isbn.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("ISBN: ${b.isbn}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (b.pageCount > 0) {
                            Text("${b.pageCount} páginas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (b.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Sinopse", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(b.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NothingButton(
                        text = "Adicionar à Biblioteca",
                        icon = Icons.Filled.LibraryAdd,
                        onClick = {
                            val entity = bookToEntity(b, isWishlist = false)
                            onAddBookToLibrary(entity)
                            Toast.makeText(context, "'${b.title}' adicionado à biblioteca!", Toast.LENGTH_SHORT).show()
                            previewBook = null
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "preview_add_to_library"
                    )

                    NothingButton(
                        text = "Salvar nos Desejos",
                        icon = Icons.Filled.BookmarkAdd,
                        isPrimary = false,
                        onClick = {
                            val entity = bookToEntity(b, isWishlist = true)
                            onAddBookToWishlist(entity)
                            Toast.makeText(context, "'${b.title}' salvo na Lista de Desejos!", Toast.LENGTH_SHORT).show()
                            previewBook = null
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "preview_add_to_wishlist"
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.ExploreDockPill(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    badgeText: String? = null,
    onClick: () -> Unit,
    testTag: String
) {
    val activeBg = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
    val activeColor = if (isSelected) CmfOrange else MaterialTheme.colorScheme.onSurfaceVariant
    val activeBorder = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, CmfOrange.copy(alpha = 0.6f)) else null

    Surface(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(22.dp),
        color = activeBg,
        border = activeBorder,
        tonalElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = activeColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = activeColor
                )
                if (badgeText != null && !isSelected) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CmfOrange)
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp),
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun AiPromptRecommendationCard(
    recommendation: AiPromptRecommendation,
    onCardClick: () -> Unit,
    onAddToLibrary: () -> Unit,
    onAddToWishlist: () -> Unit
) {
    NothingCard(
        borderColor = CmfOrange.copy(alpha = 0.5f),
        onClick = onCardClick,
        modifier = Modifier.fillMaxWidth().testTag("ai_prompt_rec_${recommendation.title.take(8)}")
    ) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = CmfOrange,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "RECOMENDAÇÃO IA // ${recommendation.genre.uppercase()}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.Bold,
                        color = CmfOrange
                    )
                }
                NothingBadge(
                    text = "${recommendation.matchPercent}% MATCH",
                    color = MatrixGreen,
                    borderColor = MatrixGreen.copy(alpha = 0.4f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Reason Callout Pill
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, CmfOrange.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 3.dp, height = 24.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(CmfOrange)
                    )
                    Column {
                        Text(
                            text = "POR QUE A IA ESCOLHEU ESTE LIVRO:",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                            fontWeight = FontWeight.Bold,
                            color = CmfOrange
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = recommendation.matchReason,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(76.dp)
                        .height(112.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (recommendation.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = recommendation.coverUrl,
                            contentDescription = recommendation.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = CmfOrange,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recommendation.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2
                    )
                    Text(
                        text = recommendation.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (recommendation.pageCount > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${recommendation.pageCount} páginas",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = recommendation.description,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NothingButton(
                    text = "+ Biblioteca",
                    icon = Icons.Filled.LibraryAdd,
                    onClick = onAddToLibrary,
                    modifier = Modifier.weight(1f).height(38.dp),
                    testTag = "ai_rec_add_library_${recommendation.title.take(6)}"
                )
                NothingButton(
                    text = "+ Desejos",
                    icon = Icons.Filled.BookmarkAdd,
                    isPrimary = false,
                    onClick = onAddToWishlist,
                    modifier = Modifier.weight(1f).height(38.dp),
                    testTag = "ai_rec_add_wishlist_${recommendation.title.take(6)}"
                )
            }
        }
    }
}

@Composable
private fun FeedHeroBookCard(
    book: RecommendedBook,
    onCardClick: () -> Unit,
    onAddToLibrary: () -> Unit,
    onAddToWishlist: () -> Unit
) {
    NothingCard(
        borderColor = CmfOrange,
        backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
        onClick = onCardClick,
        modifier = Modifier.fillMaxWidth().testTag("feed_hero_card")
    ) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(CmfOrange)
                    )
                    Text(
                        text = "★ DESTAQUE DO FEED // TOP MATCH",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.Bold,
                        color = CmfOrange
                    )
                }

                NothingBadge(
                    text = "${book.matchScorePercent}% AFINIDADE",
                    color = MatrixGreen,
                    borderColor = MatrixGreen.copy(alpha = 0.4f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(84.dp)
                        .height(124.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (book.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = book.coverUrl,
                            contentDescription = book.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = CmfOrange, modifier = Modifier.size(32.dp))
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.genre.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = CmfOrange,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "\"${book.recommendationReason}\"",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NothingButton(
                    text = "+ Biblioteca",
                    icon = Icons.Filled.LibraryAdd,
                    onClick = onAddToLibrary,
                    modifier = Modifier.weight(1f).height(38.dp),
                    testTag = "feed_hero_add_library"
                )
                NothingButton(
                    text = "+ Desejos",
                    icon = Icons.Filled.BookmarkAdd,
                    isPrimary = false,
                    onClick = onAddToWishlist,
                    modifier = Modifier.weight(1f).height(38.dp),
                    testTag = "feed_hero_add_wishlist"
                )
            }
        }
    }
}

@Composable
private fun FeedBookPostCard(
    book: RecommendedBook,
    onCardClick: () -> Unit,
    onAddToLibrary: () -> Unit,
    onAddToWishlist: () -> Unit
) {
    NothingCard(
        onClick = onCardClick,
        modifier = Modifier.fillMaxWidth().testTag("feed_card_${book.title.take(10)}")
    ) {
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
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MatrixCyan, modifier = Modifier.size(13.dp))
                    Text(
                        text = "INDICAÇÃO // ${book.genre.uppercase()}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MatrixCyan
                    )
                }
                Text(
                    text = "${book.matchScorePercent}% Match",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = MatrixGreen,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(106.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (book.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = book.coverUrl,
                            contentDescription = book.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
                    Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = book.recommendationReason,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NothingButton(
                    text = "+ Biblioteca",
                    icon = Icons.Filled.LibraryAdd,
                    onClick = onAddToLibrary,
                    modifier = Modifier.weight(1f).height(36.dp),
                    testTag = "feed_post_add_library"
                )
                NothingButton(
                    text = "+ Desejos",
                    icon = Icons.Filled.BookmarkAdd,
                    isPrimary = false,
                    onClick = onAddToWishlist,
                    modifier = Modifier.weight(1f).height(36.dp),
                    testTag = "feed_post_add_wishlist"
                )
            }
        }
    }
}

@Composable
private fun RecommendedBookCard(
    book: RecommendedBook,
    onCardClick: () -> Unit,
    onAddToLibrary: () -> Unit,
    onAddToWishlist: () -> Unit
) {
    NothingCard(
        onClick = onCardClick,
        modifier = Modifier.fillMaxWidth().testTag("rec_card_${book.isbn}")
    ) {
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
                    Text(
                        text = "MOTIVO: ${book.genre.uppercase()}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MatrixGreen
                    )
                }

                Text(
                    text = "${book.matchScorePercent}% Match",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MatrixGreen,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(68.dp)
                        .height(100.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (book.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = book.coverUrl,
                            contentDescription = book.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
                    Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = book.recommendationReason,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NothingButton(
                    text = "+ Biblioteca",
                    icon = Icons.Filled.LibraryAdd,
                    onClick = onAddToLibrary,
                    modifier = Modifier.weight(1f).height(36.dp),
                    testTag = "rec_add_library_${book.isbn}"
                )
                NothingButton(
                    text = "+ Desejos",
                    icon = Icons.Filled.BookmarkAdd,
                    isPrimary = false,
                    onClick = onAddToWishlist,
                    modifier = Modifier.weight(1f).height(36.dp),
                    testTag = "rec_add_wishlist_${book.isbn}"
                )
            }
        }
    }
}

@Composable
private fun CatalogBookCard(
    book: BookLookupResult,
    onCardClick: () -> Unit,
    onAddToLibrary: () -> Unit,
    onAddToWishlist: () -> Unit
) {
    NothingCard(
        onClick = onCardClick,
        modifier = Modifier.fillMaxWidth().testTag("catalog_book_${book.isbn}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(68.dp)
                        .height(100.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (book.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = book.coverUrl,
                            contentDescription = book.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = book.genre.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                        val badgeColor = when {
                            book.source.contains("BRASIL", ignoreCase = true) -> MatrixGreen
                            book.source.contains("GOOGLE", ignoreCase = true) -> MatrixCyan
                            book.source.contains("OPEN", ignoreCase = true) -> MatrixAmber
                            book.source.contains("GUTEN", ignoreCase = true) -> CmfOrange
                            book.source.contains("ARCHIVE", ignoreCase = true) -> Color(0xFF90A4AE)
                            else -> NothingRed
                        }
                        NothingBadge(
                            text = book.source,
                            color = badgeColor,
                            borderColor = badgeColor.copy(alpha = 0.4f)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )

                    if (book.isbn.isNotBlank()) {
                        Text(
                            text = "ISBN: ${book.isbn}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NothingButton(
                    text = "+ Biblioteca",
                    icon = Icons.Filled.LibraryAdd,
                    onClick = onAddToLibrary,
                    modifier = Modifier.weight(1f).height(38.dp),
                    testTag = "card_add_library_${book.isbn}"
                )

                NothingButton(
                    text = "+ Desejos",
                    icon = Icons.Filled.BookmarkAdd,
                    isPrimary = false,
                    onClick = onAddToWishlist,
                    modifier = Modifier.weight(1f).height(38.dp),
                    testTag = "card_add_wishlist_${book.isbn}"
                )
            }
        }
    }
}

private fun AiPromptRecommendation.toLookupResult(): BookLookupResult {
    return BookLookupResult(
        title = title,
        author = author,
        isbn = isbn,
        coverUrl = coverUrl,
        pageCount = pageCount,
        genre = genre,
        description = "$description\n\nPor que a IA recomendou: $matchReason",
        source = "IA RECOMENDAÇÃO"
    )
}

private fun AiPromptRecommendation.toEntity(isWishlist: Boolean): BookEntity {
    return BookEntity(
        title = title,
        author = author,
        isbn = isbn,
        coverUri = coverUrl,
        status = if (isWishlist) "WISHLIST" else "OWNED",
        isWishlist = isWishlist,
        genre = genre,
        pageCount = pageCount,
        notes = "Recomendado por IA: $matchReason",
        isSynced = true
    )
}

private fun RecommendedBook.toLookupResult(): BookLookupResult {
    return BookLookupResult(
        title = title,
        author = author,
        isbn = isbn,
        coverUrl = coverUrl,
        pageCount = pageCount,
        genre = genre,
        description = description,
        source = "IA // MOTOR DE GOSTO"
    )
}

private fun RecommendedBook.toEntity(isWishlist: Boolean): BookEntity {
    return BookEntity(
        title = title,
        author = author,
        isbn = isbn,
        coverUri = coverUrl,
        status = if (isWishlist) "WISHLIST" else "OWNED",
        isWishlist = isWishlist,
        genre = genre,
        pageCount = pageCount,
        notes = recommendationReason,
        isSynced = true
    )
}

private fun bookToEntity(lookup: BookLookupResult, isWishlist: Boolean): BookEntity {
    return BookEntity(
        title = lookup.title,
        author = lookup.author,
        isbn = lookup.isbn,
        coverUri = lookup.coverUrl,
        status = if (isWishlist) "WISHLIST" else "OWNED",
        isWishlist = isWishlist,
        genre = lookup.genre,
        pageCount = lookup.pageCount,
        notes = lookup.description,
        isSynced = true
    )
}
