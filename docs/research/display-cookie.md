# Giant cookie: ItemDisplay + Interaction hitbox

**Target version:** Paper 26.3 (`paper-api 26.3.build.205-stable`, Java 25, Adventure 5, resource pack format 97.1).
Feature: `bakery-core`. Researched 2026-10-18. "Verified" = read in the 26.3 Javadocs; "from vanilla knowledge" = not in the Javadocs, so check it in the in-game test.

## Summary
- Spawn one `ItemDisplay` per player per station. Its item is an `ItemStack` of `Material.COOKIE` carrying the `ITEM_MODEL` data component `cookieclicker:cookie`. Spawn one `Interaction` entity as the hitbox.
- Idle motion: do **not** teleport. Change the display's `Transformation` in short segments (about 10 ticks) with `setInterpolationDelay(0)` and `setInterpolationDuration(segmentTicks)`. The client interpolates in between. One scheduler task issues one `setTransformation` per cookie every 10 ticks (not per tick).
- Squash/pop: a short interpolated scale change (2 ticks down, 3 ticks back). It needs a read-modify-write of the scale only (see "Pop").
- Click: right click fires `PlayerInteractAtEntityEvent`. Left click fires `EntityDamageByEntityEvent` (and `PrePlayerAttackEntityEvent` before it). Listen to both, dedupe per player per tick, and check the owner.
- Every entity is created in the `spawn(..., Consumer)` callback with `setPersistent(false)`, `setVisibleByDefault(false)` and a scoreboard tag. See `per-player-visibility.md`.

## Version notes
| Topic | 26.3 |
|---|---|
| Data component API | Most of it is no longer experimental (26.3). `ItemStack#setData(DataComponentType.Valued<T>, T)` is verified. |
| `DataComponentTypes.ITEM_MODEL` | `DataComponentType.Valued<net.kyori.adventure.key.Key>` (verified). |
| `ItemStack.of(Material)` | exists (verified); throws if the material is not an item. |
| Item model definition | `assets/<ns>/items/<name>.json` (1.21.4+ system). The existing `resourcepack/assets/cookieclicker/items/cookie.json` already follows it. |
| `PlayerInteractEntityEvent` | Marked obsolete in the 26.3 Javadocs; use its subclass `PlayerInteractAtEntityEvent` (adds `getClickedPosition()`). |
| `Display#setTeleportDuration(int)` | Valid range **0 to 59**; `IllegalArgumentException` otherwise (verified). Not needed here. |

## Pack file layout (what the entity needs from the pack)
```
resourcepack/assets/cookieclicker/
  items/cookie.json                 <- item model definition, id cookieclicker:cookie (exists)
  models/item/cookie.json           <- model-designer replaces the flat model with a 3D one
  textures/item/cookie.png          <- existing 64x64
```
```json
// items/cookie.json (existing, keep)
{ "model": { "type": "minecraft:model", "model": "cookieclicker:item/cookie" } }
```
Model notes for the designer:
- Show the item with `ItemDisplayTransform.NONE`. The model is then rendered in raw model space, with no `display` block applied. Model-space 16 units equal 1 block at scale 1, and the model centre (8,8,8) sits at the entity position. Build the 3D cookie centred on (8,8,8) so it spins around its own centre.
- The final size is `Transformation` scale. The model itself should be about 16 units wide, and the coder scales it by `station.cookie.scale`.
- The existing flat `item/generated` model is a 1-px-thick extrusion, and the display looks paper-thin from the side. That is why a 3D model is needed.

## Plugin side

### Creating the stack
```java
ItemStack cookie = ItemStack.of(Material.COOKIE);
cookie.setData(DataComponentTypes.ITEM_MODEL, Key.key("cookieclicker", "cookie"));
```
Fallback (pack declined or not loaded yet): an unknown `item_model` renders as the purple/black missing model on the client, so for such a viewer use a plain `ItemStack.of(Material.COOKIE)` **without** the component. This is possible because every display is per player. When the status event reports `SUCCESSFULLY_LOADED`, call `display.setItemStack(withModel)`. See `resource-pack.md`.

