# Android device actions and approval

NEXUS exposes a narrow set of foreground Android actions through its host bridge and agent tool registry:

- Open an installed app by its exact visible app name. App names are resolved locally; the installed-app inventory is not sent to the AI provider.
- Open a validated HTTP(S) link using Android's app chooser.
- Place text into Android's Sharesheet for the user to choose a destination and send.

## Approval lifecycle

1. The structured agent runtime may propose one of the registered actions.
2. The runtime validates the tool identity, trust tier, capability policy, and bounded arguments.
3. Android's permission manager always returns REQUIRES_USER_ACTION for these tools.
4. NEXUS displays the exact action and its relevant payload in a confirmation dialog.
5. Approval executes only the matching in-memory request once. It expires after five minutes, is discarded on process death, and cannot be reused.
6. Rejection discards the request without invoking the Android bridge. The action outcome is recorded in an app-private audit log without storing payload text.

The approval is request-specific: it does not create a standing permission or grant future actions. The runtime rechecks that the tool registration is unchanged before execution.

## Safety boundaries

- No silent message sending, recipient selection, app installation, background execution, or automatic follow-through.
- Launching an app is limited to launcher-visible installed apps.
- Opening a link hands control to Android's chooser; the user chooses the destination app.
- Sharing text opens the Android Sharesheet; NEXUS does not send the text. The user must choose an app and complete the send.
- Links must use HTTP or HTTPS, include a host, contain no embedded credentials, and stay within the length limit.
- Blank or oversized share payloads are rejected.
- A chooser being launched is not proof that a message was sent or a task completed.
- The action audit records metadata and outcomes only; it does not record action arguments or shared text.

## Current limitations

- The first structured agent integration supports app launch, URL chooser, and text Sharesheet only.
- The approval request is held in memory and intentionally fails closed after process death.
- The AI provider must return the strict JSON decision format. Invalid or unsupported tool requests fail without executing an action.
- Real-device verification is still required for app visibility, chooser behavior, and the approval UI across Android versions.
