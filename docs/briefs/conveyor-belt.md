# conveyor-belt

**Status:** in progress  <!-- planned | in progress | review | done -->
**Created:** 2026-10-21 · **Approved:** 2026-10-21 · **Depends on:** factory-makeover (merged, PR #6)

## Goal
Two things the user saw missing in the first in-game test of the factory:
1. **The conveyor belt** (GDD §3.1): a belt runs from the factory's loading hatch to the pedestal, with cookies riding on it. The higher your CPS, the more cookies and the faster the belt.
2. **A zoomed-out build view:** the first-visit build is watched from a raised camera that shows the whole factory and plot, not from the ground 8 blocks from the front wall.

## Player story
I walk in through the gate for the first time. My view lifts into the air in front of the plot, and I watch the whole Cookie Works drop in, from the stacks to the fence. When the fanfare ends, I'm back on my feet at the gate. From the loading hatch, a conveyor belt runs to my cookie, and cookies slide out of the hatch along it and land on the pedestal with a small crumb puff. At first a cookie comes out now and then. Once I've bought a few buildings, they come out quicker and closer together, and the belt hums along. A friend on the same plot sees the same belt, but with the cookies from **their own** CPS.

## Acceptance criteria
**Belt**
- [ ] `/bakery plot setup` also places the belt along `layout.belt` (the 5 cells from the hatch to the cell before the pedestal), using a belt style file (`houses/belt.yml`, like `fence.yml`). The backup and `setup undo` cover the belt too, so the world comes back exactly as it was.
- [ ] The belt has a moving surface: an item model with an animated texture, one item display per cell, the same for everyone. Without the resource pack it falls back to a vanilla look (e.g. a dark carpet or slab look) that still reads as a belt.
- [ ] Cookies ride on the belt: small cookie item displays (`cookieclicker:cookie`, the same model as the pedestal cookie) move from `belt_out` to the pedestal. Each player sees only their own cookies, the same privacy rule as the pedestal.
- [ ] **CPS drives the belt:** both the time between cookies and their speed come from the viewer's CPS through a config curve (`belt.*` in `config.yml`: min/max speed, min/max interval, a CPS scale). At 0 CPS no cookies come out. The speed and interval follow CPS changes within a few seconds (after a purchase).
- [ ] **Cookies glide smoothly:** each cookie moves by display interpolation (one transformation per cookie, or a few), not a teleport every tick. When it reaches the pedestal it disappears with the `belt_arrive` effect.
- [ ] **Cost limit:** at most `belt.max-cookies` (default 8) cookies per player on the belt at once. The arrive effect is capped by the effects engine. Belts only run for players who are in the plot (and not during their own cinematic).
- [ ] The belt hums (`belt_loop` sound) for players near it while their belt runs, quieter than the oven hum.
- [ ] **Purely visual:** cookies on the belt don't add cookies. The economy is unchanged.

**Camera**
- [ ] The layout gets a `camera` point (`layout.camera: {at: [x, y, z], yaw, pitch}`, in plot cells, which may lie **outside** the plot and above the ground). In `factory.yml` it frames the whole factory, the belt, the pedestal and the fence. Check it with a preview from that point.
- [ ] With `house.cinematic.camera: true`, the player is put in spectator mode and locked to the camera point for the whole build. They can't fly off or interact. Afterwards they get their **original game mode** back and stand at the viewpoint (by the gate), looking at the factory.
- [ ] **Safety:** the original game mode is stored on the player (PDC). Quit, death, kick, `/bakery reload`, disable or a crash mid-cinematic never leave a player stuck in spectator mode. On the next join, a player still marked as in the camera is restored. All the existing abort paths of the cinematic also end the camera.
- [ ] With `camera: false`, or with a layout without `camera` (the `default` cottage), the cinematic works exactly as today.

**Quality**
- [ ] Tests: belt layout/style parsing, CPS → speed/interval curve, the max-cookies cap, camera point parsing, and game mode restore on every abort path.
- [ ] All checks green, and `ownership-check` passes on every branch.
- [ ] Performance: with 4 players on one plot at high CPS, CookieClicker stays well under 1 ms/tick (an estimate in the integration review, plus spark steps in the PR).

## Out of scope
- "Faster belt" (Expansion 1), "Extra Conveyor Belt" (building 6) and belt skins (§7.2): later features.
- Stats holograms (the next feature after this one).
- Belts that turn corners (the layout belt stays straight).
- A camera that moves during the build (the user chose a fixed raised camera).

## Assets needed
| Kind | What | Owner |
|---|---|---|
| model / texture | `cookieclicker:conveyor_belt`: belt surface item model with an animated texture (rubber belt with slats, moving north toward the pedestal); `houses/belt.yml` belt frame style (side rails, legs/rollers in vanilla blocks); `layout.camera` in `factory.yml` plus a camera-view preview; `design/conveyor-belt.md` | model-designer |
| sound | `belt_loop`: soft mechanical belt hum and rattle, a seamless loop, sitting under the oven hum | sound-designer |
| effect | `belt_arrive`: a small crumb puff and pop as a cookie lands on the pedestal, cheap at high rates | vfx-designer |

## Balance impact
None. The belt is purely visual. The CPS → speed curve is a visual setting in `config.yml`, not an economy value.

## Research needed
None as a separate note. The coder checks against the Paper 26.3 Javadocs: `Player#setSpectatorTarget` on a server-side entity at the camera point (or the alternative: spectator mode, teleport, and refuse movement), and the display interpolation (`setInterpolationDuration` / `setTeleportDuration`) for long smooth movements. If neither camera approach works on 26.3, stop and report it.

## Open questions
None.

## Contract (fixed names; change only via the orchestrator)
| Kind | ID | Owner | Notes |
|---|---|---|---|
| item model | `cookieclicker:conveyor_belt` | model-designer | belt surface, 1 cell long, animated texture; vanilla fallback picked by the coder in config |
| item model | `cookieclicker:cookie` | model-designer (exists) | belt cookies, shown smaller |
| design file | `src/main/resources/houses/belt.yml` | model-designer | frame blocks per belt cell, like `fence.yml`; the coder parses it |
| layout key | `factory.yml: layout.camera` | model-designer | `{at: [x, y, z], yaw, pitch}`, plot cells, may be outside the plot |
| sound event | `belt_loop` | sound-designer | key `cookieclicker:factory.belt_loop`, fallback `minecraft:block.beehive.work` (low pitch) |
| effect | `belt_arrive` | vfx-designer | at the pedestal end of the belt; placeholders allowed |
| config key | `config.yml: belt.{enabled, max-cookies, cookie-scale, speed.{min,max}, interval.{min,max}, cps-scale, sound-every, fallback-item}` | plugin-coder | blocks/second and ticks; the curve is the coder's choice, documented in the file |
| config key | `config.yml: house.cinematic.camera` | plugin-coder | `true` = raised camera when the layout has one |
| plots.yml | `belt` (the cells), `camera` | plugin-coder | written by `/bakery plot setup` |

## Tasks
| Agent | Task | Needs (gate) | Branch |
|---|---|---|---|
| model-designer | `conveyor_belt` item model + animated texture; `houses/belt.yml`; `layout.camera` in `factory.yml` + camera preview; spec `design/conveyor-belt.md` | contract | `conveyor-belt/model-designer` |
| sound-designer | `belt_loop` .ogg + `sounds.json` + subtitle + `sounds.yml` | contract | `conveyor-belt/sound-designer` |
| vfx-designer | `belt_arrive` preset in `effects.yml` (+ any engine support needed for cheap repeat use) | contract | `conveyor-belt/vfx-designer` |
| plugin-coder | belt placement in setup/undo/backup; belt surface displays; per-player belt cookies driven by CPS; `belt_loop` + `belt_arrive` wiring; `layout.camera` + `plots.yml`; spectator camera with safe restore; config keys | contract | `conveyor-belt/plugin-coder` |
| plugin-tester | review each branch, then integration (incl. performance) | owner DONE | (reviewed branch) |

## Status
| Agent | Status | Branch | Verdict | Notes |
|---|---|---|---|---|
| model-designer | planned | | | |
| sound-designer | planned | | | |
| vfx-designer | planned | | | |
| plugin-coder | planned | | | |
| plugin-tester | planned | | | |

## Merge order
1. model-designer
2. sound-designer
3. vfx-designer
4. plugin-coder

## Needs human review
- [ ] Listen to the sound: `belt_loop` on a loop, together with the oven hum. It should sit under the hum and sound like a soft belt, not a drill.
- [ ] Check in game: the camera framing (whole factory, belt and fence visible), the belt look with and without the pack, cookies at low and high CPS, and that you're back in survival at the gate after the build.