### Spawning (main thread only)
`RegionAccessor#spawn(Location, Class<T>, Consumer<? super T>)` is verified (`World` inherits it). Do all the setup inside the consumer, so the entity is never visible to others, even for a tick:
```java
ItemDisplay display = world.spawn(loc, ItemDisplay.class, d -> {
    d.setPersistent(false);                 // never saved with the chunk
    d.setVisibleByDefault(false);           // then owner.showEntity(plugin, d) right after spawn
    d.addScoreboardTag("cookieclicker");    // sweep tag
    d.addScoreboardTag("cookieclicker:owner:" + ownerUuid); // optional, debugging
    d.setItemStack(cookie);
    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
    d.setBillboard(Display.Billboard.FIXED);        // default; the cookie spins by itself
    d.setViewRange(0.5f);                           // optional: multiplier on the 64-block default
    d.setBrightness(new Display.Brightness(15, 15));// optional: full-bright, cookie stays readable at night
    d.setTransformation(restTransformation);
});
owner.showEntity(plugin, display);
```
Verified in the Javadocs: `Display#setBillboard(Display.Billboard)` with `FIXED` (default), `VERTICAL`, `HORIZONTAL`, `CENTER`; `setViewRange(float)`; `setBrightness(@Nullable Display.Brightness)` and `new Display.Brightness(int blockLight, int skyLight)` (0 to 15); `ItemDisplay#setItemStack(@Nullable ItemStack)`; `ItemDisplay#setItemDisplayTransform(ItemDisplayTransform)` (constants: NONE, THIRDPERSON_LEFTHAND, THIRDPERSON_RIGHTHAND, FIRSTPERSON_LEFTHAND, FIRSTPERSON_RIGHTHAND, HEAD, GUI, GROUND, FIXED).

### Interaction hitbox
```java
Interaction hit = world.spawn(hitLoc, Interaction.class, i -> {
    i.setPersistent(false);
    i.setVisibleByDefault(false);
    i.addScoreboardTag("cookieclicker");
    i.setInteractionWidth(w);     // float, applies to X and Z
    i.setInteractionHeight(h);    // float
    i.setResponsive(true);        // client plays the arm swing / feedback; false = silent
});
owner.showEntity(plugin, hit);
```
Verified: `setInteractionWidth(float)`, `setInteractionHeight(float)`, `setResponsive(boolean)`, `isResponsive()`, `getLastAttack()` / `getLastInteraction()` (nullable `Interaction.PreviousInteraction`).
Sizing (from vanilla knowledge, check in game):
- The Interaction's position is the **bottom centre** of its box. The box extends `height` upward and `width/2` to each side in X and Z. It is axis-aligned and does not rotate with yaw.
- For a cookie of diameter `D` blocks (model 16 units x `scale`), centred at `Y`: put the Interaction at `Y - h/2`, with `width = h = D + 0.1`. The extra margin covers the squash/pop scale-up and the bob.
- Keep the hitbox **static**. It does not follow the bob and does not need to.
- The box is a square footprint. For wide things (panel rows) it is also deep. See `world-panel.md`.

### Idle spin and bob without teleports
Rules (from the Paper display docs and the vanilla wiki):
- A `Transformation` is `(translation Vector3f, leftRotation, scale Vector3f, rightRotation)`. Constructors `Transformation(Vector3f, AxisAngle4f, Vector3f, AxisAngle4f)` and `Transformation(Vector3f, Quaternionf, Vector3f, Quaternionf)` are verified. The getters return `Quaternionf`.
- `setInterpolationDuration(int)` is the length in ticks. `setInterpolationDelay(int)` is the wait in ticks before the interpolation starts (0 = now; the vanilla wiki says values below 0 mean "current game time").
- All interpolated properties move together. **Several changes in the same tick count as one change** (the last wins). A change made while an interpolation is running starts from the *currently rendered* state, not from the old target.
- Rotation is interpolated by shortest path (slerp). A step of 180 degrees or more is ambiguous or reverses. A full 360 degrees equals "no change" and the client does not move (the Paper docs add a tiny offset for this reason). So **spin in steps of at most about 120 degrees**.

