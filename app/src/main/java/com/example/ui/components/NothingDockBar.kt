package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CmfOrange
import com.example.ui.theme.NothingRed

enum class ScreenDestination {
    LIBRARY,
    STUDY,
    EXPLORE,
    WISHLIST,
    CLOUD_SYNC
}

@Composable
fun NothingDockBar(
    currentScreen: ScreenDestination,
    onNavigate: (ScreenDestination) -> Unit,
    onOpenScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating Dock Container (inspired by Image 1 Dock Style #11 and Detached #03)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(16.dp, RoundedCornerShape(34.dp), spotColor = Color.Black.copy(alpha = 0.35f))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(34.dp)),
            shape = RoundedCornerShape(34.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Item 1: Minha Biblioteca
                DockNavItem(
                    title = "BIBLIO",
                    icon = if (currentScreen == ScreenDestination.LIBRARY) Icons.Filled.LocalLibrary else Icons.Outlined.LocalLibrary,
                    isSelected = currentScreen == ScreenDestination.LIBRARY,
                    onClick = { onNavigate(ScreenDestination.LIBRARY) },
                    testTag = "dock_library_tab"
                )

                // Item 2: Estudar Online // Discussões & Chats (Design da foto)
                DockNavItem(
                    title = "ESTUDAR",
                    icon = if (currentScreen == ScreenDestination.STUDY) Icons.Filled.Forum else Icons.Outlined.Forum,
                    isSelected = currentScreen == ScreenDestination.STUDY,
                    onClick = { onNavigate(ScreenDestination.STUDY) },
                    testTag = "dock_study_tab"
                )

                // Center Action Button: Scanner de Livro Físico (Nothing Vermilion Orb)
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(CmfOrange)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenScanner
                        )
                        .testTag("dock_camera_fab"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = "Escanear Livro Físico com a Câmera",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }

                // Item 4: Explorar Catálogo Mundial / Busca IA & APIs
                DockNavItem(
                    title = "EXPLORAR",
                    icon = if (currentScreen == ScreenDestination.EXPLORE) Icons.Filled.Public else Icons.Outlined.Public,
                    isSelected = currentScreen == ScreenDestination.EXPLORE,
                    onClick = { onNavigate(ScreenDestination.EXPLORE) },
                    testTag = "dock_explore_tab"
                )

                // Item 5: Nuvem / Sincronização por E-mail
                DockNavItem(
                    title = "NUVEM",
                    icon = if (currentScreen == ScreenDestination.CLOUD_SYNC) Icons.Filled.CloudDone else Icons.Outlined.CloudQueue,
                    isSelected = currentScreen == ScreenDestination.CLOUD_SYNC,
                    onClick = { onNavigate(ScreenDestination.CLOUD_SYNC) },
                    testTag = "dock_cloud_tab"
                )
            }
        }
    }
}

@Composable
private fun DockNavItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val activeColor = CmfOrange
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isSelected) activeColor else inactiveColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
            color = if (isSelected) activeColor else inactiveColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(width = 12.dp, height = 2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(NothingRed)
            )
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
