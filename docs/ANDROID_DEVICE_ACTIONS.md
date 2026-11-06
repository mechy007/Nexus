# Android device action bridge

NEXUS now has a narrow Android-side bridge for three foreground actions:

- List launchable apps and launch an installed app by package name.
- Open a validated HTTP(S) link using Android's app chooser.
- Share text through the Android Sharesheet.

## Safety boundaries

- The bridge does not send messages, choose recipients, or complete a share on the user's behalf.
- Opening links and sharing text hands control to Android UI; the user chooses the destination and completes the action.
- Only launcher-visible installed apps are launchable. The bridge does not install apps.
- Web links must use HTTP or HTTPS, include a host, contain no embedded credentials, and stay within the length limit.
- Blank or oversized share payloads are rejected.
- Action results distinguish launched, no handler, rejected input, and host failure. A launched chooser is not proof that a message was sent or a task completed.
- The bridge itself does not request broad permissions, run in the background, or listen continuously.

## Integration status

This is the Android host adapter foundation. It is not yet exposed as an AI-callable tool or wired to a confirmation UI. The agent runtime must request user approval before sensitive actions, and the UI must display the exact action and payload before invoking the bridge. Future messaging support must continue to use an explicit user-facing app flow rather than silently sending messages.
