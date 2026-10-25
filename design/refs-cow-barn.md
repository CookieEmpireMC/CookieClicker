# Reference sheet: `cow_barn` (stone ground storey with arched stalls, timber loft, one spruce roof)

Sources: Rick's survey of community builds on 2026-10-25 (Google Images grids of PlanetMinecraft, YouTube, Reddit, WBuilds, JPCORE, Graysun and EcoSMP medieval barns; looked at for what they have in common, nothing copied or traced), plus `docs/research/farm-buildings.md` (the earlier Etoile barn notes). The user rejected the first two cow barns, so this sheet replaces them. Original design.

What the well-made barns do, in our words:
- **Few material families.** A light stone lower storey (cobble, stone bricks, andesite-like), a dark timber frame with planks above, ONE roof family. Two or three families per building, no more.
- **Arch rhythm.** The best read from 10-15 blocks is a long stone ground storey with a row of arched stall openings (upside-down stairs round the corners), piers between, then a wooden hayloft and a steep dark roof with a few dormers.
- **One big gable.** A central cross-gable in timber with braced double doors and, above them, a hayloft door with a hoist beam and a chain.
- **Stepped silhouette.** Lower side aisles or lean-tos with their own lower roofs next to the high main roof.
- **Paddock attached.** The fence starts at the barn wall; hay, a trough and trampled mud at the gate; the barn sits a little into a slope with meadow behind.
- **Details that survive distance.** Arch rhythm, posts, the big door, the loft door, roof overhang, lanterns on posts. No flower pots on sills, no block-sized props in openings, no stone bricks on the timber gable.

| What | Fact used | Where it shows |
|---|---|---|
| Ground storey | stone bricks (mossy, cracked) on a cobble kerb, 3 high, 4 arches 3 wide on the north side, 4 more on the back wall | `arch()` in `house_cow_barn.py` |
| Loft | dark-oak posts + oak boards, stripped dark-oak beam course at y4 and plate at y7 | wall loops |
| Roof | one spruce stair field, dark-oak trim, half-hipped ends, four dormers | `K.hipped_roof`, `K.dormer` |
| Entrance | timber cross-gable x 22..28, braced doors, loft door, hoist beam and chain | `K.barn_door_double`, hoist code |
| Aisles | catslide aisle at the back, lean-to at the east end | `K.catslide_roof` |
| Paddock | fence from the west wall, trough, hay, mud at the gate | scenery() |

Ramps (light to dark): plinth cobblestone / mossy / andesite; stone stone bricks / mossy / cracked; board oak / dark-oak / birch planks; roof spruce stairs / dark-oak stairs / spruce planks.
