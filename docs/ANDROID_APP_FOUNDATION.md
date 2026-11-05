# NEXUS Android app foundation

This change introduces the first Android application module and a Compose-based interface.

## Included in this stage

- Android application module (:app) with a launcher activity.
- Dark NEXUS home/chat interface.
- Allium-first avatar presentation slot and an orb mode selector.
- Speech input hand-off through Android's installed speech-recognition activity.
- Android CI workflow that assembles a debug APK and runs the existing core tests.

## Explicitly not included yet

- The Allium character is currently represented by a temporary placeholder. The Cubism renderer and model assets are not bundled in this change.
- The chat screen does not call the NEXUS AI engine yet. It labels this state clearly rather than returning fabricated AI answers.
- Text-to-speech, background wake-word listening, provider credentials, Android tool permissions, and full agent execution are not wired in this stage.
- The :app module is deliberately not coupled to the JVM core until Android compatibility of the current core dependency graph is verified.

## Next integration steps

1. Validate the supplied Cubism SDK and Allium archive, then add the official Android renderer and the permitted model assets.
2. Add an Android host adapter for NexusCoordinator / AIIntelligenceCoordinator and verify all transitive dependencies on Android.
3. Connect chat submission, provider setup, persistent sessions, speech output, and explicit permission-gated device actions.
4. Produce and test a signed personal APK build.

The app is a foundation, not a completed assistant. Do not describe this stage as a working AI assistant or as a completed Live2D integration.
