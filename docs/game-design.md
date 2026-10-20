# Game Design Document: Minecraft Cookie Clicker

This document describes the concept and the full gameplay of the Cookie Clicker minigame in Minecraft, from the first click to the endgame. It is the **source of truth for the game's vision**: every agent reads it before designing or building a feature, and feature briefs (`docs/briefs/`) must not contradict it. Written by the user (originally in Dutch, translated); only the orchestrator edits it, and only on the user's instruction.

> [!note] About the numbers
> All costs, percentages and times in this document are guideline values. They are meant as consistent starting points and must be tuned in playtests. The real values live in `buildings.yml` / `upgrades.yml` (balance-designer, `docs/balance/`). Where the shipped game differs from this document, the difference is listed in [Differences from the current build](#differences-from-the-current-build) until a feature resolves it.

## 1. Server concept
A Minecraft server where every player builds their own cookie empire. It is an idle clicker that you don't play in a menu but in the world itself. You walk around your own plot, watch your factory grow and see the cookies roll towards you on the conveyor belt.

- **Relaxed:** no PvP, no stress. Competition happens on leaderboards.
- **Short and long sessions:** the factory keeps baking while you're offline.
- **Physically idle:** everything you buy is visible in the world.
- **Own identity:** custom resource pack, custom models, no standard survival.

| Part | Description |
|---|---|
| Hub | Spawn with leaderboards and a portal to your own plot. |
| Own plot | A private region per player where the game takes place. |
| Visitor mode | View the plots of friends or top players (look, don't break). |
| Leaderboards | Total cookies, highest CPS, number of rebirths, highest level. |

## 2. Atmosphere & lore (short)
Warm, cosy and a little absurd: a bakery fairy tale with wooden fences, cookie smell (particles), cheerful music and light humour.

**Premise:** you inherit a run-down little bakery with a single cookie on a pedestal. You expand it into a cookie empire and eventually ascend to the "Heavenly Bakery". Lore comes out in small pieces through hologram texts and the titles that come with your level (see 6.4). There is no mandatory storyline.

## 3. The world: plot & layout

### 3.1 The starter plot
- **Size:** compact, about 11×11, surrounded by wooden fences with a clear entrance. There is about 5 blocks of free space between the factory and the fence.
- **The factory:** an atmospheric little building in which the cookies are baked (virtually).
- **The pedestal:** in front of or inside the factory. A 3D cookie (Item Display) floats/spins on it. Players click (left or right click) to collect cookies by hand.
- **Conveyor belt:** runs from the factory to the pedestal. With the resource pack you see cookies moving along the belt. The higher the CPS, the more cookies and the faster the belt.
- **Holograms:** floating text by the building with real-time statistics (cookies, CPS, cookies per click).
- **Shop panel:** an interactive NPC/Interaction entity next to the factory. Right-click opens the buy menu (see 4.7).

### 3.2 Visual growth
Every building you buy appears physically on the plot and gets nicer at every milestone (see 4.3). To prevent lag there is a visual limit per building type (e.g. at most 10 visible entities), while the number behind it keeps counting as normal.

## 4. Economy & upgrades

### 4.1 Basic rules
| Term | Definition |
|---|---|
| Cookies | The main currency. |
| CPC | Cookies per click. Starts at 1. |
| CPS | Cookies per second (passive). Starts at 0. |
| Building cost | base cost × 1.15^(number already bought): each next one is 15% more expensive. |
| Selling | Not possible. Prevents abuse and keeps it simple. |

Total production:
```
CPS = sum(building_base × count × milestone_multiplier)
      × (1 + sum of additive bonuses)   // recipes, stars, level, prestige tree
      × crown_multiplier                // Ascension, see 6.5
      × active buff                     // golden cookies
```

### 4.2 Buildings (passive income)
Every building is a physical object on the plot.

| # | Building | Base cost | Base CPS | Available from |
|---|---|---|---|---|
| 1 | Wheat Field | 15 | 0.1 | Start |
| 2 | Baker's Apprentice (villager) | 100 | 1 | Start |
| 3 | Cow Barn (milk) | 1,100 | 8 | Expansion 1 |
| 4 | Sugar Cane Field | 12,000 | 47 | Expansion 1 |
| 5 | Industrial Oven | 130,000 | 260 | Expansion 2 |
| 6 | Extra Conveyor Belt | 1.4M | 1,400 | Expansion 2 |
| 7 | Beehive (honey cookies) | 20M | 7,800 | Expansion 3 |
| 8 | Cocoa Plantation | 330M | 44,000 | Expansion 3 |
| 9 | Shipping Centre | 5.1B | 260,000 | Expansion 4 (after Rebirth 1) |
| 10 | Alchemy Lab | 75B | 1.6M | Expansion 4 (after Rebirth 1) |
| 11 | Portal Bakery | 1T | 10M | Expansion 5 (after Rebirth 3) |

### 4.3 Building milestones
Every building gets a fixed ×2 on its own production at certain counts:

| Count | 10 | 25 | 50 | 100 | 150 | 200 |
|---|---|---|---|---|---|---|
| Production of that building | ×2 | ×2 | ×2 | ×2 | ×2 | ×2 |

At every milestone the model in the world changes too (wheat fields get flowers, the oven gets a chimney, the barn gets bigger). That is the visual reward for buying on.

### 4.4 Click upgrades (CPC)
One-time purchases, bought through the menu. Base CPC is 1 and every upgrade adds to it.

| Upgrade | Cost | Effect |
|---|---|---|
| Wooden Spatula | 100 | +1 CPC |
| Stone Rolling Pin | 500 | +2 CPC |
| Iron Rolling Pin | 5,000 | +5 CPC |
| Golden Glove | 50,000 | +20 CPC |
| Diamond Fingers | 500,000 | +100 CPC |
| Netherite Hands | 5M | +500 CPC |
| Handy Fingers I | 50M | Every click also gives +1% of your CPS |
| Handy Fingers II | 5B | This becomes +3% of your CPS |
| Handy Fingers III | 500B | This becomes +5% of your CPS |

The Handy Fingers keep clicking worthwhile in the later game.

### 4.5 Recipes (global upgrades)
One-time cookie types with a permanent bonus for the current run (reset on Rebirth). They appear in the menu once you have baked a total number of cookies in the run.

| Recipe | Cost | CPS bonus |
|---|---|---|
| Butter | 1,000 | +2% |
| Brown Sugar | 10,000 | +2% |
| Chocolate Chips | 100,000 | +3% |
| Oatmeal | 1M | +3% |
| Hazelnut | 10M | +4% |
| Macadamia | 100M | +4% |
| Caramel | 1B | +5% |
| Syrup (after Rebirth 1) | 10B | +5% |

### 4.6 Golden cookies
Appear from Expansion 2 at a random spot on the plot (particles + sound). They disappear after about 15 seconds if you don't grab them.

- **Spawn interval:** random between 2 and 5 minutes (can be shortened with prestige upgrades).
- **Effects (weighted chance):**

| Effect | Chance | Duration | What it does |
|---|---|---|---|
| Frenzy | 45% | 30 s | CPS ×7 |
| Click Frenzy | 25% | 15 s | Clicks +777% |
| Lucky | 20% | instant | You get 15 minutes of CPS (max. 15% of your balance + 13) |
| Cookie Rain | 10% | 10 s | A rain of small cookies to collect |

- Buffs may stack (e.g. Frenzy + Click Frenzy). That is the reward for staying active.
- There are no negative effects, to keep the mood relaxed.

### 4.7 The menu (shop panel)
Right-clicking the panel opens a chest GUI with tabs:

- **Buildings:** buy ×1 / ×10 / ×100 / Max.
- **Clicks:** click upgrades.
- **Recipes:** global upgrades.
- **Expansion:** the next plot expansion.
- **Prestige:** Rebirth, star tree and Ascension (visible once unlocked).
- **Statistics:** total baked, CPS, play time, golden cookies grabbed.
- **Cosmetics & Achievements.**

Large numbers are abbreviated (1.5K, 2.3M, 4.1B, 7.8T, …).

## 5. Plot expansions
Every expansion is a one-time purchase per run. The fences move out, new terrain appears, and new buildings and mechanics unlock.

| Expansion | Size | Cost | Unlocks |
|---|---|---|---|
| Start | 11×11 | — | Wheat Field, Baker's Apprentice, clicking |
| Expansion 1 | 15×15 | 10,000 | Cow Barn, Sugar Cane Field, faster belt |
| Expansion 2 | 20×20 | 500,000 | Industrial Oven, Extra Belt, Golden Cookies |
| Expansion 3 | 25×25 | 25M | Beehive, Cocoa Plantation, big factory extension, Rebirth Hologram |
| Expansion 4 | 30×30 | 1B | Shipping Centre, Alchemy Lab. Only after Rebirth 1. |
| Expansion 5 | 35×35 | 100B | Portal Bakery. Only after Rebirth 3. |

Expanding happens through FAWE: the new piece is pasted from a schematic and the plot boundary (WorldGuard region or our own script) is adjusted.

## 6. Progression, levels & prestige

### 6.1 Pacing guideline (start to end)
| Time | What the player does | Milestone |
|---|---|---|
| 0–5 min | Clicks, buys the first Wheat Field, Baker's Apprentice and Wooden Spatula | First passive CPS |
| 5–20 min | Builds up buildings, first recipes | Expansion 1 (10K) |
| 20 min – 1.5 h | More buildings, click upgrades | Expansion 2 (500K), first golden cookie |
| 1.5–6 h | Stacks buffs, builds to about 50 per building | Expansion 3 (25M), Rebirth Hologram |
| 6–10 h | Up to the Rebirth threshold (1B total) | Rebirth 1 |
| Run 2 onwards | Faster climb thanks to prestige bonuses, new expansions | Expansion 4/5, more stars |
| Endgame | Rebirthing for stars, completing the tree | Ascension |

### 6.2 Rebirth (first prestige layer)
**Requirement:** Expansion 3 bought **and** at least +10 new Prestige Stars available (see the formula). The first Rebirth is therefore at 1 billion cookies total.

**Prestige Stars** (total earned over all runs):
```
Total stars = round( 10 × (lifetime baked / 1,000,000,000) ^ (1/3) )
```
| Lifetime baked | 1B | 8B | 27B | 125B | 1T | 1,000T |
|---|---|---|---|---|---|---|
| Total stars | 10 | 20 | 30 | 50 | 100 | 1,000 |

- **Reset:** cookies, buildings, click upgrades, recipes, plot size (back to 11×11).
- **Kept:** stars and the star tree, baker level, achievements, cosmetics, Cookie Coins, statistics.

**Permanent bonus:** every Prestige Star gives +1% on all CPS and CPC, even after you spend it. So 100 stars is ×2.

### 6.3 The star tree (spending stars)
Stars are also a currency. Spending them does not lower your bonus (+1% per star).

**Economy**
| Upgrade | Cost | Levels | Effect per level |
|---|---|---|---|
| Starting Capital | 1★ | 5 | Start every run with 1K / 10K / 100K / 1M / 10M cookies |
| Click Power | 2★ | 10 | +5% CPC (cost rises per level) |
| Productivity | 3★ | 10 | +3% CPS (cost rises per level) |
| Cheaper Building | 5★ | 10 | −1% on all building costs |

**Head start (expansions in advance)**
| Upgrade | Cost | Effect |
|---|---|---|
| First Expansion in Advance | 10★ | Start every run with Expansion 1 |
| Second Expansion in Advance | 50★ | Start every run with Expansion 2 |
| Third Expansion in Advance | 150★ | Start every run with Expansion 3 |

**Golden cookies**
| Upgrade | Cost | Effect |
|---|---|---|
| Golden Touch | 10★ | Spawn interval −10% (max. 3 levels) |
| Long Glow | 15★ | Golden cookies stay 20% longer |
| Early Glow | 20★ | Golden cookies appear from Expansion 1 |
| Double Blessing | 100★ | 10% chance a golden cookie gives two effects at once |

**Comfort & offline**
| Upgrade | Cost | Effect |
|---|---|---|
| Sleeping Bakers | 5★ | Offline limit +2 hours per level (max. 5 levels, up to 12 hours) |
| Night Shift | 25★ | Offline efficiency from 50% to 75%, later 100% |
| Helper Clicker | 30★ | A villager automatically clicks the pedestal once per second |
| Auto Collect | 200★ | Golden cookies are picked up automatically after 3 seconds (at a lower reward: 50%) |

The exact cost per level in the tree is decided during balancing. Otherwise: early cheap and broad, late expensive and powerful.

### 6.4 Baker level (permanent, never reset)
The level depends on the lifetime number of cookies baked and only ever goes up.
```
Cookies needed for level L = 1,000 × 1.35 ^ (L − 1)     (L = 1 to 100)
```
| Level | Title | Example cookies needed |
|---|---|---|
| 1–9 | Apprentice Baker | up to about 11K |
| 10–24 | Baker | up to about 1M |
| 25–49 | Skilled Baker | up to about 1.8B |
| 50–74 | Master Baker | up to about 3.2T |
| 75–99 | Heavenly Baker | up to about 6,000T |
| 100 | Legendary Cookie Master | about 7,900T |

**Rewards:**
- Every level: +0.25% CPS (max. +25% at level 100).
- Every 5 levels: a cosmetic item or small unlock (new hologram style, trail effect, belt skin, etc.).
- The title is shown in chat, the tab list and on the leaderboard.

### 6.5 Ascension (second prestige layer, endgame)
For players who have nearly finished the star tree.

**Requirement:** at least 10 Rebirths and at least 500 Prestige Stars total.

**Golden Crowns:**
```
Crowns = round( total stars / 100 )       (500 stars = 5 crowns)
```
- **Reset:** everything Rebirth resets, plus stars and the star tree.
- **Kept:** baker level, achievements, cosmetics, Cookie Coins, Golden Crowns and the Crown Tree.

**Effect:**
- Every Golden Crown gives a multiplicative ×1.10 on all production (crowns stack exponentially).
- A Crown Tree with, among others: extra stars per Rebirth, Expansion 4/5 from run 1, new plot themes (Nether, End and Heavenly Bakery) and exclusive cosmetics.
- Every Ascension is counted and shows a golden star after the title.

## 7. Cosmetics, achievements & extras

### 7.1 Cookie Coins
A second currency only for cosmetics, so power and looks never compete. Earned through achievements, Rebirths and events.

### 7.2 Cosmetics
- Trail effects (particles behind you).
- Belt skins and pedestal skins.
- Hologram styles and colours.
- Hats and clothes for the villagers.
- Plot themes and fence styles.

### 7.3 Achievements (examples)
| Category | Example | Reward |
|---|---|---|
| Clicks | 100 / 1,000 / 10,000 / 100,000 clicks | Cookie Coins |
| Buildings | 10 / 50 / 100 of each building | Cookie Coins + cosmetics |
| Golden cookies | 10 / 100 / 1,000 grabbed, 2 buffs at once | Cookie Coins |
| Rebirth | First Rebirth, 10 Rebirths, first Ascension | Titles + cosmetics |
| Secrets | Hidden easter eggs on the plot | Surprises |

### 7.4 Extras
- **Daily order:** every day a small "order" from a villager (e.g. deliver 50K cookies within 10 minutes) for a small reward.
- **Events:** seasonal cookies (Christmas, Halloween, King's Day) with temporary cosmetics.
- **Co-op plots** (idea for later): run one factory together.

## 8. Offline progress & fair play
- **Offline:** the player gets CPS × offline time × efficiency, up to a maximum. Default 50% efficiency and max. 2 hours, improvable through the star tree (up to 12 hours and 100%). On login a message appears ("Your bakers baked 4.2M cookies while you were away").
- **Anti-autoclicker:** at most 15 valid clicks per second. More don't count (and are logged).
- **Server-side:** all calculations happen on the server. Clients only get the display.

## 9. Technical

### 9.1 Plugins
| Plugin | Description |
|---|---|
| Cookie Clicker plugin | Custom plugin with the cookie clicker gameplay (repo `CookieEmpireMC/CookieClicker`). |
| Cookie Core plugin | Core plugin, self-explanatory (repo `CookieEmpireMC/CookieCore`). |
| Cookie resource pack loader | Separate plugin that loads the resource pack. |
| LuckPerms | Permissions and ranks. |
| FAWE (FastAsyncWorldEdit) | Fast placing and expanding of plots/structures. |

### 9.2 Stored per player
- Cookies (current), lifetime baked, baked this run (big numbers: BigDecimal/BigInteger, or double with abbreviation).
- Count per building, bought click upgrades and recipes, plot level.
- Stars (total and spent), star tree levels, number of Rebirths.
- Crowns, crown tree, number of Ascensions.
- Baker level, achievements, Cookie Coins, cosmetics (owned + active).
- Time of last logout (for offline progress).

Storage in SQLite or MySQL. Autosave regularly and on logout.

## 10. Open questions
- [ ] Final name of the server (and spelling: "Coockie" or "Cookie"?).
- [ ] How are plots stored and loaded (a separate world per player, or regions in one world)?
- [ ] Should clicks and golden cookies go through Armor Stand/Item Display or through Interaction entities?
- [ ] Which cosmetics ship at launch?
- [ ] Is Ascension needed at launch, or only in a later update?
- [ ] Should numbers be stored server-side as double, or is BigDecimal needed for the endgame?

## Differences from the current build
Maintained by the orchestrator. Each line is resolved by a future feature (then removed here).
- **Buildings and prices (4.2):** the shipped `buildings.yml` has `villager_baker` (50 / 0.5 CPS) and `wheat_farm` (500 / 4 CPS); this document has Wheat Field 15 / 0.1 and Baker's Apprentice 100 / 1, plus 9 more buildings. The cost growth of 1.15 matches. → balance-designer, next economy feature.
- **Plot size (3.1, 5):** `factory-makeover` ships a fixed 31×30 fenced plot with a 19×14 factory; this document starts at 11×11 and grows to 35×35 through expansions. → a `plot-expansions` feature has to reconcile the factory size with the starter plot (smaller starter factory, or a bigger starter plot).
- **Shop (4.7), click upgrades (4.4), recipes (4.5), golden cookies (4.6), prestige (6), cosmetics (7), offline progress (8):** not built yet.
