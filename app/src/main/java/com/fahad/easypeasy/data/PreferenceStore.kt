package com.fahad.easypeasy.data

import android.content.Context
import com.fahad.easypeasy.ui.Preferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Singleton
class PreferenceStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    fun read(): Preferences = runCatching { Json.decodeFromString<Preferences>(prefs.getString("profile", null) ?: "{}") }.getOrDefault(Preferences())
    suspend fun write(value: Preferences) = withContext(Dispatchers.IO) {
        check(prefs.edit().putString("profile", Json.encodeToString(Preferences.serializer(), value)).commit())
    }
}
