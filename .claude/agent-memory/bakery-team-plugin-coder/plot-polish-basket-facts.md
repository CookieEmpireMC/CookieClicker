---
name: plot-polish-basket-facts
description: Cookie basket (plot-polish, 2026-10-22) - CustomModelData builder API, pack-change respawn, basket/cookie height maths, pinned StationView ctor
metadata:
  type: reference
---

Verified 2026-10-22 (branch plot-polish/plugin-coder-basket, commit 9536c9e). Stable for paper-api 26.3.build.205.
- `io.papermc.paper.datacomponent.item.CustomModelData.customModelData().addString(s).build()` with
  `DataComponentTypes.CUSTOM_MODEL_DATA` (javap 26.3). Builder also has addFloat/addFlag/addColor (+ list variants).
- Basket = `StationView.pedestal` typed `Display` (ItemDisplay basket or BlockDisplay block); `basket` field set via
  instanceof. PanelUpdaterTest calls the 9-arg StationView ctor with `null` pedestal, so keep one ctor of that arity.
- Pack status change no longer swaps stacks (updatePack removed): refresh respawns the view when `withPack` differs.
- Cookie centre with basket = pedestal up + look height + cookie-gap + scale/2 + bob, min cookie-max-height (2.2).
  Heights from design/basket.md; golden floor 0.69 for its corner posts (Rick, 2026-10-22).
- Tier-up switch tick = vfx basket_tier_up burst peak, tick 8 (Rick, 2026-10-22).
