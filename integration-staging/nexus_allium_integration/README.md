# NEXUS Allium Live2D integration bundle

This bundle adds the official Live2D Cubism Java Framework source, the Cubism Core Android AAR supplied in the user's SDK archive, the Android-adapted Allium model, and a GLSurfaceView renderer embedded in the existing Compose home screen.

## Included implementation
- Live2D Cubism Java Framework sources from Cubism SDK for Java 5-r.5.
- `Live2DCubismCore.aar` with ARM64, x86 and x86_64 native libraries.
- Allium Android-adapted model: `ariu.moc3`, model3/physics/display metadata and phone-optimized 1024px texture atlases (downscaled from the supplied Android 2048px atlases to reduce GPU memory use).
- Official Cubism Java sample renderer/texture/model support classes, adapted to render only the model on the NEXUS avatar surface.
- Allium is the default avatar; NEXUS Orb remains selectable.
- OpenGL ES 2.0 surface with touch forwarding, physics, breath and eye-blink support as configured by the model.

## Build
Merge the `app/` folder into the NEXUS Android app module. The app module needs the existing Compose dependencies and `implementation(files("libs/Live2DCubismCore.aar"))`. Build with the repository's Android Gradle workflow.

## Model credits and restrictions
Allium model artist credit: 早坂幽里. The model was supplied as free material. The included `licenses/Allium-Model-Terms.txt` is the model creator's usage notice. Do not resell the model; do not create R18 derivatives; preserve artist attribution; do not use it for targeted defamation, abusive, violent or otherwise prohibited uses described in the notice.

## Live2D SDK licensing
The Cubism Java Framework, Cubism Core AAR and sample sources have separate licensing terms. See all files in `licenses/` and the original Live2D license pages before distributing builds. This bundle does not grant additional redistribution or publication rights.

## Texture sizing
The supplied Android model contains fourteen 2048×2048 RGBA atlases, which would require about 224 MiB of uncompressed texture memory before renderer overhead. This bundle downsizes them to 1024×1024, reducing the theoretical atlas memory to about 56 MiB. The original upload remains the source of truth; the optimized textures are intended for the compact in-app avatar viewport.
