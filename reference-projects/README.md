# NEXUS AI reference projects

This branch pins the upstream source repositories as Git submodules. This keeps the NEXUS production repository lean while making the complete upstream source trees available on checkout.

## Pinned references

| Project | Upstream | Pinned commit | License indicator |
|---|---|---|---|
| OpenJarvis | https://github.com/open-jarvis/OpenJarvis | `c5a7766e04d4d7dc114d3182bd07914f45a48b2d` | Apache-2.0 |
| OpenAmika | https://github.com/zeekk0/OpenAmika | `5d5e0568aff2de9f200ed2da21d0412efcdf8f79` | MIT; inspect bundled assets separately |
| AIRI | https://github.com/moeru-ai/airi | `5673976c04bacf235009f24b42169a796c114b80` | MIT; inspect bundled assets separately |

## Clone with source trees

```bash
git clone --branch research/ai-reference-projects --recurse-submodules https://github.com/mechy007/Nexus.git
```

If NEXUS is already cloned:

```bash
git submodule update --init --recursive
```

## Important

These are pinned upstream repositories, not copied ZIP archives. The uploaded ZIPs are retained in the working conversation, but GitHub's normal browser uploader cannot accept the large archives. Submodules provide the complete upstream source trees without copying approximately 137 MB of archives into NEXUS history.

Before adapting or redistributing any component, inspect its exact license and all nested/vendor asset licenses. A repository-level MIT or Apache license does not automatically cover every bundled dependency, model, voice, or artwork asset.
