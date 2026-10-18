# HUD cookie glyph in the action bar (bitmap font provider)

**Target version:** Paper 26.3 (`26.3.build.205-stable`, Adventure 5, MiniMessage with `<font>`), resource pack format 97.1. Feature `bakery-core`. Researched 2026-10-18.

## Summary
- Add a font `cookieclicker:hud` to the pack: one JSON file with one `bitmap` provider that maps the private-use character U+E000 to a small cookie PNG.
- Write the HUD text as MiniMessage: `<white><font:cookieclicker:hud></font> <gold><cookies> <gray>(<cps>/s)`. Send it with `Audience#sendActionBar(Component)` about once per second.
- Players without the pack see an empty "missing glyph" box instead of the icon. Detect that with the pack status event and use a text-only template for them.

## Version notes
| Topic | Status on 26.3 |
|---|---|
| Bitmap provider JSON (`type`, `file`, `ascent`, `height`, `chars`, `filter`) | Unchanged. The wiki lists no 26.x change to the bitmap provider (the 26.x entry on the Font page is about Bedrock's Mojangles font, unrelated). |
| MiniMessage `<font:namespace:name>` | Supported (Adventure 5 on 26.3). Syntax verified in the MiniMessage format docs: `<font:myfont:custom_font>...</font>`. |
| `<sprite>` / `<head>` tags | Exist (26.x). Not used; the glyph font is the contract choice. |
| `<shadow>` tag | Available on 26.x. Optional: the cookie glyph also gets a text shadow in the action bar. |
| `Audience#sendActionBar(Component)` | Standard Adventure; on `Player` it is available. The `Player#sendActionBar(String)` / `BaseComponent` overloads are deprecated, do not use. |

## Pack file layout
```
resourcepack/assets/cookieclicker/
  font/hud.json                            <- font id cookieclicker:hud
  textures/font/hud_cookie.png             <- the glyph image
```
`font/hud.json`:
```json
{
  "providers": [
    {
      "type": "bitmap",
      "file": "cookieclicker:font/hud_cookie.png",
      "ascent": 7,
      "height": 8,
      "chars": [""]
    }
  ]
}
```
Rules (from the vanilla wiki Font page unless noted):
- `file` is a texture resource location including `.png`, relative to `assets/<ns>/textures/`, so it points to `textures/font/hud_cookie.png`.
- `chars` is a list of strings. The image is split evenly by the number of strings (rows) and by the length of the longest string (columns). One string with one char means the whole PNG is the single glyph.
- `height` (default 8) is the rendered glyph height in GUI pixels. The glyph is scaled to it. A 16x16 or 32x32 image at `height: 8` is a clean 2x or 4x downscale. The width follows from the image's aspect ratio.
- `ascent` is the vertical shift of the glyph relative to the text baseline. Vanilla fonts use `ascent 7` with `height 8`. Keep `ascent <= height`, as vanilla does. A larger `ascent` moves the glyph up.
- Glyph width is measured up to the right-most pixel column with alpha greater than 0. Trim transparent columns on the right yourself, or the glyph looks narrower than its box, or has an unwanted gap.
- White (or any colour) glyphs: pure white pixels take the text colour; coloured pixels keep their own colour **multiplied by the text colour**. For a full-colour cookie, wrap it in `<white>` so the colours stay as drawn.
- Each glyph must be at most 256x256 px (the wiki limit).
- Use a character from the private use area (U+E000 to U+F8FF). U+E000 is the contract character. Do not reuse it for something else in this font.

Recommended image: a **16x16 PNG, RGBA, 8-bit**, the cookie art redrawn at 16x16 (or 32x32 if the art needs detail), with a 1-px transparent margin at the top and bottom and no transparent column on the right. Rendered at `height: 8` (2x downscale of 16 px). If the cookie should be a bit bigger than the text, use `height: 10`, `ascent: 8` (non power-of-two ratios can look slightly soft; check in game).

## Plugin side

### Text (messages.yml)
YAML double quotes accept `` escapes. A literal private-use character in a UTF-8 file also works, but it is invisible in most editors, so prefer the escape:
```yaml
hud:
  # glyph part is trusted config; <white> keeps the PNG colours
  with-pack: "<white><font:cookieclicker:hud></font> <gold><cookies></gold> <gray>(<cps> per second)</gray>"
  no-pack:   "<gold>Cookies: <cookies></gold> <gray>(<cps> per second)</gray>"
```
Note: `</font>` closes the font so the following text (and its spacing) uses the default font. `<cookies>` and `<cps>` are placeholders; do not concatenate strings. The glyph and font ID may also be configurable (`hud.font`, `hud.glyph`).

### Sending
```java
Component hud = Text.parse(template,                       // shared MiniMessage instance, see docs/minimessage.md
        Placeholder.unparsed("cookies", formatter.format(cookies)),   // "1.2 million"
        Placeholder.unparsed("cps", formatter.format(cps)));
player.sendActionBar(hud);        // net.kyori.adventure.audience.Audience#sendActionBar(Component)
```
- Refresh **about once per second** (20 ticks). The action bar fades after about 3 seconds, so a 1-second refresh keeps it steady without flicker.
- Only rebuild the Component when `cookies` or `cps` (formatted) changed; otherwise skip the send (or resend rarely so it does not fade).
- Main thread (or any thread: `sendActionBar` only queues a packet), but compute the numbers from the in-memory player state, never from the database.
- Other plugins' action bars overwrite ours and vice versa. Out of scope.

### Detect "pack loaded"
`PlayerResourcePackStatusEvent` (`getID()` returns the pack UUID; `getStatus()` returns `Status`: `ACCEPTED`, `DECLINED`, `DOWNLOADED`, `FAILED_DOWNLOAD`, `SUCCESSFULLY_LOADED`, `INVALID_URL`, `FAILED_RELOAD`, `DISCARDED`; verified). Mark the player as `hasPack` only on `SUCCESSFULLY_LOADED` for **our** pack's UUID. Use `hud.with-pack` for them, `hud.no-pack` otherwise. See `resource-pack.md`.

## Pitfalls
- A character missing in the font renders as a box (or the font's missing glyph) for players without the pack. Hence the fallback template.
- `ascent` and `height` interact: wrong values make the glyph float above or sink below the text. Fix in game by adjusting `ascent` by 1 at a time.
- The `file` path must include the namespace and `.png`; wrong path = the whole provider fails and the font shows boxes (check the client log for "Failed to load font").
- A blank right edge makes the following text look too far away. Crop the image.
- If your pack and another pack both define `cookieclicker:hud`, the one **higher in the pack order** wins (our namespace `cookieclicker` is our own, so another pack only collides if it deliberately reuses it). Never define fonts in the `minecraft` namespace: files there collide with other server packs (such as ItemsAdder's) and JSON files are not merged by the client.
- Text shadow: the shadow of a coloured glyph is dark; fine. Use `<shadow:#00000000>` only if it looks wrong.
- Do not put player-controlled strings inside the font tag; the glyph is trusted config.
- Custom font text does not work through `String` APIs; use Components.

## Sources
- Minecraft Wiki, Font (bitmap provider): https://minecraft.wiki/w/Font
- MiniMessage format (font, sprite, head): https://docs.papermc.io/adventure/minimessage/format/
- PlayerResourcePackStatusEvent.Status 26.3: https://jd.papermc.io/paper/26.3/org/bukkit/event/player/PlayerResourcePackStatusEvent.Status.html
- Player Javadoc 26.3 (`sendActionBar(String)` and others deprecated in favour of `Audience#sendActionBar(Component)`): https://jd.papermc.io/paper/26.3/org/bukkit/entity/Player.html
- Project docs: `docs/minimessage.md` (custom fonts, glyphs)

## Hand-off

**model-designer** (pipeline: plain pack `resourcepack/`)
- `resourcepack/assets/cookieclicker/font/hud.json` exactly as above (font `cookieclicker:hud`, char U+E000 = `""`, `ascent` 7, `height` 8).
- `resourcepack/assets/cookieclicker/textures/font/hud_cookie.png`: 16x16 (or 32x32) RGBA PNG, the cookie art, 1-px transparent top and bottom margin, no empty column on the right, no empty row padding that shifts it; white-outline friendly so it reads on a dark and bright background.
- Check in game: icon height roughly equal to the text, baseline aligned. Adjust `ascent` / `height` if not.
- Do not touch the `minecraft` namespace.

**sound-designer**: nothing.

**vfx-designer**: nothing.

**plugin-coder**
- `messages.yml`: `hud.with-pack`, `hud.no-pack` as above (placeholders `cookies`, `cps`); config `hud.font` and `hud.glyph` if you want them configurable (default `cookieclicker:hud` and ``).
- Refresh task about every 20 ticks, `Player#sendActionBar(Component)`; skip unchanged text.
- `hasPack` flag per player from `PlayerResourcePackStatusEvent` (`SUCCESSFULLY_LOADED`, our UUID); fall back to `hud.no-pack`.
- Test: the unit tests cover number formatting; the in-game guide covers the glyph.

CONTRACT CHANGES (proposed): add messages `hud.with-pack` and `hud.no-pack` (two HUD templates, so the pack fallback works). The font `cookieclicker:hud` and char U+E000 stay as in the brief.
