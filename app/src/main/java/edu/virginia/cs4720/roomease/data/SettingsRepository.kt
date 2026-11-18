package edu.virginia.cs4720.roomease.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ChoreView { MINE, ALL }
enum class SplitDefault { ALL_ROOMMATES, MANUAL }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val choreView: ChoreView = ChoreView.MINE,
    val splitDefault: SplitDefault = SplitDefault.ALL_ROOMMATES
)

// Lecture 9: "val Context.dataStore by preferencesDataStore("settings")" -> one file for the whole app.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

/** Preferences DataStore: a few small values that only this phone needs (Lecture 9 "Choosing storage"). */
class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.dataStore

    private val themeKey = stringPreferencesKey("theme_mode")
    private val choreViewKey = stringPreferencesKey("default_chore_view")
    private val splitKey = stringPreferencesKey("default_split")

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[themeKey].toEnum(ThemeMode.SYSTEM),
            choreView = prefs[choreViewKey].toEnum(ChoreView.MINE),
            splitDefault = prefs[splitKey].toEnum(SplitDefault.ALL_ROOMMATES)
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = dataStore.edit { it[themeKey] = mode.name }
    suspend fun setChoreView(view: ChoreView) = dataStore.edit { it[choreViewKey] = view.name }
    suspend fun setSplitDefault(split: SplitDefault) = dataStore.edit { it[splitKey] = split.name }

    // Unsent chat drafts survive a full app restart, not just rotation.
    private fun draftKey(householdId: String) = stringPreferencesKey("chat_draft_$householdId")

    suspend fun chatDraft(householdId: String): String = dataStore.data.first()[draftKey(householdId)].orEmpty()

    suspend fun setChatDraft(householdId: String, text: String) = dataStore.edit {
        if (text.isEmpty()) it.remove(draftKey(householdId)) else it[draftKey(householdId)] = text
    }
}

private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
