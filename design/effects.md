# Effects

Status: draft (needs in-game review, see the end)

Every visual effect in Cookie Empire, how `effects.yml` describes them, and how code plays them.
Owner: vfx-designer. Engine: `src/main/java/dev/casperrs/cookieClicker/vfx/`. Config: `src/main/resources/effects.yml`
(copied to the plugin folder on first start; `/bakery reload` re-reads it). Style rules: `design/vfx-style-guide.md`.
Previews: `mise run vfx-previews` → `design/vfx-previews/<id>.png` (exported from the real Java shape code).

## Layer format

The same reference sits in a comment block at the top of `effects.yml`; the loader is `EffectsConfigParser`.
Keep all three in sync.

Units: blocks, degrees, ticks (20 ticks = 1 s). Colours `"#rrggbb"` (or `"#aarrggbb"` where transparency matters).
Bad values are logged at load and fall back to the default; an unknown `type`/particle/shape/item skips that layer;
an effect without valid layers is skipped. Effects missing from the server's file come from the plugin's built-in
copy, so new effects in an update work with an old file. Nothing is ever thrown mid-game.

### `limits`
| Field | Default | Meaning |
|---|---|---|
| `max-particles-per-effect` | 200 | particles one play may spawn; heavier presets are thinned at load (logged) |
| `max-display-entities-per-player` | 8 | effect display entities alive per viewer; at the cap the viewer's **oldest** display effect is removed early so the newest always shows |
| `max-display-entities-global` | 150 | effect display entities alive server-wide; at the cap new display layers are dropped (particles still play) |
| `max-falling-blocks-per-player` | 24 | `effects.dropBlock` displays falling at once for one viewer; at the cap a new drop **lands instantly** (see the API) |
| `max-falling-blocks-global` | 240 | the same, server-wide |
| `max-loops-per-player` | 8 | `effects.loop` loops per viewer (raised from 4 in factory-makeover: the factory runs up to 3 smoke + 2 steam loops per viewer, plus headroom); at the cap the new loop isn't started (logged once, `EffectHandle.NONE`) |

Falling blocks and loops don't count toward the display-entity caps, so a running house cinematic never evicts the
player's click numbers.

### Every preset
| Field | Default | Meaning |
|---|---|---|
| `layers` | required | list of layers (below) |
| `loop-every` | none | ticks between plays when code runs the preset with `effects.loop(...)`; without it a loop plays every 20 ticks (logged once). `play` ignores it |
| `min-interval-ticks` | 0 | rate cap: `play` shows the preset at most once per n ticks **per viewer** (one shared budget for `viewer == null`); plays in between are dropped silently, so code can call it once per event at any rate. 0 = no cap; 0–1200. Loops ignore it. The budget resets on reload and when the viewer quits |

### Every layer
| Field | Default | Meaning |
|---|---|---|
| `type` | required | `particle`, `floating-text`, `item-crumbs`, `scale-pop`, `block-drop` |
| `delay-ticks` | 0 | start this many ticks after `play` |
| `origin` | `[0, 0, 0]` | offset from the play location |

### `type: particle`
Personal particles: `viewer.spawnParticle(...)`; with `viewer == null`, `world.spawnParticle(...)` (everyone nearby).

| Field | Default | Meaning |
|---|---|---|
| `particle` | required | 26.3 particle key (`dust`, `item`, `end_rod`, `firework`, `minecraft:wax_on`, ...) |
| `shape` | `point` | `point`, `burst`, `sphere`, `ring`, `arc`, `line`, `helix`, `fountain`, `sparkles` |
| `points` | 8 | per tick (ring, sphere) or in total (burst, arc, line, helix per strand, fountain); sparkles: number of sparks; unused by `point` (use `count`) |
| `radius` | 0.5 | shape size; ring/sphere grow from `radius-start` to `radius` |
| `radius-start` | = radius | ring/sphere start radius (ease-out over `duration-ticks`) |
| `duration-ticks` | 1 | ring/sphere: redrawn every tick (cost × ticks); burst/arc/line/helix/fountain: points spread over the ticks; point: repeats; sparkles: life of one spark |
| `height` | 1.0 | helix height, sparkle rise |
| `turns`, `strands` | 1.0, 1 | helix revolutions and strands |
| `angle`, `start-angle` | 180, 0 | arc length; first angle of ring/arc/helix (0 = +x) |
| `spread` | 30 | fountain cone half-angle |
| `up-bias` | 0 | burst: 0 = full sphere, 1 = straight up |
| `to` | `[0, 1, 0]` | line end |
| `plane` | `horizontal` | ring/arc: `horizontal`, `vertical` (x/y), `facing` (stands up and turns to face the viewer; vertical without a viewer) |
| `stagger-ticks`, `emit-every` | 0, 1 | sparkles: start times spread over n ticks; emit each spark every n ticks |
| `direction` | `outward` for burst/fountain, else `none` | `none`: placed on the point (then `count`/`offset`/`speed` are vanilla randomness); `outward`: launched along the shape normal at `speed`; `up`: launched straight up |
| `speed` | 0 | launch speed in blocks/tick (outward/up) or vanilla random speed (none) |
| `offset` | 0 | vanilla random spread per point (direction none only) |
| `count` | 1 | particles per point (direction none only) |
| `force` | false | send in the extended range and regardless of the viewer's particle setting (like vanilla campfire smoke); for effects seen from across the plot |

