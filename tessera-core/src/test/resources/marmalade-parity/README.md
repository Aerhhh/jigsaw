# Marmalade parity fixtures

Captured against the Marmalade-backed pipeline at commit `8b77d1a` on `2026-04-17`, before
the Marmalade swap removed `com.github.SkyBlock-Nerds:Marmalade:master-SNAPSHOT` and swapped
in `dev.matrixlab.webp4j:webp4j-core:2.1.1` plus `com.madgag:animated-gif-lib:1.4` behind
the sealed `net.aerh.tessera.core.image.AnimatedEncoder` interface.

These bytes are the contract that the Marmalade replacement must match. The test that
enforces this is `net.aerh.tessera.image.MarmaladeParityTest`.

| Scenario                             | Format       | Comparison mode  | Tolerance               |
|--------------------------------------|--------------|------------------|-------------------------|
| item-diamond-sword                   | PNG          | byte-equality    | none                    |
| item-enchanted-diamond-sword         | WebP animated| per-frame PSNR   | >= 45 dB                |
| item-enchanted-diamond-sword-gif     | GIF animated | decoded-frame PSNR | >= 40 dB (Decision 1A) |
| player-head-steve-upscaled-5x        | PNG          | byte-equality    | none                    |
| inventory-9-slot-hopper              | PNG          | byte-equality    | none                    |
| composite-2x2-grid                   | WebP animated| per-frame PSNR   | >= 45 dB                |
| tooltip-rarity-with-hex-color        | PNG          | byte-equality    | none                    |
| player-model-full-3d                 | PNG          | byte-equality    | none                    |

## Comparison-mode notes

Animated WebP uses PSNR because libwebp's animated container layout is non-byte-stable across
versions and between encoders.

PNG is deterministic via `javax.imageio.ImageIO.write` for a fixed pixel array, so we use
byte-equality there.

GIF uses **decoded-frame PSNR** rather than byte-equality (Decision 1A locked by the user).
Although both the Marmalade pre-swap pipeline and the Tessera post-swap `GifEncoder` use
Kevin Weiner's `com.madgag:animated-gif-lib:1.4` under the hood, the encoder performs lossy
colour quantisation when the input palette exceeds 256 colours, and the quantiser's internal
node ordering is not deterministic across invocations (it depends on heap allocation order in
some paths). Byte-diff on GIF is therefore unsafe even with the same encoder. PSNR on decoded
frames tolerates quantiser re-ordering while still catching real pixel drift.

## Player-head and player-model fixtures - Decision 5 note

Two fixtures (`player-head-steve-upscaled-5x` and `player-model-full-3d`) cannot be produced
through the standard `PlayerHeadGenerator.render` / `PlayerModelGenerator.render` call sites
because those paths go through `SkinLoader -> java.net.http.HttpClient`, and `HttpClient`
rejects `file:` URIs with `IllegalArgumentException: unsupported scheme`. To avoid a network
dependency in the parity test, both fixtures are captured and verified by invoking the
public-static renderers (`IsometricSkullRenderer.render`, `IsometricPlayerRenderer.render`)
directly against the synthetic Steve skin at `skins/steve.png`. The upscale step for the
head fixture still exercises the call that the Marmalade swap is replacing.

`skins/steve.png` itself is a synthetic 64x64 ARGB image generated deterministically by the
now-deleted `MarmaladeFixtureCaptureHarness`. It is NOT the Mojang canonical Steve skin; it
is synthesised to avoid licensing ambiguity while Tessera is pre-1.0.
