# Floating world panel (TextDisplay + ItemDisplay + Interaction per row)

**Target version:** Paper 26.3 (`26.3.build.205-stable`, Java 25, Adventure 5, pack format 97.1). Feature `bakery-core`. Researched 2026-10-18.
"Verified" = read in the 26.3 Javadocs or the vanilla wiki. "Approx." = from vanilla knowledge, so calibrate in game and keep the number in config.

## Summary
- **Recommendation: a world panel built from displays**, as the brief asks. Per row: one `TextDisplay` (background colour carries the affordable/unaffordable look), one `ItemDisplay` icon and one `Interaction` hitbox. Add one header `TextDisplay`. All entities are per-player (see `per-player-visibility.md`).
- No custom panel texture is needed: `TextDisplay#setBackgroundColor(Color)` with an ARGB colour gives a flat panel. **The model-designer does not need a panel background asset.**
- Update a row only when its numbers change (cookies cross the cost, owned count changes, cost changes). Compare with the last rendered state and skip identical updates.
- An inventory GUI or the Dialog API do not fit "floating next to the cookie"; they are useful later for upgrades (see the comparison).

## Version notes
- `TextDisplay` / `ItemDisplay` / `Interaction` API is the same across 1.20.x to 26.3; the 26.3 Javadoc signatures below are verified.
- `Component` text: Adventure 5 (`TextDisplay#text(Component)`). Use MiniMessage with placeholders as in `docs/minimessage.md`; messages live in `messages.yml`.
- Text displays honour `<sprite>` and custom `<font>` like chat. Not needed here.

## TextDisplay options (verified signatures, defaults from the vanilla wiki)
| Need | Call | Default / notes |
|---|---|---|
| Text | `void text(@Nullable Component)` | Adventure Component, multi-line with `<newline>` |
| Background | `void setBackgroundColor(@Nullable Color)` | `org.bukkit.Color.fromARGB(a, r, g, b)`. Vanilla default is `0x40000000`. Alpha below about 26 is treated as fully transparent. `null` = default background. |
| Chat-style background | `void setDefaultBackground(boolean)` | `true` uses the chat background colour instead. Leave `false`. |
| Wrap width | `void setLineWidth(int)` | In **pixels**. Default 200. Lines longer than this wrap. |
| Alignment | `void setAlignment(TextDisplay.TextAlignment)` | `CENTER` (default), `LEFT`, `RIGHT` (verified constants) |
| See-through | `void setSeeThrough(boolean)` | `false` default: the text is hidden behind blocks. `true` shows it through walls. Keep `false`. |
| Shadow | `void setShadowed(boolean)` | default `false` |
| Text opacity | `void setTextOpacity(byte)` | `-1` = opaque. Values 0 to 3 are opaque, 4 to 26 are discarded by the client. Use at least 27 for a visible fade (use `(byte) 180`; `255` is `-1`). **Not interpolated** by the client. |
| Billboard | `void setBillboard(Display.Billboard)` | `FIXED` (default), `VERTICAL`, `HORIZONTAL`, `CENTER` |
| Brightness | `void setBrightness(@Nullable Display.Brightness)` | `new Display.Brightness(15, 15)` = full-bright (not darkened by the world light). |
| Scale | `setTransformation(new Transformation(translation, leftRot, scale, rightRot))` | Text is tiny at scale 1. **Approx.:** one font pixel is 1/40 block, a text line is about 9 px apart. |
| Range | `void setViewRange(float)` | multiplier on the 64-block default |
| Glow outline (ItemDisplay) | `Entity#setGlowing(boolean)` + `Display#setGlowColorOverride(@Nullable Color)` | handy for "affordable" |

