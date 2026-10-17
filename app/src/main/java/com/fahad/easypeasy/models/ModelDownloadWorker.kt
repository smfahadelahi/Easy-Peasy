package com.fahad.easypeasy.models

import android.content.Context
import android.os.StatFs
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.fahad.easypeasy.engine.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(@Assisted context: Context, @Assisted params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val spec = ModelCatalog.all.firstOrNull { it.id == inputData.getString("id") } ?: return@withContext failure("Unknown model")
        if (!spec.downloadable) return@withContext failure("No verified download for this model")
        val directory = File(applicationContext.noBackupFilesDir, "models")
        try {
            var lastProgress = 0L
            ModelDownloader().download(spec, directory, { StatFs(applicationContext.noBackupFilesDir.path).availableBytes }) { bytes ->
                val now = System.currentTimeMillis()
                if (now - lastProgress >= 500 || bytes == spec.bytes) {
                    setProgress(workDataOf("bytes" to bytes, "total" to spec.bytes)); lastProgress = now
                }
            }
            Result.success(workDataOf("bytes" to spec.bytes))
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: ModelIntegrityException) { failure(error.message.orEmpty())
        } catch (error: ModelStorageException) { failure(error.message.orEmpty())
        } catch (error: ModelHttpException) {
            if ((error.status == 429 || error.status >= 500) && runAttemptCount < 5) Result.retry() else failure(error.message.orEmpty())
        } catch (error: IOException) {
            if (runAttemptCount < 5) Result.retry() else failure(error.message ?: "Download failed")
        } catch (error: Exception) { failure(error.message ?: "Model setup failed") }
    }
    private fun failure(message: String) = Result.failure(workDataOf("error" to message))
}
