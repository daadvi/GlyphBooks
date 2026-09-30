package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BookEntity
import com.example.sync.CloudSyncInfo
import com.example.sync.CloudSyncManager
import com.example.sync.SyncStatus
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncScreen(
    cloudSyncManager: CloudSyncManager,
    allBooks: List<BookEntity>,
    onClearAllData: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val syncInfo by cloudSyncManager.syncInfo.collectAsState()
    val syncStatus by cloudSyncManager.syncStatus.collectAsState()

    var inputEmail by remember(syncInfo.userEmail) { mutableStateOf(syncInfo.userEmail.ifBlank { "jhuliavieira@gmail.com" }) }
    var inputVaultId by remember { mutableStateOf("") }
    var importJsonText by remember { mutableStateOf("") }
    var showImportSection by remember { mutableStateOf(false) }
    var showClearDataConfirm by remember { mutableStateOf(false) }

    val formattedSyncTime = remember(syncInfo.lastSyncTime) {
        if (syncInfo.lastSyncTime > 0) {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            sdf.format(Date(syncInfo.lastSyncTime))
        } else {
            "Nenhuma sincronização recente"
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sync_spin"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 110.dp)
    ) {
        // Futuristic Title Header
        NothingSectionHeader(
            tag = "SYS.SYNC",
            title = "Nuvem & Sincronização",
            trailingText = "EMAIL VAULT v2.0"
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Card 1: SINCRONIZAÇÃO COM E-MAIL (NOVO)
        NothingCard(
            borderColor = CmfOrange,
            backgroundColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
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
                        Icon(Icons.Filled.Mail, contentDescription = null, tint = CmfOrange, modifier = Modifier.size(20.dp))
                        Text(
                            text = "SINCRONIZAÇÃO COM E-MAIL",
                            style = MaterialTheme.typography.labelSmall,
                            color = CmfOrange
                        )
                    }

                    if (syncInfo.userEmail.isNotBlank()) {
                        NothingBadge(
                            text = "CONECTADO",
                            color = MatrixGreen,
                            borderColor = MatrixGreen.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Vincule seu e-mail para manter seus livros físicos salvos na nuvem e acessá-los em qualquer celular ou tablet:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                NothingTextField(
                    value = inputEmail,
                    onValueChange = { inputEmail = it },
                    label = "Endereço de E-mail",
                    placeholder = "seuemail@exemplo.com",
                    leadingIcon = Icons.Filled.Mail,
                    testTag = "input_sync_email"
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Botão Sincronizar com E-mail
                    NothingButton(
                        text = "Sincronizar Agora",
                        icon = Icons.Filled.CloudUpload,
                        onClick = {
                            if (inputEmail.isNotBlank()) {
                                coroutineScope.launch {
                                    cloudSyncManager.syncWithEmail(inputEmail, allBooks)
                                }
                            } else {
                                Toast.makeText(context, "Digite um e-mail válido", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1.2f),
                        testTag = "btn_sync_email"
                    )

                    // Botão Restaurar do E-mail
                    NothingButton(
                        text = "Restaurar",
                        icon = Icons.Filled.CloudDownload,
                        isPrimary = false,
                        onClick = {
                            if (inputEmail.isNotBlank()) {
                                coroutineScope.launch {
                                    val count = cloudSyncManager.restoreFromEmail(inputEmail)
                                    if (count > 0) {
                                        Toast.makeText(context, "$count livros recuperados do e-mail $inputEmail!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Digite um e-mail para restaurar", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(0.9f),
                        testTag = "btn_restore_email"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Card 2: Status Geral da Conexão & Sincronização
        NothingCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "STATUS DA NUVEM",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = syncInfo.cloudStatus,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    val isSyncing = syncStatus is SyncStatus.Syncing
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                if (syncInfo.userEmail.isNotBlank()) {
                                    cloudSyncManager.syncWithEmail(syncInfo.userEmail, allBooks)
                                } else {
                                    cloudSyncManager.performCloudSync()
                                }
                            }
                        },
                        enabled = !isSyncing,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(CmfOrange)
                            .testTag("trigger_sync_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Sync,
                            contentDescription = "Sincronizar",
                            tint = Color.White,
                            modifier = Modifier
                                .size(24.dp)
                                .then(if (isSyncing) Modifier.rotate(rotation) else Modifier)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Última sincronização: $formattedSyncTime",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (syncStatus is SyncStatus.Syncing) {
                    val status = syncStatus as SyncStatus.Syncing
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = status.stage,
                        style = MaterialTheme.typography.labelSmall,
                        color = CmfOrange
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SegmentedDotBar(
                        progress = status.progress,
                        totalSegments = 20,
                        activeColor = CmfOrange
                    )
                }

                if (syncStatus is SyncStatus.Success) {
                    val success = syncStatus as SyncStatus.Success
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MatrixGreen, modifier = Modifier.size(16.dp))
                        Text(
                            text = success.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MatrixGreen
                        )
                    }
                }

                if (syncStatus is SyncStatus.Error) {
                    val error = syncStatus as SyncStatus.Error
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = error.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NothingRed
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Card 3: Chave do Cofre (Vault ID)
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
                        text = "CHAVE DE IDENTIFICAÇÃO DO COFRE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Vault ID", syncInfo.vaultId)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Chave copiada: ${syncInfo.vaultId}", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar Chave", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }

                Text(
                    text = syncInfo.vaultId,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        letterSpacing = 1.5.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Card 4: Sincronização Automática
        NothingCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sincronização Automática",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Atualizar cofre em segundo plano ao adicionar ou avaliar livros",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = syncInfo.autoSyncEnabled,
                    onCheckedChange = { cloudSyncManager.setAutoSync(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CmfOrange
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Card 5: Gerenciamento de Dados & Opção de Zerar
        NothingCard(borderColor = NothingRed.copy(alpha = 0.5f)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = NothingRed, modifier = Modifier.size(20.dp))
                    Text(
                        text = "GERENCIAMENTO DE DADOS LOCAIS",
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingRed
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Atualmente você possui ${allBooks.size} livros no dispositivo. Você pode zerar os dados a qualquer momento para recomeçar do zero.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                NothingButton(
                    text = "Zerar Todos os Dados do App",
                    isPrimary = false,
                    onClick = { showClearDataConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "btn_clear_all_data"
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Card 6: Backup JSON
        NothingCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "BACKUP & RESTAURAÇÃO PORTÁTIL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NothingButton(
                        text = "Copiar JSON",
                        isPrimary = false,
                        onClick = {
                            coroutineScope.launch {
                                val json = cloudSyncManager.exportCloudBackupJson(allBooks)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Backup", json)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Backup JSON copiado!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "btn_copy_json"
                    )

                    NothingButton(
                        text = "Restaurar JSON",
                        isPrimary = false,
                        onClick = { showImportSection = !showImportSection },
                        modifier = Modifier.weight(1f),
                        testTag = "btn_import_json_toggle"
                    )
                }

                AnimatedVisibility(visible = showImportSection) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        NothingTextField(
                            value = importJsonText,
                            onValueChange = { importJsonText = it },
                            label = "Cole o JSON de backup",
                            placeholder = "{ \"vaultId\": \"...\", \"books\": [...] }",
                            singleLine = false
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        NothingButton(
                            text = "Importar Livros",
                            onClick = {
                                if (importJsonText.isNotBlank()) {
                                    coroutineScope.launch {
                                        val count = cloudSyncManager.importCloudBackupJson(importJsonText)
                                        Toast.makeText(context, "$count livros importados!", Toast.LENGTH_SHORT).show()
                                        importJsonText = ""
                                        showImportSection = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (showClearDataConfirm) {
        AlertDialog(
            onDismissRequest = { showClearDataConfirm = false },
            title = { Text("Zerar Todos os Dados?") },
            text = { Text("Isso removerá todos os livros da sua biblioteca e da lista de desejos localmente. Esta ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDataConfirm = false
                        onClearAllData()
                        Toast.makeText(context, "Todos os dados foram zerados com sucesso.", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Zerar Dados", color = NothingRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
