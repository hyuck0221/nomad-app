package com.nomad.travel.llm

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.nomad.travel.data.UserPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thin wrapper around LiteRT-LM — the official Android runtime for the new
 * Gemma 4 `.litertlm` bundle format.
 *
 * One [Engine] is held per active model. Each turn creates a fresh
 * [com.google.ai.edge.litertlm.Conversation] with the built system instruction
 * since our current chat flow is stateless turn-by-turn.
 */
class GemmaEngine(
    private val context: Context,
    private val prefs: UserPrefs,
    private val device: DeviceCapability
) {

    class ModelNotEligibleException(val entry: ModelEntry, val totalRamGb: Double) :
        IllegalStateException(
            "기기 RAM이 부족합니다. ${entry.shortName}는 최소 " +
                "${entry.minRamBytes / (1024L * 1024 * 1024)}GB가 필요하지만 " +
                "이 기기는 %.1fGB입니다.".format(totalRamGb)
        )

    val modelDir: File = File(
        context.getExternalFilesDir(null) ?: context.filesDir,
        "models"
    ).apply { mkdirs() }

    private val cacheDir: File = File(context.cacheDir, "litertlm").apply { mkdirs() }

    init {
        val knownNames = ModelCatalog.all.map { it.fileName }.toSet()
        modelDir.listFiles()?.forEach { f ->
            if (f.isFile && f.name !in knownNames && !f.name.endsWith(".part")) {
                f.delete()
            }
        }
    }

    fun fileFor(entry: ModelEntry): File = File(modelDir, entry.fileName)

    fun isDownloaded(entry: ModelEntry): Boolean {
        val f = fileFor(entry)
        return f.exists() && f.length() > 0
    }

    fun delete(entry: ModelEntry): Boolean {
        if (activeEntryBlocking().id == entry.id) close()
        return fileFor(entry).delete()
    }

    fun activeEntryBlocking(): ModelEntry {
        val id = runBlocking { prefs.activeModelIdBlocking() }
        return ModelCatalog.byId(id) ?: ModelCatalog.recommended
    }

    fun isActiveReady(): Boolean = isDownloaded(activeEntryBlocking())

    @Volatile private var engine: Engine? = null
    @Volatile private var loadedEntryId: String? = null

    suspend fun ensureLoaded(): Boolean = withContext(Dispatchers.IO) {
        val active = activeEntryBlocking()
        if (engine != null && loadedEntryId == active.id) return@withContext true
        if (!isDownloaded(active)) return@withContext false
        if (!device.isEligible(active)) {
            throw ModelNotEligibleException(active, device.totalRamGb())
        }
        close()
        val config = EngineConfig(
            modelPath = fileFor(active).absolutePath,
            backend = Backend.CPU(),
            cacheDir = cacheDir.absolutePath
        )
        val e = Engine(config)
        e.initialize()
        engine = e
        loadedEntryId = active.id
        true
    }

    /** Drop any current session so the next [ensureLoaded] picks up a new model. */
    fun reload() = close()

    suspend fun generate(systemInstruction: String, userMessage: String): String =
        withContext(Dispatchers.IO) {
            val e = requireNotNull(engine) { "Model not loaded — call ensureLoaded()" }
            val convConfig = ConversationConfig(
                systemInstruction = Contents.of(systemInstruction)
            )
            val conversation = e.createConversation(convConfig)
            try {
                val finalMessage = conversation.sendMessage(userMessage)
                extractText(finalMessage)
            } finally {
                runCatching { conversation.close() }
            }
        }

    fun close() {
        runCatching { engine?.close() }
        engine = null
        loadedEntryId = null
    }

    private fun extractText(message: Message): String =
        message.contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString("") { it.text }
}
