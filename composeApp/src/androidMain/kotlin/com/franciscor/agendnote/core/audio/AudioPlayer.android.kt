package com.franciscor.agendnote.core.audio

import android.media.MediaPlayer
import com.franciscor.agendnote.core.notifications.AndroidNotificationService
import com.franciscor.agendnote.core.notifications.AndroidSoundAssets
import com.franciscor.agendnote.core.notifications.VoiceMessageId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Real implementation now (was a permanently-MISSING stub) - "para pruebas implementalo tambien
 * en android" (2026-08-11): the point is to let the actual voice-message playback be verified on
 * a real emulator/device, something the iOS port cannot be in this environment. Uses
 * `android.media.MediaPlayer` against a `res/raw` resource resolved by
 * [AndroidSoundAssets.resolveVoiceMessageRawRes] - same null-safe, no-crash-if-missing contract
 * as the iOS actual.
 *
 * Reuses [AndroidNotificationService]'s already-captured application [android.content.Context]
 * (set once from `MainActivity.onCreate`) rather than adding a second context holder - if that
 * hasn't run yet (this constructed before the app finished starting, which should not happen in
 * practice), playback reports [AudioPlayerState.ERROR] instead of crashing on a null context.
 */
actual class AudioPlayer actual constructor() {
    private val _state = MutableStateFlow(AudioPlayerState.IDLE)
    actual val state: StateFlow<AudioPlayerState> = _state

    private var mediaPlayer: MediaPlayer? = null

    actual fun play(voiceMessageId: VoiceMessageId) {
        val existing = mediaPlayer
        if (existing != null && _state.value == AudioPlayerState.PAUSED) {
            existing.start()
            _state.value = AudioPlayerState.PLAYING
            return
        }

        val context = AndroidNotificationService.applicationContext
        if (context == null) {
            println("AudioPlayer: no application context available yet")
            _state.value = AudioPlayerState.ERROR
            return
        }

        _state.value = AudioPlayerState.LOADING
        val resId = AndroidSoundAssets.resolveVoiceMessageRawRes(context, voiceMessageId)
        if (resId == 0) {
            println("AudioPlayer: no bundled res/raw asset for $voiceMessageId - hiding player")
            _state.value = AudioPlayerState.MISSING
            return
        }

        releasePlayer()
        val player = runCatching { MediaPlayer.create(context, resId) }.getOrNull()
        if (player == null) {
            println("AudioPlayer: MediaPlayer.create failed for $voiceMessageId (resId=$resId)")
            _state.value = AudioPlayerState.ERROR
            return
        }
        player.setOnCompletionListener { _state.value = AudioPlayerState.IDLE }
        player.setOnErrorListener { _, what, extra ->
            println("AudioPlayer: playback error for $voiceMessageId (what=$what, extra=$extra)")
            _state.value = AudioPlayerState.ERROR
            true
        }
        mediaPlayer = player
        player.start()
        _state.value = AudioPlayerState.PLAYING
    }

    actual fun pause() {
        if (_state.value != AudioPlayerState.PLAYING) return
        runCatching { mediaPlayer?.pause() }
        _state.value = AudioPlayerState.PAUSED
    }

    actual fun stop() {
        runCatching {
            mediaPlayer?.let { if (it.isPlaying) it.stop() }
        }
        if (_state.value != AudioPlayerState.MISSING) {
            _state.value = AudioPlayerState.IDLE
        }
    }

    actual fun release() {
        releasePlayer()
    }

    private fun releasePlayer() {
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
    }
}
