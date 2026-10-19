# Plugin integrations: ItemsAdder, ModelEngine & WorldGuard

Reference for all agents. Read together with the Paper reference for the project's target version (`docs/paper-121x-reference.md` or `docs/paper-26x-reference.md`).
ItemsAdder and ModelEngine are **closed source and paid**. We compile against their public API jars only and never redistribute them. WorldGuard/WorldEdit are open source (EngineHub); same rule: `provided`, never shaded.

Last checked: 2026-10-19 (WorldGuard section); 2026-10-18 (ItemsAdder, ModelEngine)

---

## ⚠️ Version compatibility (check this first)

| Plugin | 1.21.x | 26.1 / 26.2 | 26.3 |
|---|---|---|---|
| ItemsAdder (v4) | ✅ 1.20.6 – 1.21.11 | ✅ 26.1 listed; users run it on 26.2 | ⚠️ tracked for **4.1.0** ([issue #5527](https://github.com/PluginBugs/Issues-ItemsAdder/issues/5527)); users reported errors on 4.0.18 |
| ModelEngine 4 | ✅ (supports 1.19.4+) | ✅ up to 26.2 (official listing) | ❌ not officially supported yet |
| WorldGuard + WorldEdit / FAWE | ✅ (older WG 7.0.x releases) | ✅ WG 7.0.18 (26.1–26.3), 7.0.19 (26.2+) | ✅ **WG 7.0.20** ("Requires MC 26.3+", 2026-10-16) + **WE 7.4.6** (1.21.4–26.3, 2026-10-15) or **FAWE 2.16.x** (2.16.0 "26.3 Support", 2.16.1 latest). Checked 2026-10-19, `docs/research/worldguard-plot.md` |

> **CookieClicker server (recorded 2026-10-18):** the game server runs **ItemsAdder 4.0.18** (reported by the user). CookieClicker uses a **plain resource pack** (`resourcepack/`) and does **not** depend on ItemsAdder or ModelEngine, so this is not blocking. Our pack is sent **alongside** ItemsAdder's (multiple server packs, no merging); that coexistence is to be verified in the bakery-core in-game test. 4.0.18 lists Paper support only up to 26.2, so watch IA for errors on 26.3.

**Consequence:** 1.21.x projects can use both plugins without issues. On Paper 26.3 today, ModelEngine (and possibly ItemsAdder) may not work. Before building features on them, confirm the server's actual versions:
- Paper version (the server reports it on startup and via `/version`)
- `/version ItemsAdder` and `/version ModelEngine`

If a feature depends on either plugin and the server is on a version the plugin doesn't support, **stop and report it** — don't try to work around it with reflection or NMS. The options are a project-level decision: stay on Paper 26.2 until both support 26.3, or wait for the updates.

---

## General integration rules

1. **`provided` scope, never shaded.** The plugins are on the server; their classes must not end up in our jar.
2. **Soft dependency by default** (`softdepend` in `plugin.yml`) unless the plugin is truly useless without them — then use `depend`.
3. **Isolate hook code.** Never import ItemsAdder/ModelEngine classes in the main plugin class or in classes loaded at startup. Put them behind a small interface so the plugin still loads when the other plugin is missing (otherwise you get `NoClassDefFoundError`).
4. **Check before use:** `Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")` / `"ModelEngine"`.
5. **Wait for their data to load** (see each section) — both load content asynchronously / on reload.
6. **Don't ship a competing resource pack.** Both plugins generate and send their own pack. Our custom assets go into their content folders (or are merged), not a separate server pack.

### Hook pattern

```java
// Hook interface — safe to load even if ItemsAdder is absent
public interface CustomItemProvider {
    @Nullable ItemStack getItem(String namespacedId);
    @Nullable String getId(ItemStack stack);
}

// Only this class imports ItemsAdder classes
final class ItemsAdderProvider implements CustomItemProvider { ... }

// In onEnable
CustomItemProvider items = getServer().getPluginManager().isPluginEnabled("ItemsAdder")
        ? new ItemsAdderProvider()
        : new VanillaItemProvider();
```

### plugin.yml

```yaml
softdepend: [ItemsAdder, ModelEngine]
```

---

## ItemsAdder

### Maven

ItemsAdder's API is now published on **Maven Central**, so no extra repository is needed.

```xml
<dependency>
    <groupId>beer.devs</groupId>
    <artifactId>itemsadder-api</artifactId>
    <version>4.0.18-beta-10</version> <!-- check Central for latest; match the server's ItemsAdder version -->
    <scope>provided</scope>
</dependency>
```

**Do not use the old coordinates**, which still show up in many tutorials:
- `dev.lone:api-itemsadder` from `https://maven.devs.beer/` — that repository has been unreachable since Sept 2026 and breaks builds
- `com.github.LoneDev6:api-itemsadder` via JitPack — stuck on 3.x

Latest version: https://central.sonatype.com/artifact/beer.devs/itemsadder-api

### Loading — the #1 gotcha

ItemsAdder loads its content **asynchronously**. Items don't exist in `onEnable`.

- Listen to `ItemsAdderLoadDataEvent` before looking up any custom item/block/entity.
- It fires again after `/iareload` and `/iazip`, and content may have been added or removed. Re-resolve anything you cached (item stacks, ID lists) every time it fires.

```java
@EventHandler
public void onIaLoad(ItemsAdderLoadDataEvent event) {
    customItems.reload(); // rebuild caches here, not in onEnable
}
```

### Core API

IDs are `namespace:id` strings (plain `id` also works but is ambiguous).

| Class | Common methods |
|---|---|
| `CustomStack` | `getInstance(id)` (null if missing), `isInRegistry(id)`, `byItemStack(stack)` (null if not custom), `getItemStack()`, `getNamespacedID()` |
| `CustomBlock` | `getInstance(id)`, `isInRegistry(id)`, `byAlreadyPlaced(block)`, `place(location)` |
| `CustomEntity` | `spawn(id, location)`, `byAlreadySpawned(entity)` |
| HUDs | `PlayerHudsHolderWrapper(player)` + `PlayerQuantityHudWrapper(holder, "namespace:hud")` → `setFloatValue()`, `setVisible()` |
| GUIs / glyphs | `FontImageWrapper`, `TexturedInventoryWrapper` — see the "HUDs, GUIs, images and more" page |

- Glyphs/font images in messages: see "Custom fonts, glyphs & ItemsAdder" in `docs/minimessage.md`.
- `CustomMob` is the **old** entity API — use `CustomEntity` instead.
- Always null-check lookups: IDs come from server config and can disappear on reload.
- ItemsAdder also fires its own events (custom block place/break, entity death, etc.) — prefer those over checking vanilla events by hand.

### Resource pack notes (for the researcher)

- ItemsAdder builds the pack from `plugins/ItemsAdder/contents/<namespace>/` and hosts/sends it itself.
- Items are defined in YAML there. How ItemsAdder generates item models depends on the server version: on 1.21.4+ it uses item model definitions (see the "Modern Items Creation" page); on older 1.21.x it uses the legacy custom model data approach.
- The Maven coordinates (`beer.devs:itemsadder-api`) are the same for 1.21.x and 26.x projects — match the API version to the ItemsAdder version on the server.
- To include assets made outside ItemsAdder, use its **Merge Resourcepacks** feature rather than a second pack.

### Docs

- Java API overview: https://wiki.itemsadder.com/developers/java-api
- Usage examples: https://wiki.itemsadder.com/developers/java-api/examples
- Events: https://wiki.itemsadder.com/developers/java-api/events
- HUDs, GUIs, images: https://wiki.itemsadder.com/developers/java-api/huds-guis-images-and-more
- Javadocs: https://lonedev6.github.io/API-ItemsAdder/
- API repo & examples: https://github.com/LoneDev6/API-ItemsAdder
- Merge resource packs: https://wiki.itemsadder.com/adding-content/merge-resourcepacks
- Issue tracker (check version support): https://github.com/PluginBugs/Issues-ItemsAdder/issues

---

## ModelEngine 4

### Maven

```xml
<repository>
    <id>lumine</id>
    <url>https://mvn.lumine.io/repository/maven-public/</url>
</repository>

<dependency>
    <groupId>com.ticxo.modelengine</groupId>
    <artifactId>ModelEngine</artifactId>
    <version>R4.1.0</version> <!-- current per the MEG wiki; confirm the exact string in the Lumine repo -->
    <scope>provided</scope>
</dependency>
```

### How models get in

Models are made in Blockbench, placed in ModelEngine's blueprints folder, then imported with `/meg reload`. ModelEngine generates its own resource pack from them. Our code refers to models by their **model ID** (the blueprint name).

### Core API

```java
// Apply a model to an entity
ModeledEntity modeled = ModelEngineAPI.createModeledEntity(entity);
ActiveModel model = ModelEngineAPI.createActiveModel("my_model"); // throws if the model ID doesn't exist
modeled.addModel(model, true);

// Look it up later (also works with entity ID or UUID)
ModeledEntity existing = ModelEngineAPI.getModeledEntity(entity);
existing.removeModel("my_model");

// Animations
AnimationHandler anim = model.getAnimationHandler();
anim.playAnimation("attack", 0.3, 0.3, 1, true); // lerp in, lerp out, speed, force — check Javadocs
anim.stopAnimation("attack");      // smooth transition out
anim.forceStopAnimation("attack"); // snaps out immediately
```

- `createActiveModel` **throws** on unknown IDs — validate model IDs from config at startup/reload and log clear errors.
- Removing the base entity without cleaning up the model leaves orphaned display entities; remove models on death/despawn and on plugin disable.
- Per-player models, mounting, bone behaviors and custom base entities each have their own wiki page — read the relevant one before using them.

### Docs

- API overview: https://wiki.mythiccraft.io/modelengine/API
- Apply / remove model: https://wiki.mythiccraft.io/modelengine/API/Basic/Add-Remove-Model
- Play / stop animation: https://wiki.mythiccraft.io/modelengine/API/Basic/Play-Stop-Animation
- Bone behaviors: https://wiki.mythiccraft.io/modelengine/API/Basic/Configure-Bone-Behavior
- Mounting: https://wiki.mythiccraft.io/modelengine/API/Basic/Mounting-Dismounting-Model
- Per-player models: https://wiki.mythiccraft.io/modelengine/API/Basic/Per-Player-Model
- Events: https://wiki.mythiccraft.io/modelengine/API/Basic/Events
- Javadocs: https://ticxo.github.io/Model-Engine-4.0-JavaDocs/index.html

---

## Using both together

- Both generate a resource pack. Only one pack can be the server pack, so let ItemsAdder send it and merge ModelEngine's output into it. ItemsAdder documents this: https://wiki.itemsadder.com/compatibility-with-other-plugins/compatible/modelengine
- ItemsAdder can also do custom entities itself. Don't use both for the same mob — pick one per feature and note the choice in `docs/architecture.md`.

---

## WorldGuard (bakery plots and houses)

CookieClicker **hard-depends** on WorldGuard since `starter-house`: plots and their houses are WorldGuard regions. Research and sources: `docs/research/worldguard-plot.md`.

### Versions (Paper 26.3)
Server: **WorldGuard 7.0.20** with **WorldEdit 7.4.6**, or with **FastAsyncWorldEdit 2.16.x** instead of WorldEdit (never both). WorldGuard 7.0.20 refuses older Minecraft. The plugin warns at startup when WorldGuard is older than 7.0.20.

### Maven
```xml
<repository>
    <id>enginehub</id>
    <url>https://maven.enginehub.org/repo/</url>
</repository>

<dependency>
    <groupId>com.sk89q.worldguard</groupId>
    <artifactId>worldguard-bukkit</artifactId>
    <version>7.0.20</version>
    <scope>provided</scope>
</dependency>
<dependency>  <!-- BukkitAdapter, BlockVector3: WorldEdit types used directly -->
    <groupId>com.sk89q.worldedit</groupId>
    <artifactId>worldedit-bukkit</artifactId>
    <version>7.4.6</version>
    <scope>provided</scope>
</dependency>
```
Both pull their `-core` artifacts transitively as `provided`; the jar must contain no `com/sk89q` classes. Pin exact versions (not the `-SNAPSHOT` dev lines).

### plugin.yml and startup
- `depend: [WorldGuard]` (WorldGuard itself depends on WorldEdit/FAWE). Without it Paper refuses to load the plugin with a "missing dependency" error.
- WorldGuard can be installed but **disabled** (it disables itself on an unsupported version). `onEnable` checks `getPlugin("WorldGuard").isEnabled()` and that our flags were registered; otherwise it logs `plot.missing-worldguard` (messages.yml) to the console and disables CookieClicker.
- All `com.sk89q` imports live in one class, `worldguard/WorldGuardHook`, behind the `plot.PlotRegions` interface (tests use a fake).

### Custom flags: register in `onLoad`
Two **`StringFlag`s**, value = the plot id (`[a-z0-9_-]+`):
- `bakery-plot: <id>` on the plot region, `bakery-house: <id>` on its house region. Several plots = several ids.
- Register in `onLoad` via `WorldGuard.getInstance().getFlagRegistry().register(flag)`. After WorldGuard enables, the registry is locked (registering in `onEnable` throws).
- `FlagConflictException` (same name already registered, e.g. a second `onLoad`): reuse the existing flag if it is a `StringFlag`; any other type is a hard conflict (logged, the plugin disables).
- `/bakery reload` never re-registers; it re-reads `plots.yml` and re-resolves the regions.
- Admins set and see the flags with `/rg flag` and `/rg info`. The plugin **never** sets flags or creates regions.

### Finding regions (RegionContainer / RegionQuery)
```java
RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
ApplicableRegionSet set = container.createQuery()          // cached for ~1-2 s, cheap: create per use
        .getApplicableRegions(BukkitAdapter.adapt(location)); // never pass a Bukkit Location directly
if (set.isVirtual()) { /* protection disabled or region data failed to load: treat as "no plot" */ }
for (ProtectedRegion r : set) { String plot = r.getFlag(BAKERY_PLOT); /* highest priority wins */ }
RegionManager rm = container.get(BukkitAdapter.adapt(world)); // null when regions are disabled in that world
```
- **Cache region ids, never `ProtectedRegion` objects**: `/rg redefine` replaces the object. Re-read with `rm.getRegion(id)` each time; `/bakery reload` re-resolves ids, so `/rg redefine` + `/bakery reload` moves or resizes a plot without a restart.
- Plot ↔ house: at load/reload one scan of `rm.getRegions()` per world finds the region with `bakery-plot == id` and the one with `bakery-house == id`. Two regions with the same value: error naming both, that area is ignored. `plots.yml` `plot-region` / `house-region` are only a fallback for regions without the flag.
- Player tracking: only on a block change (`PlayerMoveEvent#hasChangedBlock`), teleport, respawn and join, never on every move event.
- Shapes: `getMinimumPoint()` / `getMaximumPoint()` (inclusive block coords, `x()/y()/z()`), `getPoints()` (2D vertices; cuboids give their 4 corners), `ProtectedPolygonalRegion` for polygons, `contains(x, y, z)` for exact membership. The global region has no area (`isPhysicalArea()` false): rejected.
- The region API is thread safe, but we read it on the main thread anyway.

### Admin setup and recommended plot flags
```
//wand  (select the plot)        /rg define bakery_plot     /rg flag bakery_plot bakery-plot main
//wand  (select the house)       /rg define bakery_house    /rg flag bakery_house bakery-house main
/rg setparent bakery_house bakery_plot          (optional)
/bakery reload                                   (after defining or redefining regions)
```
A region without owners/members already stops non-members from building. Recommended on the plot (the admin sets them; the plugin never does):
```
/rg flag bakery_plot build deny              (explicit; non-members are already denied)
/rg flag bakery_plot use allow               (doors, trapdoors, buttons, levers; not containers)
/rg flag bakery_plot chest-access deny
/rg flag bakery_plot mob-spawning deny
/rg flag bakery_plot tnt deny
/rg flag bakery_plot creeper-explosion deny
/rg flag bakery_plot other-explosion deny
/rg flag bakery_plot fire-spread deny        (needs WorldGuard's high-frequency flags enabled)
/rg flag bakery_plot lava-fire deny          (same)
/rg flag bakery_plot pvp deny                (optional)
/rg flag bakery_plot invincible allow        (optional)
```
- **Interaction-click caveat:** the cookie and shop rows are `Interaction` entities. WorldGuard's `build`/`interact` checks also cover entity interactions, so with `build deny` it may cancel the click before our listener sees it. **Test the cookie and shop clicks inside the plot in game**; if they fail, add `/rg flag bakery_plot interact allow`. Prefer `use allow` over `interact allow` otherwise (`interact` also allows mounting and entity use).
- No `entry`/`exit` flags: they would block the players who should walk onto the plot.
- `/bakery house place|undo` write blocks through the Bukkit API, which WorldGuard does not block. Admins building by hand need membership or `worldguard.region.bypass.<world>` (ops have it).

### Pitfalls
- `RegionQuery` results are cached for ~1–2 s; after `/rg redefine`, reload and give it a moment.
- Region ids are case-insensitive; flag values are not: the plugin lower-cases plot ids.
- `//wand` selections live in the WorldEdit session and are not available to us (no selection commands of our own).
- The later `paper-plugin.yml` (feature `optimize`) must keep WorldGuard loading first (`load: BEFORE` on the dependency), or flag registration breaks.
