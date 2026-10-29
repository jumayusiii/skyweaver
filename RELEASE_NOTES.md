# Skyweaver 1.2.0

Clean, data-driven custom skies for **Minecraft 26.1** on **NeoForge 26.1.0.19-beta**.

## What's new in 1.2.0

- **Added a dedicated noon panorama.** Solar noon is no longer just the day texture; it has its
  own brighter, higher-key sky with a high sun, crisp white cumulus and a deeper blue zenith.
- **The full 24-hour circle now wraps seamlessly.** Day → noon → sunset → night → midnight →
  sunrise → day is one continuous timeline. The `1.00` key returns to noon at `0.00` with no snap
  or visible seam.
- **Rebuilt the 4K texture detail.** Full-resolution cloud-edge erosion, a second high cirrus
  layer, sharper cumulus silhouettes, directional cloud shading and a finer 4K detail pass remove
  much of the old airbrushed softness.
- **Better light and night skies.** Tighter sun glow, a pink anti-solar belt at dawn/dusk, richer
  Milky Way structure, more varied stars and a brighter, shaped aurora at midnight.
- **Noon stays backward compatible.** `noon` is optional in a skybox JSON; packs without it reuse
  the day panorama, and packs without `sunrise` still reuse sunset.

## The cycle

| Normalized time | Sky |
| --- | --- |
| `0.00` | noon |
| `0.16`–`0.22` | day |
| `0.25` | sunset |
| `0.33`–`0.44` | night |
| `0.44`–`0.56` | midnight |
| `0.56`–`0.67` | night |
| `0.73`–`0.80` | sunrise |
| `0.86`–`0.94` | day |
| `1.00` | noon, wrapping to `0.00` |

The order is intentional: the game's sun angle is `0` at noon, `0.25` at sunset, `0.5` at
midnight and `0.75` at sunrise. All transitions use a smoothstep crossfade.

## What's in this release

- `skyweaver-1.2.0.jar` — client-side NeoForge mod.
- `Skyweaver-4K-Skies.zip` — 4K resource pack with day, noon, sunrise, sunset, night and midnight
  panoramas.

## Texture improvements

- Six 4096 × 2048 zenith-to-horizon panoramas, all regenerated.
- Sharper cumulus edges instead of the previous soft, stretched blobs.
- A thin cirrus layer above the cumulus for more natural depth.
- Directional cloud shading with darker undersides and sunlit rims.
- High-resolution cloud detail, so the 4K pack no longer depends entirely on a half-resolution
  cloud field.
- New noon palette: high sun, clear blue zenith, bright horizon and smaller, brighter clouds.
- Stronger sunrise/sunset glow and a subtle anti-solar twilight belt.
- More structured Milky Way, more star colour variation and a more visible midnight aurora.
- Clean horizon at every phase.

## Features

- Six 4096 × 2048 skies with smooth, wrapped time-of-day crossfades.
- Moving sun and moon discs.
- No horizon overlay or mountain ring: the skyline is clean and unobstructed.
- Resource-pack JSON format for custom skyboxes, dimensions and optional celestial sprites.
- Official NeoForge hooks only: `CustomSkyboxRenderer`, `ExtractLevelRenderStateEvent` and
  `RegisterRenderPipelinesEvent`. No mixins and no terrain patches.
- Sodium/Lithium-safe by design; Iris shader packs are detected and take priority.
- Built-in 1024 px fallback skies so the mod works without the 4K pack.
- Optional `noon` and `sunrise` keys keep older four-phase packs working.

## Install

1. Install NeoForge 26.1.0.19-beta for Minecraft 26.1 (Java 25).
2. Put `skyweaver-1.2.0.jar` in `mods/`.
3. Put `Skyweaver-4K-Skies.zip` in `resourcepacks/` and enable it in Options → Resource Packs.
4. Launch the world.

## Configuration

`config/skyweaver-client.toml`:

- `enabled` — toggle custom skies.
- `skybox` — active definition, default `skyweaver:default`.
- `respect_shader_packs` — yield to active Iris shader packs.

## Compatibility notes

- The 1.2.0 build was launched with the 4K pack at noon, day, sunset and midnight. No `ERROR` or
  `FATAL` entries appeared in the client log.
- The 1.1.1 renderer was verified on a clean 26.1 client and with Sodium 0.8.9+mc26.1.1,
  Lithium 0.24.7+mc26.1.2, Iris 1.10.9+mc26.1.1 and BSL Shaders 10.1.8. All loaded together;
  Skyweaver rendered with shaders off, and Iris owned the sky with BSL active.
- 1.2.0 changes the texture content and adds an optional texture key; it does not change the
  render pipeline or mixin surface.
- Iris 1.11.4 paired with Sodium 0.8.9 currently fails in Sodium mixin application with a missing
  `uniformBufferManager` field. That is an Iris/Sodium revision mismatch, not a Skyweaver error;
  use a matching pair.
- OptiFine is not available for NeoForge 26.1; no OptiFine code is included.
- Default target is the Overworld. Add dimensions to a skybox definition to extend it.
- One full-moon texture is used; moon phase variants are not included.

## Source and license

Full source, the procedural texture generator and pack sources are in the repository.
MIT licensed.
