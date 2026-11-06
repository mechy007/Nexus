package com.mechy007.nexus.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Android voice I/O adapter for Allium.
 *
 * Listening is always explicitly started by the user after the RECORD_AUDIO
 * permission is granted. This controller does not run a wake-word service,
 * record in the background, or send audio to an AI provider.
 */
class AlliumVoiceController(
    context: Context,
    private val onStatusChanged: (Status) -> Unit = {},
    private val onTranscript: (String) -> Unit = {},
    private val onError: (String) -> Unit = {}
) {
    enum class Status { IDLE, INITIALIZING, LISTENING, SPEAKING, UNAVAILABLE, ERROR }

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var released = false

    init {
        initializeSpeechOutput()
    }

    fun startListening(locale: Locale = Locale.getDefault()) {
        if (released) return
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            fail("Speech recognition is not available on this device.")
            return
        }
        stopListening()
        val service = try {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        } catch (_: RuntimeException) {
            fail("Unable to start the speech recognition service.")
            return
        }
        recognizer = service
        service.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onStatusChanged(Status.LISTENING)
            override fun onBeginningOfSpeech() = onStatusChanged(Status.LISTENING)
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = onStatusChanged(Status.INITIALIZING)
            override fun onError(error: Int) {
                recognizer?.destroy()
                recognizer = null
                if (!released) {
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        onStatusChanged(Status.IDLE)
                    } else {
                        fail("Speech recognition failed (code $error).")
                    }
                }
            }
            override fun onResults(results: Bundle?) {
                val transcript = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                recognizer?.destroy()
                recognizer = null
                if (!released) {
                    onStatusChanged(Status.IDLE)
                    if (transcript.isNotBlank()) onTranscript(transcript)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (partial.isNotBlank()) onTranscript(partial)
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            onStatusChanged(Status.INITIALIZING)
            service.startListening(intent)
        } catch (_: RuntimeException) {
            service.destroy()
            recognizer = null
            fail("Speech recognition could not be started.")
        }
    }

    fun stopListening() {
        val active = recognizer ?: return
        recognizer = null
        try {
            active.cancel()
        } catch (_: RuntimeException) {
            // Best-effort cancellation; destroy below still releases the service.
        } finally {
            active.destroy()
        }
        if (!released) onStatusChanged(Status.IDLE)
    }

    /** Speaks only text explicitly supplied by the caller (normally a real assistant reply). */
    fun speak(text: String) {
        if (released || text.isBlank()) return
        val engine = textToSpeech
        if (!ttsReady || engine == null) {
            onError("Text-to-speech is not ready on this device.")
            return
        }
        stopListening()
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "allium-${System.nanoTime()}")
        if (result == TextToSpeech.ERROR) {
            fail("Text-to-speech could not start.")
        } else {
            onStatusChanged(Status.SPEAKING)
        }
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
        if (!released) onStatusChanged(Status.IDLE)
    }

    fun stopAll() {
        stopListening()
        stopSpeaking()
    }

    fun release() {
        if (released) return
        released = true
        recognizer?.let { active ->
            try { active.cancel() } catch (_: RuntimeException) { }
            active.destroy()
        }
        recognizer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        ttsReady = false
    }

    private fun initializeSpeechOutput() {
        onStatusChanged(Status.INITIALIZING)
        textToSpeech = TextToSpeech(appContext) { result ->
            if (released) return@TextToSpeech
            if (result == TextToSpeech.SUCCESS) {
                val engine = textToSpeech
                val languageResult = engine?.setLanguage(Locale.getDefault())
                ttsReady = languageResult != null &&
                    languageResult != TextToSpeech.LANG_MISSING_DATA &&
                    languageResult != TextToSpeech.LANG_NOT_SUPPORTED
                if (!ttsReady) onError("The device does not support text-to-speech in the current language.")
                if (recognizer == null) onStatusChanged(Status.IDLE)
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (!released) onStatusChanged(Status.SPEAKING)
                    }
                    override fun onDone(utteranceId: String?) {
                        if (!released) onStatusChanged(Status.IDLE)
                    }
                    @Deprecated("Deprecated by Android")
                    override fun onError(utteranceId: String?) {
                        if (!released) fail("Text-to-speech playback failed.")
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (!released) fail("Text-to-speech playback failed (code $errorCode).")
                    }
                })
            } else {
                fail("Text-to-speech initialization failed.")
            }
        }
    }

    private fun fail(message: String) {
        onStatusChanged(Status.ERROR)
        onError(message)
    }
}
