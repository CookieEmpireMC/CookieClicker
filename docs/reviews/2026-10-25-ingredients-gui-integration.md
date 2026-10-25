# ingredients: Recipe Book GUI branch review + integration review (2026-10-25)

Target Paper 26.3 (`26.3.build.205-stable`), Java 25. Branch `ingredients/plugin-coder-gui` 2bcd34d, merged with `feature/ingredients` 473938a; tests at `ingredients/plugin-tester`.

**VERDICT branch: READY. VERDICT integration: READY** (no HIGH/MEDIUM, no PERF-HIGH).

## Checks
`check-quiet` 439 Java tests green, `validate-assets` 117 files / 0 problems, `sound-check` 31 sounds / 0 failed, `balance-check` 30/30, `tools-test` 81 OK (1 env skip), `ownership-check` OK. No deprecation warnings, pom/mise unchanged.

## GUI judgement
- Identified by view (`Open.view`, fallback same top inventory), never by title. Every `InventoryClickEvent` in that view is cancelled at LOWEST before any buy logic (shift, number keys, offhand swap, double click collect, drop, outside, bottom inventory); `InventoryDragEvent` cancelled. Buying only for LEFT/RIGHT/SHIFT clicks on a raw slot 0-35 mapped to an AVAILABLE recipe. `InventoryCreativeEvent` handling is harmless (never a buy).
- `open()` puts the entry after `view.open()` (a previous book closing must not drop the new one); removed on close and quit; `closeAll` on `reload()` (before messages/balance reload) and in `shutdownOrder`.
- Glyph codepoints in `RecipeGlyphs` (E103, E200, E110) equal `font/gui.json`; ingredient icons E000-E004 equal `font/ingredient.json` (test `RecipeBookContentTest`). Sound ids `recipe_book_open`, `recipe_buy`, `recipes_unlock`, `cant_afford` and effects `recipe_buy`, `recipes_unlock` exist. Message keys of the contract exist. Permission `cookieclicker.recipes` default true declared and tied by test.
- Thread safety: everything on main; no async touching. No static maps; `open` map is per plugin instance.

## Performance
Per second per player: `afterProduce` lambda = `refreshPanels` (unchanged cost; the new Recipe Book row has a `renderedKey` early-out) + `menu.refresh` = one `HashMap.get` unless that player has the book open. Open book: `statuses` (7 small objects) + 7 key strings per second, and `setItem` only for slots whose key changed (verified in `fill`). Open-book cost is negligible. Profile with spark anyway (see guide).

## Findings
- LOW-1 (UX, recommend fixing in a follow-up): the Bakery Shop "Recipe Book" row is appended after the last building. With 11 buildings and the default window (next locked building in the bottom row) it is off screen for most of the run and only found by scrolling or `/bakery recipes` (the village Recipe Book station is not part of this feature). I would pin it, like the repeatable upgrades in the clicks panel: always the last visible row, buildings scrolling above it (`PanelRows.buildings/maxTop/defaultTop` take a pinned row; `RecipeBookRowState` is already a fixed link). Not blocking.
- LOW-2: `RecipeIcons.shownHave` uses a relative tolerance on the stock (`have * (1 + 1e-9)`) and `RecipeBook.covers` one on the price; at stocks above ~1e9 the icon could show up to `have * 1e-9` units too many. Cosmetic (numbers are abbreviated).
- LOW-3: `/bakery recipes` right after joining (save still loading) answers "buy the Recipe Book first". Cosmetic.
- LOW-4 (from the economy review, still open): `JdbcPlayerStore` 595 lines; `Economy.produce` scans buildings 5x per second per player; `replaceIngredients` rewrites on every autosave while producing.
- Not testable without a server (skipped on purpose): `RecipeService.buy` sound for LOCKED/ALREADY_OWNED (needs `SessionManager`, `SoundService`, `Effects`); verified by reading: only CANT_AFFORD plays `cant_afford`.

## Tests added (11)
`RecipeIconsTest` (2), `RecipeGuiSettingsTest` (3: absent section, one bad slot = one problem, shipped section covers every recipe), `RecipeBookContentTest` (4: sound/effect ids, title glyphs, ingredient glyphs, recipe texts + item models), `PanelRowsTest` (+1 recipe-book row paging), `EconomyTest` (+1 stock a hair below the price pays, no negative leftover), `PluginDescriptorTest` (+1 `cookieclicker.recipes` default true).

## In-game test guide (for the PR)
1. Join with the pack; click-panel: buy the Recipe Book (needs 500 baked). Run `/bakery recipes`: textured book opens, 7 icons in row 2, locked ones show "???". Check the title text sits on the paper strip and slot frames align with the slots (design doc "Needs an in-game screenshot" items 1-5), at GUI scale 2, 3 and auto.
2. Try to break it: shift-click, number keys, F (offhand), double click, drop (Q), click outside, drag across slots, in survival and creative; nothing moves, only a click on an available recipe buys it (sound + effect, CPS rises).
3. Keep it open while flour grows: lore numbers update each second, no flicker of other slots. Buy a recipe: slot turns glinting "Baked!".
4. `/bakery reload` and `/stop` with the book open: it closes. Join without the pack (decline): vanilla icons and plain title.
5. Bakery Shop: scroll to the end for the Recipe Book row, click opens the book (see LOW-1). A player without ops sees only `/bakery recipes` in `/bakery`.
6. spark: `/spark profiler start --timeout 60` while the book is open and clicking; the plugin should stay well under 1% of the tick, and `RecipeBookMenu.refresh` should be barely visible.
