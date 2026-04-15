package com.nomad.travel.data

import android.net.Uri
import java.util.UUID

enum class Role { USER, ASSISTANT, SYSTEM }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val text: String,
    val imageUri: Uri? = null,
    val pending: Boolean = false,
    val toolTag: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
