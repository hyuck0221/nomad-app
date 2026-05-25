package com.nomad.travel.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.nomad.travel.llm.ModelEntry
import java.io.File
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-device neural TTS through sherpa-onnx.
 */
class MeloTtsEngine(context: Context) : TtsEngine {

    override val id: String = ID

    private val appContext = context.applicationContext
    private val synthExecutor = Executors.newFixedThreadPool(SYNTH_THREADS)
    private val playbackExecutor = Executors.newSingleThreadExecutor()
    private val stopped = AtomicBoolean(false)
    private val generation = AtomicLong(0L)
    private val engineVersion = AtomicLong(0L)

    @Volatile
    private var activeModelId: String? = null

    @Volatile
    private var preferredModelId: String? = null

    @Volatile
    private var voicePreset: String = TtsModelCatalog.DEFAULT_VOICE_PRESET

    @Volatile
    private var track: AudioTrack? = null

    private val queueLock = Any()
    private var nextSequence = 0
    private var nextPlaybackSequence = 0
    private var drainScheduled = false
    private val pendingAudio = mutableMapOf<Int, QueuedAudio>()
    private val synthEngines = Collections.synchronizedSet(mutableSetOf<OfflineTts>())
    private val threadTts = ThreadLocal<TtsHolder?>()

    override var onCompletion: (() -> Unit)? = null
    override var onStart: (() -> Unit)? = null

    fun isModelDownloaded(entry: ModelEntry): Boolean =
        allFilesFor(entry).all { it.exists() && it.length() > 0L }

    fun isModelUsable(entry: ModelEntry): Boolean =
        TtsModelCatalog.isSupertonic(entry) && isModelDownloaded(entry)

    fun setPreferredModel(id: String?) {
        preferredModelId = id
    }

    fun setVoicePreset(preset: String?) {
        val normalized = TtsModelCatalog.normalizeVoicePreset(preset)
        if (voicePreset == normalized) return
        voicePreset = normalized
        stop()
        releaseSynthEngines()
    }

    fun fileFor(entry: ModelEntry): File =
        File(appContext.filesDir, "tts/${entry.fileName}")

    fun delete(entry: ModelEntry): Boolean {
        if (entry.id == activeModelId) {
            stop()
            releaseSynthEngines()
        }
        val primary = fileFor(entry)
        val dir = primary.parentFile
        return dir?.deleteRecursively() ?: primary.delete()
    }

    override fun isReady(): Boolean =
        TtsModelCatalog.all.any { isModelUsable(it) }

    fun isReadyForLanguage(languageCode: String): Boolean =
        entryForLanguage(languageCode)?.let { isModelUsable(it) } == true

    override fun supportsLanguage(languageCode: String): Boolean =
        TtsModelCatalog.forLanguage(languageCode) != null

    override fun speak(text: String, languageCode: String) {
        stop()
        speakQueued(text, languageCode)
    }

    fun speakQueued(text: String, languageCode: String) {
        val entry = entryForLanguage(languageCode)
        if (entry == null || !isModelUsable(entry)) {
            onCompletion?.invoke()
            return
        }

        stopped.set(false)
        val segment = synchronized(queueLock) {
            QueuedText(
                generation = generation.get(),
                sequence = nextSequence++,
                text = text,
                languageCode = languageCode,
                entry = entry
            )
        }
        synthExecutor.execute {
            runCatching {
                synthesize(segment)
            }.onFailure { e ->
                Log.w(TAG, "sherpa-onnx TTS failed", e)
                null
            }.getOrNull()?.let { audio ->
                if (audio.samples.isEmpty()) {
                    Log.w(TAG, "sherpa-onnx generated empty audio for ${entry.id}")
                    return@let
                }
                synchronized(queueLock) {
                    if (!isCurrentGenerationLocked(segment.generation)) return@let
                    pendingAudio[segment.sequence] = audio
                    scheduleDrainLocked(segment.generation)
                }
            }
        }
    }

