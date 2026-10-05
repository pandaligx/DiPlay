package com.shilapi.xcertplay.media

import android.annotation.TargetApi
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaCodec
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.nio.ByteBuffer

/** Platform objects introduced after KitKat stay behind API-specific implementations. */
internal data class AudioRouting(val legacyStream: Int, val attributes: Any? = null)

internal interface AudioFocusHandle {
    val result: Int
    fun abandon()
}

@Suppress("DEPRECATION")
internal object MediaPlatformCompat {
    fun defaultStream(channel: AudioChannel): Int = when (channel) {
        AudioChannel.PHONE -> AudioManager.STREAM_VOICE_CALL
        AudioChannel.MEDIA, AudioChannel.ASSISTANT, AudioChannel.NAVIGATION -> AudioManager.STREAM_MUSIC
    }

    fun routing(selection: AudioChannelSelection, streamOverride: Int = 0): AudioRouting = AudioRouting(
        if (streamOverride != 0) streamOverride else defaultStream(selection.channel),
        if (Build.VERSION.SDK_INT >= 21) Api21.attributes(selection, streamOverride) else null,
    )

    fun trackRouting(track: AudioTrack, configured: AudioRouting): AudioRouting =
        if (Build.VERSION.SDK_INT >= 29) configured.copy(attributes = Api29.attributes(track)) else configured

    fun routingDescription(routing: AudioRouting): String =
        if (Build.VERSION.SDK_INT >= 21 && routing.attributes != null) Api21.describe(routing.attributes)
        else "legacyStream=${routing.legacyStream} attributesSource=unavailable"

    fun createTrack(routing: AudioRouting, sampleRate: Int, channelMask: Int, encoding: Int, bytes: Int): AudioTrack {
        val candidate = when {
            Build.VERSION.SDK_INT >= 23 && routing.attributes != null ->
                Api23.createTrack(routing.attributes, sampleRate, channelMask, encoding, bytes)
            Build.VERSION.SDK_INT >= 21 && routing.attributes != null ->
                Api21.createTrack(routing.attributes, sampleRate, channelMask, encoding, bytes)
            else -> AudioTrack(routing.legacyStream, sampleRate, channelMask, encoding, bytes, AudioTrack.MODE_STREAM)
        }
        // Constructors may return an unusable track instead of throwing like Builder.build().
        if (candidate.state != AudioTrack.STATE_INITIALIZED) {
            runCatching { candidate.release() }
            throw IllegalStateException("AudioTrack did not initialize")
        }
        return candidate
    }

    fun requestFocus(manager: AudioManager, listener: AudioManager.OnAudioFocusChangeListener,
        routing: AudioRouting, gain: Int): AudioFocusHandle =
        if (Build.VERSION.SDK_INT >= 26 && routing.attributes != null) {
            Api26.requestFocus(manager, listener, routing.attributes, gain)
        } else {
            object : AudioFocusHandle {
                override val result = manager.requestAudioFocus(listener, routing.legacyStream, gain)
                override fun abandon() { manager.abandonAudioFocus(listener) }
            }
        }

    fun inputBuffer(codec: MediaCodec, index: Int): ByteBuffer? =
        if (Build.VERSION.SDK_INT >= 21) Api21.inputBuffer(codec, index) else codec.inputBuffers[index]

    // Fetch the current array each time: INFO_OUTPUT_BUFFERS_CHANGED invalidates the old one.
    fun outputBuffer(codec: MediaCodec, index: Int): ByteBuffer? =
        if (Build.VERSION.SDK_INT >= 21) Api21.outputBuffer(codec, index) else codec.outputBuffers[index]

    fun bufferFrames(track: AudioTrack): Int? = if (Build.VERSION.SDK_INT >= 23) Api23.bufferFrames(track) else null
    fun routeType(track: AudioTrack): Int? = if (Build.VERSION.SDK_INT >= 23) Api23.routeType(track) else null
    fun routeType(recorder: AudioRecord): Int? = if (Build.VERSION.SDK_INT >= 23) Api23.routeType(recorder) else null
    fun underruns(track: AudioTrack): Int? = if (Build.VERSION.SDK_INT >= 24) Api24.underruns(track) else null

