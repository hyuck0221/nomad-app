package com.nomad.travel.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-scoped chat log so ChatViewModel and SettingsViewModel can share
 * the same list. Cleared via Settings → "채팅 모두 지우기".
 *
 * Not persisted across app restarts yet — the current goal is a visible
 * "clear" action that meaningfully empties the on-screen conversation.
 */
class ChatHistory {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    fun append(msg: ChatMessage) {
        _messages.update { it + msg }
    }

    fun updateById(id: String, transform: (ChatMessage) -> ChatMessage) {
        _messages.update { list ->
            list.map { if (it.id == id) transform(it) else it }
        }
    }

    fun clear() {
        _messages.value = emptyList()
    }
}
