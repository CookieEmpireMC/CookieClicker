# Per-player entity visibility (hide by default, show to one player)

**Target version:** Paper 26.3 (`26.3.build.205-stable`, Java 25). Feature `bakery-core`. Researched 2026-10-18.
"Verified" = read in the 26.3 Javadocs or Paper docs. Statements marked **(source knowledge)** come from how Paper implements this and are **not** in the Javadocs; the in-game test must confirm them.

## Summary
- Create each entity hidden: `entity.setVisibleByDefault(false)` inside the `spawn(..., Consumer)` callback, then call `owner.showEntity(plugin, entity)` right after the spawn. Nobody else ever receives the entity.
- This works the same for `ItemDisplay`, `TextDisplay` and `Interaction` (all are normal entities). A player who cannot see an Interaction cannot click it.
- Visibility state belongs to the **online player object**, not to the entity. Plan for: relog (state lost), chunk unload (our non-persistent entities are gone), teleport and world change (re-tracked by the server).
- Safest lifecycle: tie the owner's views to the owner's presence. Spawn when the owner is online and near the station; remove when they leave range, quit, change world, or the chunk unloads. Spawning again re-runs `showEntity`, so nothing depends on remembered visibility state.

## Version notes
| API | 26.3 status |
|---|---|
| `Entity#setVisibleByDefault(boolean)`, `Entity#isVisibleByDefault()` | exists, verified. Javadoc: if not visible by default, `Player#showEntity(Plugin, Entity)` must be called before the entity is visible to a given player. |
| `Player#showEntity(Plugin, Entity)` | exists, verified. "Allows this player to see an entity that was previously hidden. If another plugin had hidden the entity too, then the entity will remain hidden until the other plugin calls this method too." |
| `Player#hideEntity(Plugin, Entity)` | exists, verified. "Visually hides an entity from this player." |
| `Player#canSee(Entity)` | exists, verified. True if the entity is not being hidden from this player. |
| `Entity#getTrackedBy()` | exists, verified: `Set<Player>` of players currently tracking the entity. `getTrackedPlayers()` is deprecated, do not use. |
| `PlayerShowEntityEvent` / `PlayerHideEntityEvent` | exist (26.3 Javadocs list `PlayerShowEntityEvent`). Fired only when visibility actually changes. Not needed. |
| Paper docs ("Display entities") | explicitly recommend `setVisibleByDefault(false)` plus `Player#showEntity` / `hideEntity`, and `setPersistent(false)` for temporary displays. |
Earlier Paper releases marked these methods draft/experimental. The 26.3 Javadoc pages we read show no experimental marker, but a later Javadoc check by the coder (`mise run build` with `-Xlint` shows it) will tell if that changes.

## Plugin side

### The spawn pattern (main thread)
```java
// Plugin plugin = the CookieClicker plugin instance (the same instance every time)
TextDisplay label = world.spawn(loc, TextDisplay.class, e -> {
    e.setPersistent(false);
    e.setVisibleByDefault(false);          // nobody gets spawn packets
    e.addScoreboardTag("cookieclicker");
    /* ... configure ... */
});
owner.showEntity(plugin, label);           // only the owner now sees it
```
- `RegionAccessor#spawn(Location, Class<T>, Consumer<? super T>)` (verified) runs the consumer **before** the entity is added to the world, so there is no tick where other players can see it.
- `showEntity` before the entity is tracked is fine: the event fires "regardless of whether the entity was within tracking range" (Javadoc of `PlayerShowEntityEvent`).
- Use the **same plugin instance** for `showEntity` as everywhere. If the plugin is disabled and reloaded, old hide records no longer count **(source knowledge: the records are keyed by plugin and held weakly)**.
- Pass the real owner `Player`. Never keep `Player` objects in long-lived maps; keep the UUID and re-resolve with `Bukkit.getPlayer(uuid)` each time. Remove the entry on `PlayerQuitEvent`.

### Verify, do not assume
```java
if (!owner.canSee(display)) { owner.showEntity(plugin, display); }   // cheap, idempotent self-heal
Set<Player> seen = display.getTrackedBy();  // test helper: should contain only the owner (when in range)
```
The tester can assert, in an in-game guide step: with two players at the same station each sees only their own cookie, and `getTrackedBy()` never contains the other player.

### Lifecycle recommendation (proximity based)
One repeating main-thread task, every 20 ticks (config `station.refresh-ticks`):
1. For each online player and each station in the **same world key**: `inRange = distanceSquared <= station.view-distance^2` and the chunk is loaded.
2. `inRange && !views.contains(player)`: spawn the view (cookie, hitbox, panel), `showEntity` for every entity.
3. `!inRange` or world changed: despawn the player's view (`remove()` each entity, drop registry entries).
4. `PlayerQuitEvent` / `PlayerChangedWorldEvent` / `PlayerTeleportEvent` (to another world): despawn immediately; do not wait for the task. Station removal and reload: despawn everything.
5. `EntitiesUnloadEvent`: drop registry entries of the unloaded entities (they will not exist after reload because they are non-persistent).
This avoids every question about "what does Paper remember": every spawn is followed by `showEntity`.

