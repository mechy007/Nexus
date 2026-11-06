# Allium Android voice pipeline

This milestone adds native Android speech input and speech output primitives for Allium.

## Speech input

- Uses Android `SpeechRecognizer` and the device's configured language.
- Requests `RECORD_AUDIO` only after the user taps the microphone.
- Supports partial and final transcripts in the chat composer.
- Stops the active recognition session when the user taps the microphone again.
- Releases the recognizer when the Compose screen leaves composition.

## Speech output

- Initializes Android `TextToSpeech` using the device's default locale.
- Exposes explicit `speak`, `stopSpeaking`, and `stopAll` operations for the assistant response pipeline.
- Reports initialization and playback failures instead of pretending speech succeeded.
- Does not automatically read placeholder/offline messages. The future connected response pipeline must pass actual assistant response text to `speak`.

## Privacy and scope

- No always-on microphone, wake-word listener, background recording, or audio upload is introduced.
- Speech recognition behavior and network processing depend on the Android recognition service selected by the device; this adapter does not claim that recognition is offline.
- The current chat remains honest that no AI provider is connected. Voice transcription does not itself generate an AI answer.
- This is the Android platform adapter foundation for the AIRI-inspired voice pipeline, not a complete streaming duplex voice conversation yet.
