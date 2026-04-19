# Tessera

**Pixel-perfect Minecraft image rendering for Discord bot authors and MC community tool
builders. Use as a JVM library or a self-hosted HTTP service. No Minecraft client required.**

Tessera produces pixel-perfect Minecraft visuals - items, tooltips, inventories, player
heads and 3D player models, in-game UI elements - from structured requests or raw NBT. It
targets tools that need faithful MC output without spinning up a game client.

If pixel fidelity slips, Tessera has no reason to exist; everything else is in service of
that.

## Status

Active development is landing on `feat/jigsaw-rewrite` (identity, licensing, and
asset-pipeline foundation). The library is not yet tagged 1.0 and the consumer-facing
coordinate is JitPack-only while the repository-rename to `Tessera` propagates. Expect
minor surface churn until 1.0; migration notes will be published alongside each breaking
change.

## Installation (library, JitPack)

Tessera publishes to JitPack. Add the JitPack repository and the `tessera-core` dependency:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.Aerhhh.Tessera</groupId>
    <artifactId>tessera-core</artifactId>
    <version>feat~jigsaw-rewrite-SNAPSHOT</version>
</dependency>
```

With the SkyBlock extension module:

```xml
<dependency>
    <groupId>com.github.Aerhhh.Tessera</groupId>
    <artifactId>tessera-skyblock</artifactId>
    <version>feat~jigsaw-rewrite-SNAPSHOT</version>
</dependency>
```

Replace the `feat~jigsaw-rewrite-SNAPSHOT` version with a tagged ref once a release is
published.

## Quickstart

```java
import net.aerh.tessera.api.Engine;
import net.aerh.tessera.api.assets.TesseraAssets;
import net.aerh.tessera.core.generator.ItemRequest;
import net.aerh.tessera.api.generator.GeneratorResult;

// Bootstrap: fetch Minecraft assets into the local cache. Blocking on first call; a
// cache hit on subsequent calls is ~O(1). Safe to call unconditionally at startup.
TesseraAssets.fetch("1.21.4");

// Build an engine pinned to a specific MC version.
Engine engine = Engine.builder()
        .minecraftVersion("1.21.4")
        .acceptMojangEula(true)
        .build();

// Render an enchanted diamond sword.
GeneratorResult result = engine.render(
        ItemRequest.builder()
                .itemId("diamond_sword")
                .enchanted(true)
                .scale(4)
                .build()
);
```

`Engine.builder().build()` declares
`throws net.aerh.tessera.exception.TesseraAssetsMissingException` (checked). Either
propagate it from your enclosing method or wrap the call in a try/catch that invokes
`TesseraAssets.fetch(...)` and retries. `engine.render(...)` likewise declares
`throws RenderException, ParseException`, both checked. The quickstart and examples below
omit those declarations for brevity.

## Mojang assets and EULA

Tessera does **not** ship any Minecraft assets inside its published jars. On first use
it downloads official Minecraft assets (textures, fonts, item models, icons) from Mojang's
CDN (`https://piston-meta.mojang.com/` and `https://resources.download.minecraft.net/`)
into a local cache directory owned by the machine running this library. Cold-cache first
run pulls roughly 4 MB for the bundled 1.21.4 manifest.

