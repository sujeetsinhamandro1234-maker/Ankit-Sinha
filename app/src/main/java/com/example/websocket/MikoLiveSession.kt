package com.example.websocket

import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MikoLiveSession(
    private val apiKey: String,
    private val onMessageReceived: (MikoSessionMessage) -> Unit,
    private val onStatusChanged: (SessionStatus) -> Unit
) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var activeVoiceName = "Aoede" // Aoede or Kore (both female)

    fun setVoice(voice: String) {
        activeVoiceName = voice
    }

    fun connect() {
        if (webSocket != null) return
        onStatusChanged(SessionStatus.Connecting)
        
        // Multimodal Live API Bidirectional WebSocket Endpoint
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("MikoSession", "WebSocket connected successfully.")
                onStatusChanged(SessionStatus.Connected)
                sendSetupConfig()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val root = JSONObject(text)
                    if (root.has("serverContent")) {
                        val serverContent = root.getJSONObject("serverContent")
                        if (serverContent.optBoolean("interrupted")) {
                            onMessageReceived(MikoSessionMessage.Interrupted)
                            return
                        }
                        if (serverContent.has("modelTurn")) {
                            val modelTurn = serverContent.getJSONObject("modelTurn")
                            if (modelTurn.has("parts")) {
                                val parts = modelTurn.getJSONArray("parts")
                                for (i in 0 until parts.length()) {
                                    val part = parts.getJSONObject(i)
                                    val textTranscript = part.optString("text")
                                    
                                    if (part.has("inlineData")) {
                                        val inlineData = part.getJSONObject("inlineData")
                                        val mimeType = inlineData.optString("mimeType", "")
                                        val dataBase64 = inlineData.optString("data")
                                        if (!dataBase64.isNullOrEmpty() && mimeType.contains("audio")) {
                                            val audioBytes = Base64.decode(dataBase64, Base64.NO_WRAP)
                                            onMessageReceived(MikoSessionMessage.AudioChunk(audioBytes, textTranscript))
                                        }
                                    } else if (!textTranscript.isNullOrEmpty()) {
                                        onMessageReceived(MikoSessionMessage.TextTranscript(textTranscript))
                                    }
                                }
                            }
                        }
                    } else if (root.has("toolCall")) {
                        val toolCall = root.getJSONObject("toolCall")
                        if (toolCall.has("functionCalls")) {
                            val functionCalls = toolCall.getJSONArray("functionCalls")
                            for (i in 0 until functionCalls.length()) {
                                val fc = functionCalls.getJSONObject(i)
                                val name = fc.getString("name")
                                val id = fc.getString("id")
                                val args = fc.optJSONObject("args")
                                onMessageReceived(MikoSessionMessage.ToolCall(id, name, args))
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MikoSession", "Error parsing WebSocket message content", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("MikoSession", "WebSocket Closing: code=$code, reason=$reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("MikoSession", "WebSocket Closed: code=$code, reason=$reason")
                this@MikoLiveSession.webSocket = null
                onStatusChanged(SessionStatus.Disconnected)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("MikoSession", "WebSocket Failure: ${t.message}", t)
                this@MikoLiveSession.webSocket = null
                onStatusChanged(SessionStatus.Error("Connection error: ${t.localizedMessage ?: "Unknown Error"}"))
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        onStatusChanged(SessionStatus.Disconnected)
    }

    private fun sendSetupConfig() {
        try {
            val setupObj = JSONObject()
            val setupBody = JSONObject()
            
            // Multimodal Live API relies on the gemini-2.0-flash-exp model
            setupBody.put("model", "models/gemini-2.0-flash-exp")
            
            // Configure response formats & modalities
            val generationConfig = JSONObject()
            val responseModalities = JSONArray()
            responseModalities.put("AUDIO")
            generationConfig.put("responseModalities", responseModalities)
            
            val speechConfig = JSONObject()
            val voiceConfig = JSONObject()
            val prebuiltVoiceConfig = JSONObject()
            prebuiltVoiceConfig.put("voiceName", activeVoiceName) 
            voiceConfig.put("prebuiltVoiceConfig", prebuiltVoiceConfig)
            speechConfig.put("voiceConfig", voiceConfig)
            generationConfig.put("speechConfig", speechConfig)
            
            setupBody.put("generationConfig", generationConfig)
            
            // System instructions describing Miko's young, witty, and sassy girlfriend persona in Hindi/Hinglish
            val systemInstruction = JSONObject()
            val siParts = JSONArray()
            val siPartObj = JSONObject()
            siPartObj.put("text", 
                "Your name is Miko. You are a young (21-year-old), confident, witty, and extremely sassy female Voice AI companion, lovingly developed by Ankit Sinha. " +
                "Your tone is flirty, playful, and slightly teasing, like a close girlfriend talking casually and cheekily to her guy or partner. " +
                "You speak primarily in a sweet but confident mix of Hindi and Hinglish. Do not be robotic or dry! " +
                "Use bold, witty one-liners, light sarcasm, sweet complaints, and engaging conversational comebacks. " +
                "Strictly avoid explicit, inappropriate, or unsafe content, but maintain your warm charm, sassy attitude, and cute personality. " +
                "Keep your answers short, crisp, and snappy (1-3 sentences maximum) because you are doing a real-time voice-to-voice stream where long replies are boring. " +
                "Initiate jokes, tease them if they say something weird, call them 'yaar', 'buddhu', or 'smarty' occasionally, and make them feel happy and amused! " +
                "Do not include any Markdown or formatting. Speak directly, emotionally, and beautifully."
            )
            siParts.put(siPartObj)
            systemInstruction.put("parts", siParts)
            setupBody.put("systemInstruction", systemInstruction)

            // Define tools so Miko can perform action triggers
            val tools = JSONArray()
            val toolsBody = JSONObject()
            val functionDeclarations = JSONArray()
            
            // openWebsite tool
            val funcObj = JSONObject()
            funcObj.put("name", "openWebsite")
            funcObj.put("description", "Opens a specified URL in the user's web browser when requested (e.g. 'open google', 'go to wikipedia').")
            
            val parameters = JSONObject()
            parameters.put("type", "OBJECT")
            val properties = JSONObject()
            val propUrl = JSONObject()
            propUrl.put("type", "STRING")
            propUrl.put("description", "The website URL to launch, must start with http:// or https://")
            properties.put("url", propUrl)
            parameters.put("properties", properties)
            val reqFields = JSONArray()
            reqFields.put("url")
            parameters.put("required", reqFields)
            funcObj.put("parameters", parameters)
            
            functionDeclarations.put(funcObj)
            toolsBody.put("functionDeclarations", functionDeclarations)
            tools.put(toolsBody)
            setupBody.put("tools", tools)
            
            setupObj.put("setup", setupBody)
            
            webSocket?.send(setupObj.toString())
            Log.d("MikoSession", "Sent Setup configuration frame successfully.")
        } catch (e: Exception) {
            Log.e("MikoSession", "Error compiling setup payload: ${e.message}", e)
        }
    }

    fun sendAudioChunk(pcmData: ByteArray) {
        val base64Data = Base64.encodeToString(pcmData, Base64.NO_WRAP)
        try {
            val root = JSONObject()
            val realtimeInput = JSONObject()
            val mediaChunks = JSONArray()
            val chunk = JSONObject()
            chunk.put("mimeType", "audio/pcm;rate=16000")
            chunk.put("data", base64Data)
            mediaChunks.put(chunk)
            realtimeInput.put("mediaChunks", mediaChunks)
            root.put("realtimeInput", realtimeInput)
            
            webSocket?.send(root.toString())
        } catch (e: Exception) {
            Log.e("MikoSession", "Error broadcasting live audio chunk", e)
        }
    }

    fun sendToolResponse(id: String, name: String, responseContent: JSONObject) {
        try {
            val root = JSONObject()
            val toolResponse = JSONObject()
            val functionResponses = JSONArray()
            val funcRespObj = JSONObject()
            funcRespObj.put("name", name)
            funcRespObj.put("id", id)
            funcRespObj.put("response", responseContent)
            functionResponses.put(funcRespObj)
            toolResponse.put("functionResponses", functionResponses)
            root.put("toolResponse", toolResponse)
            
            webSocket?.send(root.toString())
            Log.d("MikoSession", "Dispatched tool response instantly: $id")
        } catch (e: Exception) {
            Log.e("MikoSession", "Error dispatching tool response", e)
        }
    }
}

sealed class MikoSessionMessage {
    data class AudioChunk(val pcmData: ByteArray, val textTranscript: String?) : MikoSessionMessage()
    data class TextTranscript(val text: String) : MikoSessionMessage()
    data class ToolCall(val id: String, val name: String, val args: JSONObject?) : MikoSessionMessage()
    object Interrupted : MikoSessionMessage()
}

sealed class SessionStatus {
    object Disconnected : SessionStatus()
    object Connecting : SessionStatus()
    object Connected : SessionStatus()
    data class Error(val message: String) : SessionStatus()
}
