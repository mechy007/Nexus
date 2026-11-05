# NEXUS

A modular, multi-agent personal AI system. **Build the engine first; add the native Android host later.**

NEXUS is not a browser-first product. Its domain logic is being designed independently of any UI or operating system so that a native Android application can host it when the core is stable.

## Core principles
- Lara, JARVIS, FRIDAY, and EDITH are resident agents in one system—not separate apps.
- Agents share sessions and approved memory, but have distinct profiles and policies.
- AI providers, storage, voice, avatars, and device capabilities are replaceable adapters.
- Tools run only through explicit permission checks and are auditable.
- Memory must support inspection, export, and deletion.
- A capability is not considered working until its implementation is tested.

## Planned capabilities
Multi-provider AI routing, agent coordination, persistent memory, voice input/output, permission-gated tools, search and file connectors, automation integrations, Live2D avatar support, and eventually a native Android host.

## Project layout
- `core/` — platform-independent models and contracts.
- `docs/` — architecture, roadmap, and security notes.
- Future `adapters/` — concrete provider, database, voice, and tool integrations.
- Future `app-android/` — native Android host, intentionally deferred.

## Current status
This first foundation commit defines the domain boundary only. It does not yet connect to AI services, persist data, process speech, render Live2D, or control Android devices.

See [architecture](docs/ARCHITECTURE.md), [roadmap](docs/ROADMAP.md), and [security](docs/SECURITY.md).