### What happens when (and the source of each claim)
| Event | Result |
|---|---|
| Entity created with `setVisibleByDefault(false)` | not sent to anyone (verified Javadoc semantics) |
| `showEntity(plugin, e)` | owner receives it when in tracking range (verified Javadoc; event fires regardless of range) |
| Chunk unloads | non-persistent entities are removed with it and **not saved**; after reload they do not exist. The registry must be cleaned and the view respawned. (`setPersistent(false)`: "Sets whether or not the entity gets persisted", verified; Paper docs use it for temporary displays.) |
| Owner moves out of the entity's tracking range, comes back | the server stops and restarts tracking; per-player "shown" state **should stay** (source knowledge: kept per online player keyed by entity UUID). If a test shows the entity missing, `canSee` + `showEntity` heals it. |
| Owner teleports in the same world | same as above |
| Owner changes world | the server untracks everything. Our views are despawned by step 4 anyway. |
| Owner quits and rejoins | the player object is new; shown-state is gone (source knowledge). Views were despawned on quit; respawn them with new `showEntity` calls. |
| Server restart / `/bakery reload` | no entity survives (non-persistent); the registry and the sweep make sure of that. |
| Another player stands at the station | sees nothing: the entities are not sent. Their own view spawns for them. |

## Interaction entities
- An `Interaction` is a normal entity: hidden by default, shown to the owner only. Other players do not know it exists on their client, so they cannot target it.
- Still verify server-side that `event.getPlayer()` is the owner (see `display-cookie.md`). Do not trust the client.
- An Interaction's box is solid for the owner's click ray only; it does not block other players' movement or clicks (no collision).

## Pitfalls
- **Spawn without the consumer:** `world.spawn(loc, X.class)` followed by `setVisibleByDefault(false)` leaves a window where entities are already tracked and sent to everyone nearby. Always configure inside the consumer.
- **`PlayerJoinEvent`:** do not spawn in the join handler itself. The player is not necessarily fully tracking yet; schedule the spawn 1 to 20 ticks later (the proximity task does this for free). The Javadoc of `PlayerJoinEvent` also warns that teleporting in that event is undefined.
- **Forgetting `showEntity` after a respawn** is the most common bug: the entity exists but nobody sees it. Put spawn and `showEntity` in one helper (`ViewSpawner.spawn(owner, ...)`) and never call `spawn` directly elsewhere.
- **Different plugin instance/key:** `hideEntity` / `showEntity` are tracked per plugin; always the same instance.
- **Performance:** each `showEntity` is a map operation plus a packet; fine for a few players. Every floating "+N" entity also needs hide-by-default and `showEntity` for its viewer, then `remove()` after about 20 ticks. At 10 clicks per second that is 10 short-lived entities per second per player: pool or cap them (config `effects.max-floating-per-player`).
- **Never** use the global scoreboard team or `setInvisible` as a "hide" trick for this: that hides the model but not the entity packet.
- `Entity#getTrackedPlayers()` is deprecated: use `getTrackedBy()`.

## Sources
- Entity Javadoc 26.3: https://jd.papermc.io/paper/26.3/org/bukkit/entity/Entity.html (`setVisibleByDefault`, `isVisibleByDefault`, `getTrackedBy`, `setPersistent`)
- Player Javadoc 26.3: https://jd.papermc.io/paper/26.3/org/bukkit/entity/Player.html (`showEntity`, `hideEntity`, `canSee`)
- PlayerShowEntityEvent 26.3: https://jd.papermc.io/paper/26.3/org/bukkit/event/player/PlayerShowEntityEvent.html
- Paper docs, display entities: https://docs.papermc.io/paper/dev/display-entities/
- PlayerJoinEvent 26.3: https://jd.papermc.io/paper/26.3/org/bukkit/event/player/PlayerJoinEvent.html
- EntitiesUnloadEvent 26.3: https://jd.papermc.io/paper/26.3/org/bukkit/event/world/EntitiesUnloadEvent.html
- Origin of the API (Paper #811, "entities invisible by default"): https://ayakael.net/mirrors/papermc/commit/86d3c9caa7d82a155729ca267e843b0ca1510366

## Hand-off

**model-designer**: nothing.

**sound-designer**: nothing. (Sounds are played per player with `Player#playSound`, so they are per player already.)

**vfx-designer**
- Every entity an effect spawns (floating text, any display) uses the spawn pattern above and `viewer.showEntity(plugin, e)`. `Effects.play(id, loc, viewer, placeholders)` takes the viewer for exactly this reason.
- Particles are per player with `Player#spawnParticle` (no entity needed), which is also per player by nature.
- Cap or pool short-lived entities (see Pitfalls).

**plugin-coder**
- One `ViewSpawner` helper that does spawn-in-consumer + `showEntity`. A registry per owner UUID (`Map<UUID, StationView>`).
- Proximity task (20 ticks), quit/world-change/teleport/unload cleanup, `canSee` self-heal check (cheap) each refresh.
- Keep UUIDs, not `Player` objects.
- Add a debug command or log line for the tester: `getTrackedBy()` of each view entity.
- In the in-game guide, add the two-player test step.

CONTRACT CHANGES (proposed): add config keys `station.view-distance` (blocks) and `station.refresh-ticks` (default 20) to the `station.*` group.
