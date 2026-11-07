# OpenAmika Capability Parity for NEXUS

## Audited source

- Project: https://github.com/zeekk0/OpenAmika
- Audited revision: `5d5e0568aff2de9f200ed2da21d0412efcdf8f79`
- Source license: MIT for OpenAmika's own code (`LICENSE`); retain copyright and license notices for any code adapted.
- Repository: https://github.com/zeekk0/OpenAmika

OpenAmika is documented as a Windows-first companion stack. It is not an Android application. NEXUS will port the useful behavior and architecture into its existing Kotlin Android runtime, not embed OpenAmika's Windows/Python process launcher or web UI.

## Feature inventory and Android adaptation

### Conversation intelligence
- [ ] Fast surface response followed by a deeper answer, with duplicate-response suppression.
- [ ] Separate reflection/planning pass that can produce bounded hints and persistent plans.
- [ ] User-controlled proactive check-ins and idle prompts, with quiet hours and opt-out.
- [ ] Emotion-tagged sentence output that can drive Allium expression/motion at sentence playback start.
- [ ] Inner-thought/private reasoning separation: never expose hidden reasoning; surface only concise user-safe summaries or plans.

### Long-term memory
- [ ] Evidence-backed memory records with exact source quotes, dates, speaker attribution, uncertainty, and provenance.
- [ ] Correction/supersession history rather than silently overwriting prior memories.
- [ ] Hybrid retrieval (keyword/BM25 plus embeddings) with relevance limits and source-aware prompt rendering.
- [ ] Durable queued memory extraction with bounded retries and recovery after process restart.
- [ ] User controls to inspect, correct, export, and delete memories and their source conversations.
- [ ] Privacy boundaries between resident agents and explicit shared-memory permissions.

### Voice experience
- [ ] Streaming speech pipeline with partial transcripts and sentence-level playback.
- [ ] Voice activity detection and endpointing; interrupt/barge-in that stops playback and anchors the next turn correctly.
- [ ] Sentence splitting, emotion metadata, speech queueing, and playback acknowledgements.
- [ ] Text-to-speech provider adapter and Android-native fallback; speech output must remain usable without a proprietary voice model.
- [ ] Voice settings: language, speech rate, output device/volume, and user-selected voice.
- [ ] Keep microphone capture user-initiated and permission controlled; no covert or always-on recording.

### Avatar and expressive companion
- [ ] Reuse NEXUS's existing Allium Live2D model and renderer; adapt the event contract for expressions, motions, mouth movement, and audio synchronization.
- [ ] Configurable emotion-to-expression/motion banks for user-owned or properly licensed Live2D models.
- [ ] Audio-driven lip sync and lifecycle-safe pause/resume.
- [ ] Optional second agent/status avatar only if it fits NEXUS's resident-agent UX and does not duplicate Allium.

### Tools and delegated agents
- [ ] MCP client adapters for stdio-capable local services where Android supports process execution, and streamable HTTP for remote MCP servers.
- [ ] Tool discovery, capability summaries, health checks, enable/disable, and per-tool allow/confirm/deny policy.
- [ ] Delegated task lifecycle: ask, status, answer/clarify, approve, cancel, and result relay.
- [ ] Progress and approval requests shown in the conversation with explicit user control.
- [ ] Agent health, timeout, bounded retry, and audit history.

### Messaging and integrations
- [ ] Optional Telegram and Discord connectors through supported APIs, with account linking and per-chat allow-lists.
- [ ] Inbound message notification and user-approved reply flow; no sending as the user without confirmation.
- [ ] Smart-home adapter architecture (Home Assistant or compatible remote service) with read-only discovery/state first.
- [ ] Dangerous device categories remain blocked; state-changing actions require strong confirmation and an independent safety review.

### Vision and context
- [ ] Optional image input and vision-model adapter, using Android photo picker/camera permission flows.
- [ ] Contextual scene or place awareness only with explicit permission and clear indicators.
- [ ] Do not assume OpenAmika's PC GPU vision models can run on an Android phone; use supported on-device models or remote providers.

### Cost, reliability, and operations
- [ ] Request-level token and cost ledger, model pricing snapshots, budget reservations, and clear estimated-versus-confirmed charges.
- [ ] Provider health, model-role routing, and local/cloud fallback policies.
- [ ] Bounded queues, durable task state, cancellation, retry policy, and observability without logging prompts, credentials, or private content.
- [ ] Settings UI for model roles, tool servers, memory, voice, integrations, and usage.

## Important licensing and portability boundaries

- OpenAmika's own repository code is MIT. Preserve its copyright/license notices when adapting substantial portions.
- MekaHime/Amika character name, design, likeness, Live2D model, voice, and helper paper-doll assets are separately restricted. The upstream notices describe the model and voice as personal-use assets for users 18+, non-redistributable, and not covered by the repository MIT license. **Do not copy or bundle those assets into NEXUS.** NEXUS continues to use Allium and assets the user has rights to use.
- Live2D Cubism Core has its own proprietary redistribution terms; NEXUS must continue using its separately licensed SDK/runtime distribution.
- SenseVoice model weights, FunASR model assets, GPT-SoVITS pretrained models, embeddings, and optional vision models have their own licenses and hardware requirements. Review each model card/license before adoption.
- OpenAmika's documented TTS server and core stack expect a Windows/NVIDIA GPU workstation and substantial local storage/VRAM. Do not transplant that server into the Android APK. Implement provider adapters and Android-native fallbacks instead.
- Hermes Agent and nanoMuse are separate programs, not bundled OpenAmika components. NEXUS should integrate task-agent protocols through its own adapter boundary, not bundle their executables.

## NEXUS-specific implementation principles

- One native Android APK; no separate OpenAmika APK or mandatory PC companion.
- Allium remains NEXUS's avatar; do not replace Allium with Amika or copy Amika's proprietary model/voice.
- Reuse NEXUS's existing agent runtime, memory, provider routing, and Android approval bridge where they already satisfy a capability; do not create duplicate subsystems.
- Preserve one-time approval for Android actions and add per-tool policy controls for remote integrations.
- Keep the design free-first/local-first where practical, but do not promise large local speech/vision models will run on a phone.
- Do not claim a feature is complete until its implementation and relevant tests pass on CI; Android device behavior still needs real-device validation.

## Suggested delivery sequence

1. Compare existing NEXUS core against the feature inventory and mark reuse / gap / not applicable.
2. Add evidence-backed memory controls and bounded reflection/planning without exposing private reasoning.
3. Add streaming voice, interruption/barge-in, sentence-level emotion events, and Allium synchronization.
4. Add the MCP/tool policy layer and delegated task lifecycle.
5. Add optional messaging, vision, and safe smart-home adapters behind opt-in permissions.
6. Add usage/budget visibility, resilience tests, and Android end-to-end validation.
