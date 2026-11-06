# Allium Android Companion

Allium is the user-facing companion identity and interaction layer of NEXUS.
NEXUS remains responsible for reasoning, model routing, memory, tools and policy.
Allium is not a decorative avatar and does not replace the NEXUS intelligence runtime.

## Runtime boundaries

- **Companion state:** `AlliumCompanionRuntime` is a platform-independent reducer for listening, thinking, speaking, interruption, error and emotion state.
- **Android host:** owns microphone permission, speech recognition, text-to-speech, lifecycle, notifications and battery-aware background behavior.
- **Live2D adapter:** maps companion state to Allium model expressions, motions and mouth parameters.
- **NEXUS intelligence:** supplies actual responses, memory retrieval, web research and permission-gated tool execution.

## Event contract

Android and intelligence adapters publish events such as `ListeningStarted`,
`TranscriptUpdated`, `ThinkingStarted`, `ResponseChunk`, `SpeechMouthLevel`,
`Interrupted` and `ResponseCompleted`. The reducer returns a new immutable state.
The Live2D adapter can observe that state to select a motion/expression and animate
mouth opening. Mouth values are normalized and clamped to 0..1.

## Safety and honesty

This state model performs no microphone, network, TTS, model or device operation.
It must not be presented as a working voice or AI integration by itself.
Microphone/background access remains explicit and user-controlled. Tool execution
continues to use NEXUS permission checks and audit events. Memory retention and
deletion controls remain separate from character emotion.

## Implementation sequence

1. Add and test the shared companion state contract.
2. Connect the Android speech input and TTS lifecycle to companion events.
3. Connect streamed NEXUS responses and cancellation/interruption.
4. Map emotion and speech amplitude to the supplied Allium Live2D model.
5. Add user-controlled memory and proactive presence, with battery-aware Android services.
6. Validate on a physical Android device before describing each capability as complete.

OpenAmika is a reference for interaction patterns (streaming, interruption and avatar
synchronization), not a runtime to transplant onto Android. Its GPU-oriented Python
speech stack is not an Android dependency.
