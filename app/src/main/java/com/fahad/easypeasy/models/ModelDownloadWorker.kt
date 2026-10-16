package com.fahad.easypeasy.models

import android.content.Context
import android.os.StatFs
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.fahad.easypeasy.engine.ModelCatalog
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.*

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(@Assisted context: Context, @Assisted params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val spec = ModelCatalog.all.firstOrNull { it.id == inputData.getString("id") } ?: return@withContext fail("Unknown model")
        if (!spec.downloadable) return@withContext fail("No verified download for this model")
        val directory = File(applicationContext.noBackupFilesDir, "models").apply { mkdirs() }
        val part = File(directory, "${spec.id}.part")
        val final = File(directory, spec.id)
        try {
            if (final.isFile && hash(final) == spec.sha256) return@withContext Result.success(workDataOf("bytes" to spec.bytes))
            if (part.length() > spec.bytes) part.delete()
            val needed = (spec.bytes - part.length()).coerceAtLeast(0) + 100L * 1024 * 1024
            if (StatFs(directory.path).availableBytes < needed) return@withContext fail("Insufficient storage; free at least ${needed / 1024 / 1024} MB")
            if (part.length() < spec.bytes) {
                val offset = part.length()
                val connection = URL(spec.url).openConnection() as HttpURLConnection
                connection.connectTimeout = 20_000; connection.readTimeout = 20_000
                connection.setRequestProperty("Accept-Encoding", "identity")
                if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
                try {
                    val code = connection.responseCode
                    require(connection.url.protocol == "https") { "Insecure redirect refused" }
                    if (code != 200 && code != 206) throw IOException("HTTP $code")
                    val resume = code == 206 && offset > 0
                    if (code == 206) require(connection.getHeaderField("Content-Range")?.startsWith("bytes ${if (resume) offset else 0}-") == true) { "Invalid range response" }
                    RandomAccessFile(part, "rw").use { output ->
                        if (!resume) output.setLength(0)
                        output.seek(if (resume) offset else 0)
                        connection.inputStream.use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var lastProgress = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer); if (read < 0) break
                                require(output.filePointer + read <= spec.bytes) { "Unexpected model size" }
                                output.write(buffer, 0, read)
                                if (System.currentTimeMillis() - lastProgress >= 500) {
                                    setProgress(workDataOf("bytes" to output.filePointer, "total" to spec.bytes))
                                    lastProgress = System.currentTimeMillis()
                                }
                            }
                            output.fd.sync()
                        }
                    }
                } finally { connection.disconnect() }
            }
            if (part.length() != spec.bytes) throw IOException("Incomplete download")
            if (hash(part) != spec.sha256) { part.delete(); return@withContext fail("SHA-256 mismatch; partial model discarded") }
            check(part.renameTo(final)) { "Unable to install verified model" }
            Result.success(workDataOf("bytes" to spec.bytes))
        } catch (cancelled: CancellationException) {
            throw cancelled // WorkManager cancellation pauses; preserve .part.
        } catch (error: IOException) {
            if (runAttemptCount < 5) Result.retry() else fail(error.message ?: "Download failed")
        } catch (error: Exception) { fail(error.message ?: "Invalid model") }
    }
    private fun fail(message: String) = Result.failure(workDataOf("error" to message))
    private suspend fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer); if (read < 0) break; digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
