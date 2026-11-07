# AIRI Android Feature Parity

This document tracks the Android-specific capabilities to port from AIRI's `apps/stage-pocket` into NEXUS as native Android features.

## Source and porting rule

- Source project: https://github.com/moeru-ai/airi
- Audited source revision: `5673976c04bacf235009f24b42169a796c114b80`
- Source Android app: `apps/stage-pocket/android` (Capacitor Android host)
- Source mobile UI: `apps/stage-pocket/src`
- AIRI is MIT-licensed at the repository level. Before copying implementation or assets, review nested package, model, SDK, and asset licenses individually.
- NEXUS remains a native Kotlin/Jetpack Compose app. Port behavior and user-facing capability; do not transplant the Capacitor/Vue application shell.

## Android feature parity checklist

### Native platform integration
- [ ] Runtime microphone permission status, request flow, denial/permanent-denial handling, and a route to Android app settings.
- [ ] Notification permission status/request flow and local notification scheduling.
- [ ] External system-browser authentication, safe HTTP(S)-only URL validation, and OAuth/deep-link callback handling.
- [ ] App links/custom URI handling with strict callback validation.
- [ ] File/content URI sharing and scoped FileProvider integration where a NEXUS feature needs it.

### Voice and audio
- [ ] Audio input device enumeration and selection where Android exposes multiple inputs.
- [ ] Start/stop microphone capture with lifecycle-safe resource release.
- [ ] Audio recording/export/playback controls.
- [ ] Voice activity detection (VAD), speech start/end events, silence thresholds, and bounded audio buffers.
- [ ] Preserve NEXUS's explicit user-started microphone policy; no hidden always-on listening or background recording.

### Server connectivity
- [ ] Native WebSocket client/bridge for AIRI-compatible server channels.
- [ ] Secure TLS validation; never copy AIRI's permissive development certificate bypass into release builds.
- [ ] QR scanning for server-channel connection payloads.
- [ ] Validate QR payload schema, probe candidate endpoints, and store connection tokens securely.
- [ ] Connection state, reconnect/error reporting, and lifecycle cleanup.

### Mobile companion experience
- [ ] Mobile-first responsive Allium stage and chat layout.
- [ ] Live2D pause/resume and touch interactions adapted to Android lifecycle and screen size.
- [ ] Background/theme selection and settings surfaces that make sense on a phone.
- [ ] Permission onboarding and developer diagnostics only where useful to end users.

## Porting notes

AIRI Pocket is a Capacitor-hosted mobile client. Its Android-native host currently contributes microphone permission inspection, external-browser authentication, a native WebSocket bridge, Android app-link handling, and file-provider configuration. Several other mobile capabilities are implemented in its shared web layer (audio capture/recording, VAD, permission UI, local notifications, and QR scanning). NEXUS will implement equivalent behavior with Android APIs and Kotlin libraries rather than importing Vue/Capacitor runtime code.

The following AIRI desktop-only or browser-only utilities are not Android features and will not be copied as-is: desktop gesture controls, desktop window behavior, Electron integrations, and WebGPU-only developer experiments. Where a capability has an Android equivalent, it will be adapted rather than omitted.

## NEXUS-specific safeguards

- Keep NEXUS as one Android APK and retain the existing Allium Live2D renderer.
- Keep Android actions behind explicit user approval and auditable results.
- Do not weaken TLS, package visibility, microphone, notification, or storage permissions.
- Keep the free-first/local-first direction; avoid mandatory paid services.
- Every capability must include tests and be described as implemented only after the relevant build/tests pass.
- Device installation and real-device behavior must be tested separately from CI package validation.

## Delivery sequence

1. Native permissions, notifications, deep links, and external authentication.
2. Server-channel QR onboarding and secure WebSocket transport.
3. Audio input selection, recording lifecycle, and VAD.
4. Mobile companion UX and Live2D lifecycle refinements.
5. End-to-end Android validation and feature-by-feature parity review.
