package com.nomad.travel.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "nomad_prefs")

class UserPrefs(private val context: Context) {

    private val KEY_LANGUAGE = stringPreferencesKey("ui_language")
    private val KEY_ACTIVE_MODEL = stringPreferencesKey("active_model_id")
    private val KEY_SYSTEM_PROMPT = stringPreferencesKey("system_prompt")

    val language: Flow<String?> = context.dataStore.data.map { it[KEY_LANGUAGE] }
    val activeModelId: Flow<String?> = context.dataStore.data.map { it[KEY_ACTIVE_MODEL] }
    val systemPrompt: Flow<String?> = context.dataStore.data.map { it[KEY_SYSTEM_PROMPT] }

    suspend fun languageBlocking(): String? = language.first()
    suspend fun activeModelIdBlocking(): String? = activeModelId.first()
    suspend fun systemPromptBlocking(): String? = systemPrompt.first()

    suspend fun setLanguage(code: String) {
        context.dataStore.edit { it[KEY_LANGUAGE] = code }
    }

    suspend fun setActiveModelId(id: String) {
        context.dataStore.edit { it[KEY_ACTIVE_MODEL] = id }
    }

    suspend fun setSystemPrompt(prompt: String) {
        context.dataStore.edit { it[KEY_SYSTEM_PROMPT] = prompt }
    }
}
