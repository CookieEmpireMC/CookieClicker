# Recipe Book GUI, ingredient glyphs and recipe icons

**Status: placeholder.** Generated art, judged from renders only (mock-up `design/scripts/previews/recipe_book_mock.png`). The GUI alignment is **not yet verified in game** (see "Needs an in-game screenshot"). Original work by Pixel Pat.

Plain resource pack, namespace `cookieclicker`, Paper 26.3 (pack format 97, unchanged). Research: `docs/research/textured-gui.md`.

## Generators (run by `mise run assets`)
`design/scripts/recipe_book_gui.py` (gui font + background), `ingredient_glyphs.py` (ingredient font + atlas), `recipe_icons.py` (recipe_book + recipe icons), `building_icons.py` (chicken_farm icon), `building_world.py` (chicken_farm models). Mock-up: `python design/scripts/lib/render_recipe_book_mock.py`.

## 1. GUI font `cookieclicker:gui` (`font/gui.json`)
Title Component = white, font `cookieclicker:gui`: `` (-8) + `` (background), then optionally `` (-169) and the readable title text in the default font (dark grey). Keep the colour WHITE on the glyph part. Never use this font in chat.

| Char | Meaning |
|---|---|
| `` | `recipe_book`: 4-row chest background, 176x186, `ascent` 13, `height` 186 (1:1), advance 177 |
| `` `` `` `` | -1, -2, -4, **-8** |
| `` `` `` `` | -16, -32, -64, -128 |
| `` `` `` `` | +1, +2, +4, +8 |
| `` `` `` `` | +16, +32, +64, +128 |
| `` | **-169**: back from the end of the background to the vanilla title x (8) |
| `` | -177: back to the container's left edge |

Title string: `""` (background only, plain-text fallback otherwise) or `""` + text. `-169` assumes the glyph advance is 177 (width 176 + 1); if the screenshot shows the text off by n pixels, adjust with the +1/-1 glyphs and tell me.

## 2. Layout of the texture (4 rows; GUI pixels from the container's top-left)
- Book cover y 0..101, parchment y 4..97, bottom cover edge y 99..101. Lower part y 102..185: vanilla-look grey panel. The whole PNG is opaque (right column included).
- Title text box: (8, 6), ~9 px high, on light paper (rule line under it at y 14). A red bookmark ribbon (x 138..142) and a small cookie stamp (x 154..166) sit right of it, y 3..15: keep title text left of x 130.
- "Inventory" label: (8, 92), on a clean light paper strip x 8..167, y 90..97 (the text is dark grey and stays readable).
- **Top slot frames:** 18x18 at (7 + 18c, 17 + 18r), c 0..8, r 0..3. Item/icon origin (16x16) at **(8 + 18c, 18 + 18r)**. Raw slot index `r*9 + c` (0..35 top inventory; 36.. is the player inventory).
- Player inventory frames at (7 + 18c, 103 + 18r), r 0..2; hotbar frames at (7 + 18c, 161). Items there at y +1, as vanilla.

Slot map (raw slot -> pixel origin of the 16x16 icon):
| Row | Slots | y | x for columns 0..8 |
|---|---|---|---|
| 0 | 0-8 | 18 | 8, 26, 44, 62, 80, 98, 116, 134, 152 |
| 1 | 9-17 | 36 | same |
| 2 | 18-26 | 54 | same |
| 3 | 27-35 | 72 | same |
Suggested use: the 7 recipes in row 1 columns 1..7 = slots **10-16** (butter 10, brown_sugar 11, chocolate_chips 12, oatmeal 13, hazelnut 14, macadamia 15, caramel 16); filler or an info item elsewhere (config `recipes.gui.slots` decides). Palette: parchment `#F8EDC8 #EFDDAB #E2C98E #CDAE74 #B38F5C`, leather `#9A6240 #7C4B32 #5E3A2A #43271F`, gold `#FFE48A #E8B544 #B8812C`, ribbon `#E0675A #BC3F3F #8A2A32`, vanilla greys `#FFFFFF #C6C6C6 #8B8B8B #373737`.