    override fun stop() {
        generation.incrementAndGet()
        stopped.set(true)
        synchronized(queueLock) {
            pendingAudio.clear()
            nextSequence = 0
            nextPlaybackSequence = 0
            drainScheduled = false
        }
        track?.pause()
        track?.flush()
    }

    override fun shutdown() {
        stop()
        synthExecutor.shutdownNow()
        playbackExecutor.shutdownNow()
        track?.release()
        track = null
        releaseSynthEngines()
    }

    private fun synthesize(segment: QueuedText): QueuedAudio? {
        if (!isCurrentGeneration(segment.generation)) return null
        val engine = ensureThreadTts(segment.entry)
        val audio = engine.generateWithConfig(
            text = segment.text,
            config = GenerationConfig(
                sid = speakerIdForPreset(voicePreset),
                speed = 1.05f,
                numSteps = 5,
                extra = mapOf("lang" to supertonicLanguage(segment.languageCode))
            )
        )
        if (!isCurrentGeneration(segment.generation)) return null
        val sampleRate = audio.sampleRate
            .takeIf { it > 0 }
            ?: engine.sampleRate().takeIf { it > 0 }
            ?: error("Invalid TTS sample rate for ${segment.entry.id}")
        return QueuedAudio(
            generation = segment.generation,
            sequence = segment.sequence,
            samples = audio.samples,
            sampleRate = sampleRate
        )
    }

    private fun ensureThreadTts(entry: ModelEntry): OfflineTts {
        val key = "${entry.id}:$voicePreset:${engineVersion.get()}"
        threadTts.get()?.takeIf { it.modelId == key }?.let { return it.tts }
        threadTts.get()?.tts?.let {
            synthEngines.remove(it)
            it.release()
        }
        val modelFile = fileFor(entry)
        val modelDir = modelFile.parentFile ?: error("Missing TTS model directory")
        val config = OfflineTtsConfig(
            model = modelConfigFor(entry, modelFile, modelDir),
            maxNumSentences = 1,
            silenceScale = 0.2f
        )
        return OfflineTts(config = config).also {
            threadTts.set(TtsHolder(key, it))
            synthEngines.add(it)
            activeModelId = entry.id
        }
    }

    private fun scheduleDrainLocked(generationId: Long) {
        if (drainScheduled) return
        drainScheduled = true
        playbackExecutor.execute { drainReadyAudio(generationId) }
    }

    private fun drainReadyAudio(generationId: Long) {
        while (true) {
            val audio = synchronized(queueLock) {
                if (!isCurrentGenerationLocked(generationId)) {
                    drainScheduled = false
                    return
                }
                val next = pendingAudio.remove(nextPlaybackSequence)
                if (next == null) {
                    drainScheduled = false
                    return
                }
                nextPlaybackSequence++
                next
            }
            playAudio(audio)
            onCompletion?.invoke()
        }
    }

    private fun playAudio(audio: QueuedAudio) {
        if (!isCurrentGeneration(audio.generation)) return
        val audioTrack = ensureTrack(audio.sampleRate)
        audioTrack.pause()
        audioTrack.flush()
        audioTrack.play()
        onStart?.invoke()
        val written = audioTrack.write(audio.samples, 0, audio.samples.size, AudioTrack.WRITE_BLOCKING)
        if (written < 0) error("AudioTrack write failed: $written")
        waitForPlayback(audioTrack, audio.sampleRate, written.coerceAtMost(audio.samples.size), audio.generation)
        if (!stopped.get() && isCurrentGeneration(audio.generation)) {
            track?.pause()
            track?.flush()
        }
    }

    private fun ensureTrack(sampleRate: Int): AudioTrack {
        require(sampleRate > 0) { "Invalid sample rate: $sampleRate" }
        track?.takeIf { it.sampleRate == sampleRate }?.let { return it }
        track?.release()

        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        if (minBuffer <= 0) {
            error("Invalid AudioTrack min buffer for sample rate $sampleRate: $minBuffer")
        }
        val bufferSize = minBuffer.coerceAtLeast(sampleRate)
        val attr = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()
        return AudioTrack(
            attr,
            format,
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        ).also { track = it }
    }

