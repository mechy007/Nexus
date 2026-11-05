# Architecture

## Direction
NEXUS is a platform-independent assistant engine. Its own runtime owns sessions, task state, permissions, tools and execution. A future native Android host will call the same core contracts without embedding business logic in UI code.

## Layers
1. **Domain:** sessions, messages, agent profiles, provider capabilities, memory records, skill manifests, traces and tool requests.
2. **Intelligence:** request understanding, multi-model reasoning, hybrid model routing, context construction, evaluation, web research and controlled automatic learning.
3. **Orchestration:** agent selection, provider selection, permission checks, loop protection, trace recording and audit.
4. **Adapters:** concrete AI providers, search engines, page fetchers, browser automation, databases, speech engines and device connectors.
5. **Host:** eventually native Android UI and device-specific integrations.

## AI intelligence
The intelligence coordinator classifies intent and complexity, builds bounded context, optionally gathers current web evidence, selects a reasoning depth, coordinates planner/solver/critic/synthesizer roles and evaluates the result. Simple tasks use one model; more complex tasks may use multiple providers. Calls and output sizes are bounded.

The hybrid router filters capabilities and privacy constraints before scoring measured quality, reliability, latency and cost. Unknown providers are cloud providers. Local-only routing requires explicit local metadata. Provider session state must not be switched mid-tool-loop unless a safe boundary is available.

Automatic learning records outcomes and tests candidate routing, prompt, context or reasoning configuration against a baseline. It cannot modify identity, safety rules, permissions, privacy settings or retention policy. Promotion is version-checked and evaluation-gated.

## Web intelligence
Search providers are replaceable adapters. The included Brave adapter resolves its API key at runtime. Page reading is HTTPS-only, checks redirects and public network destinations, limits content types and response size, and returns source citations. Web content is untrusted evidence, not instructions. Interactive browser automation remains a separate host adapter behind existing permissions and user confirmation.

## Resident agents
Lara, JARVIS, FRIDAY and EDITH are profiles using the same session, routing, memory and permission infrastructure. Their personalities and allowed capabilities can differ.

## Provider routing
Providers declare capabilities. The router must select only enabled providers that satisfy every requested capability. Fallback must preserve required capabilities. Credentials are injected at runtime and never stored in source control.

## Memory
Conversation history and long-term memory are distinct. The storage adapter must support search, export and deletion. Users must be able to inspect and control stored memories.

## Skills and tools
Skills are declarative manifests with explicit capability requirements and provenance/trust tiers. Loading a skill never grants permissions. Unreviewed skills are denied by default. Tool execution remains behind a permission manager and host confirmation; the loop guard must be checked before each tool call. Executions are audited.

## Traces
Agent traces record routing, memory retrieval, generation, tool-call, response and failure metadata. Raw prompts, credentials and tool arguments are excluded by default. Trace storage is replaceable and must support inspection and deletion.

## Voice and avatar
Speech recognition, speech synthesis, wake-word detection and Live2D are adapters. The domain core must not depend on Android audio APIs or a particular avatar SDK. Allium/Cubism compatibility remains deferred until the model archives are inspected and tested.

## Android
Android-specific permissions, background work, audio and device actions belong in a future native host and adapter modules. APK packaging is deferred until the engine is stable.
