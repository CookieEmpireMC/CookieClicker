# Cookie (the big clickable cookie)

**Status: placeholder.** Generated pixel art and a generated 3D model, good enough to ship for testing; a human artist may repaint the textures or polish the model in Blockbench later. Keep the same file names, sizes and palette rules so nothing else has to change.

**Originality:** original work by Pixel Pat, generated from scratch by the scripts below. Not copied, traced or derived from any other game, resource pack, plugin or the vanilla cookie texture.

## Purpose
The main clickable cookie, shown large in the world through an `ItemDisplay` on the bakery station. It also works as a held/inventory item.

## Pipeline
Plain resource pack (`resourcepack/`), namespace `cookieclicker`. Target Paper 26.3 (`26.3.build.205-stable`), so item model definitions apply.

| What | Path |
|---|---|
| Source art (64×64) | `resourcepack/assets/cookieclicker/textures/item/cookie.png` (also the model's `particle` texture) |
| Face texture (64×64) | `resourcepack/assets/cookieclicker/textures/item/cookie_face.png`: the source art trimmed to the model's stepped silhouette, with the outline redrawn on the new edge |
| Edge texture (64×64) | `resourcepack/assets/cookieclicker/textures/item/cookie_edge.png`: baked side crust. Rows 0–15 = horizontal strip (top/bottom of the slabs), columns 0–15 of rows 16–63 = vertical strip (left/right walls); the rest is unused and transparent |
| 3D model | `resourcepack/assets/cookieclicker/models/item/cookie.json` (9 cuboids, replaces the old flat `item/generated` model) |
| Item model definition | `resourcepack/assets/cookieclicker/items/cookie.json` → `cookieclicker:item/cookie` (unchanged) |
| Generators | `design/scripts/cookie.py` (source art, seed 1337), `design/scripts/cookie_model.py` (face + edge textures and the model, seed 4242). Run `mise run assets`; both are deterministic, and `cookie.py` runs first |
| Preview | `python design/scripts/lib/render_cookie_previews.py <out_dir>` (renders the model from 5 angles, a close-up and the GUI view with `design/scripts/lib/model_render.py`) |
| Pack metadata | `resourcepack/pack.mcmeta`: `min_format` 97, `max_format` 97 (26.3 = resource pack format 97.1 per the Minecraft Wiki "Pack format" page, re-checked 2026-10-18; an integer `max_format` covers every 97.x minor) |

## IDs for the code
- `item_model` data component: **`cookieclicker:cookie`**
- Base item `Material.COOKIE` (also the fallback for players without the pack); set the component with `ItemStack#setData(DataComponentTypes.ITEM_MODEL, Key.key("cookieclicker", "cookie"))`.
- Show it in an `ItemDisplay` with **`ItemDisplayTransform.NONE`** (per `docs/research/display-cookie.md`) and scale it with the display's `Transformation` (`station.cookie.scale`). The model's `display` block is not used by `NONE`.

## Model: dimensions and pivot
- Model space, 16 units = 1 block at scale 1. **Centred on (8, 8, 8)**, so a spin around the display's Y axis turns it about its own centre.
- The cookie **stands upright**: the two big faces point north (−Z) and south (+Z); the thickness runs along Z.
  - Diameter: **14.5 units** tall (y 0.75..15.25), 13.5 units wide at the middle (x 1.25..14.75).
  - Thickness: **4 units** (z 6..10), about 0.28 of the diameter: a chunky cookie.
- Shape: 9 horizontal slabs stacked symmetrically (a centre slab y 4.5..11.5, then 4 steps above and below), widths fitted to the art's silhouette by `cookie_model.py`. All coordinates are on the 0.25-unit grid (1 texel of the 64 px art), no element rotations.
- Faces: north and south both show `cookie_face.png` **un-mirrored** as seen from outside (the back is not a mirror image of the front; that keeps the top-left light on both sides). Slab tops, bottoms and walls use `cookie_edge.png`: dark crust lip at both faces, golden middle, baked blisters and a few chocolate chunks poking out.
- No animations in the model. Spin, bob and the squash/pop are display `Transformation` interpolation in code (see `docs/research/display-cookie.md`).

### Display transforms (hand and GUI only)
| Slot | Rotation | Translation | Scale | Why |
|---|---|---|---|---|
| gui | 15, −25, 0 | 0, 0, 0 | 0.8 | turned a little so the edge shows: reads as a thick cookie, fits the slot |
| ground | 0, 0, 0 | 0, 2, 0 | 0.45 | dropped item, like vanilla generated items but a bit smaller (it is thick) |
| fixed | 0, 180, 0 | 0, 0, 0 | 0.8 | item frame, front face out |
| head | 0, 180, 0 | 0, 13, 7 | 1 | vanilla generated values |
| thirdperson (both) | 0, 0, 0 | 0, 3, 1 | 0.5 | vanilla generated values, slightly smaller |
| firstperson (both) | 0, −90, 25 | 1.13, 3.2, 1.13 | 0.6 | vanilla generated values, slightly smaller |

## Texture: dimensions
- Source art: **64×64**, RGBA, transparent background. Cookie disc centred on (32, 32), radius about 29 px with a slightly irregular, hand-made edge. Texel density: 4 px per model unit.
- Face texture: same art inside the slab silhouette; outline redrawn there (`#8E5032` lit side, `#5C2F2A` shadow side).
- Edge texture: 4 px per model unit like the face; the 16 px thickness profile is lip `#8E5032`, `#B8743A`, a wobbling `#B8743A`/`#D69A4C` row, golden middle, and the same mirrored at the back.

## Lighting
Light from the **top-left**, slightly in front. Lit crescent on the top-left of the rim, core shadow crescent on the bottom-right, chocolate chunks highlighted on their top-left with a 1 px contact shadow falling bottom-right. The edge texture has no directional light baked in (it faces every direction while the cookie spins); the game's face shading does that.

## Palette (9 colours, shared by all three textures)
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

## Placeholder status and human follow-up
- All three textures and the model are placeholders (generated). A human may repaint `cookie.png`; re-run `mise run assets` and the face texture and slab fit follow automatically.
- Check in game: the cookie's size at `station.cookie.scale`, that both faces read the right way round while it spins, and the GUI/hand views.

## Related assets (not made here)
- Click sound (sound-designer) and crumbs + squash/pop (vfx-designer): see the brief.
