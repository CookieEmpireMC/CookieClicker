# WorldGuard plot and house regions

**Target version:** Paper 26.3 (`26.3.build.205-stable`, Java 25). Feature `starter-house`. Researched 2026-10-19.
Marks: **[V]** verified in a linked page/Javadoc, **[SK]** source knowledge (not in the pages I could read; the coder confirms at compile/in game).

## 1. Version verdict: YES, WorldGuard supports Paper 26.3

**STATUS: OK. The feature may proceed.**

| Plugin | Release | Date | MC | Link |
|---|---|---|---|---|
| **WorldGuard 7.0.20** (stable "release") | "WorldGuard 7.0.20 (MC 26.3)", changelog: "Requires MC 26.3+", EntityBreakEvent support, poplar boat fix | 2026-10-16 (dev.bukkit) / 2026-10-17 (Modrinth) | **26.3 only** | [Modrinth jar](https://cdn.modrinth.com/data/DKY9btbd/versions/9YdeGXiK/worldguard-bukkit-7.0.20.jar), [dev.bukkit files](https://dev.bukkit.org/projects/worldguard/files) |
| WorldGuard 7.0.19 | "Requires MC 26.2+", lists 26.2 and 26.3 | 2026-09-18 | 26.2, 26.3 | [Modrinth jar](https://cdn.modrinth.com/data/DKY9btbd/versions/TtfwTyi6/worldguard-bukkit-7.0.19.jar) |
| WorldGuard 7.0.18 | lists 26.1 to 26.3 | 2026-07-31 | 26.1.x to 26.3 | Modrinth |
| **WorldEdit 7.4.6** (stable) | Paper 1.21.4 to 26.3; betas 7.4.6-beta-01 (2026-09-21, changelog "Update to Minecraft 26.3") and beta-02 (2026-09-24) preceded it | 2026-10-15 | 1.21.4 to 26.3 | [Hangar jar](https://hangarcdn.papermc.io/plugins/EngineHub/WorldEdit/versions/7.4.6/PAPER/worldedit-bukkit-7.4.6.jar) |
| FastAsyncWorldEdit 2.16.0 / 2.16.1 | 2.16.0 body: "26.3 Support"; 2.16.1 (bug fixes) is the latest | 2.16.0: 2026-10-04, 2.16.1: 2026-10-09 | 26.3 | [FAWE releases](https://github.com/IntellectualSites/FastAsyncWorldEdit/releases) |

All **[V]**. Sources: Modrinth API for WorldGuard, dev.bukkit.org file list, Hangar API for WorldEdit, GitHub API for FAWE, EngineHub build pages, Maven metadata (below).
Cross-checks: EngineHub CI shows WorldGuard build "Release 7.0.20" after "Add materials map for 26.3 blocks/items" and "update Paper API (26.3)"; WorldEdit build "Fixed snapshot reading for 26.3" before "Released 7.4.6". Maven Central-style metadata on `maven.enginehub.org`: WorldGuard `<release>7.0.20`, WorldEdit `<release>7.4.6`.

**Recommendation for the server:** WorldGuard **7.0.20** + WorldEdit **7.4.6** (the simpler, lower-risk pair; the pom of 7.0.20 itself is built against WorldEdit 7.4.4, so 7.4.6 satisfies it). FAWE 2.16.1 also lists 26.3 and replaces WorldEdit (never run both); WorldGuard works with either. Plan and test with WorldEdit; FAWE only if the admin already uses it.
The user's ItemsAdder 4.0.18 is irrelevant to this feature (see `docs/integrations.md`).
WorldGuard 7.0.20 refuses older Minecraft ("Requires MC 26.3+"), which matches our pinned 26.3.

## 2. Plugin integration

### Maven
Repo (**[V]** [WorldGuard docs: dependency](https://worldguard.enginehub.org/en/latest/developer/dependency/)): `https://maven.enginehub.org/repo/`.
```xml
<repository>
  <id>enginehub</id>
  <url>https://maven.enginehub.org/repo/</url>
</repository>

<dependency>
  <groupId>com.sk89q.worldguard</groupId>
  <artifactId>worldguard-bukkit</artifactId>
  <version>7.0.20</version>
  <scope>provided</scope>
</dependency>
<dependency>
  <groupId>com.sk89q.worldedit</groupId>
  <artifactId>worldedit-bukkit</artifactId>
  <version>7.4.6</version>
  <scope>provided</scope>
</dependency>
```
- Both versions exist in the repo **[V]** (maven-metadata: WG `release` 7.0.20, WE `release` 7.4.6; 7.0.20 POM read). Pin exact versions, not `7.1.0-SNAPSHOT`/`8.0.0-SNAPSHOT` (those are dev lines).
- The WG docs say: "Some WorldGuard objects come from WorldEdit. If you use them directly, add WorldEdit as a dependency too." We use `BukkitAdapter` (worldedit-bukkit), `BlockVector3` (worldedit-core), so add the WorldEdit artifact explicitly. `worldedit-bukkit:7.4.6` pulls `worldedit-core:7.4.6` at compile scope **[V]** (POM). `worldguard-bukkit:7.0.20` pulls `worldguard-core:7.0.20` **[V]**. Both come in as `provided` transitively, so nothing is shaded. Run `mise run build` and check the jar contains no `com/sk89q`.
- Nothing else needs excluding. The project's Java 25 is fine (WG 7.0.20 targets 26.3, which needs Java 25 **[SK]**).

### depend vs softdepend: recommend hard `depend: [WorldGuard]`
Reasons: after this feature the pedestal lives in a plot (Decision 2), so the game is unusable without WorldGuard; `docs/integrations.md` rule 2 says to use `depend` then. `depend` also guarantees WorldGuard is loaded **before** our `onLoad`, which flag registration requires **[V]** ([custom flags](https://worldguard.enginehub.org/en/latest/developer/regions/custom-flags/): "make sure it loads before your plugin"). WorldGuard itself depends on WorldEdit, so listing only `WorldGuard` is enough **[SK]** (list both if you want the log to be explicit).
- `plugin.yml`: `depend: [WorldGuard]` (and `[WorldGuard, WorldEdit]` is harmless). The `optimize` feature's later `paper-plugin.yml` must express the same ordering (`load: BEFORE` for the dependency entry) **[SK]**, noted for that feature.
- With `depend` and WG missing, Paper logs "missing dependency" and refuses to load our plugin; that is the clear startup error. WorldGuard can also be present but **disabled** (it disables itself on an unsupported MC version), so the coder adds an `onEnable` check: `Plugin wg = getServer().getPluginManager().getPlugin("WorldGuard"); if (wg == null || !wg.isEnabled()) { log "plot.missing-worldguard" message in console; getServer().getPluginManager().disablePlugin(this); return; }` **[SK]** (Bukkit enables dependents even if the dependency failed to enable). Also log WG's version (`wg.getPluginMeta().getVersion()`) and warn if it is lower than 7.0.20.
- The message key `plot.missing-worldguard` is then for the console and for the case where region data is unavailable at runtime (below), not for a plugin that runs without WG.
- Keep all `com.sk89q` imports in one hook package (`worldguard/`), per integrations rule 3. With `depend` this is not needed for loading safety but keeps the plugin testable (tests use an interface `PlotRegions` fake).

### Custom flag registration in `onLoad` **[V]** (custom flags page)
```java
@Override public void onLoad() {
    FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
    BAKERY_PLOT  = register(registry, new StringFlag("bakery-plot"));
    BAKERY_HOUSE = register(registry, new StringFlag("bakery-house"));
}
private static StringFlag register(FlagRegistry registry, StringFlag flag) {
    try { registry.register(flag); return flag; }
    catch (FlagConflictException e) {
        Flag<?> existing = registry.get(flag.getName());
        if (existing instanceof StringFlag sf) return sf;   // same type: reuse (e.g. after a plugin reload)
        throw new IllegalStateException("Flag " + flag.getName() + " already registered with another type", e); // serious conflict
    }
}
```
- **[V]** Registration must happen before WorldGuard is enabled; "After WorldGuard is enabled, the FlagRegistry is locked." `register` throws `FlagConflictException` for an existing name; you may fetch the existing flag and check its type, a different type is a serious conflict.
- **[V]** Once registered, WorldGuard loads, saves and lets users set the flag with `/rg flag`. If our plugin is removed, the flag stays saved on regions but does nothing.
- `/reload` and plugin hot-reload: the page says nothing. **[SK]** Registration is only possible in the `onLoad` window; `/bakery reload` can never re-register, so it must only re-read `plots.yml` and re-resolve regions. A server-wide `/reload` is unsupported by Paper; the conflict handling above makes a second `onLoad` harmless if WG's registry survived. Do not register from `onEnable` (throws because the registry is locked).
- Flag name: letters, digits, `-`, `_` are fine **[SK]** (built-in flags like `block-break` use hyphens).
- Flags are visible in `/rg flag <region>` and `/rg info`, which is the admin UI.

### RegionContainer / RegionQuery **[V]** ([spatial queries](https://worldguard.enginehub.org/en/latest/developer/regions/spatial-queries/), [flag queries](https://worldguard.enginehub.org/en/latest/developer/regions/flag-calculation/), [native objects](https://worldguard.enginehub.org/en/latest/developer/native-objects/), [managers](https://worldguard.enginehub.org/en/latest/developer/regions/managers/))
```java
RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
RegionQuery query = container.createQuery();                       // cached ~1-2 s
ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(location));   // com.sk89q.worldedit.util.Location
RegionManager rm = container.get(BukkitAdapter.adapt(world));      // may be null (protection disabled / failed to load)
```
- Coordinates: `BukkitAdapter.adapt(Location)` and `BukkitAdapter.adapt(World)` (package `com.sk89q.worldedit.bukkit`); `WorldGuardPlugin.inst().wrapPlayer(player)` gives a `LocalPlayer` **[V]**. `BukkitAdapter.asBlockVector(Location)` exists **[SK]**; or `BlockVector3.at(x, y, z)` **[V]**.
- "Which plot/house contains this block" per block-change: `query.getApplicableRegions(adaptedLoc)`, iterate `for (ProtectedRegion r : set)` **[V]**; or flag lookup `set.queryValue(null, FLAG)` which returns the value or `null` **[V]** (state flags return the "best" value; other flags the first found). Pass `null` as `RegionAssociable` for flags that are not player-related **[V]**, which holds for our custom flags.
- `container.get(world)` can return `null`; `ApplicableRegionSet.isVirtual()` is true for `PermissiveRegionSet` (protection disabled) or `FailedLoadRegionSet` (data failed to load) **[V]**. Treat both as "no plot" and log once.
- Thread safety **[V]**: the region API is completely thread safe; do not modify returned collections. We read it on the main thread anyway.
- Enter/leave: only compute when the player changes block (`PlayerMoveEvent` with `hasChangedBlock()`), compare `x/y/z` of from/to. The cached query makes this cheap. There is no region define/redefine event in the docs' list (the page lists "Region Events" but I could not read it); so `/bakery reload` re-resolves regions, which matches the brief.
- Do not keep `ProtectedRegion` objects across `/rg redefine`, `/rg load` or `/bakery reload` **[SK]**: `addRegion` replaces a region with the same id **[V]** (so `redefine` creates a new object). Cache the **id** and call `rm.getRegion(id)` when needed, and rebuild derived data (outline points, bounding-box block lists) in `reload`.
- Region ids are case-insensitive and validated by `ProtectedRegion.isValidId(String)` **[V]**.
- `RegionManager#load()` reloads from disk, `save()/saveChanges()` blocks; changes auto-save after a delay **[V]**. We never write regions.

### Reading regions: polygon and cuboid **[V]** ([regions page](https://worldguard.enginehub.org/en/latest/developer/regions/protected-region/))
```java
ProtectedRegion r = rm.getRegion(id);
BlockVector3 min = r.getMinimumPoint(), max = r.getMaximumPoint();    // bounding box, inclusive block coords [SK for exact names; both exist on ProtectedRegion]
if (r instanceof ProtectedPolygonalRegion poly) { List<BlockVector2> pts = poly.getPoints(); }   // vertices [V]
else if (r instanceof ProtectedCuboidRegion) { /* the box is min..max */ }
boolean inside = r.contains(BlockVector3.at(x, y, z));                // [V]; contains(int x,int y,int z) also exists [SK]
```
- `ProtectedPolygonalRegion` = 2D polygon of >= 3 `BlockVector2` points extended between min Y and max Y **[V]**; `getMinimumPoint()/getMaximumPoint()` give the bounding box including Y **[SK]** (in WorldEdit's polygon: min/max Y are `minY`/`maxY` also available through the region, `getMinimumPoint().y()`).
- `GlobalProtectedRegion` has no area (`-g` in `/rg define`); reject it for plot/house with a clear message **[V]**.
- **Bounding-box iteration with polygon filtering:** loop over `min..max` in x/y/z and keep `r.contains(x, y, z)`. For `ProtectedPolygonalRegion` the test is cheap; do it **once** when the cinematic starts (and cache per house), never per tick. For the outline (`/bakery plot show`): cuboid = the 12 box edges; polygon = for each consecutive pair of `getPoints()` (closing the ring) draw a line at min Y and max Y, plus a vertical line at every vertex. Note: a polygon's point coordinates are block coordinates; the region covers the block from `x` to `x+1`, so draw particles at `x`, `x+1` appropriately (outer corner = max + 1 for the max side). Walk the edge in `particle-step` blocks. Particles per player: `Player#spawnParticle`.
- Cap the house size at load (e.g. warn above ~40,000 blocks) so a mistyped `//wand` selection cannot freeze the server.

## 3. Recommendation: custom flags vs region names

**Recommend custom `StringFlag`s, not StateFlags, with the plot id as the value:**
- `bakery-plot: <plotId>` on the plot region. `bakery-house: <plotId>` on the house region. Admin: `/rg flag spawn_plot bakery-plot main` and `/rg flag spawn_house bakery-house main`.
- Why `StringFlag` (CONTRACT CHANGE): a `StateFlag` (allow/deny) carries no plot id, so with multiple plots you would still need a region-name mapping. A string value solves "plot to house" directly: the house region whose `bakery-house` equals the plot's `bakery-plot`. Multiple plots = multiple id values, no plugin config for region names.
- Lookup "which plot am I in": `set.queryValue(null, BAKERY_PLOT)` (one call). "Where is the house for plot X": at load/reload scan `rm.getRegions().values()` once for `BAKERY_HOUSE == X` and store the region id. An id with two houses or no house: log an error naming both regions and skip the plot.
- Benefits over names in config: survives `/rg rename`-style changes (WG has no rename; admin recreates), `/rg redefine` keeps flags, `/rg info` shows the link, and `plots.yml` no longer duplicates region ids.
- Costs: registration in `onLoad` (needs hard `depend`, done), an admin must set two flags, and an unknown plot id in a flag needs a clear message.
- `plots.yml` shape: keep `plots.<id>.{world, viewpoint, pedestal, chimney?}`. `plot-region` and `house-region` become **derived** values (written as comments or kept as a cache `region ids at last load` for diagnostics only). If the orchestrator prefers the original shape, names work too: the fallback is `plots.<id>.plot-region` / `house-region` strings, looked up with `rm.getRegion(name)`; behaviour above stays identical except discovery.
- Plot id characters: `[a-z0-9_-]+` (also used as a YAML key and in `house_seen`). Validate on use.
- World: the plot's world comes from `plots.yml` (use the world key `NamespacedKey`, per `docs/paper-26x-reference.md` rule 2). If a flagged region is found in another world, warn.
- Region priority: put the house inside the plot; if house is a child (`/rg setparent`), flags are inherited; since the two flags differ there is no conflict. Give the house a higher priority than the plot only if the admin sets different protection flags on it.

## 4. Recommended default flags for the plot

WorldGuard docs **[V]** ([flags](https://worldguard.enginehub.org/en/latest/regions/flags/)): non-members cannot build by default; `build` is implied by membership; protection flags like `block-break`, `interact`, `pvp` fall back to `build`; "Protection flags should generally be left unset. Use membership or `passthrough` to control access instead." Therefore the minimum is: **a region with no owners/members already protects**. Our plugin never sets flags; `docs/integrations.md` just lists these for the admin:

```
/rg define bakery_plot            (after //wand)
/rg flag bakery_plot bakery-plot main
/rg define bakery_house
/rg flag bakery_house bakery-house main
/rg setparent bakery_house bakery_plot            (optional)
# protection for everyone but owners/members (admins use bypass):
/rg flag bakery_plot build deny                   (explicit; the default for non-members already denies)
/rg flag bakery_plot use allow                    (doors, trapdoors, buttons, levers; excludes inventories)
/rg flag bakery_plot chest-access deny            (stops chests/barrels in the cottage; also covered by build)
/rg flag bakery_plot mob-spawning deny
/rg flag bakery_plot tnt deny
/rg flag bakery_plot creeper-explosion deny
/rg flag bakery_plot other-explosion deny
/rg flag bakery_plot fire-spread deny             (needs the "high frequency flags" setting in WG's config.yml to take effect)
/rg flag bakery_plot lava-fire deny               (same)
/rg flag bakery_plot pvp deny                     (optional)
/rg flag bakery_plot invincible allow             (optional, friendly plot)
```
- **Door interaction:** prefer `use allow` (doors/levers) over `interact allow` (also entities and mounts). Note the click on the cookie/panel is an `Interaction` entity; WG's `interact`/`build` checks apply to entity interactions too **[SK]**, and the player-sent right click on our per-player entities passes through WG as "interact entity". If `build` is denied for non-members, `PlayerInteractAtEntityEvent` on our Interaction entities may be cancelled by WG before our listener sees it unless our listener has `ignoreCancelled = false` and `EventPriority.HIGHEST`/`MONITOR` handling. **Test this in game and report**; if clicks fail inside the plot, set `interact allow` on the plot (and our plugin is the one that cancels game-relevant effects). This is the one real risk to the bakery-core click flow.
- No `entry`/`exit` flags: they would block the player we want to walk onto the plot.
- `/bakery house place|undo` write blocks through the API, which does not go through WG, so protection does not block the plugin.
- Admins who build by hand need `worldguard.region.bypass.<world>` (op has it) or membership.

## Pitfalls
- Passing a Bukkit `Location` directly to a WG method does not compile; always `BukkitAdapter.adapt(...)`.
- Registering flags after WG enabled throws; registering twice throws `FlagConflictException`.
- `container.get(world)` is null for a world with regions disabled.
- Region ids are case-insensitive; flags values (plot ids) are not. Normalise to lower case.
- `RegionQuery` results are cached 1-2 s; after `/rg redefine` a `/bakery reload` and a short delay may be needed. Use `container.createQuery()` each time (cheap).
- Never cache `ProtectedRegion`; cache ids.
- `//wand` selections are in the WorldEdit player session and are not available to us (we have no selection commands).

## Sources
- WorldGuard docs: [dependency](https://worldguard.enginehub.org/en/latest/developer/dependency/), [regions](https://worldguard.enginehub.org/en/latest/developer/regions/), [managers](https://worldguard.enginehub.org/en/latest/developer/regions/managers/), [protected regions](https://worldguard.enginehub.org/en/latest/developer/regions/protected-region/), [spatial queries](https://worldguard.enginehub.org/en/latest/developer/regions/spatial-queries/), [flag calculation](https://worldguard.enginehub.org/en/latest/developer/regions/flag-calculation/), [custom flags](https://worldguard.enginehub.org/en/latest/developer/regions/custom-flags/), [native objects](https://worldguard.enginehub.org/en/latest/developer/native-objects/), [flags](https://worldguard.enginehub.org/en/latest/regions/flags/)
- Versions: [Modrinth API WorldGuard](https://api.modrinth.com/v2/project/worldguard/version?limit=5), [dev.bukkit.org WorldGuard files](https://dev.bukkit.org/projects/worldguard/files), [EngineHub WorldGuard builds](https://builds.enginehub.org/job/worldguard), [EngineHub WorldEdit builds](https://builds.enginehub.org/job/worldedit), [Hangar WorldEdit 7.4.6-beta-01](https://hangar.papermc.io/EngineHub/WorldEdit/versions/7.4.6-beta-01), [Hangar API WorldEdit](https://hangar.papermc.io/api/v1/projects/EngineHub/WorldEdit/versions?limit=6), [FAWE releases (GitHub API)](https://api.github.com/repos/IntellectualSites/FastAsyncWorldEdit/releases?per_page=6)
- Maven: [worldguard-bukkit metadata](https://maven.enginehub.org/repo/com/sk89q/worldguard/worldguard-bukkit/maven-metadata.xml), [worldedit-bukkit metadata](https://maven.enginehub.org/repo/com/sk89q/worldedit/worldedit-bukkit/maven-metadata.xml), [worldguard-bukkit 7.0.20 POM](https://maven.enginehub.org/repo/com/sk89q/worldguard/worldguard-bukkit/7.0.20/worldguard-bukkit-7.0.20.pom), [worldedit-bukkit 7.4.6 POM](https://maven.enginehub.org/repo/com/sk89q/worldedit/worldedit-bukkit/7.4.6/worldedit-bukkit-7.4.6.pom)

## Hand-off

**plugin-coder** (pass 1)
- `pom.xml`: repo `https://maven.enginehub.org/repo/`, `worldguard-bukkit:7.0.20` and `worldedit-bukkit:7.4.6`, both `provided`. `plugin.yml`: `depend: [WorldGuard]`. Both under "Needs human review".
- `onLoad`: register `StringFlag` `bakery-plot` and `bakery-house` with `FlagConflictException` handling (snippet above). `onEnable`: check WG present and enabled, else log `plot.missing-worldguard` and disable.
- One `worldguard/` hook package, an interface `PlotRegions` (tests use a fake): `plotAt(Location) -> Optional<plotId>`, `inHouse(Location)`, `houseBlocks(plotId)` (bounding box filtered by `contains`), `outline(plotId)` points. Cache region ids, never `ProtectedRegion`; rebuild on `/bakery reload`.
- Region tracking only on block change; use `container.createQuery()` + `getApplicableRegions(BukkitAdapter.adapt(loc))` / `queryValue(null, flag)`.
- Test the click flow inside a `build deny` plot and report (see flags section). Document the flags and the WG section in `docs/integrations.md` (compatibility row: WG 7.0.20 + WE 7.4.6, MC 26.3, checked 2026-10-19).
- Keep the plot id `[a-z0-9_-]+`; log an error for duplicate houses.

**vfx-designer**: `/bakery plot show` outline helper may be provided by the coder; no WG classes in the vfx package.

**model-designer / sound-designer**: nothing from this note.

CONTRACT CHANGES (proposed): flags `bakery-plot` and `bakery-house` become **StringFlag** (value = plot id) instead of StateFlag; `plots.yml` `plot-region`/`house-region` become derived/optional (flag lookup is the source of truth). `plugin.yml` `depend: [WorldGuard]`. Add a `docs/integrations.md` compatibility row for WorldGuard/WorldEdit/FAWE.
