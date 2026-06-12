package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

class AudioStreamer {
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordingJob: Job? = null
    private var playbackJob: Job? = null
    private val playbackQueue = ConcurrentLinkedQueue<ByteArray>()
    private var isRecording = false
    private var isPlaying = false

    private val inputSampleRate = 16000
    private val outputSampleRate = 24000 // Gemini Live API outputs 24kHz audio
    private val bufferSizeInput = AudioRecord.getMinBufferSize(
        inputSampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ) * 2

    @SuppressLint("MissingPermission")
    fun startRecording(coroutineScope: CoroutineScope, onAudioChunk: (ByteArray) -> Unit) {
        if (isRecording) return
        isRecording = true
        
        recordingJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                val record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    inputSampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSizeInput
                )
                audioRecord = record
                
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e("AudioStreamer", "AudioRecord initialization failed!")
                    return@launch
                }
                
                record.startRecording()
                val buffer = ByteArray(2048) // small chunks (approx. 64ms)
                
                while (isActive && isRecording) {
                    val readBytes = record.read(buffer, 0, buffer.size)
                    if (readBytes > 0) {
                        val chunk = buffer.copyOf(readBytes)
                        onAudioChunk(chunk)
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioStreamer", "Error in recording thread", e)
            } finally {
                stopRecordingInternal()
            }
        }
    }

    fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        stopRecordingInternal()
    }

    private fun stopRecordingInternal() {
        try {
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
            audioRecord = null
        } catch (e: Exception) {
            Log.e("AudioStreamer", "Error closing AudioRecord", e)
        }
    }

    fun startPlayback(coroutineScope: CoroutineScope) {
        if (isPlaying) return
        isPlaying = true
        playbackQueue.clear()

        playbackJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                val minPlayBufSize = AudioTrack.getMinBufferSize(
                    outputSampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(outputSampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minPlayBufSize * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                
                audioTrack = track
                track.play()

                while (isActive && isPlaying) {
                    val chunk = playbackQueue.poll()
                    if (chunk != null) {
                        var written = 0
                        while (written < chunk.size && isPlaying && isActive) {
                            val result = track.write(chunk, written, chunk.size - written)
                            if (result <= 0) {
                                delay(10)
                                break
                            }
                            written += result
                        }
                    } else {
                        delay(15) // Polling interval
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioStreamer", "Error in playback thread", e)
            } finally {
                stopPlaybackInternal()
            }
        }
    }

    fun queuePlaybackChunk(chunk: ByteArray) {
        if (isPlaying) {
            playbackQueue.add(chunk)
        }
    }

    fun clearPlaybackQueue() {
        playbackQueue.clear()
    }

    fun stopPlayback() {
        isPlaying = false
        playbackJob?.cancel()
        playbackJob = null
        stopPlaybackInternal()
    }

    private fun stopPlaybackInternal() {
        try {
            playbackQueue.clear()
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
                flush()
                release()
            }
            audioTrack = null
        } catch (e: Exception) {
            Log.e("AudioStreamer", "Error closing AudioTrack", e)
        }
    }
}
