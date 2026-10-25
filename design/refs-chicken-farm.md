# Reference sheet: `chicken_farm` (raised wooden coop on stilts inside one fenced run)

Sources: Rick's survey of community builds on 2026-10-25 (PlanetMinecraft, YouTube, Reddit, WBuilds, JPCORE "Chicken Manor", Graysun, EcoSMP; looked at, nothing copied or traced). The user rejected the earlier stone coop with a separate dovecote, so this sheet replaces it. Original design.

What the well-made coops do, in our words:
- **Wood, not stone.** Spruce or oak planks with dark log posts at the corners and along the walls. The stone version only works as one tall hall with buttresses, which is not a small box.
- **Raised on stilts.** The coop stands 2-3 blocks up on dark log posts over a mossy-cobble pad, with a ramp or ladder to a small door, and the space underneath is used: nest boxes (composters), a water cauldron, a trough.
- **One compact footprint.** The run fence hugs the coop (1-3 blocks out); nothing is scattered across the lawn.
- **Run ground.** Coarse dirt, rooted dirt, podzol and dirt path with a few grass tufts, hay piles on the ground, a trough.
- **Roof.** Steep, one family (spruce or dark oak), small overhang, a cupola or weathervane on the ridge.
- **Silhouette variety.** A second, smaller coop (pyramid roof) next to the big one; JPCORE's manor has a tall A-frame next to a low stall.
- **Fences.** Wood fence with fence-gate entries and a taller post with a lantern every few blocks.

| What | Fact used | Where it shows |
|---|---|---|
| Coop A | oak/birch/jungle plank walls inset one block inside dark-oak log posts, floor at y3 on 11 stilts, plank ramp from the run gate, railing, hanging lanterns | `build()` |
| Under the floor | 4 composters, water cauldron, barrel, trough slabs | `build()` |
| Roof A | spruce stairs, dark-oak trim, gable with a louvre cupola and weathervane | `K.hipped_roof` |
| Coop B | smaller hut on 7 stilts, pyramid roof, finial lantern, nest-box trapdoors | `K.pyramid_roof` |
| Run | spruce fence with dark-oak log posts and lanterns, 3 gates, coarse-dirt run ground, hay, trough, orchard trees, veg plot | `scenery()` |

Ramps (light to dark): plinth cobblestone / mossy / andesite; board oak / birch / jungle planks; roof spruce stairs / dark-oak stairs / spruce planks.