Particle data by particle (checked against `Particle#getDataType()` in 26.3):

| Particles | Fields |
|---|---|
| `dust` | `color` (`#ffffff`), `size` (1.0, 0.01–4) |
| `dust_color_transition` | `color`, `to-color` (= color), `size` |
| `item` | `item` (`cookie`, vanilla item), `item-model` (optional, e.g. `cookieclicker:cookie`; used **only** for viewers the coder reports as having our pack, everyone else sees the vanilla item) |
| `block`, `falling_dust`, `dust_pillar`, `block_crumble`, `block_marker` | `block` (required) |
| `entity_effect`, `flash`, `tinted_leaves` | `color` |
| `effect`, `instant_effect` | `color`, `power` (1.0) |
| `dragon_breath`, `sculk_charge`, `shriek` | `value` |
| `vibration`, `trail`, `geyser*` | not supported (layer skipped) |

Cost = points over all ticks × `count` (launched particles: × 1).

### `type: floating-text`
One `TextDisplay`, visible to the viewer only, that pops in, rises with ease-out and shrinks/fades at the top.

| Field | Default | Meaning |
|---|---|---|
| `text` | required | MiniMessage. Placeholders from code (e.g. `<amount>`) are inserted with `Placeholder.unparsed` |
| `rise` | 0.8 | blocks risen over its life |
| `duration-ticks` | 20 | life; at least `start-tick + pop-in-ticks + 1 + fade-ticks` (raised and logged otherwise) |
| `scale` | 1.0 | text size (1.0 ≈ 0.2 block per line) |
| `pop-in-ticks`, `pop-in-scale`, `overshoot` | 3, 0.5, 1.15 | spawn at `scale × pop-in-scale`, grow to `scale × overshoot`, settle; 0 ticks = no pop-in |
| `fade-ticks` | 6 | shrink to nothing at the end; opacity steps to 170 and 90 meanwhile |
| `start-tick` | 1 | first animation tick (the client needs the spawn state first) |
| `spread` | 0.2 | rapid repeats fan out over a golden-angle disc of this radius |
| `shadow`, `see-through`, `background`, `billboard`, `view-range` | true, false, `#00000000`, `center`, 0.5 | TextDisplay looks |

Motion (keyframes, client-interpolated): tick `start` → 35 % of the rise at peak scale over `pop-in-ticks`;
then → 85 % at rest scale until the fade; then → 100 % at scale 0.01 over `fade-ticks`; removed at `duration-ticks`.
Cost: 1 display entity for its life.

### `type: item-crumbs`
Small `ItemDisplay`s (viewer only) that fly out in an upward cone, tumble and shrink away.
Fields: `item` (`cookie`), `item-model`, `count` (4, max 16), `scale` (0.2), `distance` (0.7), `spread` (60),
`lift` (0.2), `drop` (0.5), `spin` (150, max 200; sent in two steps under 120°), `duration-ticks` (14),
`start-tick` (1), `view-range` (0.5). Cost: `count` display entities for their life.

### `type: scale-pop`
Only used by `effects.pop(display, viewer)`; `play` ignores it.
`steps`: list of `{ scale: [x, y, z] or a number, ticks: n }`, multipliers of the display's rest scale. A final
`{ scale: 1, ticks: 3 }` is added when the last step isn't 1.