Recipe (segment = 10 ticks, so every 0.5 s):
```
angle(t)  = (t / 10) * stepRad            // stepRad = 30 deg -> one revolution per 6 s
bobY(t)   = A * sin(2*pi * t / periodTicks)  // A about 0.08 to 0.12 blocks, period about 80 ticks
every 10 ticks:
  T = new Transformation(new Vector3f(0, bobY(t+10), 0),
                         new AxisAngle4f(angle(t+10), 0, 1, 0),
                         new Vector3f(s, s, s),
                         new AxisAngle4f(0, 0, 0, 1));
  display.setInterpolationDelay(0);
  display.setInterpolationDuration(10);
  display.setTransformation(T);
```
- The bob is a sine sampled every 10 ticks and drawn linearly between samples. That is smooth enough, and it avoids 1 packet per tick.
- If a 1-tick hiccup between segments is visible, use duration 11 (a new change mid-interpolation continues from the rendered state).
- Use a `double`/`long` tick counter for the angle and let `Quaternionf` or `AxisAngle4f` handle wrapping.
- Cost: one `setTransformation` per cookie per 10 ticks, sent only to the tracking player. Use one shared main-thread task for all cookies.
- Alternative with a far longer period: 3 segments of 120 degrees per 3 s. That is fewer packets, but the pop snap-ahead is larger (see below).

### Pop (squash then bounce back)
The contract is `Effects.pop(display, viewer)`; the vfx-designer implements it. Constraints for it:
1. **Read-modify-write the scale only.** Read `display.getTransformation()` (it returns the current *target*), copy it, change only the scale, and set it back. Do the same again at the restore step (re-read, because the spin controller may have issued a new segment in between). That way pop and spin never stomp each other.
2. Sequence: tick 0: scale `(1.25s, 0.75s, 1.25s)`, delay 0, duration 2. Tick 2: scale `(0.92s, 1.1s, 0.92s)`, duration 2 (optional overshoot). Tick 4: scale `s`, duration 3.
3. Side effect to know: a pop restarts the interpolation toward the current *target*. That makes the rotation fast-forward to the end of the running spin segment within 2 ticks. With 10-tick, 30-degree segments the error is at most 30 degrees, hidden by the squash. Do not use long segments for that reason.
4. The coder's spin task keeps issuing segments during a pop. Segments always carry the current `getTransformation()` scale instead of a hard-coded `s`, or the controller skips the scale part (re-read scale before issuing). Simplest: the controller builds each segment from `display.getTransformation().getScale()`.
5. Everything is client-side interpolation, so no per-tick work. Use `runTaskLater` for the 2 follow-up steps, and only for the clicking player's own display.

### Click events on 26.3
| Click | Event | Notes |
|---|---|---|
| Right click | `org.bukkit.event.player.PlayerInteractAtEntityEvent` (`getRightClicked()`, `getHand()`, `getClickedPosition()`) | Check `getHand() == EquipmentSlot.HAND`. `PlayerInteractEntityEvent` is obsolete. Cancel the event. |
| Left click | `org.bukkit.event.entity.EntityDamageByEntityEvent` (`getDamager()` is the Player, `getEntity()` is the Interaction) | Paper issue #8980 ("No event called for hitting an Interaction entity") was closed as completed in 2023 by pulling in the upstream CraftBukkit change that calls the damage event. Cancel the event. |
| Left click (earlier) | `io.papermc.paper.event.player.PrePlayerAttackEntityEvent` (`getAttacked()`, `willAttack()`) | Fires before damage logic. The Javadoc does not say whether it fires for Interaction entities. |
| Polling fallback | `Interaction#getLastAttack()` / `getLastInteraction()` | Only if events turn out not to fire. Needs polling, so avoid. |

