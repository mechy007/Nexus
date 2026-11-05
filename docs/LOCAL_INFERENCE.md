# Local inference adapter

NEXUS adds a provider adapter boundary for Google's LiteRT-LM runtime.

## Design
- The core owns model identity, absolute external model path, preferred backend, token budget, provider identity, and lifecycle.
- The Android host supplies a `LiteRtLmEngine` implementation backed by Google's `Engine` and `Conversation` APIs.
- Model files remain outside the APK and are selected/downloaded by the host.
- Initialization is explicit and must run off the UI thread. The host implementation owns LiteRT-LM's single-thread engine/conversation lifecycle and closes native resources exactly once.
- The local provider advertises text capability only. Streaming, vision, audio, and model-native tool calling are not advertised until implemented and device-tested.
- Set a lower numeric provider priority to prefer local inference. The existing capability-aware router can then fall back to enabled cloud providers when local inference throws.

## Platform boundary
This core module is Kotlin/JVM and currently has no Android application module. This PR therefore adds the tested provider/lifecycle contract without pulling Android AARs or native libraries into the core. The concrete `litertlm-android` adapter, model download UX, GPU manifest declarations, thermal/memory checks, and on-device benchmarking belong in the Android host milestone. Google's LiteRT-LM Kotlin API supports Android and JVM, and requires explicit engine initialization and cleanup.
