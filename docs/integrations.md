# Plugin integrations: ItemsAdder & ModelEngine

Reference for all agents. Read together with the Paper reference for the project's target version (`docs/paper-121x-reference.md` or `docs/paper-26x-reference.md`).
Both plugins are **closed source and paid**. We compile against their public API jars only and never redistribute them.

Last checked: 2026-10-18

---

## ⚠️ Version compatibility (check this first)

| Plugin | 1.21.x | 26.1 / 26.2 | 26.3 |
|---|---|---|---|
| ItemsAdder (v4) | ✅ 1.20.6 – 1.21.11 | ✅ 26.1 listed; users run it on 26.2 | ⚠️ tracked for **4.1.0** ([issue #5527](https://github.com/PluginBugs/Issues-ItemsAdder/issues/5527)); users reported errors on 4.0.18 |
| ModelEngine 4 | ✅ (supports 1.19.4+) | ✅ up to 26.2 (official listing) | ❌ not officially supported yet |

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