### `type: block-drop`
Only used by `effects.dropBlock(...)` through the preset `house_block_drop`; `play` ignores it. A `BlockDisplay`
(viewer only, non-persistent, tagged like every effect entity) anchored at the target block's integer corner spawns
`drop-height` above it, then three client-interpolated segments run, one `setTransformation` per tick:
fall → bounce with squash → settle. At the end the display is removed and `onLanded` runs in the same tick.

| Field | Default | Meaning |
|---|---|---|
| `start-tick` | 2 | first segment (≥ 1: the client needs the spawn state first); the block hangs until then |
| `drop-height` | 4.0 | blocks above the target it starts (0–16) |
| `fall-ticks` | 6 | fall time; the **impact** is at `start-tick + fall-ticks` |
| `bounce-height` | 0.25 | pop back up after the impact (0 = squash only) |
| `bounce-ticks` | 2 | bounce time |
| `squash` | `[1.08, 0.85, 1.08]` | width/height/depth at the top of the bounce (0.25–2, one number = all three); stays centred on the block, bottom face anchored |
| `settle-ticks` | 2 | back to rest; **landing** at `start-tick + fall + bounce + settle` (12 by default) |
| `impact-effect` | `house_block_land` | effect ID the engine plays for the viewer at the block's bottom centre on impact; `none` = off |
| `impact-particles` | 4 | `block` particles of the dropped block itself on impact (0–32) |
| `brightness` | `auto` | `auto` = the light just above the highest block of the target column (the roof once the house stands; the server-side house would otherwise make the display dark), or `[block, sky]` 0–15 |
| `view-range` | 1.0 | display view range (1.0 = 64 blocks) |

Cost: 1 `BlockDisplay` per falling block for 12 ticks (counts toward `max-falling-blocks-*`), 3 transformation
updates, 1 particle packet (count 4) + `house_block_land` at the impact.

## Effect catalogue

