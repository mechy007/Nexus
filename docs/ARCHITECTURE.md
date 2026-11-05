# Architecture

## Direction
NEXUS is a platform-independent assistant engine. It is not a browser application. A future native Android host will call the same core contracts without embedding business logic in UI code.

## Layers
1. **Domain:** sessions, messages, agent profiles, provider capabilities, memory records, skill manifests, traces, and tool requests.
2. **Orchestration:** agent selection, context assembly, provider routing, permission checks, loop protection, trace recording, and audit.
3. **Adapters:** concrete AI providers, databases, speech engines, Live2D renderer, search, files, and automation connectors.
4. **Host:** eventually native Android UI and device-specific integrations.

## Resident agents
Lara, JARVIS, FRIDAY, and EDITH are profiles using the same session, routing, memory, and permission infrastructure. Their personalities and allowed capabilities can differ.

## Provider routing
Providers declare capabilities. The router must select only enabled providers that satisfy the requested capabilities. Fallback must preserve required capabilities. Credentials are injected at runtime and never stored in source control.

## Memory
Conversation history and long-term memory are distinct. The storage adapter must support search, export, and deletion. Users must be able to inspect and control stored memories.

## Skills and tools
Skills are declarative manifests with explicit capability requirements and provenance/trust tiers. Loading a skill never grants permissions. Unreviewed skills are denied by default. Tool execution remains behind a permission manager and host confirmation; the loop guard must be checked before each tool call. Executions are audited.

## Traces
Agent traces record routing, memory retrieval, generation, tool-call, response and failure metadata. Raw prompts, credentials and tool arguments are excluded by default. Trace storage is replaceable and must support inspection and deletion.

## Voice and avatar
Speech recognition, speech synthesis, wake-word detection and Live2D are adapters. The domain core must not depend on Android audio APIs or a particular avatar SDK. Allium/Cubism compatibility remains unverified until the archives are inspected and tested.

## Android
Android-specific permissions, background work, audio and device actions belong in a future native host and adapter modules. APK packaging is deferred until the engine is stable.
