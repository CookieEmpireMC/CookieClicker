# Build quality: medieval countryside loop (feature `ingredient-farm`)

Researcher: Citation Cindy. Pass 2: 2026-10-24. Not a resource-pack topic: design research for the model-designer (block builds from `design/scripts/*.py` into `src/main/resources/houses/*.yml`) plus a review pipeline for the orchestrator.

## Target version
Paper 26.3 (`paper-api 26.3.build.205-stable`; server jar `paper-26.3-218.jar`, build 218 STABLE). Blocks and guides from any Minecraft version are fine; every older block still exists in 26.3. **Block ids were checked against the Paper 26.3 `Material` Javadoc** (jd.papermc.io, fetched in chunks; I have no shell, so the designers' validator against the sources jar stays the final gate). One rename found: **`chain` is now `iron_chain`** (25w32a / 1.21.9; copper chains were added). `grass`/`grass_path` are now `short_grass`/`dirt_path`. `ladder` was not seen in the chunks I read (it surely exists; let the validator confirm). Only ids in this note were checked; do not assume others.

## Decisions (user, 2026-10-24)
1. Mostly **medieval**; touches of other styles only where they look good.
2. **No hand-built or commissioned pieces.** Everything is generated; a human build is a last resort only if generation is truly impossible (definition in section 8). The hybrid recommendation is dropped.
3. **Ground/terrain extension below the paste: yes** (the coder adds y -2..-1 and a height field).
4. **Review on a real local Paper test server, done by the orchestrator himself, judging like a player who knows nothing** (section 9).
5. **Waterwheel and villager workers are item-display models** (model-designer); blocks hold the wheel pit, axle stub and a static fallback.
6. **Cocoa Grove has a jungle feel**, still medieval (walled, terraced, keeper's lodge).
7. **The factory is reworked too** with this research (section 7).
8. **At most one chimney per building**, a hard style-guide rule (R11).

## Summary
1. The builds read cheap because they are single-volume boxes with flat walls, 1-2 materials per surface, one-colour roofs, no ground story and lone props. The fixes are rules a generator can apply (section 10) and a library of named detail kits (section 3).
2. Style: **English/Tudor half-timber on a stone plinth** as the core, with a Norman stone ground floor for the hall and mill, a brick-and-timber Sugar Works, an aisled timber barn and a warm jungle-timber Cocoa Grove. One shared palette family.
3. Hero pieces (market hall, mill) must come out of the generator: section 8 lists what it needs (kits, a roof solver, a weathering pass, interiors, lint, textured and path-traced review, an iteration budget).
4. Review: a Paper 26.3 test server filled over RCON with `setblock ... strict` (the plugin's `/bakery house place` is player-only and needs WorldGuard regions, so it cannot be driven from a console), then Chunky **snapshot** builds (26.3 chunk format merged 2026-10-13; stable 2.4.6 cannot read 26.x) render eye-level and overview shots headlessly.
5. Still uncertain: Chunky snapshot channel id and `-texture` with a 26.3 jar, Chunky camera sign conventions (verify with one test frame), render times (not measured), 26.3 structure-file palette key names (avoided).

## Sources: what is primary, what was dropped
Primary and fetched: WesterosCraft's builder docs (exteriors, interiors, gradients, layout, research), Conquest Reforged's gradient guide, Minecraft Wiki tutorials (Roof construction guidelines, Roof types, Adding beauty to constructions, Walls and buttresses, Settlement guide, Architectural terms), three builder-written tutorials (xRILLIANx 2011, Rillian's Advanced 2012, PirateCraft/Charlie 2015: old but the patterns are version-independent), Wikipedia for the real-world facts (Timber framing, Jettying, Market house, Watermill, Water wheel, Barn, Chimney, Theobroma cacao). Tags `[S#]` refer to the list at the end.
Removed because only listicle/secondary: Blockical/exitlag/architecturelab, minecraft.how longhouse, planning blogs, The Spike as a style source, "moss thatch" convention. The "7 blocks in a line" rule was attributed to a minecraft.net article I could not fetch (timed out twice): it is now an **unsourced generator heuristic** `[D]`. `[D]` = my design inference from the cited facts; judge it in the render.
Gaps I could not close with primary sources: no build team publishes a mill, market-hall or barn how-to that I could fetch. Those three are `[D]` built on Wikipedia's real-world facts plus the general rules above; the review loop (section 9) is what validates them.

## 1. Critique of the current builds (unchanged from pass 1, condensed)
| # | Problem | Seen in | Fix |
|---|---|---|---|
| 1 | One box, no second volume or asymmetry | `default` | R1, R2 |
| 2 | Flat wall plane: no studs, braces, sills, shutters, jetty. WesterosCraft: depth must be structural (overhangs, reveals, sills) `[S1]` | `default`, `factory` | R3-R5 |
| 3 | One flat infill colour over whole walls | both | R6, R7 |
| 4 | Roof one solid colour and one pitch; factory roof fills ~60% of the facade with skylight cubes. Wiki: split large roofs into 5-7 rise sections `[S5]`; WC: two wood types plus weathering `[S1]` | `factory` | R8 |
| 5 | Chimneys are foreign objects; the factory has two (a 16-high 3x3 and a 1x1) | both | R11 (one only) |
| 6 | Plinth one flat course | `default` | R12 |
| 7 | No ground story: green slab, no path, no bed | all | R13, R14 |
| 8 | Regular rhythm (posts every 4, lamps every 8) | `fence`, `decor` | R15, R16 |
| 9 | Decor = 282 isolated blocks; props without story | `decor` | section 5 |
| 10 | Empty interiors; previews are flat average colours | all | R17, section 9 |
Keep: pilasters standing proud, the lean-to idea, computed pane/stair/fence states, the 4:3 camera check, lantern placement, waxed copper.

## 2. Style: real-world facts and palettes
Real-world facts: half-timbering = timber frame with non-structural infill and diagonal braces, usually on a stone/brick plinth; England has jettied upper floors, close studding in the east and square panels in the west; German Fachwerk has a high stone plinth; France is colombage `[S13]`. A jetty projects the upper floor for floor space, shelter and shorter timbers; no standard overhang, use 1 block `[S14]`. Roof material by wealth/region: sod rural, wood common, thatch for low/middle class, slate for the wealthy or urban `[S1]`.

Palettes (3 role ramps per style, light to dark, verified ids; WC: shade first, then colour, then texture `[S1][S3][S21]`):
| Style / where | Plaster/infill | Timber | Base | Roof |
|---|---|---|---|---|
| **Tudor** (market houses, hall upper) | `calcite`, `white_terracotta`, `diorite`; weather `light_gray_terracotta` | `dark_oak_log`, `stripped_dark_oak_log`, `spruce_log`; braces `dark_oak_stairs/slab/trapdoor/fence` | `stone_bricks`, `cracked_`/`mossy_stone_bricks`, `cobblestone`, `andesite`, `polished_andesite` | `dark_oak_stairs` + `spruce_stairs` eave course + `stripped_spruce_log` or `stone_brick_slab` ridge |
| **Norman stone** (hall ground floor, mill base, bridge) | `stone_bricks` ramp + `tuff_bricks`, `andesite`, `cobblestone`, `mossy_cobblestone` | `dark_oak_log` lintels/beams | `cobbled_deepslate`, `stone_bricks` | slate look: `deepslate_tile_stairs`/`cobbled_deepslate_stairs` + `stone_brick_stairs` |
| **Brick Fachwerk** (Sugar Works) | `bricks`, `red_nether_bricks`, `terracotta`; close studs | `dark_oak_log`, `spruce_log` | `cobbled_deepslate`, `stone_bricks` | `brick_stairs`, `mud_brick_stairs` |
| **Aisled barn** | `spruce_planks`, `dark_oak_planks`, `stripped_spruce_log` | `dark_oak_log` posts | `blackstone`, `deepslate_bricks`, `tuff_bricks` | two-tier `dark_oak_stairs` + `spruce_stairs` |
| **Thatch cottage** (Chicken Farm) | `white_terracotta`, `calcite` | `spruce_log`, `oak_log` | `cobblestone`, `mossy_cobblestone` | see thatch note |
| **Jungle-timber** (Cocoa Grove) | `jungle_planks`, `stripped_jungle_log`, `orange_terracotta`, `terracotta` | `jungle_log`, `dark_oak_log` | `mossy_cobblestone`, `stone_bricks` | `jungle_stairs`, `brown_terracotta`-toned `mud_brick_stairs` |
Thatch note: vanilla has **no straw stairs**. Wiki: "yellow carpets can stand in for straw" `[S6]`; WC: thatch pitch at least 45 degrees, never mix two thatch types, keep a defined shape `[S1]`. Plan: `jungle_stairs`/`oak_stairs` body at 45 degrees, `hay_block` eave roll and ridge, `yellow_carpet` only for flat patches `[D]`; judge in render, fall back to `mud_brick_stairs`.
Material discipline (WC interiors): one stone type per build, plank types not full logs inside, half-doors and shutters rather than trapdoors/signs `[S2]`. We use trapdoors as shutters because no half-door model exists in vanilla `[D]`.
Shared family for the whole loop `[D]`: dark-oak frame colour, stone-brick plinth, one lantern type, 45 degree primary pitch, 1-block eaves.

## 3. Detail kits (the generator's component library)
Each kit is a function with seeded variants; blocks verified. Rules from sources are tagged.
- **Timber framing.** Posts every 3-5 blocks, beams 4 high per floor, panels roughly square `[S12]`; infill set one block behind the frame `[S12]`; WC: symmetrical grid is the reliable default, one daub colour per build `[S1]`. Patterns `[D]`: *square panels* (west England), *close studding* (vertical `stripped_dark_oak_log` every 2nd block, east England/Fachwerk), *St Andrew's cross* under windows (diagonals from `dark_oak_stairs` up/down, or `dark_oak_fence`). Brace filler where frame is open: stairs upright at the bottom, inverted at the top `[S12]`.
- **Jetty.** Upper floor out 1 block on the street side (xRILLIAN: "go out one block and up one block" `[S10]`; all sides but the back `[S11]`). Under it: row of inverted `dark_oak_stairs` and `dark_oak_log` beam ends every 2nd block `[S10]`; a `lantern` hanging under one bracket `[D]`.
- **Windows.** 1x2 at 1-2 blocks above ground, shutters at the ends `[S7]`; panes sit one block inside (reveal) `[S1]`; sill = stair/slab/`spruce_trapdoor` projecting; flower box = `spruce_trapdoor` + `moss_carpet`/`flowering_azalea`/`flower_pot`; mullion = a centre `dark_oak_fence` in a 3-wide opening `[S22]`; bay/oriel = glass extruded into a mini room `[S7]`; transom light above doors. Glass: `glass_pane` (walls), `glass` only for bays `[S7]`. Lit interior: floor, one prop, `lantern` or `campfire` behind the pane `[D]`.
- **Doors.** Door in a frame of `dark_oak_log`, lintel = a full `dark_oak_log` beam one block wider each side; stoop = 1-2 `stone_brick_stairs` + `stone_brick_slab` landing; hood = `dark_oak_stairs` on two fence/log brackets; `lantern` on a fence bracket; `barrel`+`flower_pot` beside `[D]`. Doorway embrasure: wall 2 thick at the base, door set back 1 `[S8]`.
- **Roof edges.** Eave 1-2 blocks always; a roof cutting straight to the wall looks unfinished `[S1]`; outline eaves and the ridge with the darker plank `[S12]`; ridge = a layer of slabs `[S6]`; inverted stairs/slabs under the eave for rafter ends `[S2][S6]`. Gable end: a bargeboard row of `dark_oak_slab`/stairs one block beyond the wall, a collar beam (`dark_oak_log`) and a small vent/loft window in the apex; wood roofs: base wood plus alternate wood for aging, sun-bleach on ridges, water damage at eaves, no random discoloration `[S1]`. Sections of 5-7 rise, cross-gable above 15x15, build the highest part first `[S5]`. Dormer = own mini gable 3 wide `[S6][S22]`.
- **Chimney (ONE per building).** Real stacks were grouped: several fireplaces share one stack (Wikipedia) `[S19]`; brick shafts; gable-end or central. Block recipe `[D]`: base 3x3 `stone_bricks`/`cobblestone` rising out of the gable wall (xRILLIAN: 3x3 base, then raise a 2x2 shaft `[S11]`), shaft 2x2 `bricks`/`stone_bricks`, corbel course of inverted `brick_stairs` or `stone_brick_stairs` then `stone_brick_slab` cap (xRILLIAN caps with stairs then slabs `[S11]`), top 2 blocks above the ridge, 1 `campfire` (lit) or none inside for smoke. Water collects at the base, so no weep detail needed in voxels.
- **Market hall (Market house) `[D]` on `[S15]`.** Real: ground floor an open arcade protecting traders; upper floor sometimes civic use; sited on the market square. Plan 17x11x12: ground floor open on both long sides, 4+4 `dark_oak_log` posts (2x2 `stone_bricks` bases + capital `stone_brick_stairs`), arches between posts from inverted `stone_brick_stairs` + slab, cobblestone/`andesite` irregular paving; upper floor jettied 1 on all sides, Tudor close-studding, oriel at the centre, `spruce_trapdoor` shutters; cross-gable roof with two end gables facing the square, one cupola/bell turret on the ridge (`bell` + 4 `dark_oak_fence`, small `dark_oak_stairs` pyramid; this is not a chimney); interior: `barrel`/`lectern`/`bookshelf` set so the hall reads as a guild hall (Recipe Book).
- **Mill and waterwheel `[D]` on `[S16][S17]`.** Real: a mill race (leat) feeds the wheel, a tail race drains it; undershot enters the bottom quarter, breastshot the middle half; masonry apron follows the wheel face of a breastshot wheel; overshot needs pond, sluice and spillway. Plan: stone mill house (Norman base 2 thick, `stone_bricks`+`mossy`, Tudor timber upper floor with hoist beam and loading door under a `dark_oak_stairs` hood), gable roof with a dormer; race = stone-lined channel 2 wide 1 deep with a `water` source; a **breastshot wheel**: stone pit 3 wide x 5 deep with an `stone_brick_stairs` apron curve, axle stub `stripped_dark_oak_log`, wheel itself the **item-display model** (Decision 5), static fallback of `dark_oak_fence` spokes and `spruce_trapdoor` paddles; sluice = `iron_bars` + `lever` hint `[D]`; `mud`/`clay`/`moss_block` banks, `lily_pad`, `sugar_cane`; sacks + `barrel`s at the loading door.
- **Barn `[D]` on `[S18]`.** Real: aisled frame, large central double doors one side and a small door on the other (threshing floor, wagon through), hay mows in the aisles, brick gable ends with vent holes or wattle walls, stone base where stone was cheaper. Plan 15x11x9: `dark_oak_log` posts every 3, tall centre bay with a 3x3 double door of `spruce_door`s, `spruce_planks`/`dark_oak_planks` vertical cladding, `tuff_bricks`/`blackstone` base, gable apex loft door with a beam + `iron_chain` pulley, two-tier roof with a lean-to aisle at 22.5 degrees (alternating slabs `[S5]`), vent slits (`iron_bars` or `spruce_trapdoor` open), `hay_block` mow inside, `coarse_dirt` yard, `oak_fence`/`spruce_fence` paddock with a gate.
- **Cocoa Grove, jungle but medieval `[D]` on `[S20]`.** Real: cacao is a 6-12 m understory tree that fruits on trunk and older branches, pods yellow to orange `[S20]`; so: a walled, terraced plantation under tall `jungle_log` canopy trees. Terraces 1 block high edged with `mossy_stone_brick_wall`/`mossy_cobblestone_wall`; `podzol`, `rooted_dirt`, `moss_block` floor; vanilla `cocoa` pods on `jungle_log` trunks (every pod needs a jungle log behind it; generator must verify the survival rule); `fern`, `large_fern`, `vine`, `bush`, `jungle_leaves`; keeper's lodge: `mossy_stone_bricks` base, `jungle_planks`/`orange_terracotta` upper, `jungle_stairs` roof with `hay_block` eave roll, drying rack of `jungle_fence` + `orange_carpet`/`yellow_carpet`, `barrel`s of beans, `campfire` roasting spot. Keeps the shared dark-oak door and lantern family so it belongs to the loop.

## 4. Landscaping, ground and terrain
- Generator heuristic `[D]`: no organic element straight for more than 7 blocks; vary edge noise 0-2.
- Gradients: weather where water runs (under windows, below eaves, corners, plinth top), moss where it collects, drip gradients under windows `[S21]`; cluster sizes vary; texture blocks 10-30%, never 50/50 splats `[S3][S21]`.
- Wiki: plinth/foundations 1-2 courses, deeper on slopes so no dirt shows `[S4]`; castles thicker at the base `[S8]`.
- Roads `[D]` on `[S9]` (streets branch from a central well, all buildings face a street, a market place if possible, a landmark): 3 wide (4 at the market), noisy centreline; ramp `dirt_path` centre, `coarse_dirt` and `gravel` shoulders, `rooted_dirt`/`podzol` patches, then `grass_block`+`short_grass`; market square = irregular `cobblestone`/`stone_bricks`/`andesite` pavers.
- Ground palette per place: Market `cobblestone`+`gravel`; Mill `mud`+`clay`+`moss_block`+`mossy_cobblestone` banks; Barn `coarse_dirt`+`hay_block` flecks; Sugar Works `packed_mud`+`dirt_path`+`sugar_cane` near water; Cocoa Grove `podzol`+`rooted_dirt`+`moss_block`+`fern`; Chicken Farm `dirt_path`+`coarse_dirt`.
- **Terrain extension (Decision 3):** the pasted area owns y -2..-1 plus a height field (mounds up to 3, mill-race ditch, grove terraces). Needs the coder (section 11). Water placed with physics off and enclosed.
- Trees: custom shapes (bent trunk, branches, noisy canopy of 3-5 blobs radius 2-3), **leaves `persistent=true`**, root ring of `podzol`/`rooted_dirt`; oak/dark oak/birch/`cherry_leaves` accents; jungle trees for the grove.
- Fields: `farmland` in 3-5 wide beds with paths; orchard rows are the only regular rhythm.

## 5. Decor and props (the user's "decos don't make sense")
Rules `[D]` from WC ("every block needs a purpose; a barrel with a mug reads as lived-in, loose crates do not") `[S2]` and WC town houses ("back yard for vegetables, washing, animals or storage") `[S2]`:
- **D1** every group has an anchor (wall, door, window, fence, path, bed, tree, well, water); **D2** at least 5 blocks and 3 types or a 9-cell ground patch; **D3** the ground under and around changes in an irregular ring; **D4** heights vary, odd counts; **D5** density at most one scene per 6 blocks of facade, none in the hologram corridor; **D6** lamp posts only at junctions, doors, the market; **D7** keep the small villager workers (item-display models on slots, one per place: miller, dairymaid, sugar boiler, cocoa picker, chicken keeper, market seller).
- Scene catalogue: flower bed at a wall (`podzol` bed edged with `cobblestone_slab`, flowers, `cauldron`), window planter, door stoop, workshop clutter (`barrel`s, sacks = `white_wool`+`white_carpet`, `hay_block`), hay stack against the barn, garden with scarecrow and `composter`, well/trough, market stall (`barrel`/`composter` goods, awning of `red_carpet`/`white_carpet` on fences), woodpile (horizontal logs 3 layers), drying rack.
- Drop: lone flowers/`path_edge` rows, hedge rectangles, raised single `grass_block` pads, the 8 grid lamp posts. Rework `front_east_wheat` into the garden scene. `decor_style.py` places scene functions, not cell lists; keep the keep-out asserts and per-level free-cell check.

## 6. Interiors seen through windows
WC interiors `[S2]`: rooms with function, work/storage downstairs and sleeping above, kitchen near the hearth, ceilings not 2 blocks high, floors from stair rows imitating cross-beams, beams across the short span, cabinets exposing one side, fireplaces/candles/lanterns. Wiki: 3-4 block ceilings, mix of stone-brick variants, `campfire` as decorative light `[S7]`. Apply `[D]`: floor + one prop pair + `lantern` one block behind each window.

## 7. Factory rework plan (`factory.yml`, `design/factory.md`, `design/scripts/house_factory.py`)
Style: **Norman/Tudor "bakehouse"**: stone ground floor, plastered timber-framed gable upper, brick only at the oven house and the single stack. Keep (spec): `points.door [8,1,2]`, `signs[0] [10,4,1]` "Cookie Works", loading hatch `[12,0..1,2]` and `points.belt_out [12,0,1]` with its copper awning, the layout block, the design box 19x17x14 and the building-slot keep-outs. Change:
1. **One chimney.** Delete the tall 3x3 boiler stack and the brick duct. The oven house gets one 2x2 brick stack over the oven flue (corbelled cap, top 2 above the hall ridge); `points.smoke` becomes a single point above it. The copper steam pipe (lightning rod) goes; the ridge vent stays as a **flush ridge louvre** (no shaft, so not a chimney) and stays `points.steam[0]` (sound anchor, per the brief).
2. **Walls.** Lower 3 courses a `stone_bricks` ramp (cracked/mossy/andesite clusters); upper walls plaster (`calcite`/`white_terracotta`) in a `dark_oak_log` frame with close studs, replacing the all-brick faces; pilasters stay proud.
3. **Roof.** Dark oak 45 degrees stays but gets a `spruce_stairs` eave course, `stone_brick_slab` ridge cap, bargeboards, and a cross-gable or two 3-wide dormers instead of the glass skylight cubes; roof area drops below half of the facade (the gable infill grows).
4. **Jetty.** Upper gable projects 1 block north from y5 up (sign at y4 unaffected, belt/hatch below unaffected) with inverted-stair brackets.
5. **Lean-tos** (oven house west, flour shed east) keep their function; add shutters, flower boxes, a lit interior, door stoop, and scene-based clutter.
6. Checks: smoke list length 1, steam list length 1, hatch still open behind `belt_out`, camera framing, 796-block budget not a limit (cap 40,000).

## 8. What the generator needs for hero quality (market hall, mill) without a human build
1. **Kit library** (section 3) as tested functions with 3+ seeded variants each (frame patterns, windows, doors, chimney, dormer, arcade bay, oriel, wheel pit).
2. **Roof solver**: gable, half-hip, cross-gable, lean-to, valleys, correct inner/outer stair corners, eaves, ridge and dormer insertion; asserts on pitch (63.4/45/22.5 `[S5]`).
3. **Post-pass weathering**: cluster-replace within ramps (moss at plinth/corners, soot above fireplaces, wet streaks under eaves) driven by a noise mask; mix ratio lint.
4. **Interior and prop kits** (section 6) and scene functions (section 5).
5. **3D-texture parts** from the wiki's list (stairs, fences, trapdoors, walls, bars, buttons, item frames) `[S8]` to avoid flat planes; **terrain ground layer** (section 4).
6. **Reference sheet + lint + path-traced review** (sections 9, 10) with a budget of **3 review rounds**. A human build is allowed only if after round 3 the same defect persists and the user agrees; this is the definition of "truly impossible".
7. Size: market hall about 17x11x12 and mill about 13x11x12 fit `house.max-blocks` 40,000 easily.

## 9. Headless review pipeline (orchestrator)
**Why not `/bakery house place` from the console:** `BakeryCommand` resolves the executor to a `Player`, finds the plot via `plots.plotIdAt(player.getLocation())` (a WorldGuard region with the `bakery-plot` flag) and needs the plot's house region; the console gets `commands.players-only`. A Mineflayer bot cannot join either (Mineflayer supports up to 26.1; minecraft-data 26.3 data is still an open PR). So the review fills blocks straight from the design YAML (`palette` char to block string, `layers[y][z][x]`, `_` = air, `.` = keep: check `house_lib.py`). That tests the design, not the plugin's paste (which has its own tests). Optional later: `/place template` with a structure `.nbt` (old `DataVersion`, `Name`/`Properties`; 26.3 renamed chunk palette keys to `id`/`properties`, structure files unverified).

**9.1 Server** (dir `~/bakery-review/server`, outside the repo; Java 25 from mise):
```bash
mkdir -p ~/bakery-review/server && cd ~/bakery-review/server
curl -L -o paper.jar https://fill-data.papermc.io/v1/objects/cdd56d33f27e77110e6f8b43cf6d9571c3854c9ae099986b24a3c18c5904cc11/paper-26.3-218.jar
echo "cdd56d33f27e77110e6f8b43cf6d9571c3854c9ae099986b24a3c18c5904cc11  paper.jar" | sha256sum -c
echo "eula=true" > eula.txt          # the user approved accepting the EULA
cat > server.properties <<'EOF'
online-mode=false
white-list=false
server-port=25599
level-name=world
level-type=minecraft\:flat
generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}]}
generate-structures=false
spawn-protection=0
enable-rcon=true
rcon.port=25575
rcon.password=review
broadcast-rcon-to-ops=false
EOF
# keep stdin open, run in background, wait for "Done"
( tail -f /dev/null | mise exec -C /home/casper-rs/Documents/GitHub/CookieClicker -- java -Xmx2G -jar paper.jar --nogui > server.log 2>&1 & )
until grep -q 'Done (' server.log; do sleep 2; done
```
(`white-list` defaults to `true` since 26.3 snapshot 2; `level-type` needs the escaped colon; grass surface: top face Y=-60, so **design y0 = world y -60**; bedrock -64, dirt -63..-62, grass -61.) Stop with RCON `stop`.

**9.2 Fill over RCON** (Python stdlib; protocol: little-endian int32 length, id, type 3 login / 2 command, body, two NULs):
```python
import socket, struct
class Rcon:
    def __init__(s, host="127.0.0.1", port=25575, pw="review"):
        s.k = socket.create_connection((host, port)); s.n = 0; s.send(3, pw)
    def send(s, t, body):
        s.n += 1; p = struct.pack("<ii", s.n, t) + body.encode() + b"\0\0"
        s.k.sendall(struct.pack("<i", len(p)) + p)
        ln = struct.unpack("<i", s.k.recv(4))[0]; d = b""
        while len(d) < ln: d += s.k.recv(ln - len(d))
        return d[8:-2].decode()
    cmd = lambda s, c: s.send(2, c)
# per design: ox,oz = plot origin; oy = -60
r = Rcon(); r.cmd("forceload add -48 -48 79 79")
r.cmd("gamerule advance_time false"); r.cmd("gamerule advance_weather false"); r.cmd("gamerule spawn_mobs false")
# sort so supports come first: solid/full blocks, then stairs/fences/panes, then lanterns/doors/trapdoors/carpets/flowers
# r.cmd(f"setblock {ox+x} {oy+y} {oz+z} {block_state} strict")   # 'strict' = no block/shape updates (setblock strict, 1.21.5+)
```
Block entities (signs, barrels) can be skipped for the visual review. A `fill ... strict` form was not verified; use `setblock`. A design of 5,000 blocks is 5,000 small RCON commands (seconds). Re-run `fill air` over the box (`fill x y z x2 y2 z2 air`) to clear before the next house.

**9.3 Chunky.** Version: **stable 2.4.6 (Jan 2024) lists support only up to 1.20.4; snapshot 2.5.0 up to 26.2-snapshot-8; master merged "support for 26.3 chunk format" (#1915, 2026-10-13) and 26.3 snapshot blocks (#1916/#1917), and the new world directory layout `world/dimensions/minecraft/overworld` (#1870, 2026-03-04). Use the Snapshot channel.** Licence GPL-3.0 (tool use only, never in our jar). Needs Java 17+ (JavaFX only for the GUI).
```bash
mkdir -p ~/bakery-review/chunky && cd ~/bakery-review/chunky
curl -L -O https://chunkyupdate.lemaik.de/ChunkyLauncher.jar
# one-time (needs a display): choose Advanced Settings > Release channel "Snapshot", Check for update, close
java -Dchunky.home=$HOME/bakery-review/chunky/home -jar ChunkyLauncher.jar --launcher
# headless alternative (channel id unverified): java -Dchunky.home=... -jar ChunkyLauncher.jar --update snapshot
# if neither works: git clone https://github.com/chunky-dev/chunky ; ./gradlew build (Java 17)
```
Textures come from a Minecraft client jar: `java ... -jar ChunkyLauncher.jar -download-mc 26.3` (docs: `-download-mc <VERSION>`; 26.3 unverified) or pass `-texture /path/26.3.jar`. Mojang's version manifest: `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json`. Never commit the jar. Our server resource pack is irrelevant here (vanilla blocks only); **item-display models (waterwheel, villagers) are not rendered**, so review them in game.

Scene file `~/bakery-review/scenes/<name>/<name>.json` (docs' Linux default is `~/.chunky/scenes/<name>/`; `-list-scenes` confirms). Fields exactly as in the Chunky scene-format page; since #1870 `world.dimension` is a string and old integers still load:
```json
{"sdfVersion": 9, "name": "factory_eye", "width": 1280, "height": 720, "sppTarget": 128,
 "outputMode": "PNG", "rayDepth": 5, "pathTrace": true, "renderActors": true,
 "world": {"path": "/home/<you>/bakery-review/server/world", "dimension": "minecraft:overworld"},
 "chunkList": [[-3,-3],[-3,-2], "... every [x,z] chunk within the view ..."],
 "camera": {"name": "camera 1", "position": {"x": 21.5, "y": -57.38, "z": 12.5},
            "orientation": {"roll": 0.0, "pitch": -1.8151, "yaw": 1.5708}, "projectionMode": "PINHOLE", "fov": 70.0},
 "sun": {"altitude": 0.9, "azimuth": 4.0},
 "sky": {"mode": "SIMULATED", "simulatedSky": "Preetham"}}
```
Camera conversion from a Minecraft eye view (first-render doc `[S23]`): Chunky X = MC X, **Y = MC Y + 1.62**, Z = MC Z; **yaw = 90 - MC yaw**, **pitch = MC pitch - 90** (degrees in the GUI; the JSON takes radians). Example above: stand at feet (21.5, -59, 12.5), MC yaw 0 (south), pitch -14: yaw 90 deg = 1.5708, pitch -104 deg = -1.8151 rad (as in the JSON). **Verify the sign convention with one 32-SPP frame before the batch.** Sun: altitude 0-pi/2, azimuth 0-2pi measured from north; defaults 60 deg / -72 deg. Shots per house (YAML `layout.viewpoint` gives the eye shot): (1) viewpoint (feet at plot cell, eye +1.62), (2) 15 blocks out at eye height, (3) overview from 40 up at pitch 35, or `"projectionMode": "PARALLEL"` for an iso-like shot. `chunkList` = all chunks (block/16 floor) within about 6 chunks.
```bash
cd ~/bakery-review/chunky
java -Dchunky.home=$PWD/home -jar ChunkyLauncher.jar -scene-dir ~/bakery-review/scenes -threads 8 -target 128 -render factory_eye
# snapshot at any time: ... -snapshot factory_eye out.png   (PNG also lands in scenes/<name>/snapshots/)
# if the launcher rejects options or opens a GUI, try the form with a separator:  ... ChunkyLauncher.jar -- -scene-dir ... -render factory_eye
```
Docs' SPP guide `[S23]`: daylight without emitters 32-1024 SPP, with emitters 4096-16384+. Use **128 SPP** for review, 512 for the user's final look, emitters off (default). Render time was **not measured**; guess 1-5 minutes per 1280x720 shot at 128 SPP on 8 threads, so measure on the first frame. **If Chunky fails on the 26.3 world**: render with the extended Python renderer (real textures, section 10) and the final truth in the real client with a `/tp` tour.

**9.4 Orchestrator checklist** (judge as a player with no context): is the facade readable in 3 seconds, what is the building (bakery? mill?), anything floating, flat or lone, is each prop at something, does the ground connect, does it match the loop. Output one PNG per shot into `design/review/` (git-ignored) and compare against the reference sheet.

## 10. Process and lint (the generator side)
- Reference sheet `design/refs/<house>.md`: 3 real-world references (style, massing, detail) and each ramp's 3-5 blocks.
- Passes: massing (placeholder blocks, greyscale silhouette) -> detail kits -> ground -> weathering -> lint -> textured render at 5/15/30 blocks -> path-traced review (9.3).
- Lint asserts in `house_lib.py`: at least 2 volumes and a non-rectangular plan; every wall at least 3 ramp blocks and a depth change every 4 blocks; roof eave >= 1 and 2 materials; plinth 1-2 courses; each window has sill + (shutter or box) + lit interior; **chimney count <= 1 and `points.smoke` length <= 1**; clear front yard 5 deep x 7 wide with a 3-wide corridor; no lone props; nothing organic straight over 7; squint test (greyscale ramp roof darkest, plaster lightest).
- Extend `voxel_render.py` with real textures (client jar cache git-ignored); optional second renderer `deepslate-mcp` (MIT; headless-gl, Xvfb).

## 11. Rules for `design/style-guide.md` (ranked)
R1 two volumes minimum. R2 roof height 1-2 storeys `[S5]`, one tallest element, asymmetry. R3 three depth planes per facade `[S1]`. R4 posts/beams/braces with panels at most 3x3 `[S12]`. R5 windows: sill, shutters or box, lit interior. R6 each material is a 3-5 block ramp ordered by value `[S3]`. R7 texture blocks in clusters 10-30%. R8 roofs: eave >= 1, two materials, pitch from {63.4, 45, 22.5}, sections 5-7 rise, dormers above 9 wide `[S5]`. R9 jetty 1 block with brackets on market buildings `[S14]`. R10 shared family (section 2). **R11 At most one chimney per building (one vertical stack/flue/pipe rising above the roofline); more fireplaces share that stack `[S19]`; flush ridge louvres, vents and turrets are not chimneys; stack top 2 blocks above the ridge, base 3x3 or at least 2x2, cap with stairs and slab; `points.smoke` has one entry per building; lint-enforced.** R12 plinth 1-2 courses `[S4]`. R13 ground story: path, stoop, bed. R14 ground ramps per place. R15 7-block heuristic `[D]`. R16 no grid for lamps and posts. R17 furnished interiors. R18 decor scenes (section 5). R19 front yard 5x7 clear. R20 trees: custom shapes, persistent leaves. R21 review: textured render plus path-traced shots and the user's approval before `Status: final`.

## 12. Still uncertain
Chunky snapshot channel id and exact `-texture`/`-download-mc 26.3` behaviour; Chunky camera angle signs (one test frame resolves); render time; headless form of the launcher command (`--` separator); `fill ... strict` and 26.3 structure palette key names (avoided); cocoa `facing` rule (validate in game); thatch look without straw stairs; mill, market hall and barn are `[D]` (no primary how-to exists); `ladder` id not seen in the Javadoc chunks I read.

## Hand-off
- **model-designer:** apply sections 2, 3, 5, 6, 8, 10, 11; rework `default` and `factory` (section 7) before new places; `design/refs/<house>.md`, `design/build-lint.md`; villager worker and waterwheel item models; use `iron_chain` not `chain`. Pipeline: plain vanilla blocks in house YAML; models via the pack.
- **plugin-coder:** ground-layer extension (y -2..-1, height field, persistent leaves, 5-deep front yard); optional `tools/review/` scripts for 9.2/9.3 (RCON fill, scene writer) and `nbtlib`/Python deps; optional console form of `/bakery house place` is NOT needed with this pipeline.
- **sound-designer / vfx-designer:** none (vfx: one smoke point per house now).
- Contract IDs proposed: none. Design files: `design/refs/<house>.md`, `design/build-lint.md`.

## Sources
- [S1] [WesterosCraft, Exteriors](https://westeroscraft.com/docs/building/fundamentals/exteriors) · [S2] [Interiors](https://westeroscraft.com/docs/building/fundamentals/interiors) · [S3] [Gradients](https://westeroscraft.com/docs/building/fundamentals/gradients) · [S4] [Layout & Foundation](https://westeroscraft.com/docs/building/fundamentals/layout-foundation) · [Research & Planning](https://westeroscraft.com/docs/building/fundamentals/research-planning) · [Repository World](https://westeroscraft.com/docs/building/repository-world)
- [S21] [Conquest Reforged, Gradienting your Builds](https://www.conquestreforged.com/guides/gradients)
- [S5] [Wiki, Roof construction guidelines](https://minecraft.wiki/w/Tutorial%3ARoof_construction_guidelines) · [S6] [Roof types](https://minecraft.wiki/w/Tutorial:Roof_types) · [S7] [Adding beauty to constructions](https://minecraft.wiki/w/Tutorial:Adding_beauty_to_constructions) · [S8] [Walls and buttresses](https://minecraft.wiki/w/Tutorial:Walls_and_buttresses) · [S9] [Settlement guide](https://minecraft.wiki/w/Tutorial:Settlement_guide) · [S22] [Architectural terms](https://minecraft.wiki/w/Tutorial:Architectural_terms) · [Chain (rename)](https://minecraft.wiki/w/Chain)
- [S10] [Rillian's Advanced Medieval House Tutorial](https://www.minecraftforum.net/forums/show-your-creation/screenshots/1601499-rillians-advanced-medieval-house-tutorial-pic) · [S11] [xRILLIANx, How To Build A Medieval Home](https://www.minecraftforum.net/forums/show-your-creation/screenshots/1593417-how-to-build-a-medieval-home-30-pics) · [S12] [PirateCraft, Houses: Better builds (2015)](https://piratemc.com/2015/11/26/houses-better-builds/)
- [S13] [Timber framing](https://en.wikipedia.org/wiki/Timber_framing) · [S14] [Jettying](https://en.wikipedia.org/wiki/Jettying) · [S15] [Market house](https://en.wikipedia.org/wiki/Market_house) · [S16] [Watermill](https://en.wikipedia.org/wiki/Watermill) · [S17] [Water wheel](https://en.wikipedia.org/wiki/Water_wheel) · [S18] [Barn](https://en.wikipedia.org/wiki/Barn) · [S19] [Chimney](https://en.wikipedia.org/wiki/Chimney) · [S20] [Theobroma cacao](https://en.wikipedia.org/wiki/Theobroma_cacao)
- Pipeline: [Chunky headless](https://chunky-dev.github.io/docs/reference/user_interface/chunky_launcher/headless/) · [scene format](https://chunky-dev.github.io/docs/reference/technical/scene_format/) · [compatibility](https://chunky-dev.github.io/docs/support/minecraft_compatibility/) · [S23 first render](https://chunky-dev.github.io/docs/getting_started/your_first_render/) · [launcher GUI/channels](https://chunky-dev.github.io/docs/reference/user_interface/chunky_launcher/chunky_launcher_gui/) · [Chunky PR 1870](https://github.com/chunky-dev/chunky/pull/1870) · [PR 1915](https://github.com/chunky-dev/chunky/pull/1915) · [issue 1913](https://github.com/chunky-dev/chunky/issues/1913) · [Chunky repo (GPL-3.0)](https://github.com/chunky-dev/chunky) · [Paper 26.3 build 218 (fill API)](https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/latest) · [wiki server.properties](https://minecraft.wiki/w/Server.properties) · [Superflat](https://minecraft.wiki/w/Superflat) · [setblock](https://minecraft.wiki/w/Commands/setblock) · [Game rule](https://minecraft.wiki/w/Game_rule) · [Mineflayer](https://github.com/PrismarineJS/mineflayer) · [Paper 26.3 Material Javadoc](https://jd.papermc.io/paper/26.3/org/bukkit/Material.html)
