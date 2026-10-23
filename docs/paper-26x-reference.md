# Paper 26.x plugin development reference

Reference for all agents working on a plugin that targets **Paper 26.x** (latest stable: **26.3**, as of Oct 2026).
For 1.21.x projects (`1.21.x-R0.1-SNAPSHOT` in pom.xml), use `docs/paper-121x-reference.md` instead.
If anything here conflicts with the official docs, the official docs win: https://docs.papermc.io/paper/dev/

Last checked: 2026-10-18

---

## 1. Build setup (what's different from 1.21.x)

**This project uses Maven, with Java and Maven pinned by mise.** Build with `mise run build`, test with `mise run test` (see `docs/toolchain.md`). Don't add Gradle files.

| | 1.21.x | 26.x |
|---|---|---|
| Java | 21 | **25** (minimum) |
| API version string | `1.21.11-R0.1-SNAPSHOT` | `[26.3.build,)` range, or a pinned build like `26.3.build.<n>-stable` |
| Server jar | obfuscated, Paper remapped at runtime | **unobfuscated**, remapper removed |
| Reobfuscation | supported (via Gradle userdev) | **removed / does not work** |
| Adventure | 4.x | **5.x** (since 26.2) |

### pom.xml

```xml
<properties>
    <maven.compiler.release>25</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <!-- Pin an exact stable build for reproducible builds; bump it deliberately -->
    <paper.version>26.3.build.NN-stable</paper.version>
</properties>

<repositories>
    <repository>
        <id>papermc</id>
        <url>https://repo.papermc.io/repository/maven-public/</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>io.papermc.paper</groupId>
        <artifactId>paper-api</artifactId>
        <version>${paper.version}</version>
        <scope>provided</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.14.0</version> <!-- any recent version that supports Java 25 -->
        </plugin>
    </plugins>
</build>
```

**Version notes (Maven):**
- Replace `NN` with the latest **stable** build number. Find it at https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml or on the Paper downloads page.
- Paper's docs show the range `[26.3.build,)`. That works, but it's open-ended: once 26.4 ships it will silently pull the 26.4 API, and it can also pick up `-alpha`/`-beta` builds. Prefer pinning an exact `-stable` build, or at least cap it: `[26.3.build,26.4)`.
- Never use the old `-R0.1-SNAPSHOT` form for 26.x.
- The build needs **JDK 25**. `mise.toml` must pin `java = "temurin-25"`; always build via `mise run` / `mise exec` so that JDK is used (see `docs/toolchain.md`).

### Server internals (NMS) — not available with Maven

`paperweight-userdev`, the only supported way to compile against server internals, is a **Gradle-only** plugin, and Paper's docs mark Maven as discouraged. With Maven, this project uses the **Paper API only**:
- Do not depend on the server jar or try to hack NMS in.
- Reflection into server internals is also off-limits (fragile, and names differ from 1.21.x tutorials since jars are now unobfuscated).
- If a feature truly can't be done with the API, stop and report it — switching the build to Gradle would be a project-level decision, not something to do inside a feature.

### paper-plugin.yml (this repo; same field in plugin.yml)

```yaml
api-version: '26.3'
```

Don't parse the Minecraft version assuming it starts with `1.` — versions are now `<year>.<drop>[.<patch>]`, e.g. `26.3`, `26.1.2`.

---

## 2. API changes by version

### 26.1 (Apr 2026)
- **World storage:** Nether/End no longer live in `world_nether` / `world_the_end`; all dimensions are under `world/dimensions/minecraft/<dimension>/`. Per-world `paper-world.yml` lives there too. Don't hardcode world folder paths.
- **World keys over names:** `WorldInfo#getName` and related API are obsolete (may be deprecated later). Use `getKey()` / `NamespacedKey`-based lookups instead of world names.
- **Per-world time / clocks:** new `ClockTimeSkipEvent` (parent of `TimeSkipEvent`). If the server sets `time.affects-all-worlds`, most time changes fire **only** the parent event — listen to `ClockTimeSkipEvent` if you care about time skips.
- New API: `World#locateNearestPoi` / `locateAllPoiInRange`, `WorldCreator#forcedSpawnPosition`, `EntityLungeEvent`, `PlayerSwapWithEquipmentSlotEvent`, `ItemCraftedEvent`, `RecipeChoice.ItemTypeChoice`, `GameRule#getDefaultValue`, `Damageable#kill`, damage source on vehicle damage/destroy events.

