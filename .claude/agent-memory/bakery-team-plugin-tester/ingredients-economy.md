---
name: ingredients-economy
description: ingredients/recipes/Recipe Book economy facts verified 2026-10-25 at ingredients/plugin-coder-economy 69f57e5 (tests at 9dbbddb); golden keys, LegacyIds, open LOW items
metadata:
  type: project
---

Verified 2026-10-25 (branch review of `ingredients/plugin-coder-economy` 69f57e5; tester commit 9dbbddb on `ingredients/plugin-tester`). Re-check if `economy/RecipeBook.java`, `EconomyConfigParser.recipes()` or `LegacyIds` changed (`git log -1 -- <path>`).

- Ingredient order = building order (`EconomyConfig.ingredients`): flour (wheat_farm), milk (cow_barn), sugar (sugar_cane_field), eggs (chicken_farm), chocolate (cocoa_plantation). Recipe order = `upgrades.yml: recipes`.
- Rates: `rate = Σ owned × per-second × 2^milestones` per building; `Economy.ingredientRate` returns 0 without the one-time `recipes-unlock` upgrade (`recipe_book`). `Economy.produce` adds stock after cookies; no cap.
- Recipe bought = level-1 entry in `upgrades` under the recipe id. Parser rejects recipe id == upgrade id, recipes without a `recipes-unlock` upgrade, unknown ingredient, `recipes-unlock` with cost-growth.
- `RecipeBook.buy` all or nothing: LOCKED / ALREADY_OWNED / CANT_AFFORD / UNKNOWN (status null); exact double compare `have < need` (see open LOW).
- Golden keys: `ingredients[]`, `ingredient-rates.<building>` = `{ingredient, per-second, rates[{owned, recipe-book, rate}]}`, `recipes.<id>` = `{cost, ingredients, type, value, unlock-baked}`, `recipe-purchase[]` = `{recipe, case, pouch, baked, recipe-book, bought, stock, affordable}` (52 cases), `recipe-cps[]` = `{owned, recipes, recipe-percent, total-cps}`, `states[].recipes` (list, level 1), `states[].recipe-percent`, `states[].ingredient-rates`.
- `LegacyIds.BUILDING_RENAMES` = beehive -> chicken_farm, applied in `JdbcPlayerStore.load`; notes go to `problems`, logged by `SessionManager` as "Repaired save of ...". Larger count wins when both exist.
- Schema v5: table `<prefix>ingredients (uuid, ingredient, amount DOUBLE, PK(uuid, ingredient))`, created by `CREATE TABLE IF NOT EXISTS` with the MariaDB options; H2Migration copies it (`TABLES` order, 5th).
- Recipe sound/effect IDs in code: `recipe_buy` (RecipeService), `cant_afford` (sounds.yml, existing), `recipes_unlock` (StationInteractions). Brief's `purchase_denied` is not used.

Open LOW (follow-ups, not blocking, 2026-10-25):
- Exact compare on stock: 1 wheat farm, 10 ticks at 0.2/s gives 1.9999999999999998, so a 2-flour recipe is refused while the display reads 2 until the next tick. Judgement: add a 1e-9 relative tolerance in `RecipeBook.status` (and cookies); GUI rounding alone cannot fix the refusal.
- `RecipeService.buy` plays `cant_afford` for LOCKED / ALREADY_OWNED / UNKNOWN too.
- `JdbcPlayerStore` 595 lines (535 before the feature); split the ingredient load/save helpers out.
- `EconomyConfigParser` uses inline `java.util.Set/HashSet/TreeSet` FQNs (nit).
- PERF-LOW: `Economy.produce` scans buildings 5× per second per player; `Hud.stockKey` formats 5 numbers per second per player; `replaceIngredients` runs DELETE + INSERTs on every autosave while producing (stock changes every tick).
