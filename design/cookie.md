# Cookie (the big clickable cookie)

**Status: placeholder.** Generated pixel art, good enough to ship for testing; a human artist may repaint it later. Keep the same file name, size and palette rules so nothing else has to change.

## Purpose
The main clickable cookie, shown large in the world through an `ItemDisplay`. Also usable as a normal inventory icon.

## Pipeline
Plain resource pack (`resourcepack/`), namespace `cookieclicker`. Target Paper 26.3 (`26.3.build.205-stable`), so item model definitions apply.

| What | Path |
|---|---|
| Texture | `resourcepack/assets/cookieclicker/textures/item/cookie.png` |
| Model | `resourcepack/assets/cookieclicker/models/item/cookie.json` (parent `minecraft:item/generated`, so it inherits the vanilla display transforms and gets extruded edges) |
| Item model definition | `resourcepack/assets/cookieclicker/items/cookie.json` → `cookieclicker:item/cookie` |
| Generator | `design/scripts/cookie.py` (run `mise run assets`; fixed seed 1337, deterministic) |
| Pack metadata | `resourcepack/pack.mcmeta`: `min_format` 97, `max_format` 97 (26.3 = resource pack format 97.1 per the Minecraft Wiki "Pack format" page; an integer `max_format` covers every 97.x minor) |

## IDs for the code
- `item_model` data component: **`cookieclicker:cookie`**
- Any base item works (for example `Material.COOKIE`); set the component with `ItemStack#setData(DataComponentTypes.ITEM_MODEL, Key.key("cookieclicker", "cookie"))`.
- For the in-world cookie, show that stack in an `ItemDisplay` (display transform `FIXED` or `NONE`) and scale it with the display's transformation.

## Dimensions and pivot
- Texture: **64×64**, RGBA, transparent background.
- Cookie disc: centred on (32, 32), radius about 29 px with a slightly irregular, hand-made edge (soft lumps, max about 1 px off a circle). About 2 px of transparent margin on every side.
- Model: flat generated item, 16×16×1 model units; pivot at the model centre (8, 8, 8), the vanilla default.
- No animations (a click "squish" should be done in code by changing the `ItemDisplay` transformation with interpolation).

## Lighting
Light from the **top-left**, slightly in front. Lit crescent on the top-left of the rim, core shadow crescent on the bottom-right, chocolate chunks highlighted on their top-left with a 1 px contact shadow falling bottom-right.

## Palette (9 colours)
| Role | Hex |
|---|---|
| Dough highlight | `#F7DC9A` |
| Dough light | `#EBBE6C` |
| Dough base | `#D69A4C` |
| Dough mid shadow | `#B8743A` |
| Dough shadow / lit-side outline | `#8E5032` |
| Outline (shadow side) | `#5C2F2A` |
| Chocolate highlight | `#86523C` |
| Chocolate base | `#54301F` |
| Chocolate dark | `#311A1E` |

Ramps are hue-shifted: highlights lean yellow, shadows lean red/purple.

## Related assets (not made here)
- A click sound (sound-designer) and crumb particles (vfx-designer) would suit the cookie; see the report.
