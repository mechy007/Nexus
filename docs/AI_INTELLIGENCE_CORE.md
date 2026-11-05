# AI Intelligence Core

## Decisions
- Reasoning: multi-model collaboration.
- Routing: hybrid, capability-first local/cloud selection.
- Learning: automatic candidate generation and evaluation-gated promotion.
- Runtime: NEXUS-owned; providers and specialist models are replaceable adapters.

## Request lifecycle
1. Understand intent and estimate complexity.
2. Build bounded context from recent conversation and retrieved memories.
3. Optionally gather current web evidence when research is requested or required.
4. Select a reasoning strategy and eligible providers.
5. Execute bounded planner, solver, critic and synthesizer roles.
6. Evaluate the response structurally and return contributions and evidence references.

## Multi-model reasoning
Simple requests use one model. Moderate and harder requests may use a solver and independent critic. Complex requests may add a planner and synthesizer. Provider IDs already used in a task are excluded where possible. Calls and output sizes are bounded. If a second model is unavailable, NEXUS falls back to the best available result and reports that independent review was unavailable.

The current core implements sequential role collaboration. Parallel specialist fan-out is a later optimization; it must preserve distinct-provider selection, cancellation, budget limits and deterministic synthesis.

The core stores concise role outputs and decisions, not hidden chain-of-thought.

## Hybrid routing
Capability filtering happens before scoring. Eligible models are scored using measured quality, reliability, latency, cost, preference and local execution metadata. Unknown providers are treated as cloud providers. Local-only requests exclude providers without an explicit local classification. Cost ceilings exclude providers with unknown or excessive cost estimates.

Metadata must be measured and refreshed by the host. Default scores are neutral estimates, not claims about provider quality.

## Context
Conversation, persistent memories, web evidence and task state remain separate inputs. The context builder applies message, memory and character limits. Retrieved memory and web content are explicitly marked untrusted.

## Evaluation
The included structural evaluator checks empty output and missing critic availability. It is not a factual verifier. Hosts can inject stronger evaluators backed by deterministic tests, trusted sources or domain-specific validators.

## Integration boundary
The current implementation is platform-independent Kotlin. A host must inject provider adapters, model metadata, memory retrieval, web providers and durable stores. The code does not claim to include a bundled local model or a ready-to-use Android UI.
