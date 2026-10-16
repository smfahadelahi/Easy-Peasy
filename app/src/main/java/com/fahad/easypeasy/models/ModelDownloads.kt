package com.fahad.easypeasy.models

import android.content.Context
import androidx.work.*
import com.fahad.easypeasy.engine.ModelCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloads @Inject constructor(@ApplicationContext context: Context) {
    private val work = WorkManager.getInstance(context)
    val status = work.getWorkInfosForUniqueWorkFlow("model-silero-vad")
    fun start(wifiOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf("id" to ModelCatalog.silero.id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).setRequiresStorageNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        work.enqueueUniqueWork("model-silero-vad", ExistingWorkPolicy.KEEP, request)
    }
    fun pause() { work.cancelUniqueWork("model-silero-vad") }
}
