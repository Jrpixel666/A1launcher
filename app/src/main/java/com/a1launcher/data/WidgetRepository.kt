package com.a1launcher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.widgetDataStore by preferencesDataStore(name = "a1launcher_widgets")

/** Persists which AppWidgetHost widget IDs are pinned to the home screen. */
class WidgetRepository(private val context: Context) {

    private val pinnedKey = stringSetPreferencesKey("pinned_widget_ids")

    val pinnedWidgetIds: Flow<List<Int>> =
        context.widgetDataStore.data.map { prefs ->
            prefs[pinnedKey]?.mapNotNull { it.toIntOrNull() }?.sorted() ?: emptyList()
        }

    suspend fun addWidget(widgetId: Int) {
        context.widgetDataStore.edit { prefs ->
            val current = prefs[pinnedKey] ?: emptySet()
            prefs[pinnedKey] = current + widgetId.toString()
        }
    }

    suspend fun removeWidget(widgetId: Int) {
        context.widgetDataStore.edit { prefs ->
            val current = prefs[pinnedKey] ?: emptySet()
            prefs[pinnedKey] = current - widgetId.toString()
        }
    }
}
