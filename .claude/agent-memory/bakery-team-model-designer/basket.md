---
name: basket
description: Cookie basket and world-building generators (basket.py, building_world.py, building_slots.py), spec locations, size rules
metadata:
  type: project
---

- Basket (2026-10-22): `design/scripts/basket.py` writes `items/cookie_basket.json`, `models/item/basket/<tier>_<fill>.json`, `textures/item/basket/<tier>.png`; spec `design/basket.md`. Packer-based atlas (2 texels/unit; golden needs 256). Preview via scratch script using `lib/model_render.render`. All four sides identical, so a 180 degree turn doesn't matter.
- Buildings (2026-10-22): `design/scripts/building_world.py` (6 buildings x 3 stages, local-coordinate Box lists, `footprint()`, seeded `random.Random`), `building_slots.py` (yaml + zone_cells), `building_icons.py`; spec `design/buildings-world.md`. Model height cap 24 units (y 8 floor, y 32 max). Slot yaw = Minecraft facing yaw; model front = north.
