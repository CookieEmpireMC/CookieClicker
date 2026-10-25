# Bakery buildings on the plot (world models + slots)

**Status: placeholder.** Generated models and textures, judged from renders only (no in-game look yet). Original work by Pixel Pat.

## Pipeline and generators
Plain resource pack (`cookieclicker`), Paper 26.3, item model definitions. Generators (run by `mise run assets`):
`design/scripts/building_world.py` (models, atlases, item definitions), `building_icons.py` (9 panel icons), `building_slots.py` (`houses/building-slots.yml`), `decor_style.py` (decor, reads the slots). Renders: `design/scripts/lib/render_building_previews.py` -> `design/scripts/previews/building_<id>.png`.

## World models (contract)
- Item model `cookieclicker:building_world/<id>` (definition `items/building_world/<id>.json`): `select` on `custom_model_data` strings[0] = `"1"`, `"2"`, `"3"` (fallback = stage 1) -> model `cookieclicker:item/building_world/<id>_<stage>`.
- ids: `villager_baker` (Baker's Apprentice), `industrial_oven`, `extra_belt`, `shipping_centre`, `alchemy_lab`, `portal_bakery`, `chicken_farm` (7th building, ingredients feature; was the unmodelled Beehive: hen coop, stage 1 small coop on legs with ramp, nest box, 2 hens; stage 2 wider coop + fenced run + feeder, stage 3 red walls, striped roof, weather vane, golden eggs; 37/60/75 elements, 20x19, 23x24, 23x24 units, h 18/19/23.6; footprint half extent 0.85 blocks; no slots yet). Code: Material.COOKIE (or fallback) + `ITEM_MODEL` = `cookieclicker:building_world/<id>` + custom model data string `"<stage>"`; stage 2 from 25 owned, stage 3 from 100.
- **Convention:** the building's front faces **north (-Z) in the model files**, floor (bottom) at **model y 8** (ItemDisplay `NONE` draws model (8,8,8) at the display position; asserted in the generator: lowest element y == 8), x/z centred on 8, scale 1, no `display` transform needed. Max height 24 units (model y 32).
- **Slot `yaw`** = the Minecraft yaw of the direction the building faces (0 south, 90 west, 180 north, 270 east), like the layout spots (pedestal yaw 180). The code handles the item display flip (config yaw offset if it comes out mirrored).
- Textures: one atlas per building `textures/item/building_world/<id>.png` (128 or 256 px, 2 texels per unit), shared by its 3 stages. Elements per model 7-31; only `rotation` used: 45 deg on y (alchemy crystal).

## Stages, footprints, heights (units; 16 = 1 block; x width, z depth, h height; front = -z)
| Building | Stage 1 | Stage 2 (25 owned) | Stage 3 (100 owned) |
|---|---|---|---|
| villager_baker | little baker, chef hat; 9x7, h 20.5 | red neckerchief, gold buttons, rolling pin, taller hat; 9x8, h 22.5 | gold hat band + star badge, blue apron, tray of cookies; 9x10, h 24 |
| industrial_oven | brick oven, glowing mouth, iron vent; 20x16, h 15 | + tall brick chimney, log pile behind; 20x20, h 23 | copper twin chimneys, gold band, side glow windows, roof lantern; 20x20, h 24 |
| extra_belt | belt on iron legs, 3 cookies; 25x8, h 6.9 | + copper side rails, 5 cookies; 25x8, h 7.5 | + two gold gantries with lamps; 25x11, h 14.5 |
| shipping_centre | pallet with crates and a parcel; 24x16, h 15 | + posts and striped awning; 25x17, h 20.5 | + ridge, gold trim, lantern, golden crate; 26x18, h 22.5 |
| alchemy_lab | table with 3 bottles, green cauldron; 24x10, h 12.5 | + back shelf with bottles; 24x12, h 18.4 | + purple brew, floating crystal, glow lamps; 24x12, h 22 |
| portal_bakery | obsidian arch, swirl portal, gold runes; 22x10, h 21 | + cookie keystone, more runes; 22x10, h 23.5 | + gold spires and base trim, warm swirl; 22x11, h 24 |

Placement uses a centred **footprint square** per building (side = 2 x max abs extent over all stages, valid for any yaw): apprentice 0.82 blocks, oven 1.55, extra belt 1.56, shipping 1.60, alchemy 1.50, portal 1.41.

## Slots (`src/main/resources/houses/building-slots.yml`, generated)
Format `slots: { <id>: [ {at: [x, 0, z], yaw: <deg>}, ... ] }`, plot cells from the plot's min corner (frame of `layout:` in `factory.yml`); the model's bottom centre stands at the cell centre (x + 0.5, z + 0.5), y 0 = first air layer. Instance i shows on slot i; slot count = visual cap.
| Building | Cap | Slots (x, z, yaw) | Cells covered by the footprint (add to the decor keep-out, per slot) |
|---|---|---|---|
| villager_baker | 8 | (5,2) (7,2) (9,2) (5,4) (7,4) (9,4) (7,6) (9,6), yaw 270 (queue beside the walk, facing east) | the slot cell only |
| industrial_oven | 4 | (27,12) (27,15) (27,18) (27,21), yaw 90 (east wall, front to the factory) | x 26..28, z = cell z-1 .. cell z+1 |
| extra_belt | 2 | (10,26) (14,26), yaw 180 (behind the factory) | x = cell x-1 .. +1, z 25..27 |
| shipping_centre | 2 | (2,12) (2,15), yaw 270 | x 1..3, z = cell z-1 .. +1 |
| alchemy_lab | 2 | (2,19) (2,22), yaw 270 | x 1..3, z = cell z-1 .. +1 |
| portal_bakery | 1 | (27,4), yaw 90 (NE corner) | x 26..28, z 3..5 |
Simple rule for the coder: the keep-out of a slot is the square of cells `floor(c+0.5-h) .. ceil(c+0.5+h)-1` with h = footprint/2 above. The generator asserts the squares stay inside the fence (>= 0.2 blocks), off the factory zone (x5..25, z9..24) and front zone (x12..23, z1..8), and 0.25 blocks apart. `decor.yml` keeps a further 0.35 blocks clear.

## Decor rework
`decor.yml` regenerated (282 blocks, 7 clusters): west store, wheat bed and scarecrow moved to the front-west corner (x1..4), east yard now holds lamps and flowers along x 29, portal corner gets flowers and hay, back yard keeps hedges, log pile and trees clear of the belts. Details in `design/decor.md`; preview `design/scripts/previews/decor_sheet.png` (slot zones pink).

## Placeholder / needs a human
Everything. Judge in game: scale next to the factory and basket, the 180 deg item display turn, stage 3 readability from the camera point, texture density.
