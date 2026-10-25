# Farm details: cow pasture, chicken run, pen mistakes and the mill's water side (feature `countryside-builds`)

Researcher: Citation Cindy, 2026-10-25. Follow-up to `docs/research/farm-buildings.md` after the user's review of the rebuilt Cow Barn, Chicken Farm and Mill: "both are not really typical cow and chicken places; maybe a bigger chicken pen and a pen for cows; both still have ugly details; the mill has a weird stone brick thing above the water; the trapdoors at the chicken pen". For the model-designer (generator-built places in `design/scripts/` -> `src/main/resources/houses/*.yml`). Not a resource-pack topic.

## Target version
Paper 26.3. Only vanilla blocks. Block ids are the names I expect; the sources-jar validator stays the gate. Ids I am not sure behave as described are tagged **[verify]** (check in a preview render).

## Source honesty (read first)
- Web search for Minecraft build advice is mostly SEO text. **I found no r/Minecraftbuilds thread and no builder blog that discusses animal pens or mill water in depth**; searches for Reddit returned none. YouTube tutorials (watermill, animal pen) came up but I cannot read video or transcripts. PMC and BuiltByBit are 403 for my fetch tool (no new first-hand views this round).
- What I could read in full: Diamond Lobby and buildingguide.app chicken-coop guides, 4netplayers and Gamerheadquarters fence guides, an Astroworld animal pen page, Wikipedia (Water wheel, Watermill, Mill race, Hedge, Stile, Chicken coop), Weald & Downland (cattle sheds), Mother Earth News (real overshot wheel). Search summaries only: Sportskeeda, shockbyte/namehero (barns), mc-mod.net (mistakes), Northamptonshire HER, Highland HER.
- Tags: **[read]** page read; **[snippet]** search summary only; **[D]** my design judgement from real-world facts (not a Minecraft source). Much of section 3 and 4 is `[D]` because no Minecraft guide covers it. That is the honest state; the real-world facts are solid.
- **I own one mistake:** `farm-buildings.md` section 7 proposed a "hurdle fence (`oak_fence` + `spruce_trapdoor` panels)" `[D]`. That produced the trapdoor mesh the user dislikes. It is withdrawn here.

## Summary
1. **An animal place reads as "cow farm"/"chicken farm" through the yard, not the building:** a fenced pasture or run that is clearly bigger than the shed, a worn-mud zone at the gate and trough, a water trough, feed (hay rack/trough), shelter, and 2-3 animal-specific props. Our barn and coop were buildings with a small pen; flip that: **pasture/run about 1.5x the building footprint, with its own sub-zones.**
2. **Cow place = barn + muddy fold-yard + open-fronted shelter shed + big grass pasture behind a hedge/drystone wall + roadside milk-churn stand.** (Real farms: Weald & Downland, Northamptonshire HER.)
3. **Chicken place = small coop + big run (about 22 x 14) with ground in two zones (scratched dirt, grass), dust bath, shade tree or lean-to, feeder, water, nest-box row, perch visible, haystack/scarecrow.** Real numbers: about 10 m2 run per hen, one nest box per 3-4 hens, 8-12 in (about 1 block per 3 birds) of perch.
4. **Fences:** posts are *capped, full blocks* on a regular rhythm (every 4-5), plain rail fence between, taller gate posts with a lantern or cap; one fence material per place. **No trapdoor mesh, no iron-bar boxes.**
5. **Mill water:** the "stone brick thing" is the sluice tunnel/beam box built over the head race (`house_mill.py` lines 279-291: iron-bar gates at x32 and x34, log posts and a `beam` at y4 across the channel, stone-brick coping). Remove it. A real sluice is a *small timber gate frame in an open channel*; water levels must be exact (see section 5).

## 1. What makes the animal places read instantly