## Layout and sizing
Set the panel in the station's own yaw (the admin's facing when running `/bakery station set`), not billboarded:
- Use `Billboard.FIXED` and set the entity yaw. An `Interaction` hitbox is an **axis-aligned square** and cannot rotate, so a panel that turns with the viewer (`VERTICAL`) would no longer match its hitboxes. Per-player panels do not need to follow players: each player's panel is spawned facing the same direction as the station.
- If the station's yaw is not a multiple of 90 degrees, hitboxes will not line up exactly. Option: round the panel yaw to the nearest 90 degrees when the station is created (`stations.yml` stores the rounded yaw) and mention it in the admin message.
- Approx. text size: width in blocks = `lineWidthPx * 0.025 * scale`. A row with `lineWidth 110` and scale `0.55` is about 1.5 blocks wide. Row height with 2 lines and scale 0.55 is about `2 * 9 * 0.025 * 0.55 = 0.25` block plus padding. **Calibrate in game** and read the numbers from config (`station.panel.*`), never hard-code them.
- The text background hugs the **longest line**, it is not as wide as `lineWidth`. To keep both rows the same width, pad shorter text (same line count, similar length), or use the same template for every row. Do not rely on `setLineWidth` to widen the background.
- Rows stack vertically with a small gap. Row `i` is at `panelBaseY - i * rowPitch`.
- Icon on the left of each row: an `ItemDisplay` with `ItemDisplayTransform.FIXED` (flat model from `item/generated`, like an item frame, 1 block at scale 1), scale about 0.35 to match row height. The item is `ItemStack.of(Material.COOKIE)` plus `ITEM_MODEL` `cookieclicker:building/villager_baker` or `.../wheat_farm`. Same fallback rule as the cookie (no pack: plain vanilla item, see Hand-off).
- Hitbox per row: `Interaction` with `width = rowWidth` and `height = rowHeight`; position is the **bottom centre** of the box (approx., check). Because `width` applies to X and Z, a 1.5-wide row box is also 1.5 deep: keep it in front of or at the panel plane and away from the cookie hitbox (leave `cookieHitboxWidth/2 + rowWidth/2 + gap` between centres).
- Hitboxes stay static. Do not make them taller than the visible row, or neighbouring rows steal clicks.

```java
TextDisplay row = world.spawn(rowLoc, TextDisplay.class, t -> {
    t.setPersistent(false);
    t.setVisibleByDefault(false);
    t.addScoreboardTag("cookieclicker");
    t.setBillboard(Display.Billboard.FIXED);
    t.setAlignment(TextDisplay.TextAlignment.LEFT);
    t.setLineWidth(cfgLineWidth);
    t.setSeeThrough(false);
    t.setShadowed(true);
    t.setBrightness(new Display.Brightness(15, 15));
    t.setBackgroundColor(Color.fromARGB(0xB0, 0x20, 0x20, 0x20));  // from config
    t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(s, s, s), new AxisAngle4f()));
    t.text(rowComponent);
});
owner.showEntity(plugin, row);
```

## Affordable vs unaffordable
Options, cheapest first. Use 1 + 2 + 3; keep 4 optional.
1. **Text colours (MiniMessage in `messages.yml`).** Affordable: name in `<green>`, cost in `<gold>`. Unaffordable: everything `<gray>`, cost in `<red>`, name with `<strikethrough>` only if it helps readability (it does not need to). Two templates per row: `panel.row.affordable` and `panel.row.unaffordable`.
2. **Background colour.** Affordable: a warm tint (for example `ARGB B0 3A 2A 10` with a gold feel). Unaffordable: dark grey (`B0 20 20 20`). Changing it is one metadata update.
3. **Icon brightness.** Unaffordable: `setBrightness(new Display.Brightness(4, 4))` (dim). Affordable: `(15, 15)`.
4. **Glow:** `itemDisplay.setGlowing(true)` with `setGlowColorOverride(Color.fromRGB(...))` while affordable. Looks strong; test it with the pack's item models, and the outline also shows through walls for that player. Optional.
5. A gentle "ready" pulse (scale 1.0 to 1.1 with interpolation) is possible but needs a task; skip for this slice.
No extra item model is needed. If the model-designer later wants a locked icon, that is a CONTRACT CHANGE (not proposed).

## Refresh strategy
- Keep, per row and per player, the last rendered state: `(owned, cost, cps, affordable)`. A refresh task (every 10 to 20 ticks, or directly after a purchase / cookie gain) recomputes the state and calls `text(...)`, `setBackgroundColor(...)`, `setBrightness(...)` **only if it changed**.
- Cookie count changes every second, but the **affordable flag** changes rarely. Rebuilding the component on every tick is the thing to avoid. Re-render when `affordable` flips, or `owned` or `cost` change. The CPS shown on a row only changes on purchase.
- The HUD (action bar) shows the cookie total; the panel does not. Do not put the cookie total on the panel.
- Build Components from the parsed MiniMessage templates with placeholders (`Placeholder.component` / `Formatter.number`); do not concatenate. Cache the parsed template, only resolve placeholders on change. Numbers use the shared number formatter with the suffixes from `messages.yml`.
- A click on a row: re-check `owner cookies >= cost` server-side, buy, then refresh that panel immediately.

