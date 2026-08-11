package com.franciscor.agendnote.core.audio

import com.franciscor.agendnote.core.notifications.VoiceMessageId
import kotlinx.coroutines.flow.StateFlow

/**
 * Minimal playback state for a long-form voice message (Operación Aniversario, "Sprint Final"
 * directive item 10 - "no necesito waveform, editor, playlists, streaming"). [MISSING] is
 * distinct from [ERROR]: the asset resolved to a real problem (not bundled, or bundled but
 * unplayable) vs. [ERROR] covering an unexpected platform failure while trying to play a asset
 * that *was* found. [PersonalMessageDetailOverlay] hides the player entirely for [MISSING]
 * (directive item 11: "voice message largo faltante -> player oculto/deshabilitado", not a
 * visible broken control) and shows a real error affordance only for [ERROR].
 */
enum class AudioPlayerState {
    IDLE,
    LOADING,
    PLAYING,
    PAUSED,
    MISSING,
    ERROR,
}

/**
 * Plays one of the three long voice-message assets (message_anniversary/encouragement/always.wav)
 * bundled with the app. Resolves [VoiceMessageId] -> platform asset itself (see the iOS/Android
 * actuals) - callers never touch a filename. A missing/unplayable asset is reported via [state]
 * becoming [AudioPlayerState.MISSING]/[AudioPlayerState.ERROR], never a thrown exception - see
 * each actual's doc comment for how that guarantee is implemented per platform.
 */
expect class AudioPlayer() {
    val state: StateFlow<AudioPlayerState>

    /** Starts playback from the beginning if nothing is loaded yet, or from a paused position. */
    fun play(voiceMessageId: VoiceMessageId)

    fun pause()

    /** Stops playback and resets position to the start (Play afterwards starts over). */
    fun stop()

    /** Releases any native player resource. Call when the owning screen leaves composition. */
    fun release()
}
