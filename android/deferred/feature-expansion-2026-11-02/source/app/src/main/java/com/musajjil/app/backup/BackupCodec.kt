package com.musajjil.app.backup

import com.musajjil.app.data.*
import java.io.*
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable

@Serializable
data class BackupManifest(val format: String = "musajjil-backup", val version: Int = 1,
    val schema: Int = 2, val createdAt: Long, val kind: String, val sha256: String)
data class BackupArchive(val manifest: BackupManifest, val snapshot: RegisterSnapshot)

object BackupCodec {
    private const val MAX_BYTES = 64 * 1024 * 1024
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun write(snapshot: RegisterSnapshot, kind: String, createdAt: Long, output: OutputStream) {
        require(kind in setOf("manual", "automatic", "before-restore"))
        snapshot.validate()
        val payload = stored(snapshot).toByteArray(Charsets.UTF_8)
        require(payload.size <= MAX_BYTES)
        val manifest = BackupManifest(createdAt = createdAt, kind = kind, sha256 = digest(payload))
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(stored(manifest).toByteArray(Charsets.UTF_8)); zip.closeEntry()
            zip.putNextEntry(ZipEntry("register.json")); zip.write(payload); zip.closeEntry()
        }
    }

    fun read(input: InputStream): BackupArchive {
        val entries = mutableMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name in setOf("manifest.json", "register.json") && entry.name !in entries)
                val limit = if (entry.name == "manifest.json") 16 * 1024 else MAX_BYTES
                val bytes = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    total += count
                    require(bytes.size() + count <= limit && total <= MAX_BYTES + 16 * 1024)
                    bytes.write(buffer, 0, count)
                }
                entries[entry.name] = bytes.toByteArray()
                zip.closeEntry()
            }
        }
        require(entries.size == 2)
        val manifest = restored<BackupManifest>(requireNotNull(entries["manifest.json"]).toString(Charsets.UTF_8))
        require(manifest.format == "musajjil-backup" && manifest.version == 1 && manifest.schema == 2 && manifest.createdAt > 0)
        require(manifest.kind in setOf("manual", "automatic", "before-restore"))
        val payload = requireNotNull(entries["register.json"])
        require(MessageDigest.isEqual(digest(payload).toByteArray(), manifest.sha256.toByteArray()))
        val snapshot = restored<RegisterSnapshot>(payload.toString(Charsets.UTF_8))
        snapshot.validate()
        return BackupArchive(manifest, snapshot)
    }
}
