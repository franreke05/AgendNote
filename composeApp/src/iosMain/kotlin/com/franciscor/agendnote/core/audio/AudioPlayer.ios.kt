package com.franciscor.agendnote.core.audio

import com.franciscor.agendnote.core.notifications.IosSoundAssets
import com.franciscor.agendnote.core.notifications.VoiceMessageId
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.darwin.NSObject

/**
 * NOT INDEPENDENTLY VERIFIED - written against the standard, widely-documented Kotlin/Native +
 * AVFoundation interop pattern (`memScoped`/`alloc<ObjCObjectVar<NSError?>>()` for the
 * `NSError **` out-param `AVAudioPlayer(contentsOfURL:error:)` takes), but there is no Xcode/
 * macOS available in this environment to actually compile the iosMain source set or run it on a
 * simulator/device. Static review only - see IOS_RUNTIME = NOT_VERIFIED in the operation status
 * doc. Verify by building on a Mac before trusting this in production.
 */
actual class AudioPlayer actual constructor() {
    private val _state = MutableStateFlow(AudioPlayerState.IDLE)
    actual val state: StateFlow<AudioPlayerState> = _state

    private var player: AVAudioPlayer? = null

    private val delegate = object : NSObject(), AVAudioPlayerDelegateProtocol {
        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
            _state.value = if (successfully) AudioPlayerState.IDLE else AudioPlayerState.ERROR
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun play(voiceMessageId: VoiceMessageId) {
        // Resume from a pause without reloading the file.
        val existing = player
        if (existing != null && _state.value == AudioPlayerState.PAUSED) {
            existing.play()
            _state.value = AudioPlayerState.PLAYING
            return
        }

        _state.value = AudioPlayerState.LOADING
        val path = IosSoundAssets.resolveVoiceMessagePath(voiceMessageId)
        if (path == null) {
            // Directive item 11: a missing long-audio asset must never crash or throw - the
            // caller (PersonalMessageDetailOverlay) hides the player entirely for MISSING.
            println("AudioPlayer: no bundled asset found for $voiceMessageId - hiding player")
            _state.value = AudioPlayerState.MISSING
            return
        }

        val url = NSURL.fileURLWithPath(path)
        val loaded = memScoped {
            val errorVar = alloc<ObjCObjectVar<NSError?>>()
            val instance = runCatching {
                AVAudioPlayer(contentsOfURL = url, error = errorVar.ptr)
            }.getOrNull()
            if (instance == null || errorVar.value != null) {
                println(
                    "AudioPlayer: failed to load $voiceMessageId at $path - " +
                        (errorVar.value?.localizedDescription ?: "unknown error"),
                )
                null
            } else {
                instance
            }
        }

        if (loaded == null) {
            _state.value = AudioPlayerState.ERROR
            return
        }

        loaded.delegate = delegate
        loaded.prepareToPlay()
        loaded.play()
        player = loaded
        _state.value = AudioPlayerState.PLAYING
    }

    actual fun pause() {
        if (_state.value != AudioPlayerState.PLAYING) return
        player?.pause()
        _state.value = AudioPlayerState.PAUSED
    }

    actual fun stop() {
        player?.stop()
        player?.currentTime = 0.0
        if (_state.value != AudioPlayerState.MISSING) {
            _state.value = AudioPlayerState.IDLE
        }
    }

    actual fun release() {
        player?.stop()
        player?.delegate = null
        player = null
    }
}
