package com.a1launcher.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "a1launcher_settings")

/**
 * Non-secret install/setup state lives in DataStore; the OpenRouter API key (the one
 * secret this app ever holds directly) lives in EncryptedSharedPreferences instead.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        val LINUX_ENV_READY = booleanPreferencesKey("linux_env_ready")
        val INSTALLED_AGENTS = stringSetPreferencesKey("installed_agents")
    }

    val isSetupComplete: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.SETUP_COMPLETE] ?: false }

    val isLinuxEnvironmentReady: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.LINUX_ENV_READY] ?: false }

    val installedAgentIds: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.INSTALLED_AGENTS] ?: emptySet() }

    suspend fun markSetupComplete() {
        context.dataStore.edit { it[Keys.SETUP_COMPLETE] = true }
    }

    suspend fun markLinuxEnvironmentReady(ready: Boolean) {
        context.dataStore.edit { it[Keys.LINUX_ENV_READY] = ready }
    }

    suspend fun markAgentInstalled(agentBin: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.INSTALLED_AGENTS] ?: emptySet()
            prefs[Keys.INSTALLED_AGENTS] = current + agentBin
        }
    }

    private val encryptedPrefs by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "a1launcher_secrets",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun saveOpenRouterApiKey(key: String) {
        encryptedPrefs.edit().putString("openrouter_api_key", key).apply()
    }

    fun getOpenRouterApiKey(): String? = encryptedPrefs.getString("openrouter_api_key", null)
}
