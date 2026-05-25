package com.nomad.travel.tts

/**
 * Pluggable text-to-speech engine. Implementations: [SystemTtsEngine] (Android default),
 * [MeloTtsEngine] (on-device neural). Routing happens in [TtsManager].
 */
interface TtsEngine {

    /** Stable id used for prefs and routing. */
    val id: String

    /** True only when the engine can actually produce speech right now. */
    fun isReady(): Boolean

    /** True if the engine has a voice for [languageCode] (BCP-47 primary, e.g. "ko"). */
    fun supportsLanguage(languageCode: String): Boolean

    /** Speak [text] in [languageCode]. Best-effort; safe to call when not ready (no-op). */
    fun speak(text: String, languageCode: String)

    /** Cancel current utterance immediately. */
    fun stop()

    /** Release resources. */
    fun shutdown()

    /** Optional callback invoked when the most recent [speak] finishes. */
    var onCompletion: (() -> Unit)?

    /** Optional callback invoked when audio playback actually starts. */
    var onStart: (() -> Unit)?
}
