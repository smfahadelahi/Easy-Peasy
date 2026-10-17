package com.fahad.easypeasy.engine

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class ModelIntegrityException(message: String) : IOException(message)
class ModelStorageException(message: String) : IOException(message)
class ModelHttpException(val status: Int) : IOException("HTTP $status")

class ModelResponse(
    val status: Int,
    val contentRange: String?,
    val stream: InputStream,
    private val disconnect: () -> Unit = {},
) : Closeable {
    override fun close() { try { stream.close() } finally { disconnect() } }
}
fun interface ModelTransport { fun open(url: String, offset: Long): ModelResponse }
class HttpsModelTransport : ModelTransport {
    override fun open(url: String, offset: Long): ModelResponse {
        require(URL(url).protocol == "https")
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20_000; connection.readTimeout = 20_000
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
            val status = connection.responseCode
            require(connection.url.protocol == "https") { "Insecure redirect refused" }
            if (status != 200 && status != 206) throw ModelHttpException(status)
            return ModelResponse(status, connection.getHeaderField("Content-Range"), connection.inputStream) { connection.disconnect() }
        } catch (error: Exception) { connection.disconnect(); throw error }
    }
}

/** IO-only, resumable and independently testable. Partial bytes are never usable model files. */
class ModelDownloader(private val transport: ModelTransport = HttpsModelTransport()) {
    suspend fun download(
        spec: ModelSpec,
        directory: File,
        availableBytes: () -> Long,
        reserveBytes: Long = 100L * 1024 * 1024,
        progress: suspend (Long) -> Unit = {},
    ): File {
        require(spec.downloadable && spec.id.matches(Regex("[a-z0-9-]+")))
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create model storage" }
        val part = File(directory, "${spec.id}.part")
        val installed = File(directory, spec.id)
        if (installed.isFile) {
            if (installed.length() == spec.bytes && sha256(installed) == spec.sha256) return installed
            check(installed.delete()) { "Cannot remove corrupt model" }
        }
        if (part.length() > spec.bytes) check(part.delete())
        val needed = (spec.bytes - part.length()).coerceAtLeast(0) + reserveBytes
        if (availableBytes() < needed) throw ModelStorageException("Insufficient storage: $needed bytes required")
        if (part.length() < spec.bytes) {
            val offset = part.length()
            transport.open(checkNotNull(spec.url), offset).use { response ->
                if (response.status != 200 && response.status != 206) throw ModelHttpException(response.status)
                val resumed = response.status == 206
                if (resumed) {
                    val range = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(response.contentRange.orEmpty())
                        ?: throw ModelIntegrityException("Invalid range response")
                    val (start, end, total) = range.destructured
                    if (start.toLong() != offset || total.toLong() != spec.bytes || end.toLong() != spec.bytes - 1) {
                        throw ModelIntegrityException("Incorrect range response")
                    }
                } else if (offset > 0 && availableBytes() < spec.bytes + reserveBytes) {
                    // A server that ignores Range requires enough space for a full restart.
                    throw ModelStorageException("Server cannot resume; free space for a full model")
                }
                RandomAccessFile(part, "rw").use { output ->
                    if (!resumed) output.setLength(0)
                    output.seek(if (resumed) offset else 0)
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = response.stream.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        if (output.filePointer + count > spec.bytes) throw ModelIntegrityException("Unexpected model size")
                        output.write(buffer, 0, count)
                        progress(output.filePointer)
                    }
                    output.fd.sync()
                }
            }
        }
        currentCoroutineContext().ensureActive()
        if (part.length() != spec.bytes) throw IOException("Incomplete download; retry to resume")
        if (sha256(part) != spec.sha256) { part.delete(); throw ModelIntegrityException("SHA-256 mismatch; partial model discarded") }
        currentCoroutineContext().ensureActive()
        check(part.renameTo(installed)) { "Cannot install verified model" }
        progress(spec.bytes)
        return installed
    }
    companion object {
        suspend fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer); if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        }
    }
}