    @TargetApi(21)
    private object Api21 {
        fun attributes(selection: AudioChannelSelection, streamOverride: Int): AudioAttributes {
            if (streamOverride in AudioManager.STREAM_SYSTEM..AudioManager.STREAM_ACCESSIBILITY) {
                // Vendor bus numbers are not necessarily valid AudioAttributes legacy streams.
                runCatching { AudioAttributes.Builder().setLegacyStreamType(streamOverride).build() }
                    .getOrNull()?.let { return it }
            }
            val usage = when (selection.channel) {
                AudioChannel.MEDIA -> AudioAttributes.USAGE_MEDIA
                AudioChannel.PHONE -> AudioAttributes.USAGE_VOICE_COMMUNICATION
                AudioChannel.ASSISTANT -> if (Build.VERSION.SDK_INT >= 26) AudioAttributes.USAGE_ASSISTANT
                    else AudioAttributes.USAGE_MEDIA
                AudioChannel.NAVIGATION -> AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
            }
            val content = when (selection.contentType) {
                AudioContentType.MUSIC -> AudioAttributes.CONTENT_TYPE_MUSIC
                AudioContentType.SPEECH -> AudioAttributes.CONTENT_TYPE_SPEECH
            }
            return AudioAttributes.Builder().setUsage(usage).setContentType(content).build()
        }

        fun createTrack(attributes: Any, rate: Int, mask: Int, encoding: Int, bytes: Int): AudioTrack = AudioTrack(
            attributes as AudioAttributes,
            AudioFormat.Builder().setSampleRate(rate).setChannelMask(mask).setEncoding(encoding).build(),
            bytes, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE,
        )

        fun describe(attributes: Any): String = (attributes as AudioAttributes).let {
            "usage=${it.usage} contentType=${it.contentType} " +
                "attributesSource=${if (Build.VERSION.SDK_INT >= 29) "track" else "configured"}"
        }
        fun inputBuffer(codec: MediaCodec, index: Int): ByteBuffer? = codec.getInputBuffer(index)
        fun outputBuffer(codec: MediaCodec, index: Int): ByteBuffer? = codec.getOutputBuffer(index)
    }

    @TargetApi(23)
    private object Api23 {
        fun createTrack(attributes: Any, rate: Int, mask: Int, encoding: Int, bytes: Int): AudioTrack =
            AudioTrack.Builder()
                .setAudioAttributes(attributes as AudioAttributes)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(mask).setEncoding(encoding).build())
                .setBufferSizeInBytes(bytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        fun bufferFrames(track: AudioTrack): Int = track.bufferSizeInFrames
        fun routeType(track: AudioTrack): Int? = track.routedDevice?.type
        fun routeType(recorder: AudioRecord): Int? = recorder.routedDevice?.type
    }

    @TargetApi(24)
    private object Api24 {
        fun underruns(track: AudioTrack): Int = track.underrunCount
    }

    @TargetApi(26)
    private object Api26 {
        fun requestFocus(manager: AudioManager, listener: AudioManager.OnAudioFocusChangeListener,
            attributes: Any, gain: Int): AudioFocusHandle {
            val request = AudioFocusRequest.Builder(gain)
                .setAudioAttributes(attributes as AudioAttributes)
                .setOnAudioFocusChangeListener(listener, Handler(Looper.getMainLooper()))
                .build()
            return object : AudioFocusHandle {
                override val result = manager.requestAudioFocus(request)
                override fun abandon() { manager.abandonAudioFocusRequest(request) }
            }
        }
    }

    @TargetApi(29)
    private object Api29 {
        fun attributes(track: AudioTrack): AudioAttributes = track.audioAttributes
    }
}

/** Kept for callers that already hold API21 attributes; KitKat uses AudioRouting instead. */
@TargetApi(21)
internal fun audioTrackAttributesForFocus(track: AudioTrack, configured: AudioAttributes): AudioAttributes =
    MediaPlatformCompat.trackRouting(track, AudioRouting(AudioManager.STREAM_MUSIC, configured)).attributes as AudioAttributes