    private fun waitForPlayback(
        audioTrack: AudioTrack,
        sampleRate: Int,
        framesWritten: Int,
        generationId: Long
    ) {
        if (framesWritten <= 0 || sampleRate <= 0) return
        val durationMs = ((framesWritten * 1000L) / sampleRate).coerceAtLeast(1L)
        val deadline = SystemClock.elapsedRealtime() + durationMs + PLAYBACK_DRAIN_GRACE_MS
        while (!stopped.get() && isCurrentGeneration(generationId) && SystemClock.elapsedRealtime() < deadline) {
            if (audioTrack.playbackHeadPosition >= framesWritten) break
            SystemClock.sleep(20L)
        }
    }

    private fun allFilesFor(entry: ModelEntry): List<File> {
        val primary = fileFor(entry)
        val dir = primary.parentFile
        return buildList {
            add(primary)
            entry.companionFiles.forEach { add(File(dir, it.fileName)) }
        }
    }

    private fun entryForLanguage(languageCode: String): ModelEntry? {
        val normalized = languageCode.lowercase()
        val preferred = TtsModelCatalog.byId(preferredModelId)
        if (preferred != null && TtsModelCatalog.supportsLanguage(preferred, normalized)) {
            return preferred
        }
        return TtsModelCatalog.forLanguage(normalized)
    }

    private fun modelConfigFor(entry: ModelEntry, modelFile: File, modelDir: File): OfflineTtsModelConfig =
        OfflineTtsModelConfig(
            supertonic = OfflineTtsSupertonicModelConfig(
                durationPredictor = modelFile.absolutePath,
                textEncoder = File(modelDir, "text_encoder.int8.onnx").absolutePath,
                vectorEstimator = File(modelDir, "vector_estimator.int8.onnx").absolutePath,
                vocoder = File(modelDir, "vocoder.int8.onnx").absolutePath,
                ttsJson = File(modelDir, "tts.json").absolutePath,
                unicodeIndexer = File(modelDir, "unicode_indexer.bin").absolutePath,
                voiceStyle = File(modelDir, "voice.bin").absolutePath
            ),
            numThreads = 2,
            debug = false,
            provider = "cpu"
        )

    private fun speakerIdForPreset(preset: String): Int =
        TtsModelCatalog.voicePresets.indexOf(TtsModelCatalog.normalizeVoicePreset(preset))
            .takeIf { it >= 0 }
            ?: TtsModelCatalog.voicePresets.indexOf(TtsModelCatalog.DEFAULT_VOICE_PRESET)
                .takeIf { it >= 0 }
            ?: 0

    private fun supertonicLanguage(languageCode: String): String =
        when (languageCode.lowercase()) {
            "en", "ko", "ja" -> languageCode.lowercase()
            else -> "en"
        }

    private fun isCurrentGeneration(generationId: Long): Boolean =
        !stopped.get() && generation.get() == generationId

    private fun isCurrentGenerationLocked(generationId: Long): Boolean =
        !stopped.get() && generation.get() == generationId

    private fun releaseSynthEngines() {
        engineVersion.incrementAndGet()
        synthEngines.toList().forEach { it.release() }
        synthEngines.clear()
        activeModelId = null
        threadTts.remove()
    }

    companion object {
        const val ID = "melo"
        const val LEGACY_KOKORO_ID = "kokoro"
        private const val TAG = "NomadTts"
        private const val SYNTH_THREADS = 2
        private const val PLAYBACK_DRAIN_GRACE_MS = 250L
    }
}

private data class TtsHolder(
    val modelId: String,
    val tts: OfflineTts
)

private data class QueuedText(
    val generation: Long,
    val sequence: Int,
    val text: String,
    val languageCode: String,
    val entry: ModelEntry
)

private data class QueuedAudio(
    val generation: Long,
    val sequence: Int,
    val samples: FloatArray,
    val sampleRate: Int
)