### 26.2 (Jun 2026)
- **Adventure 5:** previously deprecated API removed.
  - `BookMeta` no longer extends Adventure's `Book` — use `BookMeta`'s own page methods, not the removed builder.
  - Deprecated `ClickEvent` / `HoverEvent` usage removed.
  - Migration guide: https://docs.papermc.io/adventure/migration/adventure-4.x/
- **Beds are no longer block entities** → no PDC on beds. Store bed-related data elsewhere (e.g. chunk PDC keyed by position). `AsyncServerDataFixerRemoveBlockEntityEvent` can rescue old bed PDC data.
- **Cube mobs:** new `AbstractCubeMob` parent. `MagmaCube` no longer extends `Slime`; `SlimeSplitEvent#getEntity` returns `AbstractCubeMob`. Fix any `instanceof Slime` checks that were meant to include magma cubes.
- Deprecated: `Vex#getSummoner` (→ `getOwner`), `World#setSpawnFlags` / `getAllowAnimals`, `PointedDripstone` block data (→ `Speleothem`).
- New: `EntityIgniteEvent` (parent of `CreeperIgniteEvent`), trim material/pattern registry events, client brand on `PlayerCommonConnection`.

### 26.3 (Sep 2026)
- Most of the **data component API is no longer experimental** — it's the preferred way to work with item properties (custom models, names, lore, etc.).
- New events: `EntityBreakEvent`, `EntityBreakByEntityEvent`, `EntityConstructEvent` (golems/Wither from block patterns), `EntityCollideWithEntityEvent` (control pushing), `SulfurCubeSwallowItemEvent`, `PlayerCodeOfConductSendEvent`.
- New API: `Player#postEffects`, `Entity#getRemovalReason` + `RemovalReason`, `CreakingHeart` protector access, `Mob#setDespawnInPeacefulOverride` (un-deprecated).
- Deprecated: `PlayerAnimationEvent` (→ `PlayerArmSwingEvent`), `PotionEffectTypeCategory` (→ `PotionEffectType.Category`), `PotionBrewer` (→ data-driven `BrewingRecipe`).

### Coming in 26.4
- Paper is planning to merge the Bukkit/Spigot/Paper config files into one. Don't build plugin logic that reads `bukkit.yml` / `spigot.yml` directly. Tracking: https://github.com/PaperMC/Paper/issues/14304

---

## 3. Rules for this project

1. Never use APIs marked deprecated in 26.3 Javadocs when a replacement exists.
2. Use world keys, not world names, for storage and lookups.
3. Use the Brigadier-based Paper command API (lifecycle registration) for new commands.
4. Use the data component API for item properties (custom item models for resource packs included).
5. Maven + Paper API only: no NMS, no reflection into server internals, no reobfuscation. Build through mise (`docs/toolchain.md`).
6. All player-facing text follows `docs/minimessage.md` (Adventure 5 + MiniMessage; presets available).
7. For ItemsAdder and ModelEngine, follow `docs/integrations.md` — and check its compatibility table: ModelEngine officially supports up to 26.2 only.
8. If unsure whether an API exists in 26.3, check the Javadocs before using it — don't assume 1.21.x knowledge still applies.

---

## Links

- Docs (dev): https://docs.papermc.io/paper/dev/
- Project setup (has a Maven tab): https://docs.papermc.io/paper/dev/project-setup/
- Javadocs 26.3: https://jd.papermc.io/paper/26.3/
- Data components: https://docs.papermc.io/paper/dev/data-component-api/
- Command API: https://docs.papermc.io/paper/dev/command-api/basics/introduction/
- Adventure 4 → 5 migration: https://docs.papermc.io/adventure/migration/adventure-4.x/
- Release notes: https://papermc.io/news/26-1 · https://papermc.io/news/26-2 · https://papermc.io/news/26-3
