# Android AI provider runtime

The Android host now connects chat to the NEXUS core coordinator through an Android-native provider adapter.

## Supported providers

The Settings screen includes OpenAI, OpenRouter and Groq presets. Each preset uses an OpenAI-compatible `/chat/completions` endpoint. The model and HTTPS base URL are editable for compatible deployments.

## Credential handling

- API keys are encrypted with AES-GCM using a non-exportable key held by Android Keystore.
- Only the encrypted value and IV are stored in app-private SharedPreferences.
- The key is never placed in provider profiles, chat messages, or logs.
- Disconnect removes the saved credential and provider configuration.
- Use HTTPS endpoints only; the UI rejects non-HTTPS URLs.

## Conversation handling

- Chat requests pass through `NexusCoordinator` and the core provider router.
- The primary Allium agent supplies the system instruction.
- Session messages are persisted in the app-private files directory by `JsonFileSessionStore`.
- Conversation content is sent to the selected provider when the user submits a message. The provider's own retention and data policies apply.
- A provider is considered configured after saving credentials; the first real message verifies the key, model and endpoint.
- Provider errors are shown as a generic recovery message so upstream response bodies and possible sensitive data are not exposed.

## Voice behavior

- Speech recognition remains explicitly user-started.
- Text entered after a voice transcript is sent through the same NEXUS coordinator.
- Allium speaks the generated reply only for a voice-initiated turn. Typed chat replies are not spoken automatically.
- Always-on listening, wake-word detection, streaming audio and lip-sync are not included in this milestone.

## Current limits

- The Android adapter currently supports non-streaming OpenAI-compatible text chat.
- Gemini's distinct Generative Language API format, tool calling, vision, streaming and provider health routing are not included in this adapter.
- Local model execution remains a separate provider integration.
