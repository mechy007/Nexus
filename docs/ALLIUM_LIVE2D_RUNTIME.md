# Allium Live2D runtime

## Android rendering path

The Home screen hosts the actual `ariu` Cubism model in a dedicated `GLSurfaceView`. The Cubism Java Framework sources, Android Core AAR, model MOC, model metadata, physics data and optimized 1024px texture atlases remain in `integration-staging/nexus_allium_integration/`; the app Gradle source set and dependency point to that bundle.

The renderer is separate from the NEXUS AI coordinator. Chat, provider routing and voice remain usable if the avatar surface cannot be created or a GL frame fails.

## Startup and recovery

- Resolve the hosting `MainActivity` through nested Android `ContextWrapper` instances rather than assuming Compose exposes the Activity directly.
- Initialize the Cubism delegate before attaching the renderer.
- Defer model creation until the GL surface reports a positive width and height.
- On EGL context recreation, rebuild existing model renderers and textures in the new context.
- Ignore invalid model indices, missing model folders, empty model lists and invalid canvas dimensions rather than crashing the host.
- Read model assets to end-of-stream; `InputStream.available()` is not treated as the complete file length.
- Catch renderer runtime failures, log the cause, clear the GL surface and show a visible fallback message.
- Forward touch cancellation as a touch end; sample gear/power sprites are not part of the NEXUS UI.
- Pause the GL view when its Compose surface leaves composition and resume it with the Activity lifecycle.

## Animation and interaction

The Cubism model runtime drives its configured physics, breath and eye-blink parameters and model idle motions. Touch is forwarded to Cubism hit testing for supported head/body reactions. Lip-sync is not claimed: the current Allium model declares no lip-sync parameter IDs, and TTS amplitude/phoneme data is not connected to the renderer.

## Asset footprint

The model uses fourteen 1024×1024 texture atlases. Their approximate uncompressed RGBA texture memory is 56 MiB before renderer overhead. The larger original design preview is not bundled into the runtime app.

## Licensing and attribution

- Allium model artist: **早坂幽里**. Preserve attribution and comply with `licenses/Allium-Model-Terms.txt`.
- Live2D Cubism Core, Java Framework and sample renderer have separate license terms. See all three Live2D license documents in the same `licenses/` directory and review current upstream agreements before distributing the app.
- Do not resell the model or create R18 derivatives. Do not use it for targeted defamation, abusive, violent or prohibited purposes.
