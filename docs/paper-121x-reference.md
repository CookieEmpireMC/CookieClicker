# Paper 1.21.x plugin development reference

Reference for all agents working on a plugin that targets **Paper 1.21.x** (latest: **1.21.11**).
For 26.x projects, use `docs/paper-26x-reference.md` instead. If anything here conflicts with the official docs, the official docs win: https://docs.papermc.io/paper/dev/

Last checked: 2026-10-18

---

## 1. Which reference applies?

Check the `paper-api` version in `pom.xml`:

| Version in pom.xml | Read |
|---|---|
| `1.21.x-R0.1-SNAPSHOT` | this file |
| `26.x.build.…` | `paper-26x-reference.md` |

**1.21.x is not one target.** Item and resource pack features changed a lot between minor versions (see section 3). Always note the exact minor version, e.g. 1.21.4 vs 1.21.11, and don't use features newer than it.

---

## 2. Build setup (Maven)

Java and Maven are pinned in `mise.toml` (`java = "temurin-21"`). Build with `mise run build`, test with `mise run test`. See `docs/toolchain.md`.

| | 1.21.x |
|---|---|
| Java | **21** |
| API version string | `1.21.11-R0.1-SNAPSHOT` (no way to pin a specific build) |
| Server runtime | Mojang-mapped since 1.20.5 |
| Adventure | 4.x |
| NMS | Gradle + paperweight-userdev only — **not available in this Maven project** |

```xml
<properties>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <paper.version>1.21.11-R0.1-SNAPSHOT</paper.version>
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
```

plugin.yml:

```yaml
api-version: '1.21'
```

The `-SNAPSHOT` version always resolves to the newest build of that version, so builds aren't perfectly reproducible. That's expected on 1.21.x.

---

## 3. What differs inside 1.21.x (important for items & resource packs)

Verify specifics against the Minecraft Wiki and the Javadocs for the exact version before relying on them.

- **1.21–1.21.1:** classic resource pack item models. Custom items use `CustomModelData` overrides on a vanilla item's model.
- **1.21.2/1.21.3:** new item data components such as `item_model` and `equippable`. An item can point at its own model ID without overriding a vanilla model.
- **1.21.4:** **item model definitions** (`assets/<ns>/items/*.json`) replace the old override system. This is the modern way to do custom items; old override-based packs need conversion.
- **1.21.5+:** `custom_model_data` changed from a single integer into lists (floats, flags, strings, colors) that item model definitions can select on. Code that sets an int CustomModelData is legacy here.
- **1.21.6+:** Dialog API (custom server-side dialog screens).
- **1.21.11:** last version on Java 21 and the last with obfuscated server jars. Gamerule API changes and bed event changes (`BedEnterAction`).

Rule of thumb: for a server on **1.21.4 or newer**, use item model definitions plus the `item_model` component. Only use the `CustomModelData` override approach when the server is older than 1.21.4.

---

## 4. API notes for 1.21.x

- **Commands:** use the Brigadier-based Paper command API (lifecycle registration, available since 1.20.6) for new commands.
- **Data component API:** exists but is **marked experimental** in 1.21.x and may change. It's fine to use, but keep usage in one place so it's easy to update.
- **Registries:** many former enums (e.g. `Biome`, `Sound`, `Enchantment`) are registry-backed. Use `RegistryAccess` / `Registry` lookups and keys, not `valueOf` or ordinals.
- **Worlds:** dimensions live in separate folders (`world`, `world_nether`, `world_the_end`). Still, prefer world keys over names so code survives a later move to 26.x.
- **Adventure 4 / text:** follow `docs/minimessage.md` for all player-facing text. MiniMessage presets don't exist on 1.21.x; build restricted instances with the builder. Avoid deprecated Adventure API, which was removed in Adventure 5 (Paper 26.2).
- **Scheduler:** async work for I/O only; Bukkit API calls stay on the main thread.

---

## 4b. Rules for this project

1. Use only API that exists in the project's exact 1.21.x version; check the Javadocs for that version when unsure.
2. Prefer world keys over world names, even though 1.21.x still uses names (eases a later move to 26.x).
3. Use the Brigadier-based Paper command API (lifecycle registration) for new commands.
4. On 1.21.4+, use item model definitions plus the `item_model` component for custom items; legacy `CustomModelData` overrides only below 1.21.4.
5. Maven + Paper API only: no NMS, no reflection into server internals. Build through mise (`docs/toolchain.md`).
6. All player-facing text follows `docs/minimessage.md` (Adventure 4 + MiniMessage; no presets on 1.21.x).
7. For ItemsAdder and ModelEngine, follow `docs/integrations.md`. Both fully support 1.21.x.
8. Avoid deprecated API, so the code ports cleanly to 26.x.

---

## 5. Supporting both 1.21.x and 26.x in one jar

Only do this if the project explicitly asks for it. Otherwise, target one version.

- Compile against the **oldest** supported version (e.g. 1.21.11, Java 21 release). A Java 21 build also runs on Java 25.
- Use only API that exists in both and wasn't removed in 26.x. In particular, avoid:
  - deprecated Adventure 4 API, and `BookMeta` used as an Adventure `Book`
  - assuming `MagmaCube instanceof Slime`
  - storing PDC on beds
  - hardcoded world folder paths, or world names as storage keys
  - API that 26.x deprecated (`PlayerAnimationEvent`, `Vex#getSummoner`, `World#setSpawnFlags`, etc.)
- Parse server versions without assuming they start with `1.`.
- Test on a real server of **each** version. Compiling against 1.21.11 doesn't prove it runs on 26.x.

---

## Links

- Docs (dev): https://docs.papermc.io/paper/dev/
- Javadocs 1.21.11: https://jd.papermc.io/paper/1.21.11/ (swap the version in the URL for older ones)
- Data components: https://docs.papermc.io/paper/dev/data-component-api/
- Command API: https://docs.papermc.io/paper/dev/command-api/basics/introduction/
- Release notes: https://papermc.io/news/1-21-11 · https://papermc.io/news/1-21-9-and-10 · https://papermc.io/news/1-21-7
- Item model definitions (Minecraft Wiki): https://minecraft.wiki/w/Items_model_definition