Recommended listener design (one `ClickRouter`):
- Register `PlayerInteractAtEntityEvent` and `EntityDamageByEntityEvent`; also register `PrePlayerAttackEntityEvent` only if the in-game test shows the damage event does not fire for Interaction entities. If both fire, **dedupe** with `(playerUuid, currentTick)` so one click counts once.
- Route by the clicked entity: look up its UUID in a `Map<UUID, ClickTarget>` of the plugin's own spawned Interactions (cookie, panel rows). Ignore everything else. Do not rely on tags alone.
- **Owner check:** `clicker.getUniqueId().equals(target.owner())`. Hidden entities should not receive clicks from other players, but never rely on that.
- Also check the distance (clicker within about 6 blocks) and the world key.
- Do all game state changes on the main thread (events already are).
- Left and right clicks both give cookies. Say so in the in-game guide; the player story does not distinguish them.
- Do not let a stray left click damage anything: the Interaction has no health, so nothing happens either way, but cancel anyway.
- Attack cooldown does not limit the events. Left-click autoclickers produce a click per client swing; rate-limiting (for example max 20 CPS) is a balance decision, not covered here.

### Non-persistence, tags and cleanup
- `Entity#setPersistent(false)` (verified): the entity is not saved with the chunk. When the chunk unloads, the entity vanishes with it. Never rely on this alone.
- Tag with `Entity#addScoreboardTag(String)` (verified; returns boolean). Use one shared tag (`cookieclicker`). It is the sweep key.
- Track every spawned entity in a registry (`Map<UUID(owner), StationView>`); remove with `Entity#remove()`; `Entity#isValid()` tells if it is still alive. `Entity#getRemovalReason()` (26.3, `io.papermc.paper.entity.RemovalReason`) tells why an entity went away, useful in tests and logs.
- Cleanup paths the tester will check:
  1. Player quit, world change, or walking out of range: despawn that player's views.
  2. `EntitiesUnloadEvent` (`org.bukkit.event.world`, `getEntities()`, `getChunk()`): drop registry entries for the unloaded entities. Respawn when the owner is near again (see the lifecycle in `per-player-visibility.md`).
  3. `/bakery reload` and `onDisable`: remove all registry entities, then run the sweep.
  4. `onEnable` sweep: for each loaded world, remove every entity that has the `cookieclicker` tag (`world.getEntities()` after worlds are loaded; also handle chunks loaded later with `EntitiesLoadEvent`). Non-persistent entities should never be on disk, but the sweep covers crashes and plugin reloads.
- Spawn only in loaded chunks. Check `world.isChunkLoaded(x >> 4, z >> 4)` first, or use the owner's proximity (a player nearby means the chunk is loaded).

## Pitfalls
- Entity operations are main-thread only. The save/load path is async and must never touch entities.
- `setInterpolationDuration` / `Delay` must be set, otherwise the change is applied instantly (duration 0).
- Two `setTransformation` calls in one tick collapse into one (the last wins), so a pop and a spin segment in the same tick lose one of them. The pop reads the current target first, as described above.
- Quaternion shortest path: never step 180 degrees or more.
- Hitbox is axis-aligned and square: a big hitbox around the cookie blocks clicks on anything behind it, including the panel. Leave a gap between the cookie and the panel boxes.
- Missing item model renders as a purple/black cube. Use the fallback stack when the viewer has no pack.
- `Display#setTeleportDuration` accepts 0 to 59 only. Not needed here because the cookie never moves.
- The text/Display brightness override changes lighting only; it does not change the model's own shading.
- The `view_range` default is 1.0 (64 blocks base). Our displays are only spawned when the owner is within a short range anyway.

