# NEXUS

A modular, multi-agent personal AI system with a native Android application.

NEXUS owns its agent runtime and intelligence lifecycle. AI providers, search services, browser backends, storage and device capabilities are replaceable adapters.

## Core principles
- Lara, JARVIS, FRIDAY, and EDITH are resident agents in one system—not separate apps.
- Agents share sessions and approved memory, but have distinct profiles and policies.
- Multi-model reasoning is bounded and used when task complexity justifies it.
- Hybrid routing checks capabilities, privacy, quality, latency, cost and provider health.
- Web research returns source links and treats page content as untrusted evidence.
- Automatic learning promotes only candidates that pass evaluation; security and permission policies are protected.
- Tools run only through explicit permission checks and are auditable.
- A capability is not considered working until its implementation is tested.

## Android app

The repository includes a native Android app in `app/`.

- Application ID: `com.mechy007.nexus`
- Minimum Android version: Android 8.0 (API 26)
- Target Android version: API 36
- The current CI pipeline builds a debug APK and verifies its package ID, SDK metadata, APK archive integrity and debug signature.

### Download and install the CI APK

1. Open the latest successful **NEXUS Android CI** run in the repository's Actions tab.
2. Download the **nexus-installable-apk** artifact. GitHub downloads workflow artifacts as ZIP files.
3. Extract that ZIP on your Android device.
4. Open **nexus-debug.apk** from the extracted folder—not the ZIP file.
5. If Android asks, allow the file manager or browser to install unknown apps.

The artifact also includes `INSTALL.txt` and `SHA256SUMS.txt`. CI checks the APK before publishing the artifact. These checks verify the package structure and signature, but cannot guarantee installation on every device.

**Debug-build update note:** GitHub-hosted CI creates a temporary debug signing key for each run. A later build may therefore not install as an in-place update over an earlier CI build. Uninstalling the older app can remove its local app data, so back up anything important before doing that.

## AI capabilities
- Multi-model planner, solver, critic and synthesizer roles.
- Capability-first local/cloud model routing with cost and privacy constraints.
- Bounded conversation and memory context construction.
- Search and page-reading adapters, including Brave Search and an HTTPS page fetcher.
- Citation-bearing web research integrated into the AI request context.
- Evaluation-gated automatic learning with versioned configuration promotion.

## Project layout
- `core/` — platform-independent models, orchestration and adapter contracts.
- `app/` — native Android host.
- `docs/` — architecture, implementation and security notes.
- `integration-staging/` — staged third-party integration assets and attribution.

## Integration status
The core contains the orchestration and adapter implementations. Real AI provider credentials and model metadata, durable learning/trace stores, candidate evaluators and an isolated browser automation backend still require host configuration. No local model is bundled, and no unrestricted device control or background listening is claimed.

See [AI Intelligence Core](docs/AI_INTELLIGENCE_CORE.md), [Web Intelligence](docs/WEB_INTELLIGENCE.md), [Automatic Learning](docs/AUTOMATIC_LEARNING.md), [Architecture](docs/ARCHITECTURE.md), and [Security](docs/SECURITY.md).
