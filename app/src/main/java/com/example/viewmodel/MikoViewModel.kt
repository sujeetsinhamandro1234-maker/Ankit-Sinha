package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioStreamer
import com.example.websocket.MikoLiveSession
import com.example.websocket.MikoSessionMessage
import com.example.websocket.SessionStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

class MikoViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(MikoUiState())
    val uiState: StateFlow<MikoUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MikoUiEvent>()
    val events: SharedFlow<MikoUiEvent> = _events.asSharedFlow()

    private var liveSession: MikoLiveSession? = null
    private val audioStreamer = AudioStreamer()

    fun toggleSession(apiKey: String) {
        val currentStatus = _uiState.value.status
        if (currentStatus is SessionStatus.Disconnected || currentStatus is SessionStatus.Error) {
            startSession(apiKey)
        } else {
            stopSession()
        }
    }

    fun changeVoice(voiceName: String) {
        _uiState.update { it.copy(selectedVoice = voiceName) }
        liveSession?.setVoice(voiceName)
        viewModelScope.launch {
            _events.emit(MikoUiEvent.ShowToast("Voice changed to $voiceName. Reconnect to apply!"))
        }
    }

    private fun startSession(apiKey: String) {
        if (apiKey.trim().isEmpty() || apiKey.contains("MY_GEMINI_API_KEY")) {
            viewModelScope.launch {
                _events.emit(MikoUiEvent.ShowToast("Please enter an active GEMINI_API_KEY in the Secrets panel!"))
            }
            return
        }

        stopSession()

        val session = MikoLiveSession(
            apiKey = apiKey,
            onMessageReceived = { message -> handleSessionMessage(message) },
            onStatusChanged = { status -> handleStatusChange(status) }
        )
        session.setVoice(_uiState.value.selectedVoice)
        liveSession = session
        session.connect()
    }

    fun stopSession() {
        audioStreamer.stopRecording()
        audioStreamer.stopPlayback()
        liveSession?.disconnect()
        liveSession = null

        _uiState.update {
            it.copy(
                status = SessionStatus.Disconnected,
                isListening = false,
                isSpeaking = false,
                transcript = ""
            )
        }
    }

    private fun handleStatusChange(status: SessionStatus) {
        _uiState.update { it.copy(status = status) }
        
        when (status) {
            SessionStatus.Connected -> {
                // Initialize low latency speech player & mic recording standard stream
                audioStreamer.startPlayback(viewModelScope)
                
                audioStreamer.startRecording(viewModelScope) { chunk ->
                    liveSession?.sendAudioChunk(chunk)
                }
                
                _uiState.update { it.copy(isListening = true, isSpeaking = false, transcript = "MIKO is waking up... Say 'Hello Miko'!") }
            }
            SessionStatus.Disconnected, is SessionStatus.Error -> {
                audioStreamer.stopRecording()
                audioStreamer.stopPlayback()
                _uiState.update { it.copy(isListening = false, isSpeaking = false) }
            }
            SessionStatus.Connecting -> {
                _uiState.update { it.copy(isListening = false, isSpeaking = false, transcript = "") }
            }
        }
    }

    private fun handleSessionMessage(message: MikoSessionMessage) {
        when (message) {
            is MikoSessionMessage.AudioChunk -> {
                _uiState.update { 
                    it.copy(
                        isSpeaking = true,
                        transcript = message.textTranscript ?: it.transcript
                    ) 
                }
                audioStreamer.queuePlaybackChunk(message.pcmData)
            }
            is MikoSessionMessage.TextTranscript -> {
                _uiState.update { it.copy(transcript = message.text) }
            }
            is MikoSessionMessage.Interrupted -> {
                // Immediate audio cancellation on interrupt request
                audioStreamer.clearPlaybackQueue()
                _uiState.update { it.copy(isSpeaking = false, transcript = "[Interrupted] Miko is listening to you... ❤️") }
                Log.d("MikoViewModel", "Miko was interrupted safely by user input.")
            }
            is MikoSessionMessage.ToolCall -> {
                if (message.name == "openWebsite" && message.args != null) {
                    val url = message.args.optString("url")
                    if (!url.isNullOrEmpty()) {
                        viewModelScope.launch {
                            _events.emit(MikoUiEvent.OpenUrl(url))
                            
                            // Send a tool response package back to continue conversation state
                            val payload = JSONObject()
                            payload.put("success", true)
                            payload.put("action", "Opened website $url")
                            liveSession?.sendToolResponse(message.id, message.name, payload)
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopSession()
    }
}

data class MikoUiState(
    val status: SessionStatus = SessionStatus.Disconnected,
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val transcript: String = "",
    val selectedVoice: String = "Aoede" // Aoede, Kore (Female options)
)

sealed class MikoUiEvent {
    data class OpenUrl(val url: String) : MikoUiEvent()
    data class ShowToast(val message: String) : MikoUiEvent()
}