By running Tessera you agree that you have accepted the
[Mojang End-User Licence Agreement](https://www.minecraft.net/en-us/eula) and the
[Minecraft Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines). Acceptance
must be expressed through one of:

- **Programmatic:** `EngineBuilder.acceptMojangEula(true)` (or the three-arg overload
  `TesseraAssets.fetch(mcVer, cacheDirOverride, acceptedEula)` where the final `boolean`
  signals EULA acceptance).
- **Environment variable:** `TESSERA_ACCEPT_MOJANG_EULA=true`.
- **JVM system property:** `-Dtessera.accept.mojang.eula=true`.

If none of the three is set, Tessera refuses to download any Mojang bytes and throws a
`TesseraEulaNotAcceptedException` naming the three acceptance paths.

Tessera is not affiliated with, endorsed by, or a product of Mojang AB or Microsoft.

## Supported platforms

Tessera targets Java 25 with preview features enabled (`--enable-preview`). The WebP
encoder uses a small JNI native binary bundled for:

- Windows x64 / ARM64
- macOS x64 / arm64
- Linux x64 / ARM64

Environments with `noexec` on `/tmp` (some Docker configurations, some serverless
runtimes) need a writable + executable temporary directory for the JNI native to load.
Set `-Djava.io.tmpdir=/some/writable/exec/path` if your default `/tmp` is mounted
`noexec`.

## Supported Minecraft versions

Tessera 1.0.x targets Minecraft 1.21.x. The `minecraftVersion(...)` builder method selects
which asset manifest to load; additional versions are added by publishing new asset
artifacts.

## Modules

- **tessera-core** - core rendering engine. Generic Minecraft image generation with no
  game-specific dependencies.
- **tessera-skyblock** - Hypixel SkyBlock data types, registries, and
  `SkyBlockTooltipBuilder` placeholder resolution on top of the core engine.

## What you can render

- **Items** - static and animated (enchantment glint → animated WebP or GIF), with dye
  colour overlays for leather armour and potions.
- **Tooltips** - multi-line, formatting-code aware, optional borders.
- **Inventories** - arbitrary row/slot grids with titles, borders, and per-slot items.
- **Player heads** - from Base64 skin values or `textures.minecraft.net` URLs.
- **Player models** - 3D isometric renders of a full player skin.
- **Composites** - side-by-side or stacked combinations (for example, item + tooltip).

## Rendering examples

### Item

```java
GeneratorResult result = engine.render(
        ItemRequest.builder()
                .itemId("diamond_sword")
                .enchanted(true)
                .scale(10)
                .build()
);

BufferedImage firstFrame = result.firstFrame();

if (result.isAnimated()) {
    byte[] webp = ((GeneratorResult.AnimatedImage) result).toWebpBytes();
    byte[] gif = ((GeneratorResult.AnimatedImage) result).toGifBytes();
}
```

### Tooltip

```java
engine.render(
        TooltipRequest.builder()
                .line("&aHyperion")
                .line("&7Damage: &c+500")
                .line("&7Strength: &c+200")
                .line("")
                .line("&6&lLEGENDARY SWORD")
                .renderBorder(true)
                .build()
);
```

### Composite (item + tooltip, side-by-side)

```java
engine.render(
        CompositeRequest.builder()
                .add(ItemRequest.builder().itemId("diamond_sword").enchanted(true).scale(2).build())
                .add(TooltipRequest.builder()
                        .line("&aHyperion")
                        .line("&7Damage: &c+500")
                        .line("")
                        .line("&6&lLEGENDARY SWORD")
                        .build())
                .build()
);
```

### Player head

```java
// From a Base64 texture value
engine.render(PlayerHeadRequest.fromBase64(base64TextureValue).scale(4).build());

// From a textures.minecraft.net URL
engine.render(PlayerHeadRequest.fromUrl("https://textures.minecraft.net/texture/...").scale(4).build());
```

### Inventory

```java
engine.render(
        InventoryRequest.builder()
                .rows(3)
                .slotsPerRow(3)
                .title("Crafting")
                .drawBorder(true)
                .item(InventoryItem.builder().slot(0).itemId("diamond").build())
                .item(InventoryItem.builder().slot(4).itemId("stick").build())
                .build()
);
```

### NBT

Parse Minecraft NBT (JSON or SNBT) into item data, or render directly from NBT:

```java
ParsedItem item = engine.parseNbt(nbtString);
GeneratorResult result = engine.renderFromNbt(nbtString);
```

## Formatting codes

Text uses Minecraft's formatting system with `&` or `\u00a7` as the prefix:

| Code | Colour | Code | Format |
| ---- | ------ | ---- | ------ |
| `&0` | Black | `&k` | Obfuscated |
| `&1` | Dark Blue | `&l` | Bold |
| `&2` | Dark Green | `&m` | Strikethrough |
| `&3` | Dark Aqua | `&n` | Underline |
| `&4` | Dark Red | `&o` | Italic |
| `&5` | Dark Purple | `&r` | Reset |
| `&6` | Gold | | |
| `&7` | Gray | | |
| `&8` | Dark Gray | | |
| `&9` | Blue | | |
| `&a` | Green | | |
| `&b` | Aqua | | |
| `&c` | Red | | |
| `&d` | Light Purple | | |
| `&e` | Yellow | | |
| `&f` | White | | |

## Extending Tessera

Tessera is designed for extensibility through an SPI layer (`net.aerh.tessera.spi.*`).
Custom effects, NBT format handlers, font providers, overlay renderers, and data
registries can be registered either programmatically via `EngineBuilder` or auto-discovered
through `ServiceLoader` with descriptors under
`META-INF/services/net.aerh.tessera.spi.*`.

### Custom effect

```java
public class MyEffect implements ImageEffect {
    @Override public String id() { return "my_effect"; }
    @Override public int priority() { return 150; }
    @Override public boolean appliesTo(EffectContext ctx) { return true; }

    @Override
    public EffectContext apply(EffectContext ctx) {
        BufferedImage modified = /* ... transform ctx.image() */ ctx.image();
        return ctx.withImage(modified);
    }
}

Engine engine = Engine.builder()
        .minecraftVersion("1.21.4")
        .acceptMojangEula(true)
        .effect(new MyEffect())
        .build();
```

### Custom NBT format handler

```java
public class MyNbtHandler implements NbtFormatHandler {
    @Override public String id() { return "mytool:custom"; }
    @Override public int priority() { return 50; }

    @Override public boolean canHandle(String input) { /* ... */ return false; }

    @Override
    public ParsedItem parse(String input, NbtFormatHandlerContext ctx) throws ParseException {
        /* ... parse and return item data */
        return null;
    }
}

Engine engine = Engine.builder()
        .minecraftVersion("1.21.4")
        .acceptMojangEula(true)
        .nbtHandler(new MyNbtHandler())
        .build();
```

## SkyBlock module

The `tessera-skyblock` module adds Hypixel SkyBlock data types and tooltip formatting on
top of the core engine.

```java
TooltipRequest request = SkyBlockTooltipBuilder.builder()
        .name("Hyperion")
        .rarity(Rarity.byName("legendary").orElse(null))
        .lore("%%damage:500%%\n%%strength:200%%\n\n&7Ability: &6Wither Impact")
        .type("SWORD")
        .build();

GeneratorResult result = engine.render(request);
```

Placeholders use the `%%key:value%%` format (for example `%%damage:500%%`,
`%%soulbound%%`); they are resolved against the bundled SkyBlock data registries.

## Migrating from Jigsaw

Tessera is the re-architected successor to the Jigsaw library that previously lived at
this repository. There is no API shim and no tagged Jigsaw release. Migration is
coordinate-, package-, and one-bootstrap-call-deep: change the JitPack coordinate to
`com.github.Aerhhh.tessera`, rename `net.aerh.jigsaw` imports to `net.aerh.tessera`,
drop the Marmalade dependency, add a `TesseraAssets.fetch(...)` bootstrap call, and
purge `~/.m2/repository/com/github/Aerhhh` to evict stale SNAPSHOTs.

## Requirements

- Java 25+ with `--enable-preview`
- Maven (a bundled `mvnw` / `mvnw.cmd` wrapper is provided for contributors)
- A consumer-supplied SLF4J backend (Tessera depends only on the SLF4J API)

## Dependencies (runtime)

- [webp4j-core](https://github.com/matrixlab-official/webp4j) - WebP encode (static and animated)
- [animated-gif-lib](https://github.com/rtyley/animated-gif-lib-for-java) - animated GIF encode
- [Caffeine](https://github.com/ben-manes/caffeine) - async result caching
- [Gson](https://github.com/google/gson) - JSON parsing
- [SLF4J API](https://www.slf4j.org/) - logging facade

## Licence

Licence is undecided pre-1.0 and tracked as a release blocker. See the in-repo planning
notes in `.planning/` for the decision state.

## Not affiliated with Mojang AB or Microsoft.
