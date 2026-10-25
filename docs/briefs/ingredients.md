# ingredients

**Status:** in progress  <!-- planned | in progress | review | done -->
**Created:** 2026-10-25 · **Depends on:** build-quality (merged, PR #17, 1.4.0)

Replaces the ingredient-farm plan. On 2026-10-25 the user stopped new builds ("creating builds is pointless, just keep
the factory one"), so there is no farm location: the ingredient buildings stay economy buildings, and the Recipe Book
is a GUI. The parked branch `feature/countryside-builds` (farm setup, Mill, waterwheel) is not used here. The old draft
on `ingredient-farm-old-draft` is the source for the economy and GUI parts.

## Goal
Add the GDD §4.5 **recipes** with a light supply chain. The five ingredient buildings also produce an **ingredient**
once the player buys the one-time **Recipe Book** upgrade. Recipes cost cookies plus ingredients and give a permanent
+% CPS. They are bought in a **textured chest GUI**. Nothing ever slows production (idle-friendly, no punishment).
Version **1.5.0**.

## Player story
At about 10K baked, a **Recipe Book** shows up on my upgrade shelf. I buy it: fanfare, and the chat says my farms now
deliver flour, milk, sugar, chocolate and eggs. The HUD shows my stock with small glyphs. I click "Recipe Book" on
the Bakery Shop panel (or type `/bakery recipes`), and a recipe book GUI opens with a page-turn sound.
- Butter costs 1,000 cookies + 200 milk for +2% CPS, and its tooltip shows in red what I still miss.
- When I can afford it, I click it: a sparkle and a chime, the page shows "Baked!", and my CPS goes up everywhere.

## Acceptance criteria
### Ingredients
- [ ] Five ingredients with fixed IDs `flour`, `milk`, `sugar`, `chocolate`, `eggs`. Each ingredient building gets
      `produces: { ingredient: <id>, per-second: <rate> }` in `buildings.yml`:
      production = rate × owned × that building's milestone multiplier (maths in `docs/balance/model.md`, new golden
      values). Its cookie CPS is unchanged.
- [ ] Building 7 is renamed from the Beehive to the **Chicken Farm**: id `beehive` → `chicken_farm`, with a save
      migration (owned count and milestones kept), new name and lore in messages.yml. It produces `eggs`.
- [ ] Ingredients are produced only after the Recipe Book is bought, on the same tick as cookies, while the player is
      online (offline progress is GDD §8, later). Stock has **no cap** and never limits anything else.
- [ ] Stock is saved per player in a new `ingredients` table (uuid, ingredient, amount DOUBLE). It is created like the
      existing tables on H2 and MariaDB, with async I/O. Old saves start at 0. Schema version +1 with migration.
- [ ] The **Recipe Book** (`recipe_book`) is a one-time upgrade in `upgrades.yml` with the new effect type
      `recipes-unlock`, bought on the existing upgrade shelf. Cost and unlock come from `upgrades.yml`
      (balance-designer). Buying it plays `recipes_unlock` (sound + effect) and sends a chat line.
### Recipes
- [ ] `upgrades.yml` gets a `recipes:` section with the 7 GDD recipes (Syrup waits for Rebirth): `butter`,
      `brown_sugar`, `chocolate_chips`, `oatmeal`, `hazelnut`, `macadamia`, `caramel`.
  - Each has `cost` (cookies), `ingredients: { <id>: <amount>, … }`, `effect: { type: cps-percent, value: <fraction> }`
    and `unlock: { baked: N }`.
  - A recipe shows once baked ≥ N **and** the Recipe Book is owned.
  - Starting mapping (the balance-designer tunes amounts):

    | Recipe | Ingredients |
    |---|---|
    | Butter | milk |
    | Brown Sugar | sugar |
    | Chocolate Chips | chocolate |
    | Oatmeal | flour |
    | Hazelnut | flour + chocolate |
    | Macadamia | eggs + sugar |
    | Caramel | sugar + milk |
- [ ] CPS follows GDD §4.1: `sum(building CPS × milestones) × (1 + sum of recipe percents)`. The HUD, the panel
      `+<cps>/s` and everything else that shows CPS include it.
- [ ] Buying a recipe takes the cookies and ingredients atomically. If either is short, the row shows which part is
      missing, `purchase_denied` plays and nothing is taken.
### Recipe Book GUI
- [ ] A **4-row** chest inventory (fixed; 7 recipes + room) with a **textured background**: the title is a WHITE
      component in font `cookieclicker:gui`: a `-8` space glyph + the `recipe_book` background glyph (176×186 PNG,
      ascent 13), plus the readable title text from messages.yml (per `docs/research/textured-gui.md`). Opened with
      `MenuType.GENERIC_9X4`; the GUI is identified by its view/holder, never by its title.
- [ ] Slots (from `config.yml: recipes.gui.slots`) hold one icon per recipe, in three looks:
  - **locked:** "bake N to unlock";
  - **available:** name, `+x% CPS`, the cookie cost, the ingredient costs with glyphs, and the missing amounts in red;
  - **bought:** glowing, "Baked!".
- [ ] Clicking an available recipe buys it: `recipe_buy` sound + effect at the player, and the slot updates in place.
- [ ] Players can't take or move items: every click and drag is cancelled, including shift, hotbar keys and offhand.
- [ ] Without the pack, the title falls back to plain text and the icons to vanilla items
      (`config.yml: recipes.gui.fallback-items`).
- [ ] Opening: `/bakery recipes` (permission `cookieclicker.recipes`, default true) anywhere, and a **"Recipe Book" row
      on the Bakery Shop panel** (same row style; shown once the Recipe Book is owned). Opening plays
      `recipe_book_open`. Before the unlock, the command says to buy the Recipe Book first.
### HUD
- [ ] Once the Recipe Book is owned, the HUD shows the ingredient stock (short numbers with ingredient glyphs) on its
      own line or as a compact group. It updates only on change, not every tick, if the HUD allows.
### Release
- [ ] Version **1.5.0**. Checks green: `check`, `validate-assets`, `sound-check`, `balance-check`, `tools-test`.
- [ ] GDD update (CookieMemory PR, the user merges):
  - §3.3 no farm location for now; the parked countryside is noted;
  - §4.5 the Recipe Book unlock;
  - Differences updated.

## Out of scope
- A farm location, ingredient fields, animals, the bonus harvest, ripe spots → parked (`feature/countryside-builds`).
- Ingredient-gated upgrades or buildings, Syrup (needs Rebirth), selling or trading ingredients → later.
- Offline ingredient growth → offline progress (§8).
- Chest-GUI tabs for the other shops (§4.7): this feature builds the textured GUI for recipes only, written so later
  tabs can reuse it.

## Assets needed
| Kind | What | Owner |
|---|---|---|
| model / texture / glyph | Recipe Book upgrade icon; 7 recipe icons (+ locked look); **textured GUI background** + negative-space glyphs (font `cookieclicker:gui`); 5 ingredient glyphs (GUI, HUD); Chicken Farm building icon + world model rename (`beehive` → `chicken_farm`, keep the look or a quick re-skin); `design/recipe-book-gui.md` | model-designer |
| sound | `recipes_unlock`, `recipe_book_open` (page turn), `recipe_buy` | sound-designer |
| effect | `recipes_unlock` (celebration at the player), `recipe_buy` (page-flip sparkle) | vfx-designer |

## Balance impact
- New: ingredient rates per ingredient building, Recipe Book cost and unlock (around 10K baked), and the 7 recipes
  (cookie cost, ingredient amounts, +%, unlock).
- The simulator models ingredients and recipes, bought by the same cost-per-extra-CPS strategy.
- New targets in `targets.md`:
  - Recipe Book in its window;
  - the first recipe soon after it;
  - all 7 within the horizon;
  - no recipe waits more than X minutes on ingredients alone for `idle`.
- The late-tier pacing issue (1B at ~3h50m vs the GDD's 6–10 h) may be retuned here if cheap. balance-designer.

## Research needed
`docs/research/textured-gui.md` (resource-pack-researcher), for Paper 26.3 and its client version:
- **Custom chest background through the inventory title:**
  - a bitmap font provider plus negative-space (space provider) glyphs;
  - the exact offsets for a 3–6 row chest;
  - the max texture size and height, and the `ascent` rules;
  - how the vanilla title text and the "Inventory" label behave (hide or overlay).
- **Paper API:**
  - opening a custom inventory with an Adventure Component title (`MenuType` vs `Bukkit.createInventory` on 26.3);
  - the safe way to cancel all item movement;
  - updating a slot in place without reopening.
- **Item tooltips on 26.3:** hiding the vanilla tooltip lines, and whether `tooltip_style` is worth using.
- **GUI scale:** does the background stay aligned at every GUI scale and with other packs loaded?

## Open questions
None. Decided with the user on 2026-10-25:
- ingredients + recipes as the next feature;
- a one-time Recipe Book unlock;
- access by command plus a Bakery Shop panel row;
- no farm location or builds.

## Contract (fixed names; change only via the orchestrator)
| Kind | ID | Owner | Notes |
|---|---|---|---|
| ingredient | `flour`, `milk`, `sugar`, `chocolate`, `eggs` | balance-designer | producers: wheat_farm, cow_barn, sugar_cane_field, cocoa_plantation, chicken_farm |
| building rename | `beehive` → `chicken_farm` | balance-designer (yml) + plugin-coder (migration) | save migration; old id accepted once on load |
| config key | `buildings.yml: buildings.<id>.produces` `{ ingredient, per-second }` | balance-designer | format in `docs/balance/model.md` |
| upgrade | `recipe_book`, effect type `recipes-unlock` | balance-designer | upgrade shelf |
| recipe | `butter`, `brown_sugar`, `chocolate_chips`, `oatmeal`, `hazelnut`, `macadamia`, `caramel` | balance-designer | `upgrades.yml: recipes.<id>` `{ cost, ingredients, effect: {type: cps-percent, value}, unlock }` |
| item model | `cookieclicker:upgrade/recipe_book`, `cookieclicker:recipe/<id>` (+ `cookieclicker:recipe/locked`) | model-designer | icons |
| item model | `cookieclicker:building_world/chicken_farm` (+ icon) | model-designer | replaces `beehive` |
| glyph | the 5 ingredient glyphs in their own font `cookieclicker:ingredient` | model-designer | codepoints in `design/recipe-book-gui.md`; no negative spaces in this font |
| GUI glyph | font `cookieclicker:gui`: `recipe_book` background (4 rows, 176×186) + negative-space glyphs | model-designer | codepoints and offsets in `design/recipe-book-gui.md`, per the research note; light area behind the "Inventory" label; no `tooltip_style` |
| sound event | `recipes_unlock`, `recipe_book_open`, `recipe_buy` | sound-designer | keys `cookieclicker:recipe.unlock` / `recipe.open` / `recipe.buy`; fallbacks `minecraft:ui.toast.challenge_complete` / `minecraft:item.book.page_turn` / `minecraft:entity.player.levelup` |
| effect | `recipes_unlock`, `recipe_buy` | vfx-designer | at the player |
| message key | `ingredients.<id>.name`, `recipes.<id>.name` / `.lore`, `upgrades.recipe_book.*`, `gui.recipes.*`, `panels.shop.recipe-book` | plugin-coder | |
| command | `/bakery recipes` | plugin-coder | perm `cookieclicker.recipes` (default true) |
| config key | `config.yml: recipes.gui.slots`, `recipes.gui.fallback-items` | plugin-coder | 4 rows fixed |
| storage | table `<prefix>ingredients`, schema v5 | plugin-coder | H2 + MariaDB |

## Tasks
| Agent | Task | Needs (gate) | Branch |
|---|---|---|---|
| resource-pack-researcher | `docs/research/textured-gui.md` | — | feature branch (docs) |
| balance-designer | ingredient rates, Recipe Book, 7 recipes, chicken_farm rename in yml; model.md + golden values + simulator + targets + report | contract | `ingredients/balance-designer` |
| model-designer | icons, glyphs, GUI background + `design/recipe-book-gui.md`, chicken_farm model/icon | contract, research | `ingredients/model-designer` |
| sound-designer | 3 sound events | contract | `ingredients/sound-designer` |
| vfx-designer | 2 effect presets | contract | `ingredients/vfx-designer` |
| plugin-coder (economy) | ingredients in Economy/BuildingDef/parser, `ingredients` table + schema v5 + chicken_farm migration, Recipe Book upgrade, recipes + cps-percent, CPS formula, HUD stock, messages, version 1.5.0 | contract | `ingredients/plugin-coder-economy` |
| plugin-coder (gui) | textured Recipe Book GUI, `/bakery recipes`, Bakery Shop panel row, config `recipes.gui` | economy merged, research | `ingredients/plugin-coder-gui` |
| plugin-tester | review each code branch, then integration (sonnet) | owner DONE | (reviewed branch) |
| orchestrator | CookieMemory PR: GDD §3.3/§4.5 + Differences | brief approved | CookieMemory branch |

## Status
| Agent | Status | Branch | Verdict | Notes |
|---|---|---|---|---|
| | planned | | | |

## Merge order
0. research note, 1. model-designer, 2. sound-designer, 3. vfx-designer, 4. balance-designer, 5. plugin-coder (economy),
6. plugin-coder (gui)

## Needs human review
- [ ] Look: the Recipe Book GUI background lines up with the slots (GUI scale 2, 3 and auto), plus the fallback without the pack
- [ ] Listen: `recipes_unlock`, `recipe_book_open`, `recipe_buy`
- [ ] In game: buy the Recipe Book, watch the stock grow, open the book from the panel and the command, buy a recipe and see CPS go up
- [ ] An old save with Beehives loads as Chicken Farms with the same count
- [ ] Balance report: Recipe Book and recipe pacing for idle/casual/active
