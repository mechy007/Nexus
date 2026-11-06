# NEXUS Android UI Direction

## Decision

NEXUS uses a native Jetpack Compose interface. The visual direction combines selected interaction patterns from AIRI, OpenAmika and OpenJarvis without embedding or copying any of their application shells.

- **AIRI Stage Pocket:** primary reference for a character-first mobile home, with the Live2D character as the visual center and compact interaction controls.
- **OpenAmika:** reference for a companion-oriented conversation experience and the relationship between the character and chat. Its purple/gothic palette is intentionally not adopted.
- **OpenJarvis frontend:** reference for clear navigation, readable chat surfaces, connection/health visibility and restrained blue-accent design. Its desktop sidebar and dashboard density are not adopted on Android.

## NEXUS visual system

- Light theme: sky blue, white and cool pale-blue surfaces.
- Dark theme: midnight blue, deep navy surfaces and soft sky-blue accents.
- No purple/violet or neon gradient treatment.
- Allium remains the primary companion and the main visual focus of Home.
- Use native Android Compose components and the existing Live2D Cubism renderer.
- Use bottom navigation for Home, Chat, Browser, Mind Space and Settings.
- Keep the AI connection state visible and truthful.
- Unimplemented features must be labelled as unavailable or in development; do not present mock functionality as working.

## Screen responsibilities

- **Home:** Allium stage, quick access to Chat/Browser/Mind Space, and AI connection status.
- **Chat:** conversation history, text entry and Android speech-recognition handoff. The UI explicitly states that messages are not sent to an AI provider while no provider is connected.
- **Browser:** honest unavailable state until browser/search integration exists.
- **Mind Space:** NEXUS Core foundation and Allium as the primary companion; future residents are not shown as configured.
- **Settings:** light/midnight theme selection and truthful AI-provider status.

## Integration boundaries

The UI is an Android-native presentation layer. AIRI, OpenAmika and OpenJarvis remain upstream references for selected capabilities. Their Vue, React, browser, desktop or Python application shells are not dependencies of the NEXUS Android app.

The Live2D model and Cubism SDK remain governed by their own asset and SDK terms. This UI change does not copy upstream character art, voice recordings or model assets.
