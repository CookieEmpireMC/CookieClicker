---
name: ledger-plot-polish-basket
description: plot-polish cookie basket (coder branch plot-polish/plugin-coder-basket) reviewed at 9cea984 + Gandalf tests b3f1b56 (2026-10-22); BasketLook/BasketSettings maths, tier-up and respawn paths, open LOWs
metadata:
  type: project
---

Verified 2026-10-22: branch plot-polish/plugin-coder-basket, coder head 9cea984, Gandalf tests b3f1b56 (BasketLookTest 6, StationVisualSettingsTest +2 basket tests). `mise run check-quiet` BUILD SUCCESS, 654 tests. Verdict READY (no HIGH/MEDIUM). Assets not on this branch (sound/effect/item model are on feature/plot-polish; `basket_tier_up` present in sounds.yml, sounds.json, effects.yml there).

Passed by reading and tests:
- BasketLook maths: level() log fill per tier (fill starts 10^(0, 1.2, 2.4, ...) per tier range), 0/neg/NaN -> 0, +Inf/1e300 -> golden_4, ofLevel clamps, pick hysteresis both ways (up at B*(1+h), down at B/(1+h)), multi-step up and down in one call. Known tiny lag (not visible): up/down show level(cookies/h) or level(cookies*h), at most 5% off in value.
- cookieCentre: basketBottom + top + gap + scale/2 + bob (bob keeps the lowest point at the gap), capped at cookie-max-height 2.2. Default golden_4 centre 2.088 (not capped).
- Tier-up triggers only in updateBasket when next.tier > current.tier and the view exists. Spawn/join/reload/plot re-entry/pack respawn use pick(pouch, null) and then an equal pick: no effect. Drops switch at once. Effect at pedestalBottom, look switch in animationTick at +8 ticks; no per-view task, pendingLook lives in the view, remove() drops it.
- Quit/unload/world change: removeOwner -> remove for every view (List.copyOf). animationTick iterates live maps only; showLook checks isValid.
- Pack change: refresh compares view.withPack with hasPack (ResourcePackTracker.loaded HashSet, cheap). Respawn rebuilds the shelf from the pouch in update()/syncShelf. In-flight shelf flights are dropped (fine).
- Cookie hitbox and cookieCentre move with dy (aim() uses cookieHitbox bounding box; StationInteractions fallback uses cookieCentre).
- Paper 26.3.build.205-stable API present: CustomModelData.Builder.addString, Display.setTeleportDuration, ItemDisplay.setItemDisplayTransform.

Open (LOW, follow-ups, no fix round):
- Brief says "switches at most once per refresh"; refreshPanels runs update() on every cookie click too, so the look can change on a click (still at most one change per call, hysteresis keeps it rare).
- Hitbox teleports instantly while the cookie glides for move-ticks (10): clicks during the glide aim at the final height.
- Architecture: StationViews is 1061 lines; basket logic (updateBasket/showLook/moveUp) would fit a small CookieBasket helper.
- design/basket.md golden_0/1 tops (0.563/0.625) differ from config 0.69 on purpose; the design file's own note already asks for the 0.69 clearance. Keep the note in the PR.
- In-game (cannot run offline): cookie gap and clearance next to golden corner posts, glide look, tier-up sparkle timing vs the 8-tick switch, pack-load respawn flicker, panel clearance from the basket (golden 1.375 wide).

PERF: updateBasket adds 2-4 Math.log and one small record per in-range view per refresh (20 ticks) and per cookie click: about 0.2 us. animationTick adds one null check per view per tick. Entity count unchanged (basket replaces the pedestal entity). Pack respawn happens once per pack change and re-spawns the whole view.

Test recipe: BasketLookTest uses BasketSettings.defaults() and package-private BasketLook; shipped check uses YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml")) via StationVisualSettings.from.
Re-review: `git diff b3f1b56..HEAD -- src/main/java/dev/casperrs/cookieClicker/view src/main/java/dev/casperrs/cookieClicker/config/BasketSettings.java src/main/resources/config.yml` (plus the coder's head if it moved past 9cea984).