## Sources
- Paper Javadocs 26.3: [Display](https://jd.papermc.io/paper/26.3/org/bukkit/entity/Display.html), [Display.Billboard](https://jd.papermc.io/paper/26.3/org/bukkit/entity/Display.Billboard.html), [Display.Brightness](https://jd.papermc.io/paper/26.3/org/bukkit/entity/Display.Brightness.html), [ItemDisplay](https://jd.papermc.io/paper/26.3/org/bukkit/entity/ItemDisplay.html), [ItemDisplayTransform](https://jd.papermc.io/paper/26.3/org/bukkit/entity/ItemDisplay.ItemDisplayTransform.html), [Interaction](https://jd.papermc.io/paper/26.3/org/bukkit/entity/Interaction.html), [Transformation](https://jd.papermc.io/paper/26.3/org/bukkit/util/Transformation.html), [Entity](https://jd.papermc.io/paper/26.3/org/bukkit/entity/Entity.html), [RegionAccessor](https://jd.papermc.io/paper/26.3/org/bukkit/RegionAccessor.html), [DataComponentTypes](https://jd.papermc.io/paper/26.3/io/papermc/paper/datacomponent/DataComponentTypes.html), [ItemStack](https://jd.papermc.io/paper/26.3/org/bukkit/inventory/ItemStack.html)
- Events: [PlayerInteractAtEntityEvent](https://jd.papermc.io/paper/26.3/org/bukkit/event/player/PlayerInteractAtEntityEvent.html), [PlayerInteractEntityEvent](https://jd.papermc.io/paper/26.3/org/bukkit/event/player/PlayerInteractEntityEvent.html), [PrePlayerAttackEntityEvent](https://jd.papermc.io/paper/26.3/io/papermc/paper/event/player/PrePlayerAttackEntityEvent.html), [EntityDamageByEntityEvent](https://jd.papermc.io/paper/26.3/org/bukkit/event/entity/EntityDamageByEntityEvent.html), [EntitiesUnloadEvent](https://jd.papermc.io/paper/26.3/org/bukkit/event/world/EntitiesUnloadEvent.html)
- Paper docs, display entities: https://docs.papermc.io/paper/dev/display-entities/
- Paper issue, Interaction left click: https://github.com/PaperMC/Paper/issues/8980
- Minecraft Wiki, Display (start_interpolation, teleport_duration): https://minecraft.wiki/w/Display

## Hand-off

**model-designer** (pipeline: plain pack `resourcepack/`)
- Replace the flat `resourcepack/assets/cookieclicker/models/item/cookie.json` with a 3D cookie model. Keep the item model definition `cookieclicker:cookie` (`items/cookie.json`) and the 64x64 `textures/item/cookie.png`.
- Build it centred on (8,8,8), about 16 units wide, with a visible thickness (about 3 to 4 units), so it looks right when it spins. The coder shows it with `ItemDisplayTransform.NONE`, so the `display` block in the model is not applied; do not rely on it.
- Make it look good from both faces and from the side (it rotates through 360 degrees).
- Two icon models `cookieclicker:building/villager_baker` and `cookieclicker:building/wheat_farm` follow in `world-panel.md`.

**sound-designer**: nothing from this note.

**vfx-designer**
- `Effects.pop(ItemDisplay, Player)` must follow the "Pop" rules above: read-modify-write of the scale only, re-read at the restore step, 2 + 2 + 3 tick sequence via `setInterpolationDelay(0)` / `setInterpolationDuration(n)`, no teleports, no per-tick tasks.
- Any floating "+N" text must be created like the entities above (`spawn` with a consumer: non-persistent, hidden by default, tagged `cookieclicker`, then `viewer.showEntity`). See `world-panel.md` and `per-player-visibility.md` for details. The rise is a translation interpolation; text opacity is not interpolated (see `world-panel.md`).

**plugin-coder**
- Spawn `ItemDisplay` + `Interaction` per player per station as above; spin/bob controller with 10-tick segments in one shared task; `ClickRouter` with dedupe and owner check; cleanup paths 1 to 4; fallback stack when the viewer has no pack.
- Config knobs (no numbers in Java): `station.cookie.scale`, `station.cookie.bob-amplitude`, `station.cookie.bob-period-ticks`, `station.cookie.spin-seconds-per-revolution`, `station.cookie.hitbox-margin`.
- Test the left-click event in game first (debug listener for the three events). Report which ones fire.

CONTRACT CHANGES (proposed): none to IDs. Clarify that `Effects.pop(display, viewer)` is scale-only read-modify-write as described. The coder's spin controller and the vfx pop must follow the same rule.
