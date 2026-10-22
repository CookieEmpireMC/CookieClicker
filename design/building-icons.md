# Building icons (shop panel)

**Status: placeholder.** Generated pixel art, good enough for testing; a human artist may repaint them later (same file names and size).

**Originality:** original work by Pixel Pat, generated from scratch by the scripts below. Not copied or traced from any other game, pack or plugin (the baker is "villager-ish" only in being long-nosed with a single brow; it is not the vanilla villager texture).

## Purpose
One icon per building row on the floating shop panel (`docs/research/world-panel.md`): an `ItemDisplay` with `ItemDisplayTransform.FIXED`, scale about 0.35, left of the row text. They must read at that small size.

## Pipeline
Plain resource pack, namespace `cookieclicker`, Paper 26.3 (item model definitions). Flat `minecraft:item/generated` models, so they inherit the vanilla display transforms (`FIXED` = item frame view) and get the usual extruded edge.

| Building | Item model ID (`item_model` component) | Item definition | Model | Texture | Generator |
|---|---|---|---|---|---|
| Villager Baker | **`cookieclicker:building/villager_baker`** | `items/building/villager_baker.json` | `models/item/building/villager_baker.json` | `textures/item/building/villager_baker.png` | `design/scripts/building_villager_baker.py` |
| Wheat Farm | **`cookieclicker:building/wheat_farm`** | `items/building/wheat_farm.json` | `models/item/building/wheat_farm.json` | `textures/item/building/wheat_farm.png` | `design/scripts/building_wheat_farm.py` |

All paths are under `resourcepack/assets/cookieclicker/`. Code: `ItemStack.of(Material.COOKIE)` (or the configured fallback material) plus `setData(DataComponentTypes.ITEM_MODEL, Key.key("cookieclicker", "building/villager_baker"))`. Without the pack, use the fallback materials from `config.yml` (`station.panel.icons.<building>.fallback-material`, e.g. `BREAD` / `WHEAT`). No unaffordable variant: dimming is done with display brightness in code.

Shared helpers: `design/scripts/lib/pixel.py` (masks, height-field shading, outline, orphan cleanup).

## Dimensions
- **32×32** RGBA, transparent background, power of two. Texel density 2 px per model unit (flat item).
- Villager Baker: chef hat rows 1–14 (three puffs + band), head rows 13–29 (cols 9–22), 1 px outline around the silhouette. Silhouette roughly a tall rectangle with a wide rounded top.
- Wheat Farm: five grain ears fanning out (rows 0–15), straw bundle with a red twine tie at rows 18–19, standing on a soil mound (rows 24–31, cols 3–28) with two sprouts.
- No animations. Pivot: vanilla item centre (8, 8, 8).

## Lighting
Top-left light, as in the style guide: lit outline one step lighter on the top-left, darkest outline bottom-right; the nose casts a 1 px shadow to the right; each grain ear is shaded as its own rounded lump.

## Palettes
**Villager Baker (14 colours)**
| Role | Hex |
|---|---|
| Hat highlight / base / shadow / outline | `#FFFBEF` `#ECE3D2` `#CDBDA8` `#94807A` |
| Skin light / base / shadow / outline | `#F4CDA0` `#DCA67A` `#B97A58` `#7C4A3B` |
| Brow + moustache light / base | `#8A5A40` `#5E3A2C` |
| Eye white, iris, iris dark | `#FFF6EA` `#4E8A3C` `#2F5A2C` |
| Cheeks | `#E08C74` |

**Wheat Farm (17 colours)**
| Role | Hex |
|---|---|
| Grain highlight / light / base / shadow / outline | `#FFE68C` `#F0BF4C` `#CC922C` `#8E5A22` `#5E3519` |
| Straw light / base / shadow | `#EED27A` `#C9A243` `#93702C` |
| Twine light / base / dark | `#D2654A` `#9E3A2E` `#66241F` |
| Soil light / base / furrow / outline | `#8C6040` `#6A452E` `#4A2E22` `#2F1C17` |
| Sprout light / dark | `#9CC452` `#5E8A34` |

## Placeholder status and human follow-up
- Both textures are placeholders. Check them in game at the panel's icon scale (0.35 block): the baker's hat and nose and the sheaf's fan shape should read at a glance.

## building-roster additions (2026-10-22)
Nine more icons, same pipeline and size (32x32, flat `item/generated`, `cookieclicker:building/<id>`): `cow_barn`, `sugar_cane_field`, `industrial_oven`, `extra_belt`, `beehive`, `cocoa_plantation`, `shipping_centre`, `alchemy_lab`, `portal_bakery`. Generator `design/scripts/building_icons.py` (placeholder, outline #2E1A22, flat shading). `wheat_farm` (sheaf, = Wheat Field) and `villager_baker` (chef-hatted head, = Baker's Apprentice) still fit their new names and are unchanged. Fallback materials for the code: cow_barn MILK_BUCKET, sugar_cane_field SUGAR_CANE, industrial_oven FURNACE, extra_belt RAIL, beehive BEEHIVE, cocoa_plantation COCOA_BEANS, shipping_centre CHEST, alchemy_lab BREWING_STAND, portal_bakery OBSIDIAN (suggestions).
