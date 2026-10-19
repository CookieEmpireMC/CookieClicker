# Per-player fake blocks, keep-out, falling block displays, block place sounds

**Target version:** Paper 26.3 (`26.3.build.205-stable`, Java 25). Feature `starter-house`. Researched 2026-10-19.
**[V]** = read in the 26.3 Javadocs / Paper docs (linked). **[SK]** = source knowledge (how vanilla/Paper behave), not in the pages I could read: the in-game test must confirm it.

## Summary
- Hide the house per player with `Player#sendMultiBlockChange(Map<Position, BlockData>)` (fake `AIR`), reveal with the same method (real `BlockData`), and send tile-entity data (sign text) with `sendBlockUpdate(Location, TileState)`. The server world is never changed.
- The client forgets fake blocks whenever the server sends the real block or the whole chunk again. The plugin must re-assert fake air for the still-hidden blocks after any interaction near the house, and may refresh periodically.
- Keep the player out by holding them still at the viewpoint with **transient attribute modifiers** (`MOVEMENT_SPEED` to 0, `JUMP_STRENGTH` to 0), not persisted if the server crashes. Back it with a `PlayerMoveEvent` guard. Never rely on the server accepting movement through fake air.
- Falling blocks: per-player `BlockDisplay`, spawned a tick above in the consumer with the start transformation, then one or two interpolated `setTransformation` steps (`runTaskLater`) for drop and bounce. Removed in the same tick as the real block is resent.
- Place sound: `BlockData#getSoundGroup()` and `SoundGroup#getPlaceSound()` **still exist on 26.3 and are not deprecated [V]**.

