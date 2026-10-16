package com.fahad.easypeasy.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.fahad.easypeasy.engine.VoiceActivityDetector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface AudioEvent {
    data object SpeechStarted : AudioEvent
    data class Utterance(val pcm: ShortArray) : AudioEvent
}
class Microphone(private val context: Context) {
    @Volatile private var recorder: AudioRecord? = null
    /** Cold stream: collection owns the mic. No audio is written to disk. */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun events(vad: VoiceActivityDetector, silenceSeconds: () -> Float): Flow<AudioEvent> = flow {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        require(min > 0) { "Microphone does not support 16 kHz PCM" }
        val record = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 4096))
        recorder = record
        val echo = if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(record.audioSessionId)?.apply { enabled = true } else null
        val preRoll = ArrayDeque<ShortArray>()
        val speech = ArrayList<ShortArray>()
        var active = false
        var voiced = 0
        var silent = 0
        try {
            check(record.state == AudioRecord.STATE_INITIALIZED)
            vad.reset(); record.startRecording()
            while (currentCoroutineContext().isActive) {
                val frame = ShortArray(512)
                var offset = 0
                while (offset < frame.size && currentCoroutineContext().isActive) {
                    val count = record.read(frame, offset, frame.size - offset, AudioRecord.READ_BLOCKING)
                    check(count > 0) { "Microphone disconnected ($count)" }; offset += count
                }
                currentCoroutineContext().ensureActive()
                val probability = vad.probability(FloatArray(512) { frame[it] / 32768f })
                if (!active) {
                    preRoll.addLast(frame); if (preRoll.size > 10) preRoll.removeFirst().fill(0)
                    voiced = if (probability >= 0.6f) voiced + 1 else 0
                    if (voiced >= 3) {
                        active = true; silent = 0; speech.addAll(preRoll); preRoll.clear()
                        emit(AudioEvent.SpeechStarted)
                    }
                } else {
                    speech.add(frame)
                    silent = if (probability < 0.35f) silent + 1 else 0
                    val timeoutFrames = (silenceSeconds().coerceIn(1f, 6f) * 16000 / 512).toInt()
                    if (silent >= timeoutFrames || speech.size >= 625) {
                        val pcm = ShortArray(speech.size * 512)
                        speech.forEachIndexed { index, samples -> samples.copyInto(pcm, index * 512); samples.fill(0) }
                        speech.clear(); active = false; voiced = 0
                        emit(AudioEvent.Utterance(pcm))
                    }
                }
            }
        } finally {
            runCatching { record.stop() }; record.release(); recorder = null; echo?.release(); vad.reset()
            preRoll.forEach { it.fill(0) }; speech.forEach { it.fill(0) }
        }
    }
    fun stop() { runCatching { recorder?.stop() } }
}
