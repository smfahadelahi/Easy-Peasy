package com.fahad.easypeasy.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Mission(val id: String, val title_en: String, val title_bn: String, val ko: String, val romanization: String, val bn: String)
@Serializable
data class Chapter(val id: String, val title_en: String, val title_bn: String, val missions: List<Mission>)
object Lessons {
    suspend fun load(context: Context): List<Chapter> = withContext(Dispatchers.IO) {
        context.assets.open("lessons.json").bufferedReader().use { Json.decodeFromString(it.readText()) }
    }
}
