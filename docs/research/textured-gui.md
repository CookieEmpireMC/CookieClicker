# Textured chest GUI (Recipe Book) - research note

## Target version
Paper `26.3.build.205-stable` (`pom.xml`), client 26.3, resource pack format 97 (`min_format`/`max_format` 97, see `docs/research/resource-pack.md`). Researched 2026-10-25.

Confidence tags: **[V]** verified in a source linked below, **[K]** well-known vanilla/community behaviour I could not re-confirm in a 26.x source (the rendering constants come from vanilla's `AbstractContainerScreen`/font code and the Oraxen guide), **[U]** unverified, needs an in-game check by the model-designer/tester. The pixel offsets are all **[K]**: render one screenshot per row count before calling the asset final.

## Summary
- Open a plain generic chest (3-6 rows) with `MenuType.GENERIC_9XN.builder().title(component).build(player)`, then `view.open()`. The title is a Component whose text is **private-use glyphs in font `cookieclicker:gui`**, coloured white: first a negative space (-8), then the background glyph.
- The background is one bitmap glyph per row count, at most 256x256 px (a 6-row chest is 176x222), `height` = texture pixel height (1:1 mapping), `ascent` 13. That puts the glyph's top-left exactly on the container's top-left, over the vanilla chest background, and under the slots and items.
- The vanilla title is not hidden; the glyphs are the title (no plain text, or a short overlay text after a negative space). The "Inventory" label cannot be removed by the title; design the lower part of the texture to sit behind it (or override `container.inventory`, see Pitfalls).
- Cancel every click and drag in the view, track the view in a map, update slots with `getTopInventory().setItem(...)`.
- Skip `tooltip_style`; hide the vanilla lines with `tooltip_display` and write your own `item_name` + `lore`.
- Everything is in GUI pixels, so alignment holds at every GUI scale. Only a pack that replaces the same chest textures or changes fonts globally can disturb it.

## Version notes
- `MenuType` builders exist since 1.21.x and are the current API on 26.1/26.2/26.3. `Bukkit.createInventory(holder, size, Component)` is still there, not deprecated [V]. The String-title overloads are deprecated.
- `InventoryView#setTitle(String)` is deprecated since 1.21.1 ("poorly defined and broken"); there is no `title(Component)` setter on 26.3 [V]. **A title cannot be changed in place: reopen the GUI** (e.g. page change) or keep the title static and change items only.
- Pack format 97 uses `min_format`/`max_format`; `pack_format` is absent (existing `pack.mcmeta` is right).
- Font changes in 1.21.4 - 26.3 are only Unifont updates (default font); the bitmap/space providers are unchanged [V, wiki Font].
- 1.21-era tutorials that use `Bukkit.createInventory(null, 54, String)` or legacy `§` titles are outdated; use Components.

## Pack file layout
Namespace is `cookieclicker` (contract font `cookieclicker:gui`).

```
resourcepack/assets/cookieclicker/
  font/gui.json
  textures/font/gui/recipe_book_3.png      176 x 168   (3 rows)
  textures/font/gui/recipe_book_4.png      176 x 186
  textures/font/gui/recipe_book_5.png      176 x 204
  textures/font/gui/recipe_book_6.png      176 x 222
```
The contract names one glyph `recipe_book`; the config has `recipes.gui.rows`, so ship one texture per allowed row count (or only the one the default uses, 3 to 6 are cheap) and name the glyphs `recipe_book_<rows>`. The model-designer lists codepoints in `design/recipe-book-gui.md`.

Chest geometry (vanilla `generic_54`; `imageHeight = 114 + rows*18`, `imageWidth = 176`) [K]:

| rows | image height (texture height) | title (x,y) | top slot rows start y | player inv starts y | hotbar y | "Inventory" label y |
|---|---|---|---|---|---|---|
| 3 | 168 | 8, 6 | 18 | 86 | 144 | 74 |
| 4 | 186 | 8, 6 | 18 | 104 | 162 | 92 |
| 5 | 204 | 8, 6 | 18 | 122 | 180 | 110 |
| 6 | 222 | 8, 6 | 18 | 140 | 198 | 128 |

Slot item origin (16x16 icon top-left, relative to container top-left): `x = 8 + 18*col`, `y = 18 + 18*row`; the 18x18 slot frame starts 1 px up-left of that (`7+18*col`, `17+18*row`). Raw slot index of the top inventory = `row*9 + col`. Player inventory rows are at `x = 8 + 18*col`, `y = (imageHeight-82) + 18*row`, hotbar at `y = imageHeight-24`.

`font/gui.json` (example, space and bitmap providers):
```json
{
  "providers": [
    { "type": "space", "advances": {
        "": -1, "": -2, "": -4, "": -8,
        "": -16, "": -32, "": -64, "": -128,
        "": -192, "": 1
    } },
    { "type": "bitmap", "file": "cookieclicker:font/gui/recipe_book_3.png",
      "ascent": 13, "height": 168, "chars": [""] },
    { "type": "bitmap", "file": "cookieclicker:font/gui/recipe_book_4.png",
      "ascent": 13, "height": 186, "chars": [""] },
    { "type": "bitmap", "file": "cookieclicker:font/gui/recipe_book_5.png",
      "ascent": 13, "height": 204, "chars": [""] },
    { "type": "bitmap", "file": "cookieclicker:font/gui/recipe_book_6.png",
      "ascent": 13, "height": 222, "chars": [""] }
  ]
}
```
(Codepoints are suggestions; only the designer's file is binding. Keep the ingredient glyphs, used in chat/HUD, in a separate font `cookieclicker:ingredient` so negative spaces never mix in.) The `chars` array has one string per texture row, here one row with one char, so the whole PNG is the glyph.

Rules behind the numbers:
- **Max size** [V wiki Font]: a glyph must not exceed 256x256 px. A 176x222 background fits. Keep one glyph per PNG.
- **height** is the rendered height in GUI pixels and acts as the scale (`height / PNG height`). Use `height` = PNG height so every texel is exactly one GUI pixel at scale 1. [V wiki: height scales; K: exact formula]
- **ascent** must be `<= height` [V mcfonts docs, ascent cannot exceed height]. Vanilla text is drawn with its top at the label y (6) and baseline 7 below; a bitmap glyph's top is `labelY + 7 - ascent`, so `ascent 13` gives `6 + 7 - 13 = 0` = container top. [K, matches the Oraxen guide: ascent 13, height 256, shift -8]
- **Width**: a glyph's width is its right-most column with alpha > 0 [V wiki] and its advance is that width plus 1 (the cursor moves `176 + 1 = 177`) [K]. Keep the right column of the PNG opaque (the vanilla chest background does fill it) so width is deterministic.
- **Horizontal**: the title starts at container `x = 8`, so a `-8` space first puts the glyph's left edge on the container's left edge.
- **Colour** [V wiki: white glyphs can be tinted]: the title text is drawn in 0x404040 (dark grey) unless the Component sets a colour; that would darken the texture. Set `NamedTextColor.WHITE` on the whole title. Never use colour codes inside the background glyph's component.
- **Draw order** [K]: vanilla chest background, then labels (the title and "Inventory"), then slot items and slot highlights. So the glyph covers the vanilla chest art and sits under items, tooltips and the cursor item. Transparent pixels of the PNG show the vanilla background through.

## Plugin side
Title Component (Adventure 5, `Key.key("cookieclicker", "gui")`):

```java
Component title = Component.text()
    .append(Component.text(""))   // -8 space, then 6-row background
    .style(Style.style().font(Key.key("cookieclicker", "gui")).color(NamedTextColor.WHITE))
    .build();
```
In MiniMessage the same is `<white><font:cookieclicker:gui></font>` (see `docs/minimessage.md`; the characters must be the literal codepoints, not tags).

Optional readable title overlay: after the background, step back by the advance, then add normal text in the default font:
`"" + bg + "\uF8??"` where the back-step is `-(176 + 1) + 8 = -169` to land at the vanilla title position (x = 8). Build -169 as `-128 -32 -8 -1` using the space glyphs above. Then append `Component.text("Recipe Book", NamedTextColor.DARK_GRAY).font(Key.key("minecraft","default"))`. [K, verify with a screenshot.] The plain text is also the pack-less fallback.

Open it (MenuType, 26.3 [V Javadoc: `MenuType.GENERIC_9X1..6`, `MenuType.Typed#builder()`, `InventoryViewBuilder#title(Component)`, `#build(Player)`, `InventoryView#open()`, `#getTopInventory()`):

```java
InventoryView view = MenuType.GENERIC_9X6.builder()
    .title(title)
    .build(player);           // GENERIC_9X3 / 9X6 use a LocationInventoryViewBuilder: do not call .location(...)/checkReachable
view.open();
open.put(player.getUniqueId(), view);
```
Source for the pattern: Paper "Menu Type API" docs (generic 9x6 example, `.title(...).build(p)`, `view.open()`). The Javadoc lists `GENERIC_9X3`/`GENERIC_9X6` as `LocationInventoryViewBuilder`; omit `location`, so the menu is not bound to a block. [U: confirm `build(player)` works without a location in a quick test; the docs example does exactly this for 9X6.]

Choice: use **MenuType**. It binds the view to the player, needs no holder and gives `InventoryView` with `title()`. `Bukkit.createInventory(holder, size, Component)` also works [V], is simpler if you want a custom `InventoryHolder` to recognise your GUI, and has the same title/font behaviour. Either way identify your GUI through a map `UUID -> InventoryView` (MenuType) or the holder (createInventory), never through the title string. Remove the entry on `InventoryCloseEvent` and `PlayerQuitEvent`.

### Making it read-only (cancel everything)
Rule: for any player with the Recipe Book view open, cancel **every** `InventoryClickEvent` and `InventoryDragEvent` of that view, including clicks in the player's own inventory (shift-click and double-click can move items between inventories from a bottom-side click). [V Javadoc facts below]

- `InventoryClickEvent`: `getClick()` (insulated from other plugins), `getAction()`, `getHotbarButton()` (0-8 for `NUMBER_KEY`, -1 for an off-hand swap), `getRawSlot()`, `getClickedInventory()` (null for a click outside), `isShiftClick()`. Slot changes by the event can be overwritten; cancel and set items yourself. Do not call `closeInventory`/`openInventory` inside the handler: `runTask` it.
- `InventoryDragEvent`: drags do not fire the click event; cancelling blocks all `getNewItems()` changes. `getRawSlots()` has top-inventory slots `< topSize`.
- Cases to cover by cancelling the whole event: left/right click, shift-click (`MOVE_TO_OTHER_INVENTORY`), number keys, offhand swap (F), drop (Q / Ctrl-Q), double-click collect (`COLLECT_TO_CURSOR`, which can pull from the top inventory), middle click (creative clone), click outside (would drop the cursor).
- Handler: `ignoreCancelled = false`, priority `HIGHEST` (or `LOW` to win early; whichever the project's convention), plus `event.setCancelled(true)` and handle the recipe slot logic in the same handler (`rawSlot < topSize` and left click -> buy). After cancelling a click, the client may show a ghost cursor/slot: call `player.updateInventory()` on the next tick if a test shows desync. [U]
- Creative players: `InventoryCreativeEvent` extends the click event; the same handler cancels it. [U: check in game]
- Also consider `InventoryClickEvent` for the **bottom** inventory with a cursor item: cancelling prevents the cursor item being dropped into the GUI.

### Updating a slot in place
`view.getTopInventory().setItem(slot, item)` sends the single slot packet; no reopen, no flicker, the title stays. Use it for the buy/ripe state changes (locked, affordable, owned, cost lore), on the main thread. Do not use `setTitle`. For a per-second affordability refresh, update only slots whose look changed and stop the task on close (see the performance rule: no idle repeating tasks).

### Item tooltips on 26.3
- Paper data components (all [V]): `DataComponentTypes.TOOLTIP_DISPLAY` (`Valued<TooltipDisplay>`), `TOOLTIP_STYLE` (`Valued<Key>`), `ITEM_NAME`, `CUSTOM_NAME` (`Valued<Component>`), `LORE` (`Valued<ItemLore>`), `ITEM_MODEL` (`Valued<Key>`).
- `TooltipDisplay.Builder`: `hideTooltip(boolean)`, `addHiddenComponents(DataComponentType...)`, `hiddenComponents(Set<DataComponentType>)`; read side `hideTooltip()`, `hiddenComponents()`. Data component JSON: `tooltip_display: {hide_tooltip: bool, hidden_components: ["minecraft:enchantments", ...]}` [V wiki].
- Recommended: set `ITEM_NAME` (not italic by default; `CUSTOM_NAME` is italic unless overridden) and `LORE` for text; hide the vanilla lines the base item would add, e.g. a base item with attribute modifiers, enchantments, `minecraft:potion_contents`, `minecraft:dyed_color`, `minecraft:jukebox_playable`, `minecraft:trim`. Pick a base material with no lines of its own (e.g. `PAPER`, with `ITEM_MODEL` for the look) so you hide nothing. Never use `hide_tooltip: true` on clickable icons (they need a name/cost on hover); do use it on pure filler/decoration slots.
- Lore is italic and purple-less by default only for `CUSTOM_NAME`/lore styled text: lore lines are italic dark purple by default [K]; set `.decoration(TextDecoration.ITALIC, false)` and explicit colours on every line (`ItemLore` of Components; MiniMessage-built).
- **`tooltip_style`: not worth it for this feature.** It needs two sprites per id at `assets/<ns>/textures/gui/sprites/tooltip/<id>_background` and `_frame`, and an unknown id shows the missing texture instead of falling back [V wiki]. That is an extra asset pair, and a failure mode if the pack is missing or older. Skip it. If the user later wants a themed tooltip, set `TOOLTIP_STYLE` only when the player's pack status is loaded.

### Pack-less / pack not loaded
PUA glyphs in an unloaded custom font render as missing-glyph boxes [U]. Track `PlayerResourcePackStatusEvent` (`SUCCESSFULLY_LOADED`) per player; if not loaded, use the plain text title (fallback requirement in the brief) and fallback items from `recipes.gui.fallback-items`.

## Alignment: GUI scale and other packs
- Container background, slots, labels and our glyph are all laid out in **GUI pixels** and scaled by the same integer GUI scale (1-4, or auto), so the background stays on the slots at every scale [K, same coordinate space in `AbstractContainerScreen`]. Test scale 2, 3, auto and at least one window size where the chest does not fit (small windows force a lower scale).
- Glyph scaling is `height / texture height`; with `height` = PNG height it is 1:1, so no resampling. Do not draw the texture at another scale.
- Other packs: (1) a pack that **redefines `minecraft:default`** only matters if our title uses that font (the overlay text); the background glyph uses our own font. (2) A pack replacing the vanilla `generic_54.png` only changes what is behind transparent pixels; make our PNG opaque over the whole 176 x N area to avoid mixing. (3) Another pack defining `cookieclicker:gui` (collision) is unlikely; the namespace is ours. (4) Packs that change title/label colour or position (mods, Optifine-style GUI tweaks, some client mods) shift the glyph; not fixable.
- The "Inventory" label (drawn at `y = imageHeight - 94`, dark grey 0x404040, no shadow [K]) is drawn **over** the background glyph. Hiding it is only possible via a global lang override `container.inventory` in `assets/minecraft/lang/en_us.json` (blank string), which affects every chest screen for all players and clashes with other packs: **not recommended**. Instead, paint the area behind the label so the dark-grey text stays readable (the vanilla light grey #C6C6C6 behind it, as in the original texture).

## Pitfalls
- The Recipe Book title has no shadow and dark default colour; forget `WHITE` and the whole background is darkened.
- Advance width = last opaque column + 1; a PNG with a transparent right column shifts every following glyph and the back-step arithmetic. Re-check with a screenshot after any texture edit.
- Negative-space glyphs in *chat* are clamped to 0 (wiki), but in container titles they work [K, widely used: Oraxen, Nexo, ItemsAdder]. Do not use the GUI font for chat.
- Do not put the glyph in `item_name`/lore: tooltip rendering would shift and the text-wrap breaks.
- PUA range: pick codepoints in `U+E000-U+F8FF`; avoid ones the default font's new PUA glyphs use only if you ever mix the default font into the same Component (the GUI font does not fall back to default).
- Reopening a GUI (page switch) resets the cursor and re-sends the title; avoid flicker by changing slots instead.
- Never rely on the title text to recognise the GUI.
- Keep the PNG within 256x256 and do not use the pack's `pack.png`-style compression of the alpha channel; export 8-bit RGBA.

## Sources
- Minecraft Wiki, Font: https://minecraft.wiki/w/Font | bitmap `height` default 8 and scaling, `ascent`, glyph max 256x256, glyph width = right-most opaque column, white glyphs tintable, space provider `advances`, negative values clamped in chat, no 1.21.4-26.3 provider changes.
- mcfonts docs (bitmap provider): https://mcfonts.readthedocs.io/latest/providers/bitmap.html | ascent cannot exceed height; vanilla height 8 / ascent 7.
- Oraxen custom GUI: https://docs.oraxen.com/configuration/glyphs/custom-gui | ascent 13, height 256, textures not above 256x256, `<shift:-8>`; and https://docs.oraxen.com/usage/tutorials/f3g7k (shift in title, adjust to taste).
- Paper Menu Type API: https://docs.papermc.io/paper/dev/menu-type-api/ | `MenuType.X.builder().title(...).build(player)`, `view.open()`, `getTopInventory()`.
- Paper 26.3 Javadocs: https://jd.papermc.io/paper/26.3/org/bukkit/inventory/MenuType.html (GENERIC_9X1..6, `create(HumanEntity, @Nullable Component)`), https://jd.papermc.io/paper/26.3/org/bukkit/inventory/InventoryView.html (`title()`, `setTitle(String)` deprecated since 1.21.1), https://jd.papermc.io/paper/26.3/org/bukkit/Bukkit.html (`createInventory(holder, int, Component)` not deprecated), https://jd.papermc.io/paper/26.3/org/bukkit/event/inventory/InventoryClickEvent.html, https://jd.papermc.io/paper/26.3/org/bukkit/event/inventory/InventoryDragEvent.html, https://jd.papermc.io/paper/26.3/io/papermc/paper/datacomponent/item/TooltipDisplay.html and `.Builder.html`, https://jd.papermc.io/paper/26.3/io/papermc/paper/datacomponent/DataComponentTypes.html (TOOLTIP_DISPLAY, TOOLTIP_STYLE, ITEM_NAME, CUSTOM_NAME, LORE, ITEM_MODEL).
- Minecraft Wiki, Data component format: https://minecraft.wiki/w/Data_component_format | `tooltip_display` fields, `tooltip_style` sprite paths and missing-texture behaviour, `item_name` vs `custom_name` italics.
- Minecraft Wiki, Resource pack: https://minecraft.wiki/w/Resource_pack | pack format 97.1 for 26.3.
- Not found: a 26.x source for the container label coordinates (6, 8, imageHeight-94) and the +1 advance; taken from vanilla code knowledge [K].

## Hand-off
**model-designer** (plain pack, no ItemsAdder/ModelEngine for this GUI):
- Create `resourcepack/assets/cookieclicker/font/gui.json` as above with space glyphs (`-1,-2,-4,-8,-16,-32,-64,-128`, positive `+1` helper) and one bitmap glyph per row count the config may use (default rows: confirm with the brief, 3-6 ok).
- Textures `textures/font/gui/recipe_book_<rows>.png`, 8-bit RGBA, **width 176**, height `114 + 18*rows` (168/186/204/222), opaque over the whole area (right column opaque), slot frames at `7+18c, 17+18r` (18x18), the player inventory slot frames at the rows in the table, area behind the "Inventory" label light grey. `ascent` 13, `height` = PNG height, glyph PUA codepoints listed in `design/recipe-book-gui.md` with the exact back-step values.
- Document the codepoints, the offsets (`-8` first; `-169` to return to the vanilla title x) and a screenshot at GUI scale 2/3/auto in `design/recipe-book-gui.md`. Mark `Status: final` only after the in-game alignment check.
- Ingredient glyphs go into a separate font `cookieclicker:ingredient`; icons are item models (`ITEM_MODEL` keys) so they render in slots.
**sound-designer:** nothing for this note (page-turn sound is in the brief's contract).
**vfx-designer:** nothing.
**plugin-coder:**
- Title builder as above (Style font + WHITE), MenuType open, map of views, cancel-all click/drag handler (also `InventoryCreativeEvent`), slot updates via `getTopInventory().setItem`, plain-text title fallback when the pack is not loaded, `ITEM_NAME` + `LORE` + `TOOLTIP_DISPLAY` (no `tooltip_style`). Glyph strings and fonts from `design/recipe-book-gui.md`, not hard-coded guesses; keep them in one constants class.
- Gate the textured title on the player's pack status; cache the pre-built title Components.
**Proposed contract IDs:** font `cookieclicker:gui`; glyph names `recipe_book_3`..`recipe_book_6` (instead of the single `recipe_book`); font `cookieclicker:ingredient` for the 5 ingredient glyphs; no `tooltip_style` ID.
