package com.musajjil.app.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.work.*
import com.musajjil.app.MusajjilApplication
import com.musajjil.app.R
import com.musajjil.app.data.RegisterRepository
import com.musajjil.app.data.RegisterSnapshot
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000

data class BackupStatus(val folderName: String = "", val folderUri: String? = null,
    val lastAutomatic: Long = 0, val lastManual: Long = 0, val lastSafetyUri: String? = null, val error: String = "")

/** Persisted SAF access; no broad storage permission. WorkManager schedules approximate weekly work. */
class BackupManager(private val context: Context, private val repository: RegisterRepository) {
    private val preferences = context.getSharedPreferences("backup-storage", Context.MODE_PRIVATE)
    private val operations = Mutex()
    private val _status = MutableStateFlow(readStatus())
    val status = _status.asStateFlow()
    private fun readStatus() = BackupStatus(preferences.getString("folderName", "").orEmpty(),
        preferences.getString("folderUri", null), preferences.getLong("lastAutomatic", 0), preferences.getLong("lastManual", 0),
        preferences.getString("lastSafetyUri", null), preferences.getString("error", "").orEmpty())
    private fun publish() { _status.value = readStatus() }

    suspend fun chooseFolder(uri: Uri) = withContext(Dispatchers.IO) {
        operations.withLock {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val document = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
            var name = context.getString(R.string.backup_folder_selected)
            context.contentResolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) name = it.getString(0)
            }
            val changed = preferences.getString("folderUri", null) != uri.toString()
            val edit = preferences.edit().putString("folderUri", uri.toString()).putString("folderName", name).remove("error")
            if (changed) edit.putLong("lastAutomatic", 0).putLong("lastManual", 0).remove("lastSafetyUri")
            check(edit.commit()); publish()
        }
        schedule()
    }

    fun schedule() {
        if (status.value.folderUri == null) return
        val work = WorkManager.getInstance(context)
        val weekly = PeriodicWorkRequestBuilder<WeeklyBackupWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(7, TimeUnit.DAYS).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS).build()
        work.enqueueUniquePeriodicWork("musajjil-weekly-backup", ExistingPeriodicWorkPolicy.KEEP, weekly)
        if (System.currentTimeMillis() - status.value.lastAutomatic >= WEEK_MILLIS) {
            work.enqueueUniqueWork("musajjil-overdue-backup", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<WeeklyBackupWorker>().setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS).build())
        }
    }

    suspend fun manualBackup(): Uri = withContext(Dispatchers.IO) {
        operations.withLock {
            try {
                val uri = writeArchive(repository.snapshot(), "manual")
                check(preferences.edit().putLong("lastManual", System.currentTimeMillis()).remove("error").commit()); publish()
                uri
            } catch (error: Exception) { reportFailure(error); throw error }
        }
    }

    suspend fun automaticIfDue() = withContext(Dispatchers.IO) {
        operations.withLock {
            if (status.value.folderUri == null || System.currentTimeMillis() - status.value.lastAutomatic < WEEK_MILLIS) return@withLock
            try {
                writeArchive(repository.snapshot(), "automatic")
                check(preferences.edit().putLong("lastAutomatic", System.currentTimeMillis()).remove("error").commit())
                try { pruneAutomatic() } catch (error: Exception) {
                    Log.e("MusajjilBackup", "Automatic backup saved; retention failed", error)
                    preferences.edit().putString("error", context.getString(R.string.backup_retention_failed)).commit()
                }
                publish()
            } catch (error: Exception) { reportFailure(error); throw error }
        }
    }

    suspend fun readArchive(uri: Uri): BackupArchive = withContext(Dispatchers.IO) {
        BackupCodec.read(requireNotNull(context.contentResolver.openInputStream(uri)))
    }

    suspend fun restore(archive: BackupArchive) = withContext(Dispatchers.IO) {
        operations.withLock {
            require(status.value.folderUri != null)
            repository.restore(archive.snapshot) { current ->
                val uri = writeArchive(current, "before-restore")
                check(preferences.edit().putString("lastSafetyUri", uri.toString()).commit()); publish()
            }
        }
    }

    private fun writeArchive(snapshot: RegisterSnapshot, kind: String): Uri {
        val tree = Uri.parse(requireNotNull(status.value.folderUri))
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val now = System.currentTimeMillis()
        val prefix = if (kind == "automatic") "auto" else kind
        val uri = requireNotNull(DocumentsContract.createDocument(context.contentResolver, parent, "application/zip",
            "musajjil-$prefix-$now-${UUID.randomUUID()}.msbak"))
        try {
            BackupCodec.write(snapshot, kind, now, requireNotNull(context.contentResolver.openOutputStream(uri, "wt")))
            // Read the provider's actual bytes before recording success or pruning anything.
            val check = BackupCodec.read(requireNotNull(context.contentResolver.openInputStream(uri)))
            check(check.manifest.createdAt == now && check.manifest.kind == kind && check.snapshot == snapshot)
            return uri
        } catch (error: Exception) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            throw error
        }
    }

    private fun pruneAutomatic() {
        val tree = Uri.parse(requireNotNull(status.value.folderUri))
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val pattern = Regex("musajjil-auto-[0-9]{13}-[0-9a-f-]{36}\\.msbak")
        val candidates = mutableListOf<Uri>()
        context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) if (pattern.matches(cursor.getString(1)))
                candidates += DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
        } ?: error("Could not enumerate backup folder")
        // Only verified archives of our automatic kind qualify. Manual/unrelated files are untouched.
        val valid = candidates.mapNotNull { uri ->
            runCatching { BackupCodec.read(requireNotNull(context.contentResolver.openInputStream(uri))) }.getOrNull()
                ?.takeIf { it.manifest.kind == "automatic" }?.let { uri to it.manifest.createdAt }
        }.sortedByDescending { it.second }
        valid.drop(8).forEach { check(DocumentsContract.deleteDocument(context.contentResolver, it.first)) }
    }

    private fun reportFailure(error: Exception) {
        Log.e("MusajjilBackup", "Backup could not complete", error)
        preferences.edit().putString("error", context.getString(R.string.backup_failed)).commit(); publish()
    }
}

class WeeklyBackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        (applicationContext as MusajjilApplication).backups.automaticIfDue(); Result.success()
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (_: Exception) { Result.retry() }
}
