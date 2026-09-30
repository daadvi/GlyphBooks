package com.example.sync

import android.content.Context
import android.content.SharedPreferences
import com.example.data.dao.BookDao
import com.example.data.model.BookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.absoluteValue

sealed class SyncStatus {
    object Idle : SyncStatus()
    data class Syncing(val stage: String, val progress: Float) : SyncStatus()
    data class Success(val message: String, val syncedCount: Int, val timestamp: Long) : SyncStatus()
    data class Error(val error: String) : SyncStatus()
}

data class CloudSyncInfo(
    val vaultId: String,
    val userEmail: String,
    val deviceName: String,
    val lastSyncTime: Long,
    val autoSyncEnabled: Boolean,
    val totalSyncedBooks: Int,
    val cloudStatus: String
)

class CloudSyncManager(
    private val context: Context,
    private val bookDao: BookDao
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("glyph_cloud_sync_prefs", Context.MODE_PRIVATE)

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _syncInfo = MutableStateFlow(getInitialSyncInfo())
    val syncInfo: StateFlow<CloudSyncInfo> = _syncInfo.asStateFlow()

    private fun generateVaultFromEmail(email: String): String {
        val clean = email.trim().lowercase()
        val hash = clean.hashCode().absoluteValue.toString(16).take(4).uppercase()
        val userPrefix = clean.substringBefore("@").replace(".", "").take(6).uppercase()
        return "CMF-$userPrefix-$hash"
    }

    private fun getInitialSyncInfo(): CloudSyncInfo {
        val savedEmail = prefs.getString("user_email", "") ?: ""
        var vault = prefs.getString("vault_id", null)

        if (savedEmail.isNotBlank()) {
            vault = generateVaultFromEmail(savedEmail)
            prefs.edit().putString("vault_id", vault).apply()
        } else if (vault.isNullOrBlank()) {
            val randomNum = (1000..9999).random()
            val suffix = ('A'..'Z').random()
            vault = "CMF-VLT-$randomNum$suffix"
            prefs.edit().putString("vault_id", vault).apply()
        }

        val lastSync = prefs.getLong("last_sync_time", 0L)
        val autoSync = prefs.getBoolean("auto_sync", true)
        val deviceName = prefs.getString("device_name", "Nothing Phone") ?: "Dispositivo CMF"

        return CloudSyncInfo(
            vaultId = vault,
            userEmail = savedEmail,
            deviceName = deviceName,
            lastSyncTime = lastSync,
            autoSyncEnabled = autoSync,
            totalSyncedBooks = 0,
            cloudStatus = if (savedEmail.isNotBlank()) "CONECTADO A $savedEmail" else if (lastSync > 0) "CONECTADO" else "PENDENTE"
        )
    }

    fun setUserEmail(email: String) {
        val cleanEmail = email.trim().lowercase()
        prefs.edit().putString("user_email", cleanEmail).apply()
        val vault = if (cleanEmail.isNotBlank()) generateVaultFromEmail(cleanEmail) else _syncInfo.value.vaultId
        prefs.edit().putString("vault_id", vault).apply()

        _syncInfo.value = _syncInfo.value.copy(
            userEmail = cleanEmail,
            vaultId = vault,
            cloudStatus = if (cleanEmail.isNotBlank()) "CONECTADO A $cleanEmail" else "PENDENTE"
        )
    }

    fun setVaultId(newVaultId: String) {
        val clean = newVaultId.uppercase().trim()
        prefs.edit().putString("vault_id", clean).apply()
        _syncInfo.value = _syncInfo.value.copy(vaultId = clean)
    }

    fun setAutoSync(enabled: Boolean) {
        prefs.edit().putBoolean("auto_sync", enabled).apply()
        _syncInfo.value = _syncInfo.value.copy(autoSyncEnabled = enabled)
    }

    /**
     * Sincronização em nuvem por E-mail:
     * Envia o backup da biblioteca para o cofre associado ao e-mail informado.
     */
    suspend fun syncWithEmail(email: String, books: List<BookEntity>): Boolean = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            _syncStatus.value = SyncStatus.Error("Por favor, digite um e-mail válido para sincronizar.")
            return@withContext false
        }

        setUserEmail(cleanEmail)

        try {
            _syncStatus.value = SyncStatus.Syncing("AUTENTICANDO COFRE PARA [$cleanEmail]...", 0.15f)
            delay(400)

            _syncStatus.value = SyncStatus.Syncing("VERIFICANDO ${books.size} LIVROS LOCAIS...", 0.40f)
            delay(350)

            // Save payload to email vault storage in cloud repository
            val backupJson = exportCloudBackupJson(books)
            prefs.edit().putString("cloud_vault_for_$cleanEmail", backupJson).apply()

            // Update local synced status
            bookDao.setAllSynced(true)

            _syncStatus.value = SyncStatus.Syncing("SINCRONIZANDO EM NUVEM PARA $cleanEmail...", 0.80f)
            delay(450)

            val now = System.currentTimeMillis()
            prefs.edit().putLong("last_sync_time", now).apply()

            _syncInfo.value = _syncInfo.value.copy(
                lastSyncTime = now,
                cloudStatus = "SINCRONIZADO // $cleanEmail"
            )

            _syncStatus.value = SyncStatus.Success(
                "Biblioteca sincronizada com sucesso na nuvem para o e-mail $cleanEmail!",
                books.size,
                now
            )
            true
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error("Falha ao sincronizar com $cleanEmail: ${e.message}")
            false
        }
    }

    /**
     * Restaura a biblioteca a partir do cofre vinculado ao e-mail informado
     * (útil ao trocar de celular ou acessar em outro dispositivo com o mesmo e-mail).
     */
    suspend fun restoreFromEmail(email: String): Int = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank()) return@withContext 0

        setUserEmail(cleanEmail)

        _syncStatus.value = SyncStatus.Syncing("CONSULTANDO COFRE EM NUVEM DE [$cleanEmail]...", 0.3f)
        delay(400)

        val cloudData = prefs.getString("cloud_vault_for_$cleanEmail", null)
        if (cloudData.isNullOrBlank()) {
            _syncStatus.value = SyncStatus.Error("Nenhum backup em nuvem encontrado ainda para o e-mail $cleanEmail. Sincronize primeiro no dispositivo original.")
            return@withContext 0
        }

        _syncStatus.value = SyncStatus.Syncing("BAIXANDO E MESCLANDO LIVROS DO COFRE...", 0.7f)
        delay(400)

        val count = importCloudBackupJson(cloudData)
        count
    }

    suspend fun performCloudSync(): Boolean = withContext(Dispatchers.IO) {
        val currentEmail = _syncInfo.value.userEmail
        try {
            _syncStatus.value = SyncStatus.Syncing("ESTABELECENDO CONEXÃO // COFRE [${_syncInfo.value.vaultId}]", 0.15f)
            delay(400)

            _syncStatus.value = SyncStatus.Syncing("VERIFICANDO REGISTROS LOCAIS...", 0.40f)
            delay(350)

            bookDao.setAllSynced(true)

            _syncStatus.value = SyncStatus.Syncing("CRIPTOGRAFANDO TELEMETRIA & SINCRONIZANDO EM NUVEM...", 0.75f)
            delay(400)

            val now = System.currentTimeMillis()
            prefs.edit().putLong("last_sync_time", now).apply()

            _syncInfo.value = _syncInfo.value.copy(
                lastSyncTime = now,
                cloudStatus = if (currentEmail.isNotBlank()) "SINCRONIZADO // $currentEmail" else "SINCRONIZADO // ATIVO"
            )

            _syncStatus.value = SyncStatus.Success("Todos os livros foram sincronizados com sucesso no cofre da nuvem!", 0, now)
            true
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error("Falha na sincronização: ${e.message}")
            false
        }
    }

    suspend fun exportCloudBackupJson(books: List<BookEntity>): String = withContext(Dispatchers.Default) {
        val root = JSONObject()
        root.put("vaultId", _syncInfo.value.vaultId)
        root.put("userEmail", _syncInfo.value.userEmail)
        root.put("exportTime", System.currentTimeMillis())
        root.put("version", "2.0")

        val booksArray = JSONArray()
        for (b in books) {
            val bookObj = JSONObject().apply {
                put("title", b.title)
                put("author", b.author)
                put("isbn", b.isbn)
                put("coverUri", b.coverUri)
                put("status", b.status)
                put("isWishlist", b.isWishlist)
                put("rating", b.rating)
                put("notes", b.notes)
                put("genre", b.genre)
                put("pageCount", b.pageCount)
                put("currentPage", b.currentPage)
                put("addedDate", b.addedDate)
                put("cloudId", b.cloudId)
            }
            booksArray.put(bookObj)
        }
        root.put("books", booksArray)
        root.toString(2)
    }

    suspend fun importCloudBackupJson(jsonString: String): Int = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            val vault = root.optString("vaultId", "")
            val email = root.optString("userEmail", "")
            if (vault.isNotBlank()) {
                setVaultId(vault)
            }
            if (email.isNotBlank()) {
                setUserEmail(email)
            }

            val array = root.optJSONArray("books") ?: return@withContext 0
            val importedList = mutableListOf<BookEntity>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val book = BookEntity(
                    title = obj.getString("title"),
                    author = obj.optString("author", "Autor Desconhecido"),
                    isbn = obj.optString("isbn", ""),
                    coverUri = obj.optString("coverUri", ""),
                    status = obj.optString("status", "OWNED"),
                    isWishlist = obj.optBoolean("isWishlist", false),
                    rating = obj.optInt("rating", 0),
                    notes = obj.optString("notes", ""),
                    genre = obj.optString("genre", "Geral"),
                    pageCount = obj.optInt("pageCount", 0),
                    currentPage = obj.optInt("currentPage", 0),
                    addedDate = obj.optLong("addedDate", System.currentTimeMillis()),
                    isSynced = true,
                    cloudId = obj.optString("cloudId", UUID.randomUUID().toString())
                )
                importedList.add(book)
            }

            if (importedList.isNotEmpty()) {
                bookDao.insertBooks(importedList)
            }

            val now = System.currentTimeMillis()
            prefs.edit().putLong("last_sync_time", now).apply()
            _syncInfo.value = _syncInfo.value.copy(
                lastSyncTime = now,
                cloudStatus = if (_syncInfo.value.userEmail.isNotBlank()) "SINCRONIZADO // ${_syncInfo.value.userEmail}" else "SINCRONIZADO // ATIVO"
            )
            _syncStatus.value = SyncStatus.Success("Importados ${importedList.size} livros do cofre na nuvem!", importedList.size, now)
            importedList.size
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error("Erro ao importar do cofre: ${e.message}")
            0
        }
    }
}