## Alternatives, compared
| | World panel (recommended) | Inventory GUI | Dialog API (`io.papermc.paper.dialog.Dialog`, since 1.21.7) |
|---|---|---|---|
| Fits "floating next to the cookie" | yes | no, it is a screen | no, it is a modal screen |
| Player keeps clicking the cookie meanwhile | yes | no (screen open) | no |
| Live update while open | yes, per entity | yes | not live; rebuild and show again |
| Per-player isolation | by entity visibility | automatic | automatic |
| Custom pack art needed | no | optional (negative-space title) | no |
| Effort and risk | medium: entity lifecycle and hitboxes | low | low |
| Best use later | status/ambient info | shop with many items and upgrades | confirmations, settings, text input |
Recommendation: the world panel for the 2 building rows now (as the brief requires). For many buildings and upgrades later, a chest GUI opened from a panel button scales better. Dialogs are a good fit for settings or "reset" confirmations. Dialog button callbacks default to one use and 12 hours lifetime (Paper docs).

## Pitfalls
- A panel behind the cookie hitbox is unclickable: keep the hitboxes apart.
- `TextDisplay` backgrounds with alpha under about 26 vanish; use at least `0x40`.
- A `TextDisplay` with `see_through = false` can be hidden by blocks in front of it; place the panel clear of walls.
- Text lighting: without brightness override, text darkens in dark places. Use `(15, 15)`.
- Line width is in pixels, wrapping happens unexpectedly if the template text is wider than `lineWidth`; localised/formatted large numbers ("1.2 million") are wide.
- `setTextOpacity(byte)`: `(byte) 255` is `-1` (opaque). Values 4 to 26 are discarded.
- Never put player-supplied text into the MiniMessage string. Player names are inserted with `Placeholder.unparsed` (the panel shows none now).
- Entity operations are main-thread only; never touch the panel from the async save task.

## Sources
- TextDisplay Javadoc 26.3: https://jd.papermc.io/paper/26.3/org/bukkit/entity/TextDisplay.html, [TextAlignment](https://jd.papermc.io/paper/26.3/org/bukkit/entity/TextDisplay.TextAlignment.html)
- Display / Brightness / Billboard: https://jd.papermc.io/paper/26.3/org/bukkit/entity/Display.html
- Interaction: https://jd.papermc.io/paper/26.3/org/bukkit/entity/Interaction.html
- Vanilla wiki, Display (text display field defaults): https://minecraft.wiki/w/Display
- Paper docs, display entities: https://docs.papermc.io/paper/dev/display-entities/
- Paper docs, dialogs: https://docs.papermc.io/paper/dev/dialogs/
- MiniMessage format: https://docs.papermc.io/adventure/minimessage/format/

## Hand-off

**model-designer** (pipeline: plain pack `resourcepack/`)
- Two flat icon models: item model definitions `assets/cookieclicker/items/building/villager_baker.json` and `.../building/wheat_farm.json`, models `models/item/building/villager_baker.json` and `.../wheat_farm.json` (parent `minecraft:item/generated`), textures `textures/item/building/villager_baker.png` and `.../wheat_farm.png`. Pixel art 16x16 (or 32x32), crisp, readable at about 0.35 block on screen. Contract IDs: `cookieclicker:building/villager_baker`, `cookieclicker:building/wheat_farm`.
- **No panel background texture is needed** (flat TextDisplay background colour). Do not build one.
- No locked/unaffordable variant models; the dimming is done with brightness.

**sound-designer**: `cant_afford` and `purchase` are played by the coder when a row is clicked (no panel-specific sounds).

**vfx-designer**
- Effect `purchase` plays at the clicked row (a location the coder passes) for the buyer only. Short-lived entities: pattern in `per-player-visibility.md`.
- Floating text rise: use a translation interpolation (for example +0.8 block over 20 ticks, `setInterpolationDelay(0)`, `setInterpolationDuration(20)`); `setTeleportDuration` also works (range 0 to 59). Fade: text opacity is not interpolated, so step `setTextOpacity` at about 10, 14 and 17 ticks (values at least 27), or shrink the scale to 0 at the end (scale is interpolated).

**plugin-coder**
- Panel builder (header + 2 rows), config under `station.panel.*` (row pitch, line width, text scale, colours, icon scale, hitbox sizes, offsets from the station). Row definitions come from `buildings.yml` (names and icons by building id).
- Two templates per row in `messages.yml` (`panel.row.affordable`, `panel.row.unaffordable`), placeholders `name`, `owned`, `cost`, `cps`.
- Last-rendered-state cache per row; update only on change.
- Fallback when the viewer has no pack: item icons use vanilla materials from config (for example `building.villager_baker.fallback-material: BREAD`, `building.wheat_farm.fallback-material: WHEAT`).
- Click handling via the shared `ClickRouter` (see `display-cookie.md`); re-validate affordability server-side.

CONTRACT CHANGES (proposed): add to config `station.panel.*` and `buildings.yml` keys `fallback-material` (vanilla item shown when the pack is not loaded). Add messages `panel.row.affordable`, `panel.row.unaffordable`, `panel.header`.
