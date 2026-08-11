package com.franciscor.agendnote.core.audio

import com.franciscor.agendnote.core.notifications.VoiceMessageId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Deliberately minimal per the directive ("Sprint Final", item 0: "TODO el esfuerzo de
 * notificaciones/audio debe ir a iOS... Android NO es prioridad"). No WAV delivery mechanism
 * (res/raw entries, asset packaging) has been requested or set up for Android this pass - always
 * reporting [AudioPlayerState.MISSING] is honest about that rather than pretending playback works
 * here. Never crashes; satisfies the "no audio faltante puede tumbar AgendNote" rule on this
 * platform too. Revisit if/when Android becomes a real target for the voice-message feature.
 */
actual class AudioPlayer actual constructor() {
    private val _state = MutableStateFlow(AudioPlayerState.IDLE)
    actual val state: StateFlow<AudioPlayerState> = _state

    actual fun play(voiceMessageId: VoiceMessageId) {
        _state.value = AudioPlayerState.MISSING
    }

    actual fun pause() {
        // No-op: nothing ever actually starts playing on this platform this pass.
    }

    actual fun stop() {
        _state.value = AudioPlayerState.IDLE
    }

    actual fun release() {
        _state.value = AudioPlayerState.IDLE
    }
}
