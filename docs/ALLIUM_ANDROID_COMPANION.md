# Allium Android Companion

Allium is the user-facing companion identity and interaction layer of NEXUS.
NEXUS remains responsible for reasoning, model routing, memory, tools and policy.
Allium is not a decorative avatar and does not replace the NEXUS intelligence runtime.

## Runtime boundaries

- **Companion state:** `AlliumCompanionRuntime` is a platform-independent reducer for listening, thinking, speaking, interruption, error and emotion state.
- **Persona/context:** `AlliumPersona` owns Allium's identity and conversational guidance. It accepts optional user, locale, scene and mood context without importing another character's identity or story.
- **Conversation protocol:** `AlliumConversationProtocol` models streamed surface/deep response phases, protects a turn when speech starts, records a short interruption marker and ignores stale chunks from older turns.
- **History selection:** `AlliumHistoryWindow` selects a stable, bounded recent-history window; `AlliumRecallIntentDetector` flags explicit recall questions so the host can prioritize memory retrieval.
- **Android host:** owns microphone permission, speech recognition, text-to-speech, lifecycle, notifications and battery-aware background behavior.
- **Live2D adapter:** maps companion state to Allium model expressions, motions and mouth parameters.
- **NEXUS intelligence:** supplies actual responses, memory retrieval, web research and permission-gated tool execution.

## OpenAmika adaptations

OpenAmika is a reference for companion interaction, not a runtime to transplant onto Android. NEXUS adapts selected portable ideas in Kotlin:

- The separation of persona identity, scene context and conversation style.
- The surface/deep response phases and turn IDs from its streaming protocol.
- Protect-before-TTS and short interruption markers for barge-in.
- Step-based history windows and explicit recall-intent detection.

The Android implementation does not include OpenAmika's Flask/Socket.IO server, Python runtime, CUDA speech stack, local embedding server, provider billing/telemetry, or desktop UI. It does not copy Amika's name, character design, voice or persona story. See `LICENSE-OPENAMIKA` and `THIRD_PARTY_NOTICES.md`.

## Event contract

Android and intelligence adapters publish events such as `ListeningStarted`,
`TranscriptUpdated`, `ThinkingStarted`, `ResponseChunk`, `SpeechMouthLevel`,
`Interrupted` and `ResponseCompleted`. The companion reducer returns a new
immutable state. The Live2D adapter can observe that state to select a
motion/expression and animate mouth opening. Mouth values are normalized and
clamped to 0..1.

## Safety and honesty

These core components are deterministic models and helpers; they do not perform
microphone, network, TTS, model or device operations by themselves. The Android
host must connect them to real services and report unavailable integrations
honestly. Microphone/background access remains explicit and user-controlled.
Tool execution continues to use NEXUS permission checks and audit events. Memory
retention and deletion controls remain separate from character emotion.

## Implementation sequence

1. Add and test the shared companion state contract.
2. Add Allium-owned persona/context and streamed-turn protocol.
3. Connect the Android speech input and TTS lifecycle to companion events.
4. Connect streamed NEXUS responses and cancellation/interruption.
5. Map emotion and speech amplitude to the supplied Allium Live2D model.
6. Add user-controlled memory and proactive presence, with battery-aware Android services.
7. Validate on a physical Android device before describing each capability as complete.
