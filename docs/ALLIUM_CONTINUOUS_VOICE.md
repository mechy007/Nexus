# Allium Continuous Voice Conversation

## What the Android voice flow does

1. The user explicitly taps the microphone and grants Android's `RECORD_AUDIO` permission when requested.
2. Android speech recognition emits partial results. NEXUS shows these in the chat composer only; partial text is never submitted.
3. When Android emits a final recognition result, NEXUS submits that transcript as a normal Allium chat turn.
4. The same configured provider and persisted conversation session used by typed chat generate the response.
5. A voice-originated turn is spoken through Android TextToSpeech. Typed chat remains silent.
6. While Allium is speaking, tapping the voice control stops playback. While recognition is active, tapping it cancels recognition.

## Privacy and platform boundaries

- Microphone activation remains user-initiated.
- This flow does not add a wake word, background microphone service, or continuous recording.
- Audio recognition is provided by the Android speech-recognition service. Provider requests contain the recognized text and conversation history, not raw microphone audio.
- Partial recognition text is local UI draft state and is not sent to the AI provider.
- The app does not claim a transcript is final until Android delivers `onResults`.

## Manual device validation

- Deny microphone permission: voice input stays off and typed chat remains usable.
- Grant permission and speak a sentence: partial words appear in the composer; no provider request should occur until final recognition.
- Finish speaking: exactly one user chat turn is submitted automatically.
- Configure an AI provider: the resulting answer is spoken aloud.
- Tap the voice control during listening: recognition is cancelled.
- Tap the voice control during TTS playback: playback stops.
- Send a typed message: the answer appears but is not spoken.
- Navigate away from chat or destroy the activity: the voice controller is released with the composable lifecycle.