### Cow / dairy
Real-world facts (all **[read]**/**[snippet]** from the pages named):
- Cattle sheds were **open-fronted shelter sheds around a foldyard**, bounded by walls or buildings; cattle were not tethered and moved freely; sizes are given in bays (3-bay small, 7-bay large) (Weald & Downland https://wealddown.co.uk/buildings/cattle-sheds **[read]**, https://www.wealddown.co.uk/?p=17062 **[snippet]**). Take: **bay rhythm, open front, a yard in front.**
- Fittings: **feeding troughs along the walls with a hay rack above**, tether rings (Northamptonshire HER https://her.northamptonshire.gov.uk/Monument/MNN144470 **[snippet]**). Regional names byre/shippon/hovel; linhay = open shed with a hayloft over the cattle (ADS HFPC PDF **[snippet]**). Take: **hay rack over trough, loft over the shed.**
- Hedges as stock-proof boundary: hawthorn/blackthorn "particularly effective barriers to livestock"; laid hedges; **Devon hedge = earth bank topped with shrubs, stone-faced, flat stones around gateways**; hedges usually about 1 m wide, dense ones above 2 m; hedgerow trees left no closer than 10 m apart (Wikipedia Hedge https://en.wikipedia.org/wiki/Hedge **[read]**). Take: **1-wide, 2-3 high leaf hedge on a low stone bank; a tree every 10+ blocks.**
- Stiles: step and ladder stiles over walls; **squeeze stile = narrow gap in a drystone wall with stone pillars either side** (Wikipedia Stile https://en.wikipedia.org/wiki/Stile **[read]**). Take: a **step stile** (two stair steps up one side, down the other) over the wall at a footpath: strong "field" tell, 3 blocks wide.
- Dairying: cows pastured by day between milkings, milked by hand in the barn; water is a big need (Wikipedia Dairy farming https://en.wikipedia.org/wiki/Dairy_farming **[read]**, partial). Churns on a roadside stand and milking stools are not in any page I read: `[D]` from common farm imagery, keep them small.

Minecraft-side evidence (weak but consistent):
- Animal pen guides all use **fence + gate + hay bales + cauldron or slab water troughs + torches**, 10x10 to 16x16 in size (Astroworld https://builds.astroworldmc.com/builds/animal-pen **[read]**: 16 x 12 area with separate pens per animal, 48 fence, 4 gates, 8 hay; shockbyte https://shockbyte.com/blog/minecraft-barn-ideas **[snippet]**: pens, fenced grazing areas, water troughs from cauldrons or slabs, hay lofts). They say nothing about style, so the "typical" read here is `[D]` from the real-world list above.
- Medieval barn material lists (search summary): dark oak planks, tuff/stone bricks, fence gates, lanterns, chains, hay bales **[snippet]**: matches what we use.

**Tells for cows, in order of how fast they read** `[D]`: (1) big grass pasture with a hedge/wall; (2) muddy yard with trough at the gate; (3) open-fronted shed with a hay rack; (4) milk churns on a stand by the road; (5) hay stack/rick or cart; (6) milking stool and pail at the barn door; (7) lantern-lit stall doors.

### Chicken / poultry
- Real-world run and coop numbers (Wikipedia Chicken coop https://en.wikipedia.org/wiki/Chicken_coop **[read]**: free-range layers about 10 m2 per hen outdoors; open-air houses with wire mesh vs closed houses; hens shut in at night; predators (foxes, hawks)). Retail/care-sheet pages (search summaries, not peer reviewed **[snippet]**: Chewy, Modern Farmer, Nestera, RSPCA Australia): coop floor 3-4 sq ft per hen, **run about 10 sq ft per hen**, **roost 8-12 in per hen, raised**, **one 12 in nest box per 3-4 hens**, separate **dust-bath area (sand, ash, fine dirt)**. Convert at 1 block = 1 m: **10 hens need about 100 blocks of run; nest boxes 1 block each; roost: a 4-block log perch seats about 12 birds.**
- Minecraft guides: ground of **podzol, coarse dirt, muddy mangrove roots** instead of grass "adds a lot of nice detail"; slab floors look bulky; **rail as a ramp** (Diamond Lobby https://diamondlobby.com/minecraft/how-to-build-chicken-coop-minecraft/ **[read]**). buildingguide.app https://buildingguide.app/tutorials/chicken-coop **[read]**: henhouse 4x3x3 in the middle of an open run, podzol 69 + coarse dirt 18, 4-6 hay-bale nests, **a single lantern ("multiple lanterns would look too industrial")**, flock of 4-6 so the pen does not look crowded. **That guide and Diamond Lobby use trapdoors as the perimeter fence ("chicken wire")**: that is where the trapdoor idea is common, and it is exactly what the user rejected; the guide's own reason (a spruce fence "reads as a horse paddock") is opinion, and we solve it with a different fence rhythm (section 2).
- Coop tells `[D]`: **ramp to a pop hole, a row of nest boxes with hatches on the outside, a perch, a feeder and a waterer, a dust bath, a scarecrow or haystack**, a lantern at the door, scratched bare dirt. Dovecote tells (Wikipedia Dovecote, already in `farm-buildings.md`): nesting holes, rat ledge, louvre.

## 2. Pens done well vs badly

### Rules from the sources
| Rule | Source / basis |
|---|---|
| Keep a tight palette: 3-5 blocks with roles (primary wall, trim/frame, accent) | mc-mod.net "common mistakes" https://www.mc-mod.net/common-mistakes-new-builders-make/ **[snippet]**; Sportskeeda tips **[snippet]** |
| Trapdoor/slab/stair overuse makes visual noise; leave plain "rest areas" so details stand out | same **[snippet]** |
| Do not mix fence families: nether-brick fences do not connect to wooden ones and leave gaps; one fence type per place | 4netplayers https://www.4netplayers.com/en/blog/minecraft/minecraft-fence-ideas/ **[read]**, minecraft.how **[snippet]** |
| Fence rhythm: **pillar every 5 blocks, infill between** (stone pillars + iron bars in the 4net garden fence) | 4netplayers **[read]** |
| Post caps: brick post + slab on top; **gate-adjacent post taller (brick, brick, cap), middle left open; one fence piece angled to suggest an open gate** | Gamerheadquarters https://gamerheadquarters.com/minecraft/fancy-brick-fence-guide.html **[read]** |
| Weathered field fence: cobble stairs/slabs/walls with mossy variants and leaves, placed irregularly rather than modular | 4netplayers idea 7 **[read]** |
| Pen size: leave room to walk around and see every animal; one main gate that opens fully without hitting posts | minecraft.how https://minecraft.how/blog/post/build-animal-pen-minecraft **[snippet]** |
| Do not overcrowd; 4-6 hens look right in a small pen | buildingguide.app **[read]** |
| A single lantern at the door; many look industrial | buildingguide.app **[read]** |

### Ugly details and the vanilla alternative (the fix list)
| Ugly detail | Why it looks wrong | Replace with |
|---|---|---|
| **Trapdoors as the run's fence/mesh** (also my hurdle idea) | repeated 1-block slats read as a grey/brown comb, neither fence nor wire; at 15 blocks it is noise | post-and-rail `spruce_fence` between **capped full-block posts every 4**, hedge or `sweet_berry_bush`/`azalea` row outside; the run is big so the fence line stays calm |
| Trapdoor rows as "shutters/vents" everywhere | detail spam (mc-mod.net) | trapdoors only as **window shutters (pairs), one pop-hole, one egg hatch row (<=5, same facing, under a slab shelf)**; budget: <=12 trapdoors per place `[D]` |
| Random 1-3 block stone-brick boxes (sluice tunnels, plinth lumps in a yard) | stone brick is the building's block; floating stone pieces read as a mistake | stone in the yard only as **pillars that carry a rail**, **troughs**, **walls**, **steps**; each lump must have a job |
| Fence posts without caps | bare fence tops look unfinished | stone/brick pillar + `stone_brick_slab` cap, or a `lantern` on gate posts; wooden posts get a `*_slab`/stairs cap, or a log post |
| Floating slabs / half blocks (roof lips, sills hanging over air) | no support | every slab touches a wall/post on one side; checked by a "no floating non-solid" lint |
| Gaps in the fence line / gate that touches a post | animals "leak"; gate not full width | gate between two taller posts, 2-wide double gate at the field; fence both sides connect to the post (full block) |
| Mixing 3+ wood types in one fence/trough/shed | palette noise | one wood for fence+shed posts (`spruce`), one dark accent (`dark_oak`) on the building |
| Props too big for their spot (a hay cart bigger than the yard, giant haystack on the ground path) | scale | props 1-3 blocks, **one hero prop per zone** |
| Flat unbroken ground (5x5 same block) | build-quality rule L-F6 | mud/grass/podzol/coarse patches with irregular edges, a worn path to each gate |
| Fence connects to a wall post wrongly | in Java a fence connects to full-face blocks, not to `cobblestone_wall` **[verify in preview]** | use full `cobblestone`/`stone_bricks` pillars where a fence must attach |

## 3. Cow place: concrete fix list (generator-ready)
Keep the barn (25 x 13, catslide, corbie gable, hoist). Change the *setting*; extend the plot east/south as needed.
- **Fold-yard (mud yard) 15 x 10** attached to the barn's side, enclosed on three sides: the barn wall, a **3-bay open-fronted shelter shed** opposite (11 x 4, back wall `stone_bricks`/`cobblestone`, 3 front posts `dark_oak_log`, 22.5 degree catslide roof in the barn's brick stairs, eave low at y3; hay loft opening in the gable), and a **drystone wall** (`cobblestone_wall`/`mossy_cobblestone_wall`, 2 high, irregular top with slab fleck) with a 3-wide double gate.
  - Ground: `mud` / `packed_mud` / `coarse_dirt` patches at the gate and trough, `dirt_path` worn lines between gate, trough and shed (curved), a few `hay_block`s at the shed.
  - Inside the shed: **hay rack** (row of `hay_block` on `spruce_fence` or `oak_slab` shelf) above a **trough** (`barrel` open top x2 or `spruce_stairs` + slab), a milking stool corner.
  - **Sunken water trough** 4 x 2 at the yard centre, stone-brick bottom, water 1 deep, rim `stone_brick_slab` (a cauldron row is the fallback, shockbyte/namehero use cauldrons/slabs **[snippet]**).
- **Pasture 28 x 18** (about 500 blocks, 1.5x the barn's footprint, `[D]`) beyond a field gate on the yard's far side, **grass only** (`grass_block`, `short_grass`, `dandelion`, `azure_bluet`, `oxeye_daisy`), a **2-wide worn trail** from the gate, a slight swale (1-2 block height change) so it is not a table.
  - Boundary: **hedge on the two back sides** = 1 wide, 3 high `oak_leaves` (persistent) over a 1-high `mossy_cobblestone`/`cobblestone` bank (Devon hedge), `oak_fence` core hidden inside, 2 hedgerow trees at least 10 apart; **drystone wall (2 high, `cobblestone_wall` with irregular tops) along the road side** so the scene is readable from the road; one **step stile** over it (two `cobblestone_stairs` steps each side) near the road gate.
  - Shade: one big oak in the pasture, one **field shelter** 5 x 3 (3-sided, open front to the south, `spruce` roof).
- **Roadside milk-churn stand** at the yard gate: stone slab platform 3 x 2 with 3 churns (`stripped_birch_log` upright with `iron_trapdoor` closed on top as a lid, **[verify]**; fallback `barrel`), lantern post. Keep to 3-6 blocks.
- **Props (pick 4):** hay cart (blocks; one wheel set only), haystack/rick (stepped `hay_block`, <=4 x 3), feed sacks (`white_wool` x3 + `brown_carpet`), bucket-and-stool at the barn door (`spruce_slab` on `spruce_fence` + `cauldron`), cart ruts (`coarse_dirt` pair).
- Dress: fence posts capped, one lantern per gate, no more than 12 trapdoors.

## 4. Chicken place: concrete fix list (generator-ready)
Keep the stone coop (parapets, buttress) and dovecote; **remove the trapdoor/hurdle mesh**.
- **Run 22 x 14** (308 blocks, enough for about 30 hens at the real 10 m2 rule; looks generous `[D]`), coop 9 x 5 set into the run's north edge, dovecote at the north-east corner outside the run. Run in two zones: **scratched yard (front, 12 x 14)**: `coarse_dirt`, `dirt_path`, `rooted_dirt`, `podzol` patches; **grass (back, 10 x 14)**: `grass_block` + `short_grass` + `dandelion`, one **apple or cherry tree** in the back-right as shade, with `podzol` ring.
- **Fence:** capped full-block **stone pillars every 4 blocks** (`cobblestone`/`stone_bricks`, 2 high, `stone_brick_slab` cap; this is the EcoSMP rhythm and the 4net rhythm) with **`spruce_fence` rail between** (top and bottom bar, which is vanilla fence's own two bars), **a `sweet_berry_bush`/`azalea` hedge along the long road side**, **double fence gate (2 wide) flanked by 3-high pillars, a single lantern on the left one**, a half-open single fence piece beside the gate `[Gamerheadquarters]`.
- **Coop (outside face to the run):** raised floor with an **access ramp of 3 alternating `spruce_stairs`/slabs** (no rails), **pop hole** (a 1 x 1 opening, a closed `spruce_trapdoor` as the hatch beside it, shown open), **external nest-box row**: a shelf of 5 boxes = 5 `spruce_trapdoor` hatches, closed, in one row under a `spruce_slab` roof strip, facing the run (the one allowed row), inside `hay_block`/`barrel` for the visible boxes; a **perch** (`stripped_dark_oak_log` horizontal, 4 long) visible through the open pop-hole window; one lantern.
- **Zones in the run:** dust bath 3 x 3 (hollow 1 deep, `sand` + `coarse_dirt` mix, ring of `cobblestone` slabs); **feeder** = `spruce_stairs` + `spruce_slab` trough 3 long; **waterer** = 2 `cauldron`s (water) on a `stone_brick_slab`; **lean-to shelter** 5 x 3 on 4 `spruce_fence`/`spruce_log` posts with a `spruce_stairs` roof and `hay_block` (also a rain shelter); **haystack** (stepped `hay_block`, <=3 x 3) and a **scarecrow** at the field edge (`oak_fence` cross, `carved_pumpkin` or `hay_block` head, `hay_block` body, a `*_stairs` hat) in the grass half, not the yard.
- **Dovecote:** nesting holes via `chiseled_bookshelf` empty slots `[D]` **[verify]** (dark 2 x 3 recesses read as pigeonholes) or open `dark_oak_trapdoor` pairs; rat ledge slab band; louvre cupola.
- **Flock size:** the 3-stage animals stay a handful (5-8); the pen must still look bigger than them.
- No more than 8 trapdoors in the whole place (shutters and the 5 hatches).

## 5. Mill: the water side done right
### Facts
- Head race (leat/lade) leads water from the pond or weir to the wheel; tail race leads it away; a sluice gate controls the water; races are controlled by a series of sluice gates; weirs raise the head (Wikipedia Mill race https://en.wikipedia.org/wiki/Mill_race **[read]**, Watermill **[read]**).
- **Breastshot:** water enters about mid-wheel; a **masonry apron closely follows the wheel face** to contain the water (Wikipedia Water wheel https://en.wikipedia.org/wiki/Water_wheel **[read]**). **Overshot:** water enters just past the top, usually via a **launder/flume or penstock**, needs head over a couple of metres (same page). **Undershot:** wheel set directly in the flow, small head.
- Real overshot practice: **avoid a long flume**, strengthen it with an exterior frame, **support it from the dam to the wheel with uprights**, put the sluice gate anywhere along the flume, and set the gate so buckets run about one-quarter full (Mother Earth News https://www.motherearthnews.com/sustainable-living/renewable-energy/overshot-water-wheel-zmaz72jmazraw/ **[read]**; figures there are inches-scale, not usable for us).
- Real mills: a **timber trough crosses a sluice, boarded on all sides with iron rods and brackets, drystone walls at the flanks funnel the water** (Nottinghamshire HER Moor Pond Wood https://her.nottinghamshire.gov.uk/Monument/MNT28333 **[snippet]**); small mills with a **wooden lade on a line** and a **stone-lined tail race to ground level** (Highland HER https://her.highland.gov.uk/Monument/MHG28385 **[snippet]**); wheel pit **lined on both sides with planks, floor planked against scour** (Killoteran https://excavations.ie/?p=28811 **[snippet]**).
- Minecraft-side: I found no builder write-up of the mill race. A PMC builder says they tried to make the wheel scoops go round and "not just smack some slabs on it" (search summary **[snippet]**). The only visual reference I have is the first-hand look at the Coniferous mill (`farm-buildings.md`): wheel in a stone slot between two piers, water about one third of the view.

### What the current build gets wrong (from `house_mill.py`)
Lines 279-291: the head race passes through the east pier as a **tunnel with iron-bar gates, then a stone-brick "beam box" with posts, a 4-wide `beam` at y4 and bars at y3 over the channel, with stone-brick coping slabs at y2**. That is a closed stone/log frame sitting over the water: it reads as a random brick box. Real sluice gates are small timber frames in an open channel; real wheel races are open to the sky.

### Design (wheel pit, race, sluice)
Assumes the wheel stays a **breastshot item-display (7 across, 2 wide)** as decided. Option B (overshot) is below.
1. **Open head race:** 2 wide, stone-lined (`stone_bricks` / `mossy_stone_bricks` / `cobblestone` sides 2 high, no coping box), straight or one gentle bend, **length 6-8**, water level **equal to the pond level**, with the pond directly connected (no dead-end pools). The channel enters the **wheel bay between the two piers** at the wheel's **axle height** (breastshot).
2. **Sluice:** a **timber gate frame** at the end of the race: two `dark_oak_log` posts 3 high on the race sides, a `stripped_dark_oak_log` cross-beam 1 above the water, a **plank gate** (`spruce_planks` 2 wide x 1 above the water line, partly raised) and a `lever` on a post; no iron bars, nothing over the water but the beam, **maximum 1 block above the water on the water cells**. The beam sits only at the posts' top, not across a roofed tunnel.
3. **Wheel pit:** stone-lined slot (the existing wheel slot), floor 1 below the tail race; **apron** (`stone_brick_stairs` curve) hugging the wheel's lower-front quarter; the pit is open at the top or under the building's overhang. **Axle stub** through the wall.
4. **Tail race:** leaves the pit at the pit floor level, 2 wide, one **1-block drop (weir) over `stone_brick_slab` lip** then a stream; stone sides continue to ground level, mossy; **end it in the stream or pond B**, never in a dead end.
5. **Levels (all water cells are exact levels, enclosed; no air gaps):** pond y = Y, head race y = Y, pit water Y-2, tail race Y-3, stream Y-4. Falling water between levels: the generator places source/falling blocks with the neighbours enclosed `[verify]` (a source above a source in a static paste looks like a still column; flowing "ribbed" water needs the physics tick or level-1..7 states).
6. **Footbridge** over the *tail* race downstream, not over the wheel; fenced `stone_bricks`.
7. **Optional hoist** stays on the gablet; not over the water.

**Option B (overshot, only if the user wants a flume):** the pond must sit **higher than the wheel top**, on a retaining wall (Coniferous idea). Flume = **a timber box**: floor `spruce_planks`, sides `stripped_spruce_log`/planks 1 high, same width as the wheel (2), carried on **braced posts every 3 blocks (`dark_oak_log` 2 high with `spruce_stairs` knee braces)** from the pond wall to the wheel; length <=6; flume floor ends 1 above the wheel top; every flume block is supported or hangs from the pond wall; water level in flume = pond level; a plank sluice at the pond end. **Never** a stone box, **never** a channel with no support below it.

### What looks wrong (checklist)
- A closed box, tunnel or lintel over water (the current build). A channel that floats above the wheel with no posts. A flume wider or narrower than the wheel. A dead-end pool or a race that ends in a wall. Water levels that jump by 2+ with no weir. Iron bars as a sluice gate (reads as a prison grating). Three different stones in the race walls. A wheel in a closed room with no water.

## 6. Lint rules to add (`design/build-lint.md`, from this note)
- **L-D1 fence cap:** every pillar/post at a fence line end/corner/gate has a cap (slab/lantern) or is a full block; no bare 1-block fence tops at gates.
- **L-D2 trapdoor budget:** <= 12 trapdoors per place (Cow Barn), <= 8 (Chicken Farm); trapdoor rows of >=6 in a line are rejected.
- **L-D3 fence family:** one fence block family per place (+ stone pillars).
- **L-D4 race open:** no non-air block directly above a water cell of the race within 1 block except sluice posts/beam; water cells are all connected from pond to stream.
- **L-D5 flume support:** every non-water block under a flume has support within 3 blocks or touches a wall.
- **L-D6 pasture ratio:** each animal place has a fenced yard/run >= 1.3x the building footprint, with >= 2 ground block types.
- **L-D7 props:** every prop within 6 blocks of the viewpoint is >= 1 per zone but <= 1 hero prop per zone.

## Hand-off
- **model-designer** (Pixel Pat): rebuild `cow_barn.yml` and `chicken_farm.yml`, patch `mill.yml`/`house_mill.py`; no new pack assets and no new item models are needed. Sizes: cow fold-yard 15 x 10, shelter shed 11 x 4, pasture 28 x 18; chicken run 22 x 14, coop 9 x 5; mill race 6-8 long x 2 wide, still the 7-across item-display wheel. New kits in `lib/farm_kits.py`: `capped_pillar_fence` (pillar every 4, caps, tall gate posts, lantern), `shelter_shed` (open front, bays, hay rack, trough), `water_trough` (sunken, rim slabs), `hedge_bank`, `drystone_wall`, `step_stile`, `churn_stand`, `nest_row` (<=5 trapdoor hatches under a slab strip), `dust_bath`, `scarecrow`, `sluice_gate` (posts, beam, plank gate, lever), `open_race` (no coping box; connected to pond and stream); delete the tunnel/beam box in `house_mill.py` lines 279-291. Verify `chiseled_bookshelf` empty-slot look, the `iron_trapdoor`-lid churn and falling-water rendering in a preview before committing to them. Add lint rules L-D1..L-D7 with tests.
- **plugin-coder:** none, unless the plots grow: the cow place needs about 60 x 40 and the chicken place about 40 x 26 around the building; check the plot map and the yard/hologram/panel/ripe-spot cells still sit within 5 blocks of the viewpoint. Water must stay enclosed with physics off.
- **vfx-designer / sound-designer:** optional ambience only (mill water, hens, cows); no blockers.
- **plugin-tester:** if `points` change, parser cases for the new yard/pasture rectangles.
- **Proposed contract IDs:** none new (item models unchanged: `cookieclicker:waterwheel`); house files stay `cow_barn.yml`, `chicken_farm.yml`, `mill.yml`.

## Sources
- Diamond Lobby chicken coop https://diamondlobby.com/minecraft/how-to-build-chicken-coop-minecraft/ (read)
- buildingguide.app chicken coop https://buildingguide.app/tutorials/chicken-coop (read)
- Astroworld animal pen https://builds.astroworldmc.com/builds/animal-pen (read)
- 4netplayers fence ideas https://www.4netplayers.com/en/blog/minecraft/minecraft-fence-ideas/ (read); Gamerheadquarters fancy brick fence https://gamerheadquarters.com/minecraft/fancy-brick-fence-guide.html (read)
- Wikipedia (all read): https://en.wikipedia.org/wiki/Water_wheel , /Watermill , /Mill_race , /Gristmill (thin) , /Hedge , /Stile , /Chicken_coop , /Dairy_farming (partial) , /Pasture (nothing useful)
- Weald & Downland cattle sheds https://wealddown.co.uk/buildings/cattle-sheds (read)
- Mother Earth News overshot wheel https://www.motherearthnews.com/sustainable-living/renewable-energy/overshot-water-wheel-zmaz72jmazraw/ (read)
- Search summaries only (snippet): mc-mod.net https://www.mc-mod.net/common-mistakes-new-builders-make/ , Sportskeeda tips https://www.sportskeeda.com/minecraft/best-tips-tricks-minecraft-builders (405 on fetch), minecraft.how https://minecraft.how/blog/post/build-animal-pen-minecraft , shockbyte https://shockbyte.com/blog/minecraft-barn-ideas (403), Northamptonshire HER https://her.northamptonshire.gov.uk/Monument/MNN144470 , Nottinghamshire HER https://her.nottinghamshire.gov.uk/Monument/MNT28333 , Highland HER https://her.highland.gov.uk/Monument/MHG28385 , Killoteran https://excavations.ie/?p=28811 , Chewy/Modern Farmer/Nestera/RSPCA Australia chicken space pages
- Not readable: Planet Minecraft waterwheel blog https://www.planetminecraft.com/blog/how-to-build-a-waterwheel/ (403), YouTube tutorials (video), r/Minecraftbuilds (nothing found)
- Earlier: `docs/research/farm-buildings.md`, `docs/research/build-quality.md`; repo files `design/scripts/house_mill.py`, `design/scripts/lib/farm_kits.py` (mill_race)
