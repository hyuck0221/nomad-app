package com.nomad.travel.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.nomad.travel.NomadApp
import com.nomad.travel.data.ChatHistory
import com.nomad.travel.data.ChatMessage
import com.nomad.travel.data.Role
import com.nomad.travel.tools.ToolRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isResponding: Boolean = false
)

class ChatViewModel(
    private val router: ToolRouter,
    private val history: ChatHistory
) : ViewModel() {

    private val responding = MutableStateFlow(false)

    val state: StateFlow<ChatUiState> = combine(history.messages, responding) { msgs, busy ->
        ChatUiState(messages = msgs, isResponding = busy)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatUiState())

    fun send(context: Context, text: String, image: Uri?) {
        if (text.isBlank() && image == null) return
        val userMsg = ChatMessage(role = Role.USER, text = text, imageUri = image)
        val pendingMsg = ChatMessage(role = Role.ASSISTANT, text = "", pending = true)
        history.append(userMsg)
        history.append(pendingMsg)
        responding.value = true

        viewModelScope.launch {
            runCatching {
                router.handleStream(
                    context = context,
                    turn = ToolRouter.Turn(
                        userText = text,
                        imageUri = image,
                        uiLanguage = Locale.getDefault().language
                    )
                ).collect { evt ->
                    when (evt) {
                        is ToolRouter.StreamEvent.Delta -> {
                            history.updateById(pendingMsg.id) {
                                it.copy(
                                    text = evt.text,
                                    pending = false,
                                    streaming = true
                                )
                            }
                        }
                        is ToolRouter.StreamEvent.Complete -> {
                            history.updateById(pendingMsg.id) {
                                it.copy(
                                    text = evt.text,
                                    pending = false,
                                    streaming = false,
                                    toolTag = evt.toolTag
                                )
                            }
                        }
                    }
                }
            }.onFailure { e ->
                history.updateById(pendingMsg.id) {
                    it.copy(
                        text = "⚠️ ${e.message ?: "error"}",
                        pending = false,
                        streaming = false,
                        toolTag = "error"
                    )
                }
            }
            responding.value = false
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NomadApp
                return ChatViewModel(
                    router = app.container.toolRouter,
                    history = app.container.chatHistory
                ) as T
            }
        }
    }
}
