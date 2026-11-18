package edu.virginia.cs4720.roomease.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Dictation for the chat composer using the platform SpeechRecognizer.
 * Text goes INTO the editable composer for review; nothing is sent automatically.
 * The microphone is released whenever the screen pauses (call, app switch, notification),
 * the same register/unregister discipline as Lecture 15's sensors.
 */
class SpeechToTextController(private val context: Context) {
    var isListening by mutableStateOf(false)
        private set
    var partialText by mutableStateOf("")
        private set

    private var recognizer: SpeechRecognizer? = null

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (isListening) return
        if (!isAvailable) {
            onError("Speech recognition isn't available on this device. You can still type.")
            return
        }
        release()
        val speech = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = speech
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                partialText = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                release()
                if (text.isNotBlank()) onResult(text) else onError(errorMessage(SpeechRecognizer.ERROR_NO_MATCH))
            }

            override fun onError(error: Int) {
                release()
                if (error != SpeechRecognizer.ERROR_CLIENT) onError(errorMessage(error))
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        isListening = true
        try {
            speech.startListening(intent)
        } catch (_: Exception) {
            release()
            onError("Couldn't start the microphone. You can still type.")
        }
    }

    /** Stop listening and deliver what was heard so far. */
    fun stop() {
        recognizer?.stopListening()
    }

    /** Free the microphone and the recognizer service. */
    fun release() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        isListening = false
        partialText = ""
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "Didn't catch that. Try again, or type your message."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Speech recognition needs a connection right now. You can still type."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Microphone permission is off. You can still type."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "The speech service is busy. Try again in a moment."
        SpeechRecognizer.ERROR_AUDIO ->
            "Couldn't use the microphone. Another app may be using it."
        else -> "Speech recognition failed. You can still type."
    }
}

@Composable
fun rememberSpeechToText(): SpeechToTextController {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { SpeechToTextController(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) controller.release()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.release()
        }
    }
    return controller
}
