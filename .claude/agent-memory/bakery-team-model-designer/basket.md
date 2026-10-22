---
name: basket
description: Cookie basket assets (select on custom_model_data, 3 tiers x 5 fills), generator and spec locations
metadata:
  type: project
---

- 2026-10-22: `design/scripts/basket.py` writes `items/cookie_basket.json`, `models/item/basket/<tier>_<fill>.json`, `textures/item/basket/<tier>.png`; spec `design/basket.md` (widths, top heights). Packer-based atlas (2 texels/unit; golden needs 256). Preview: scratch script using `lib/model_render.render`. Item display front/180 turn avoided by making all four sides identical.

- building-roster (2026-10-22): `design/scripts/building_world.py` (6 buildings x 3 stages, local-coordinate Box lists, `footprint()`), `building_slots.py` (yaml + zone_cells), `building_icons.py`; spec `design/buildings-world.md`. Model height cap 24 units (y 8 floor, y 32 max). Slot yaw = Minecraft facing yaw; model front = north.