| ID | Looks like | When | Cost per play | Placeholders |
|---|---|---|---|---|
| `cookie_click` | 8 cookie crumbs (`item` particle with the cookie, our 3D model's texture with the pack) pop off the hit point in an up-biased spray and fall with gravity (~0.6 block); a cookie-gold `+<amount>` pops in above, rises 0.8 and fades within 1 s. Repeats fan out so numbers don't stack. | every normal click, viewer = the clicker | 8 particles, 1 TextDisplay for 20 ticks | `amount` |
| `cookie_crit` | Golden flash: a light-gold → deep-gold dust shell expands 0.25 → 0.75 over 3 ticks; 14 white firework sparks burst outward (~1 block); 12 cookie crumbs spray; 6 gold sparkle trails rise 1.1 around the number; 3 mini cookies (ItemDisplay) tumble out and drop; a big bold gold-gradient `+<amount>` (×1.8, overshoot 1.3) rises 1.0 and fades over 1.5 s. | crispy crit, instead of `cookie_click` | 122 particles, 4 display entities (≤ 30 ticks) | `amount` |
| `purchase` | A cookie-gold ring opens 0.25 → 0.55 over 3 ticks, standing up and facing the buyer; 6 light-gold sparkle trails rise 0.9; 8 small white firework sparks burst 1 tick later. | a building was bought; play at the clicked panel row, viewer = buyer | 80 particles, 0 entities | none |
| `upgrade_bought` | A white-to-gold ring snaps open 0.15 → 0.5 over 3 ticks facing the buyer, 4 sparkle trails rise 0.8, 8 firework sparks burst. | a one-time upgrade was bought; play at the clicked row as the icon pops out, viewer = buyer | 60 particles, 0 entities | none (placeholders accepted, unused) |
| `upgrade_shelved` | Tiny glint: 4 `end_rod` flecks and a small warm ring (0.05 → 0.16, 2 ticks). | the upgrade icon lands in its shelf slot; viewer = owner | 10 particles, 0 entities | none |
| `pop` | Squash (1.25, 0.75, 1.25) 2 ticks → stretch (0.92, 1.10, 0.92) 2 ticks → rest 3 ticks, scale only. | through `effects.pop(display, viewer)` on the clicked cookie display | 3 metadata updates on an existing display | none |
| `house_block_drop` | The block appears 4 blocks above its spot, hangs 2 ticks, falls in 6 (impact at tick 8), pops up 0.25 while squashing wide and flat (1.08 × 0.85), settles in 2; at tick 12 the display vanishes and the real block is there. A few crumbs of the block itself and the `house_block_land` puff at the impact. | only through `effects.dropBlock(...)`, never `play` | 1 BlockDisplay × 12 ticks + 4 block particles + 8 | none |
| `house_block_land` | 8 small white poofs roll outward along the ground from just outside the block's foot. | played **by the engine** at every drop impact; play it yourself only for a block revealed without a drop | 8 particles | none |
| `house_foundation_dust` | A dusty beige ring (#c9b79c) rolls out over the ground from under the block (0.75 → 1.1), plus 4 low poofs to the corners. | bottom layer only, per block, in `onImpact`; location = block bottom centre | 28 particles | none |
| `house_sparkle` | 3 light-gold → gold sparks rise 0.7 off the top face of the new block (staggered), and 2 orange wax glints twinkle just above it. | every block, in `onLanded` (when the real block appears); location = block bottom centre | 20 particles | none |
| `house_confetti` | Finale: a warm flash, 24 white firework sparks, a fountain of 24 totem confetti, and four dome shells of tinted leaves (gold, pink, mint, sky blue; radius 1.8 → 4.2, staggered by 1 tick) that hang around the roof and flutter down over the next seconds. | once when the roof is done; location = top centre of the house, about 1 block above the ridge | 153 particles | none |
| `house_chimney_smoke` | `loop-every: 10`: each play sends one big campfire smoke puff drifting up (lingers ~5 s, so a column of ~10 puffs) and two small dark wisps; extended range (`force`). | `effects.loop("house_chimney_smoke", chimneyTop, viewer)` for every player in the plot who isn't mid-cinematic; location = centre of the chimney's top face | 3 particles per play = 6/s per viewer | none |
| `factory_steam` | `loop-every: 37` (prime, never in step with the 10-tick smoke): short white roof-vent puffs at uneven gaps, a big puff at tick 0 (cloud launched up + 2 white_smoke wisps), a small one at 5, a big one at 15, a small one at 24, then a 13-tick pause; each puff a few cm off centre. Cloud fades in ~1 s, so it reads as separate puffs, not a column; white and short-lived vs the grey, lingering campfire column of `house_chimney_smoke`. Extended range (`force`). | `effects.loop("factory_steam", ventTop, viewer)` at every `points.steam` cell, same viewers/lifecycle as the chimney smoke; location = centre of the vent's top face (the steam cell's bottom centre, `(x + 0.5, y, z + 0.5)`) | 8 particles per cycle ≈ 4.3/s per vent per viewer; counts 1 toward `max-loops-per-player` | none |
| `belt_arrive` | A belt cookie lands on the pedestal: 5 cookie crumbs (`item` particle, our model with the pack) hop up ~0.2 in a tight up-biased spray and fall back (~0.5 wide), and a small warm-white (`#fff2a8`, size 0.5) ring of 6 dust motes pops open 0.08 → 0.22 over 2 ticks just above the landing point. Reads as a soft "plip", not a celebration: it repeats several times a second. | once per belt cookie as it reaches the pedestal end and vanishes; location = the cookie's centre at that moment; viewer = the belt's owner (never null: it's their own CPS) | 17 particles, 0 entities. `min-interval-ticks: 4` → at most 5 plays/s = **85 particles/s per viewer**; extra arrivals in the same 4 ticks land without an effect | none |
| `plot_outline` | **Admin-only tool.** One sky-blue (`#8ec5ff`) dust mote, size 1.5, extended range (`force`) so the outline shows from across the plot. | `/bakery plot show`: once per outline point of the **plot** region, viewer = the admin (`plot.show.plot-effect`) | 1 particle per play (≈120 per region redraw) | none |
| `house_outline` | **Admin-only tool.** Same as `plot_outline` in cookie gold (`#f5c542`), so plot and house borders are told apart at a glance. | `/bakery plot show`: once per outline point of the **house** region (`plot.show.house-effect`) | 1 particle per play (≈120 per region redraw) | none |

Previews: `design/vfx-previews/<id>.png` for every preset (pop shows the outline of a 1-block disc at the
interpolated scale per tick, `house_block_drop` the edges of the falling block per tick, `house_chimney_smoke` three
loop cycles, `factory_steam` too).

Per-block budget during the cinematic, per block: drop 1 entity + ~3 packets for the impact, sparkle 20,
foundation dust 28 (bottom layer only). At 2 blocks per tick that is about 50–100 particle packets per tick to one
player for a second or two, then nothing but 6 smoke particles per second.

## Java API (for the plugin-coder)

Package `dev.casperrs.cookieClicker.vfx`, class `Effects`. Main thread only; everything fails soft (logged once,
skipped).

```java
// construction (onEnable), after config is available
Effects effects = new Effects(this);              // or new Effects(this, sharedMiniMessage)
effects.load();                                   // writes effects.yml if missing, loads it, registers the
                                                  // cleanup listener, sweeps leftover effect entities
effects.setPackCheck(player -> packStatus.isLoaded(player));   // optional: who sees item-model crumbs

// playing
effects.play("cookie_click", hitLoc, player, Map.of("amount", formatted));   // formatted = "+N" number text
effects.play("cookie_crit",  hitLoc, player, Map.of("amount", formatted));
effects.play("purchase",     rowLoc, player);
effects.pop(cookieDisplay, player);               // the clicked player's own cookie ItemDisplay

// /bakery reload
effects.reload();                                 // = load(): stops running effects, removes their entities

// onDisable
effects.shutdown();                               // stops all, removes all, restores popped displays, unregisters
```

### House cinematic (starter-house)

```java
// one block of the reveal (target = the block's location, data = the real block's BlockData there)
boolean animating = effects.dropBlock(viewer, data, target,
        () -> {                                   // onImpact: the falling block hits the ground (tick 8)
            sounds.play("house_block_land", ...); sounds.play("house_hammer", ...);
            if (bottomLayer) effects.play("house_foundation_dust", bottomCentre, viewer);
        },
        () -> {                                   // onLanded: display already removed, same tick (tick 12)
            fakeBlocks.reveal(viewer, pos);       // resend the real block
            sounds.play("house_block_place", ...);  // "@block": the block's own place sound
            effects.play("house_sparkle", bottomCentre, viewer);
        });
// or without the impact callback: effects.dropBlock(viewer, data, target, onLanded)

// completion
effects.play("house_confetti", houseTopCentre, viewer);
EffectHandle smoke = effects.loop("house_chimney_smoke", chimneyTop, viewer);   // keep it per viewer
smoke.cancel();                                   // the viewer left the plot
effects.cancelFor(viewer);                        // abort: all this viewer's drops and loops, no callbacks
```

`bottomCentre` = `(x + 0.5, y, z + 0.5)` of the block; `chimneyTop` = the centre of the chimney's top face.
The engine already plays `house_block_land` at the impact (the `impact-effect` of `house_block_drop`), so don't
play it yourself for dropped blocks.

**`dropBlock` callback rules** (`onImpact` may be null; both run on the main thread, at most once each,
`onImpact` always first; exceptions are logged and swallowed):

| Result | When | Callbacks |
|---|---|---|
| `true` | the block is falling | `onImpact` at the impact, `onLanded` at the landing, the display removed **before** `onLanded` in the same tick. If the display disappears early (chunk unload) while the viewer is still there, both run on the next tick ("land now"), so the reveal never stalls. |
| `true`, then cancelled | `cancelFor(viewer)`, viewer quit or world change, `reload()`, `shutdown()` | display removed; a callback that hasn't run **never** runs. The caller's own cleanup resends the real blocks. |
| `false`, callbacks already ran | at `max-falling-blocks-per-player` / `-global`, block data is air, engine not loaded, spawn failed | "landed instantly": `onImpact` then `onLanded` run **inside** the `dropBlock` call (impact effects still play) |
| `false`, nothing ran | called off the main thread, or the viewer is offline or not in the target's world | nothing; restore the block yourself |

Pace the reveal with your own `house.cinematic.max-falling-displays` (≤ 24); the engine's cap is the hard safety
net, and a capped block simply appears without its fall. `onLanded` frees the cap slot before it runs, so starting
the next drop from inside `onLanded` is fine.

**`loop`**: `effects.loop(id, loc, viewer[, onRepeat])` returns an `EffectHandle` (`cancel()`, `isActive()`).
First play immediately, then every `loop-every` ticks (10 for the smoke). Skips plays while the location's chunk
isn't loaded. `onRepeat` runs after every play (e.g. replay `house_chimney_crackle` every 6th cycle ≈ 3 s; sounds
stay in `SoundService`). Ends on `cancel()`, `cancelFor(viewer)`, viewer quit or world change, `reload()` and
`shutdown()`; **after `/bakery reload` start the smoke loops again**. Returns `EffectHandle.NONE` (inactive) for an
unknown ID, an offline/other-world viewer, or at `max-loops-per-player`.

**Wiring**: nothing new. The same `Effects` instance; `load()`/`reload()` and `shutdown()` already stop all drops
and loops, and the engine's own listener handles quit. Debug counters: `activeFallingBlocks()`, `activeLoops()`.
Constant `Effects.BLOCK_DROP` = `"house_block_drop"`.

### Conveyor belt (conveyor-belt)

```java
// a belt cookie reached the pedestal end: remove its ItemDisplay, then
effects.play("belt_arrive", cookieCentre, owner);   // owner = the player whose CPS drives this belt
```

Call it for **every** arriving cookie; don't throttle in code. The preset's `min-interval-ticks: 4` drops plays
that come within 4 ticks of the last one for the same viewer (at most 5 per second), so the cost is bounded no
matter how high the CPS curve goes. No entities, so it never evicts click numbers from the display caps. Nothing to
wire: same instance, the budget resets on reload and quit.

### Other public methods

Other public methods: `play(id, loc, viewer)` (no placeholders), `pop(display, viewer, presetId)`, `has(id)`,
`effectIds()`, `sweepLeftovers()` (also run by `load()`; returns the count), `entityKey()` (the
`cookieclicker:vfx` PDC key), `activeDisplayEntities()` (for a debug command / the tester), constant `Effects.POP`.

Notes for the caller:
- **`amount`** is inserted as unparsed text, so pass the already formatted number (e.g. `"15"` or `"1.2 million"`);
  the `+` is in the config text. Placeholder names must be lower-case tag names (`amount`).
- **Where to play**: for clicks, pass the point where the player hit the cookie, moved ~0.1 block toward the player
  (e.g. the `Interaction` hit position, or the cookie centre plus 0.1 × the direction to the player's eye). Half of
  the crumb burst goes "into" the cookie; spawning slightly in front keeps it visible. For purchases, the centre of
  the clicked row. The crit replaces the click effect (don't play both).
- **Viewer** is the clicking player for all three; `null` would show the effect to everyone nearby.
- **Pop and the spin/bob controller** (contract rule): the pop only changes the scale, re-reading
  `getTransformation()` at every step, and keeps translation and rotations. The spin controller must also keep the
  current scale (build each segment from `display.getTransformation().getScale()`). If both run in the same tick,
  issue the spin segment **before** calling `pop` (the last `setTransformation` in a tick wins, and the pop's short
  duration should be the one that sticks). A pop briefly fast-forwards the running spin segment (≤ 30° with 10-tick
  segments), hidden by the squash. A pop during a pop restarts from the same rest scale, so spam never drifts.
- **Cleanup** is the engine's job for its own entities: removed on completion, on `EntitiesUnloadEvent`, when the
  viewer quits, on reload and on shutdown; chunk loads and `load()` sweep anything carrying the PDC key. Effect
  entities also carry the shared `cookieclicker` scoreboard tag, so the coder's own sweep removes them too.
- **Pack check**: without `setPackCheck`, everyone gets the vanilla cookie item for crumbs (safe: never a missing
  texture). Once the coder knows a player has `SUCCESSFULLY_LOADED` the pack, return true for them.

## Custom-looking particles

- Crumbs use `Particle.ITEM` with an `ItemStack` (`Material.COOKIE`) carrying the `item_model` data component
  `cookieclicker:cookie`. Since the 1.21.4 item model system the client builds item particles from the item's model,
  taking the texture from the model's `particle` texture. No vanilla particle is retextured, so nothing else on the
  server changes. This is **unverified in game** (see review list). Fallback is automatic: viewers without the pack
  (or until `setPackCheck` is wired) get the vanilla cookie item, whose crumbs already look right.
- No resource-pack particle retextures are used (`assets/minecraft/particles/` untouched).

## Needs in-game review (human)

1. `cookie_click` crumbs with the pack: do item particles show our cookie texture (needs `"particle"` set in the 3D
   model's textures) and look like crumbs, not purple/black squares? Without the pack: vanilla cookie crumbs.
2. Launched particles (`count = 0` + direction × speed) on 26.3: crumbs and firework sparks should fly outward, not
   sit still. If they don't move, report it; the fallback is `direction: none` with `offset`/`speed`.
3. Floating text: does it pop in and rise smoothly from the first tick (interpolation needs the client to have the
   spawn state; `start-tick: 1`)? If it jumps, try `start-tick: 2`. Readable size at the station distance?
4. Fast clicking (10+ CPS): numbers fan out, at most 8 effect entities per player, oldest disappear early, no lag.
5. Crit: clearly bigger than a click; the 3 mini cookies tumble and vanish; nothing lingers after 1.5 s.
6. Purchase ring faces the buyer at the panel row and isn't hidden inside the panel.
7. Pop: squash/stretch reads well on the 3D cookie and never stops or reverses the spin; spam-clicking doesn't
   change the cookie's size.
8. Two players at the same station: neither sees the other's particles, numbers or crumbs.
9. `/bakery reload` and a restart mid-effect: no floating numbers or crumbs left behind.
10. Block drop: the block appears in the air and falls smoothly (no snap to the ground on the first tick; if it
    snaps, raise `start-tick` to 3), bounces with a readable squash, and swaps to the real block at the landing
    without a flicker, z-fighting or a lighting jump. Check at night and inside the hollow (fake-air) house:
    `brightness: auto` should match the real blocks; else try `[15, 15]` or report.
11. Drops are seen only by the cinematic player (second account sees nothing falling).
12. `house_block_land` and `house_foundation_dust` read as a soft puff/dust roll at the block's foot, not as
    smoke; impact `block` crumbs use the block's own texture.
13. `house_sparkle` per block isn't too busy when ~2 blocks land per tick; tone down to `points: 2` if it is.
14. `house_confetti`: the tinted leaves hang in a dome over the roof and flutter down (they live ~15 s on the
    client and may rest on the roof); the totem fountain shows colourful confetti. Not too much at 1 block above
    the ridge?
15. `house_chimney_smoke`: a calm continuous column visible from the plot edge (`force`); the column isn't broken
    (gaps mean `loop-every` too long) or too thick. Stops when leaving the plot, on relog and after reload until
    the coder restarts it.
16b. `factory_steam` (factory roof vent): reads as white steam puffs with an uneven rhythm, clearly different from the grey
    smokestack columns; puffs not too small from the plot entrance (raise `speed`/add a `white_smoke` count if they
    vanish), and two vents don't look identical (both loops start together; ask the coder to start the second a few
    ticks later if they do).
16. Quit, `/bakery reload` and a world change mid-cinematic: no BlockDisplay left floating (`/minecraft:kill
    @e[type=block_display,tag=cookieclicker]` should find nothing).
17. `plot_outline` / `house_outline` (`/bakery plot show`): blue plot and gold house borders are easy to tell
    apart and visible from the far side of the plot. Dust motes live a random, short time on the client, so with a
    20-tick redraw the line may flicker or show gaps; if it does, raise `size` (longer-lived, fatter motes) or ask
    the coder for a shorter redraw interval rather than adding particles per point.
18. `belt_arrive` at low CPS (one cookie now and then) and at max belt speed: the crumb hop and tiny ring read as
    a soft landing on the pedestal, not a burst; at high CPS the effect doesn't strobe (5/s cap) and the arrival
    point isn't hidden inside the pedestal cookie (if it is, ask the coder to play it ~0.1 above the belt surface,
    or raise `origin` y). Crumbs with the pack show our cookie texture.


## Motion-aware pop (upgrade-holograms)
`effects.pop(display, viewer, presetId, PopMotion motion)`: every step, including the final rest step, sets translation and left rotation from `motion` at that step's end tick (`ticksAhead` = the step's ticks, "now" = the tick of the call) and the scale from the preset, so squash, spin and bob share one interpolation. `PopMotion` has `Vector3f translation(int ticksAhead)` and `Quaternionf leftRotation(int ticksAhead)`. The 2- and 3-argument overloads are unchanged (they keep the current translation/rotation, which is what freezes a segmented spin). Coder: implement `PopMotion` over `CookieMotion` (`bob(now + ahead)`, `rotationY(angle(now + ahead))`), call `beforePop` as today, and let the motion task start its next segment at the pop's end tick (`now + totalTicks`, 7 for the default pop) from the same functions.
