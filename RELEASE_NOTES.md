# Skyweaver 1.0.0

Clean, data-driven custom skies for **Minecraft 26.1** on **NeoForge 26.1.0.19-beta**.

## What's in this release

- `skyweaver-1.0.0.jar` — client-side NeoForge mod.
- `Skyweaver-4K-Skies.zip` — 4K resource pack with clean day, sunset, night and midnight panoramas.

## Features

- Four 4096 × 2048 equirectangular skies with smooth time-of-day crossfades.
- Moving sun and moon discs.
- No horizon overlay or mountain ring: the skyline is clean and unobstructed.
- Resource-pack JSON format for custom skyboxes, dimensions and optional celestial sprites.
- Official NeoForge hooks only: `CustomSkyboxRenderer`, `ExtractLevelRenderStateEvent` and
  `RegisterRenderPipelinesEvent`. No mixins and no terrain patches.
- Sodium/Lithium-safe by design; Iris shader packs are detected and take priority.
- Built-in 1024 px fallback skies so the mod works without the 4K pack.

## Install

1. Install NeoForge 26.1.0.19-beta for Minecraft 26.1 (Java 25).
2. Put `skyweaver-1.0.0.jar` in `mods/`.
3. Put `Skyweaver-4K-Skies.zip` in `resourcepacks/` and enable it in Options → Resource Packs.
4. Launch the world.

## Configuration

`config/skyweaver-client.toml`:

- `enabled` — toggle custom skies.
- `skybox` — active definition, default `skyweaver:default`.
- `respect_shader_packs` — yield to active Iris shader packs.

## Compatibility notes

- Sky rendering was smoke-tested on NeoForge 26.1.0.19-beta; the 4K pack loads as resource format 84.
- Sodium, Lithium and Iris were not installed in the test instance. Compatibility is by construction:
  no mixins, no terrain access, and Iris detection.
- OptiFine is not available for NeoForge 26.1; no OptiFine code is included.
- Default target is the Overworld. Add dimensions to a skybox definition to extend it.
- One full-moon texture is used; moon phase variants are not included.

## Source and license

Full source, the procedural texture generator and pack sources are in the repository.
MIT licensed.
