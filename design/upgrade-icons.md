# Upgrade icons (click upgrades panel and shelf)

**Status: placeholder.** Generated pixel art for testing; a human artist may repaint them (same file names and size).

**Originality:** original work by Pixel Pat, generated from scratch by `design/scripts/upgrade_icons.py`. Not copied or traced.

## Purpose
One icon per click upgrade, used as a small `ItemDisplay` (`FIXED`, scale about 0.35) in a row of the **Click upgrades** panel and as a smaller icon on the **upgrade shelf**. Same style and pipeline as `design/building-icons.md`: flat `minecraft:item/generated` models, 32x32 RGBA, namespace `cookieclicker`, Paper 26.3.

## IDs (all placeholders)
Code: `setData(DataComponentTypes.ITEM_MODEL, Key.key("cookieclicker", "upgrade/<id>"))` on the fallback item. Paths under `resourcepack/assets/cookieclicker/`: `items/upgrade/<id>.json`, `models/item/upgrade/<id>.json`, `textures/item/upgrade/<id>.png`.

| Upgrade id | Item model | Looks like | Suggested fallback item (`config.yml station.upgrade-fallback-items`) |
|---|---|---|---|
| `bakers_strength` | `cookieclicker:upgrade/bakers_strength` | cookie with a green up arrow | `COOKIE` |
| `wooden_spatula` | `cookieclicker:upgrade/wooden_spatula` | wooden spatula, slotted blade | `WOODEN_SHOVEL` |
| `stone_rolling_pin` | `cookieclicker:upgrade/stone_rolling_pin` | grey rolling pin, wooden grips | `STONE_SHOVEL` |
| `iron_rolling_pin` | `cookieclicker:upgrade/iron_rolling_pin` | pale iron rolling pin | `IRON_INGOT` |
| `golden_glove` | `cookieclicker:upgrade/golden_glove` | golden glove, silver cuff | `GOLDEN_CARROT` |
| `diamond_fingers` | `cookieclicker:upgrade/diamond_fingers` | cyan crystal glove with gems | `DIAMOND` |
| `netherite_hands` | `cookieclicker:upgrade/netherite_hands` | dark glove, ember cracks, gold cuff | `NETHERITE_INGOT` |
| `handy_fingers_1` | `cookieclicker:upgrade/handy_fingers_1` | hand, 1 finger up, red cuff | `STICK` |
| `handy_fingers_2` | `cookieclicker:upgrade/handy_fingers_2` | hand, 2 fingers up, amber cuff | `BLAZE_ROD` |
| `handy_fingers_3` | `cookieclicker:upgrade/handy_fingers_3` | hand, 3 fingers up, purple cuff | `AMETHYST_SHARD` |

(`WOODEN_SHOVEL` etc. are vanilla items that exist in 26.3; the coder checks the enum names.) No unaffordable variant; dimming is done in code. The fallback items are a suggestion: any item that reads close to the idea is fine.

## Dimensions, pivot, animation
32x32, transparent background, 2 px per model unit, vanilla item centre pivot (8,8,8), no animation.

## Palette (hex, light / base / shadow / outline)
Wood `#D9AE6E #B58444 #8A5E2E #4F321B`; stone `#B9B9B2 #8E8E8A #66666A #37373C`; iron `#F2F2EE #CFCFCC #9C9CA2 #4A4A54`; gold `#FFEFA0 #F7C93C #C98A1E #6E4312`; diamond `#D8FFFA #6FE6DC #2FA6B4 #14556A`; netherite `#7A6A6C #4E4244 #322A2D #17121A` (+ ember `#FFC25A #E8702A`); cookie `#E0A25A #B9783A #8A5326 #4A2B17`; arrow green `#B8F07A #5EBB4A #2F7A3A #173F26`; skin `#F7D9B0 #E3AE7C #BC7F58 #6B4030`; cuffs red/amber/purple in the script. Light comes from the top-left (style guide).

## Layout key
`factory.yml: layout.clicks: {at: [16, 0, 6], yaw: 180}` (plot cells, bottom centre), the mirror of `shop` on the other side of the pedestal (`[18,0,6]`). Details in `design/factory.md`. `default.yml` (the cottage) has no `layout:` section and no shop, so nothing was added there.

## Human follow-up
Judge the icons in game at panel scale (0.35 block) and shelf scale; the three Handy Fingers hands differ mainly by the number of raised fingers and the cuff colour, check they are told apart at that size.