## Version notes (26.3)
| API | 26.3 status |
|---|---|
| `Player#sendBlockChange(Location, BlockData)` | exists, not deprecated **[V]**: "fakes a block change packet ... will not actually change the world in any way". |
| `Player#sendBlockChanges(Collection<BlockState>)` | exists, not deprecated **[V]**. "A packet must be sent for each chunk section modified" (so several packets). Cannot guarantee changes reach chunks the client has not loaded: "the caller must ensure the client is in range". The `(Collection, boolean suppressLightUpdates)` overload is **deprecated** ("not functional in versions greater than 1.19.4"). |
| `Player#sendMultiBlockChange(Map<? extends Position, BlockData>)` | exists, not deprecated **[V]**: "fakes a multi block change packet for each chunk section that a block change occurs". The `(Map, boolean)` overload is deprecated ("no longer available in 1.20+"). `Position` = `io.papermc.paper.math.Position` (`@Experimental`, may break); build keys with `Position.block(x, y, z)` **[V]** ([Position](https://jd.papermc.io/paper/26.3/io/papermc/paper/math/Position.html)). |
| `Player#sendBlockUpdate(Location, TileState)` | exists, not deprecated **[V]**: fakes a TileState change; "If the client lacks an appropriate tile there, it may show an error message"; create a state with `BlockData#createBlockState()`; throws `IllegalArgumentException` on null. `sendSignChange` overloads are deprecated: use this. |
| `Player#sendBlockDamage(...)` | exists **[V]**, not needed. |
| `BlockData#getSoundGroup()` | `@NotNull SoundGroup`, not deprecated **[V]** ([BlockData](https://jd.papermc.io/paper/26.3/org/bukkit/block/data/BlockData.html)). |
| `SoundGroup` | `getBreakSound()`, `getStepSound()`, `getPlaceSound()`, `getHitSound()`, `getFallSound()` return `org.bukkit.Sound`; `getVolume()`, `getPitch()` floats (may differ from what the client really plays) **[V]** ([SoundGroup](https://jd.papermc.io/paper/26.3/org/bukkit/SoundGroup.html)). |
| `org.bukkit.Sound` | now an **interface** (`extends OldEnum<Sound>, Keyed (Bukkit and Adventure), Sound.Type`) **[V]** ([Sound](https://jd.papermc.io/paper/26.3/org/bukkit/Sound.html)). Do not use `Sound.valueOf`/`name()`/`switch` on it; get the key with Adventure `sound.key()` (`.asString()`) or `Registry.SOUNDS.getKey(sound)` **[SK]**. |
| `AttributeInstance#addTransientModifier(AttributeModifier)` | exists: "Transient modifiers are not persisted (saved with the NBT data)" **[V]**; `removeModifier(Key)` exists; the `UUID` overload is deprecated for removal. |
| `AttributeModifier(NamespacedKey key, double amount, Operation op)` | the current constructor; `(String/UUID, ...)` ones are deprecated **[V]**. Operation values not shown on the page; use `AttributeModifier.Operation.MULTIPLY_SCALAR_1` **[SK]** (check with the compiler). |
| `Attribute.MOVEMENT_SPEED`, `JUMP_STRENGTH`, `SNEAKING_SPEED`, `GRAVITY`, `SAFE_FALL_DISTANCE` | exist **[V]** ([Attribute](https://jd.papermc.io/paper/26.3/org/bukkit/attribute/Attribute.html)). |
| `Display`: transformation interpolation, `setTeleportDuration` (0 to 59), `BlockDisplay#setBlock(BlockData)` | exist; Paper docs show all three **[V]** ([display entities](https://docs.papermc.io/paper/dev/display-entities/)). |

## Q5. Fake blocks over real blocks

### What the calls do
- All three send packets to **one player**; the server world, other players and the saved chunks stay untouched **[V]**.
- Positions must be in a chunk the client already has loaded; otherwise the packet is lost **[V]** (`sendBlockChanges` Javadoc). The house is next to the player's plot entry point, so it is always inside view distance when we start; still guard with `player.isChunkSent(chunkKey)`-style checks or skip when `!world.isChunkLoaded(...)`.
- `sendMultiBlockChange(Map<Position, BlockData>)` is the right bulk call: it needs no `BlockState` snapshot per block and splits by chunk section internally **[V]**. A multi-block-change packet covers one 16x16x16 section and carries up to 4096 entries at about 2 to 3 bytes each **[SK]**, so even a 30x30x30 region is about 8 to 16 packets of a few KB.

### When the client loses the fake blocks
| Event | Result |
|---|---|
| Server sends the real block for that position (any server-side change there: another player opens/closes a door or edits the sign, random tick, physics, `/bakery house place|undo`) | fake replaced by real **[SK]** (vanilla sends block updates to everyone tracking the chunk). |
| The player clicks/places/breaks near or at a fake block, and the server **cancels** or rejects the action | Paper resends the real block state (and neighbours and tile data) to roll back the client prediction **[V-ish]**: Paper commit ["Don't resend blocks on interactions (#9413)"](https://ayakael.net/mirrors/papermc/commit/9271ee7643614663a6d93323a9e8db2d52123166) shows the rollback sends block updates for the clicked position and its neighbours plus tile-entity data; for blocks out of reach only an acknowledgement is sent. This is the common way a "hidden" block pops back in. WorldGuard (`build deny`) cancels the same events, so it triggers the resend. |
| Chunk leaves and re-enters the player's view distance, client view-distance setting changes, `Player#setSendViewDistance`, far teleport | chunk is sent again; fake blocks in it are gone **[SK]**. |
| World change | client rebuilds the level; all fake blocks gone **[SK]**. |
| Respawn after death | **[SK]** may or may not resend chunks; treat as lost. The brief already requires "resend on death". |
| Relog | gone. |
| Light update | the client re-lights locally after a block change; no loss, but a big batch can cause a client lag spike (the `suppressLightUpdates` option no longer works since 1.19.4 **[V]**), so batch moderately. |
| Another plugin or WorldGuard teleport etc. | no effect on fake blocks. |

Design consequences:
1. Track per player: `hidden` = set of still-fake positions (as packed longs), `revealed` set, current phase. The cinematic state is the source of truth, not the client.
2. **Re-assert** hidden positions after anything that might have resent them: a `PlayerInteractEvent`, `BlockBreakEvent`, `BlockPlaceEvent`, `PlayerInteractEntityEvent` by this player whose clicked block lies within **2 blocks** of the house box, scheduled 1 tick later (after the rollback packets). Resend only `hidden ∩ nearby`. Add a **keepalive refresh**: every 40 ticks, resend fake air for the hidden positions of one section per tick (budgeted, configurable, can be off). This also covers other players toggling the cottage door (real change) while the cinematic runs.
3. Do not cancel interact events yourself just to protect fake blocks; cancelling is what makes the server resend. Prefer the keep-out (below) so the player cannot act on the box, plus step 2.
4. If a chunk-section resend wipes the fake air (view distance flap), the player sees the finished house early. Acceptable and self-healing at the end because the reveal sends real blocks anyway. Optional: hook `PlayerChunkLoadEvent` (Paper, `io.papermc.paper.event.packet.PlayerChunkLoadEvent`) to re-assert hidden positions in that chunk **[SK]**; check it exists in the 26.3 Javadocs before using.

### Restoring real blocks per player, in batches
```java
Map<Position, BlockData> batch = new HashMap<>();
for (long packed : nextBatch) {                      // up to blocks-per-tick entries
    int x = unpackX(packed), y = unpackY(packed), z = unpackZ(packed);
    batch.put(Position.block(x, y, z), world.getBlockData(x, y, z));   // RegionAccessor#getBlockData(int,int,int) [SK]
}
player.sendMultiBlockChange(batch);
// tile entities (the sign, any container with a visible state): after the block is real again
for (Location loc : tilePositionsInBatch)
    if (world.getBlockAt(loc).getState() instanceof TileState ts) player.sendBlockUpdate(loc, ts);   // [V] method
```
- A block entity sign shows blank text on the client when the block was replaced and the real block is resent **without** tile data **[SK]** (vanilla sends the sign text in a separate block-entity packet). So: after revealing a sign (the cottage has one), call `sendBlockUpdate` for it. Find tile positions once with `block.getState() instanceof TileState`, not for every block (`getState()` allocates).
- `sendBlockChanges(Collection<BlockState>)` also works and may include tile data itself **[SK]**, but needs a snapshot per block and is slower; stay with `sendMultiBlockChange` + `sendBlockUpdate`.
- Always resend **real** data read from the world right now (not from the design file): the admin may have edited the house by hand.
- Cleanup/safety path (quit not needed; death, teleport, world change, reload, disable): resend ALL `hidden` positions in one go. For a normal house (< 2000 blocks) that is 1 to 3 sections = 1 to 3 packets, so do it synchronously with no budget. For unusually large houses use the budget but at 4x the rate.

### Sensible limits (estimates, put them in config)
| Setting | Default | Reason |
|---|---|---|
| Initial fake air | all in the same tick if <= ~4,000 blocks, else one section (4096) per tick | a 9x7x7 cottage is 441 blocks = 1 to 2 packets |
| Reveal `blocks-per-tick` | 4 to 8 blocks per tick per player (that is the cinematic's pacing: 441 blocks in ~25 s = ~0.9 per tick; so 1 to 2 per tick) | pacing, not network, is the limit |
| `max-falling-displays` | 24 per player, 64 server-wide | each display is a spawned entity plus 2 packets; keep entity tracker cheap |
| Re-assert after interaction | <= 256 positions per call | |
| Keepalive refresh | 1 section per 40 ticks per player | |
Packet limits: none in the Javadocs; the practical limit is client re-lighting and the 4096-entry section cap **[SK]**.

## Q6. Keeping the player out of an invisible-but-solid region

### What the server does with fake air over real blocks **[SK]**
The client believes the area is empty and predicts movement through it. The server validates each movement packet against **its own** collision world (`ServerGamePacketListenerImpl`): if the client's reported position differs from what the server's physics allows by more than the "moved wrongly" threshold, it logs "<name> moved wrongly!" and teleports the player back to the last accepted position ("rubber-banding"); the new position being inside a block triggers the same. Spigot's `moved-wrongly-threshold` (default 0.0625, squared blocks) controls this ([Paper docs: spigot configuration](https://docs.papermc.io/paper/reference/spigot-configuration), **[V]** for the setting and default). This is a correction, not a kick. Kicks come from other checks: "Flying is not enabled on this server" if a player floats for ~80 ticks while the server thinks they are not on ground (possible when a player stands on fake blocks the server doesn't have; our fake blocks are *air* over solids so the opposite case, falling into solid, happens: the player sinks and is pushed back) and "moved too quickly" if lots of corrections accumulate **[SK]**. Anti-cheat plugins (Grim, Vulcan, NoCheatPlus) will flag the same mismatches **[SK]**: tell the server admin if they run one, ask them to exempt the cinematic window or the plot.
Do not touch `moved-wrongly-threshold` (global setting, hides real problems). Paper 26.4 plans to merge the config files **[V]** (`docs/paper-26x-reference.md`).

### Recommended keep-out (config `house.cinematic.keep-out`)
1. **`hold` (default)**: teleport to the viewpoint (if configured, once, before starting) and apply for the cinematic's duration:
   ```java
   Key key = Key.key("cookieclicker", "house_cinematic");   // NamespacedKey for AttributeModifier
   AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
   speed.addTransientModifier(new AttributeModifier(new NamespacedKey(plugin, "house_cinematic"), -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
   AttributeInstance jump = p.getAttribute(Attribute.JUMP_STRENGTH);   // same, so no hop onto the box
   // on end/cleanup: speed.removeModifier(thatKey); jump.removeModifier(thatKey)
   ```
   `addTransientModifier` is not saved with player data **[V]**, so a crash cannot leave the player frozen; still remove on every cleanup path including `PlayerQuitEvent`, and sweep the key on join. The player can still look around and the camera is free. Attribute changes are synced to the client, so there is no rubber-banding. Do not use `Player#setWalkSpeed(0)` (saved in the player's abilities and persists after a crash) **[SK]**. Also remove modifiers on `disable`.
2. **`slow`**: same modifier with e.g. -0.8 (20% speed), plus the guard below.
3. **`none`**: only the guard.
4. **Guard (always on while a cinematic runs)**: in `PlayerMoveEvent` (cheap integer compare, only when the block changed), if the destination block is inside the **hidden box** (axis-aligned bounding box expanded by 0.3), call `event.setTo(event.getFrom())` or better teleport to the last safe position. This is the supported way to deny a move; the client gets a position correction, so a rare jitter, never a kick. Add a velocity nudge away from the box centre (`player.setVelocity`, small) when within 1.5 blocks and moving towards it, to make the correction smooth **[SK]**. Block `PlayerTeleportEvent` destinations inside the box likewise.
5. Rejected options: spectator mode (changes UI/game mode and persists on crash), riding an invisible vehicle (dismount handling, other players see a sitting player; usable as fallback with `EntityDismountEvent` cancel but not recommended), `Slowness 255` effect (visible effect icon, FOV change).
6. Elytra/ender pearl/knockback: out of scope; the guard covers teleports, pearls land via teleport events.

Also: the cinematic should only start when the player is **outside** the box (brief). If someone is already inside at start (another trigger path), push them out to the viewpoint first.

## Q7. BlockDisplay falling block with drop and bounce

Verified in the Paper docs **[V]**: `world.spawn(loc, BlockDisplay.class, e -> e.setBlock(BlockData))`; interpolation via `setInterpolationDelay(0)`, `setInterpolationDuration(n)` then `setTransformation(...)` (a "0.1F offset stops reverse interpolation" for rotations); `setTeleportDuration(n)` then `teleport(...)`; and "The first transformation is delayed by one tick after the display is created. The scheduler's delay argument (1 in the example) handles this." Teleport duration range is 0 to 59 (`display-cookie.md`).

### Recipe (all main thread)
```java
BlockDisplay d = world.spawn(corner /* integer block corner of the target */, BlockDisplay.class, e -> {
    e.setPersistent(false);
    e.setVisibleByDefault(false);                // per-player, see per-player-visibility.md
    e.addScoreboardTag("cookieclicker");
    e.setBlock(blockData);
    e.setBrightness(new Display.Brightness(blockLight, skyLight));   // see lighting below
    e.setInterpolationDelay(0);
    e.setTransformation(new Transformation(new Vector3f(0, dropHeight, 0), new AxisAngle4f(), new Vector3f(1,1,1), new AxisAngle4f()));  // start state
});
viewer.showEntity(plugin, d);
// next tick (runTaskLater(plugin, ..., 2)): fall
d.setInterpolationDelay(0); d.setInterpolationDuration(fallTicks);        // e.g. 6 to 8
d.setTransformation(translation (0, 0, 0));
// after fallTicks: first bounce up
d.setInterpolationDelay(0); d.setInterpolationDuration(2); d.setTransformation(translation (0, bounceH, 0), scale (1.04,0.92,1.04));
// then: settle
d.setInterpolationDelay(0); d.setInterpolationDuration(2); d.setTransformation(translation 0, scale 1);
// onLanded: d.remove(); viewer.sendMultiBlockChange(...real block...); sound; effect.
```
- A `BlockDisplay` renders the block model from the entity position as its **corner** (0,0,0) to (1,1,1), not centred **[SK]**; so spawn at the target block's integer corner (x, y, z) and move with translation. Spin or squash around the centre needs a translation of `-0.5*scale + 0.5`.
- Interpolation is linear, so approximate easing with 2 to 3 short segments (fall, small up, settle), each started with `runTaskLater`. The client interpolates only the **latest** change per tick, so never issue two `setTransformation` in the same tick.
- Prefer transformation interpolation over teleport interpolation for the bounce (it can scale/squash too); teleport duration is cleaner for a pure straight drop of more than ~10 blocks since translation is not culled. Either is fine for 4 to 8 blocks.
- Per-player visibility: exactly the pattern of `per-player-visibility.md` (hidden by default, `showEntity`, consumer spawn). Others never get the entity. Also sets `Display#setViewRange` small (e.g. 0.5) so it isn't tracked far away.
- Entity culling: displays with width/height 0 are never culled by the client; leave `setDisplayWidth/Height` at default **[SK]**.
- Lighting: the display samples light at its own position. The client re-lights locally after the fake air, so the position inside the (fake) hollow normally looks right, but the interior could look dark at night for a tick. Give it a fixed brightness taken from the real light above the house top (`block.getLightFromSky()` / `getLightFromBlocks()` of the block above the roof) **[SK]**.
- Z-fighting: at the landing tick `remove()` the display and resend the real block in the same tick; no overlap is visible.
- Cap concurrent displays (config `max-falling-displays`), remove all with `Effects.cancelFor(viewer)` and on `PlayerQuitEvent`, world change, reload and disable. `Entity#isValid()` check before every scheduled step (the viewer can be gone, the chunk unloaded).

## Q8. Vanilla place sound per block

```java
SoundGroup g = blockData.getSoundGroup();                 // [V] not deprecated
Sound s = g.getPlaceSound();                              // [V]
String key = s.key().asString();                          // e.g. "minecraft:block.wood.place"  (Sound is Keyed, [V] interface) 
viewer.playSound(loc, key, SoundCategory.BLOCKS, volume, pitch);    // per-player; volume/pitch from sounds.yml, not from g.getVolume() [V: may not match client]
```
- Contract `house_block_place` with `key: "@block"`: `SoundService` resolves the key at play time from the block data of the block being placed. All volume, pitch variance and category come from `sounds.yml`, as the brief says.
- `SoundGroup#getVolume/getPitch` are vanilla's 1.0/1.0 style defaults and "may not match the volume the client actually receives" **[V]**; ignore them, but a place-sound pitch of `g.getPitch() * variation` keeps vanilla feel (e.g. grass is 1.0, glass 1.0) **[SK]**.
- Fallback when the lookup fails: use the `fallback` of the event in `sounds.yml`.
- Use a cache `Map<BlockData/Material, String>` to avoid recreating strings 400 times (key by `Material`, since the sound group depends on the block type).
- Replacement not needed: nothing deprecated on `BlockData`, `SoundGroup`, `Sound#key` in the 26.3 Javadocs. (Do not use `Sound.valueOf` or `Sound.BLOCK_WOOD_PLACE.name()`.)

## Pitfalls
- Fake blocks are per **position**, not per block: after any resend, the client's copy is real again. Track hidden positions and re-assert.
- `sendBlockChange(Location, BlockData)` per block is one packet each: never call it in a loop for 400 blocks; use `sendMultiBlockChange`.
- `Position` is `@Experimental`: wrap it in one helper (`FakeBlocks.send(player, Map<BlockPos, BlockData>)`) so an API change touches one place.
- Signs and other block entities lose their data on a bare block resend; follow with `sendBlockUpdate`.
- `PlayerInteractEvent` cancellation by WorldGuard resends blocks (the rollback above). Test in a `build deny` plot.
- Chunk unload/reload and world change drop fake blocks silently; cinematic state must be torn down on those events anyway.
- Do not store `Player` objects; keep UUIDs and re-resolve.
- Never modify the server world for the effect: the cinematic must use only packets plus entities.
- Light recalculation on the client for a large fake-air batch can stutter weaker clients: split above ~4,000 blocks.

## Sources
- Player Javadoc 26.3 (`sendBlockChange`, `sendBlockChanges`, `sendMultiBlockChange`, `sendBlockUpdate`, `sendSignChange`, `sendBlockDamage`): https://jd.papermc.io/paper/26.3/org/bukkit/entity/Player.html
- Position: https://jd.papermc.io/paper/26.3/io/papermc/paper/math/Position.html
- BlockData / SoundGroup / Sound: https://jd.papermc.io/paper/26.3/org/bukkit/block/data/BlockData.html , https://jd.papermc.io/paper/26.3/org/bukkit/SoundGroup.html , https://jd.papermc.io/paper/26.3/org/bukkit/Sound.html
- Attributes: https://jd.papermc.io/paper/26.3/org/bukkit/attribute/AttributeInstance.html , https://jd.papermc.io/paper/26.3/org/bukkit/attribute/AttributeModifier.html , https://jd.papermc.io/paper/26.3/org/bukkit/attribute/Attribute.html
- Paper docs, display entities: https://docs.papermc.io/paper/dev/display-entities/
- Paper docs, spigot configuration (`moved-wrongly-threshold`): https://docs.papermc.io/paper/reference/spigot-configuration
- Paper commit "Don't resend blocks on interactions (#9413)": https://ayakael.net/mirrors/papermc/commit/9271ee7643614663a6d93323a9e8db2d52123166
- Spigot patch making "moved wrongly" configurable: https://ayakael.net/mirrors/papermc/commit/f90b460b99f2901b7d41381f2216daefe1090a7f , issue https://hub.spigotmc.org/jira/browse/SPIGOT-2090
- Earlier notes: `docs/research/per-player-visibility.md`, `docs/research/display-cookie.md`

## Hand-off

**plugin-coder** (pass 2, cinematic)
- One `FakeBlocks` helper: `hide(player, Collection<BlockPos>)`, `reveal(player, batch)`, `restoreAll(player)` using `sendMultiBlockChange(Map<Position, BlockData>)`, real data via `world.getBlockData(x,y,z)`, tile positions via `sendBlockUpdate`. Track hidden positions per player UUID.
- Re-assert on interact/break/place near the box (1 tick later), optional keepalive refresh, `restoreAll` on death, teleport, world change, quit (if online), reload, disable. The restore is synchronous.
- Keep-out: transient `MOVEMENT_SPEED`/`JUMP_STRENGTH` modifiers for `hold`/`slow`; `PlayerMoveEvent` + `PlayerTeleportEvent` guard; remove modifiers on every cleanup path and on join.
- `@block` sound: `blockData.getSoundGroup().getPlaceSound().key().asString()`, cached per `Material`, played via `SoundService` only.
- Test in game: (a) client clicks near the box (hidden blocks pop back?), (b) sign text after reveal, (c) walking into the box while `keep-out: none` (rubber-band message in log), (d) `build deny` plus click on the cookie (see `worldguard-plot.md`), (e) relog mid-cinematic.
- Config additions (names may shift): `house.cinematic.refresh-hidden-ticks` (0 = off), `house.cinematic.reassert-radius`, `house.cinematic.fake-air-per-tick` (default 4096).

**vfx-designer**
- `Effects.dropBlock(viewer, BlockData, Location target, Runnable onLanded)`: `BlockDisplay` per the recipe: spawn in consumer (non-persistent, hidden by default, tag `cookieclicker`, start translation above), `viewer.showEntity(plugin, d)`, **first transformation >= 1 to 2 ticks after spawn**, 3 segments (fall `fall-ticks`, bounce up/squash, settle), each via `runTaskLater`, one `setTransformation` per tick; `remove()` then `onLanded` in the same tick; check `isValid()` and viewer online in each step; `Effects.cancelFor(viewer)` removes all and drops callbacks (no `onLanded` after cancel). Cap with `limits.max-falling-displays` (suggest 24 per viewer). Corner anchoring, brightness from config (default from light above the target).
- Timings in `effects.yml` (`fall-ticks` ~6, `bounce-ticks` 2+2, `drop-height` ~4 blocks, bounce height ~0.25).
- Particles stay per player (`Player#spawnParticle`).

**sound-designer**
- `house_block_place` has `key: "@block"`; volume/pitch/source in `sounds.yml`; nothing to author. Note for the sound-check script: `@block` is a legal special key.

**model-designer**
- Design file: tile entities (sign) are allowed; give the sign position via the palette/layers. The coder resends sign text from the real world, so the text must be set when the house is placed. Keep total blocks < ~2,000.

CONTRACT CHANGES (proposed): none to IDs. Clarify `Effects.dropBlock`: the first transformation happens >= 1 tick after spawn; `cancelFor` never calls `onLanded`. Add config keys above under `house.cinematic.*`.