## 3. Ingredient font `cookieclicker:ingredient` (`font/ingredient.json`, no negative spaces)
One bitmap provider, atlas `textures/font/ingredients.png` (80x16, five 16x16 cells), `ascent` 7, `height` 8 (2x downscale, like the HUD cookie), 1 px margin top and bottom.
| Char | Ingredient | Look |
|---|---|---|
| `` | flour | white sack, rope tie, kraft badge |
| `` | milk | bottle, blue cap and label |
| `` | sugar | three sugar cubes |
| `` | chocolate | bar with 4 squares, gold foil, bitten corner |
| `` | eggs | cream egg |
MiniMessage: wrap in white so the colours stay: `<white><font:cookieclicker:ingredient></font></white> <gray>200`. (Glyph colours are multiplied by the text colour.)

## 4. Item models (flat `item/generated`, 32x32)
| `ITEM_MODEL` key | Texture | Look |
|---|---|---|
| `cookieclicker:upgrade/recipe_book` | `item/upgrade/recipe_book.png` | shelf icon: leather book, gold frame, cookie emblem, ribbon |
| `cookieclicker:recipe/butter` | `item/recipe/butter.png` | butter block with paper label |
| `cookieclicker:recipe/brown_sugar` | | open sack with brown sugar heap |
| `cookieclicker:recipe/chocolate_chips` | | five chocolate chips |
| `cookieclicker:recipe/oatmeal` | | blue bowl of oats, spoon, oat stalk |
| `cookieclicker:recipe/hazelnut` | | two hazelnuts |
| `cookieclicker:recipe/macadamia` | | three macadamia nuts, one cracked |
| `cookieclicker:recipe/caramel` | | caramel jar with a drip |
| `cookieclicker:recipe/locked` | | blank page with a padlock (the "bake N to unlock" look) |
"Baked!" has no own model: give the recipe model an enchantment glint (`enchantment_glint_override`) in code. Suggested fallback materials: recipe_book WRITABLE_BOOK, locked BARRIER or GRAY_DYE, butter HONEYCOMB, brown_sugar SUGAR, chocolate_chips COCOA_BEANS, oatmeal WHEAT, hazelnut COOKIE, macadamia COOKIE, caramel HONEY_BOTTLE.

## 5. Building 7 rename
- Panel icon: `cookieclicker:building/chicken_farm` (32x32 coop with a hen). Fallback material suggestion EGG. The old `building/beehive` files are removed; `VisualFallbacks.java` still maps `beehive` (coder).
- World model: `cookieclicker:building_world/chicken_farm`, custom model data `"1"`, `"2"`, `"3"` (stage 1, 25 owned, 100 owned), same rules as the other buildings (bottom at model y 8, front = north, atlas `textures/item/building_world/chicken_farm.png`). No world model existed for the Beehive, so this one is new. Footprint half extent 0.85 blocks (max over stages; hens and ramp reach z -13.6). No slots in `building-slots.yml` yet (no world builds in this feature).

## Needs an in-game screenshot (research note [verify] items)
1. Background origin: top-left of the glyph on the container's top-left (ascent 13, shift -8). Check at GUI scale 2, 3 and auto.
2. The `-169` back-step puts the readable title at x 8, y 6.
3. Right edge: the next glyph after the background starts 177 px later (width 176 + 1).
4. "Inventory" label readable on the paper strip; slot frames sit exactly on the real slots.
5. `MenuType.GENERIC_9X4` without `.location(...)` opens and the glyph font renders; ingredient glyphs size inline in chat/lore.
When confirmed, add `Status: final` for the GUI part only after a human also likes the art.

## Placeholder / needs a human
Everything is generated placeholder art. A human may repaint: the book texture, the 8 recipe icons and the 5 ingredient glyphs (keep sizes, slot frames and margins). Sounds (page turn, unlock) and effects belong to the sound/vfx designers.
