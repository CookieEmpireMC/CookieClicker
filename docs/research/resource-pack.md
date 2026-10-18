# Resource pack for 26.3: format, sending it, coexisting with ItemsAdder, and shipping JDBC drivers

**Target version:** Paper 26.3 (`26.3.build.205-stable`, Java 25, Adventure 5), Minecraft resource pack format **97.1**. Feature `bakery-core`. Researched 2026-10-18.
Project state: plain pack in `resourcepack/` (no ItemsAdder/ModelEngine dependency). ItemsAdder is installed on the game server, and its newest release lists Paper support only up to 26.2 (`docs/integrations.md`): the coexistence section below is **not verified against that server**.
"Verified" = read in the 26.3 Javadocs, Adventure Javadocs, Paper docs or the wiki.

## Summary
- `pack.mcmeta` for 26.3: `min_format` and `max_format`, no `pack_format`, no `supported_formats`. The existing file (`97` / `97`) is correct.
- Send the pack on join with `player.sendResourcePacks(ResourcePackRequest)` (Adventure): a stable UUID, `URI`, lower-case SHA-1 hex, prompt Component, `required` from config (default `false`, so declining works with vanilla fallbacks). Never use the legacy `Player#setResourcePack`: it replaces the player's other packs.
- Packs stack from 1.20.3, so our pack and ItemsAdder's can both be loaded. Our assets live only in the `cookieclicker` namespace so nothing needs merging. Fallback if stacking fails on the real server: merge our `assets/` into ItemsAdder's `contents/` and turn our sending off (`resource-pack.enabled: false`).
- JDBC drivers: **use `libraries:` in `plugin.yml`** for H2, MariaDB and (optionally) HikariCP, with `provided` scope in Maven. Do not shade. Register drivers explicitly.

## Version notes
| Topic | Detail |
|---|---|
| Format number | Resource pack format for 26.3 (all of 26.3 from pre-release 1 to release) is **97.1** (wiki, Pack format). 26.2 is 88.0, 26.1 is 84.0. |
| `pack.mcmeta` keys | Since 25w31a (1.21.9) packs use `min_format` / `max_format` instead of `pack_format`. A bare integer in `min_format` means minor 0; a bare integer in `max_format` means "any minor". `[major, minor]` arrays are also valid. `pack_format` is optional and only needed to support old versions (resource packs older than format 65). `supported_formats` is not needed. (Minecraft Wiki, Pack.mcmeta.) |
| Why `97` and not `[97,1]` | Minor versions are for non-breaking changes. `min_format: 97` (= 97.0) is accepted by 97.1 clients, and `max_format: 97` accepts every 97.x. |
| Multi-pack | Since Minecraft 1.20.3 the server can send several packs and remove them individually (Paper docs, Adventure resource packs). |
| `Player#setResourcePack(...)` | The overloads with String/byte[] are deprecated in favour of `Audience#sendResourcePacks(ResourcePackRequest)` (26.3 Javadoc). Paper's implementation clears previous packs first (Paper issue #10027). Avoid. |
| Adventure 5 | `ResourcePackInfo` and `ResourcePackRequest` are the same API as in Adventure 4.15+; the signatures below were verified in the 4.24 Javadocs and the Paper docs. Re-check with `mise run build` (a changed signature fails to compile). |

## Pack file layout
```
resourcepack/                         <- zip the CONTENTS of this folder (pack.mcmeta at the zip root)
  pack.mcmeta
  assets/cookieclicker/
    items/cookie.json                              (cookieclicker:cookie)
    items/building/villager_baker.json             (cookieclicker:building/villager_baker)
    items/building/wheat_farm.json                 (cookieclicker:building/wheat_farm)
    models/item/...                                (models)
    textures/item/...                              (textures)
    font/hud.json                                  (cookieclicker:hud)
    textures/font/hud_cookie.png
    sounds.json                                    (sound-designer)
    sounds/...ogg                                  (sound-designer)
```
`pack.mcmeta` (26.3):
```json
{
  "pack": {
    "description": "Cookie Empire resources",
    "min_format": 97,
    "max_format": 97
  }
}
```
(Optional: `"min_format": 88` if 26.2 clients should be allowed; test before promising it.)
Only the `cookieclicker` namespace. Do not add files in `minecraft/` (not merged with other packs; it is how pack conflicts start).

## Plugin side

### Config (`config.yml`)
```yaml
resource-pack:
  enabled: true
  url: "https://github.com/<owner>/<repo>/releases/download/<tag>/cookieclicker-pack.zip"
  sha1: "<40 hex chars, lower-case>"
  required: false
  prompt: "<gold>Cookie Empire</gold> needs a small resource pack for the cookie, icons and sounds."   # MiniMessage
```
Empty `url` or `sha1`: do not send, log one warning. Validate `sha1` with the pattern `[0-9a-fA-F]{40}` and normalise to lower case.

### Sending (main thread; the request only queues packets)
```java
UUID packId = UUID.nameUUIDFromBytes("cookieclicker:resourcepack".getBytes(StandardCharsets.UTF_8)); // stable, no config needed
ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(packId, URI.create(url), sha1);   // static factory (UUID, URI, String hash)
ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
        .packs(info)
        .prompt(Text.parse(promptMiniMessage))      // Component, nullable
        .required(required)
        .replace(false)                              // keep other plugins' packs (ItemsAdder)
        .build();
player.sendResourcePacks(request);                   // Audience#sendResourcePacks(ResourcePackRequestLike)
```
Verified: `ResourcePackInfo.resourcePackInfo(UUID, URI, String)` and `resourcePackInfo()` (builder); `ResourcePackRequest.resourcePackRequest()` (builder), `packs()`, `replace()`, `required()`, `prompt()`, `callback()`, `addingRequest(...)`; the Paper docs example builds the request with `packs(...)`, `prompt(...)`, `required(...)` and calls `sendResourcePacks(request)`. Builder method names `id/uri/hash` exist on `ResourcePackInfo.Builder` (Paper docs example); `replace(boolean)` is the toggle that clears applied packs before pushing.
- The **hash is the SHA-1 of the zip**, as a hex string. Compute it with `sha1sum cookieclicker-pack.zip` (or the build task) after every pack change and put it in `config.yml`. A wrong hash makes the client fail the download (`FAILED_DOWNLOAD`).
- The pack UUID is the pack's identity. Re-sending the same UUID with a new URL/hash replaces it on the client.
- `required(true)` disconnects players who decline (vanilla client behaviour). Keep `false` by default: the brief wants vanilla fallbacks to work.
- When to send: in `PlayerJoinEvent`, via a 1-tick-delayed task (no teleporting in that event; Javadoc warns). Do not block the join with the send.
- On `/bakery reload`: if url/sha1 changed, re-send to online players (same UUID, so it replaces).
- Remove (admin tools only): `Audience#removeResourcePacks(UUID...)` and `Audience#clearResourcePacks()` (Paper docs). Do not call `clearResourcePacks` on join.

### Status events
- `PlayerResourcePackStatusEvent`: `getID()` returns `UUID` (the pack), `getStatus()` returns `Status` (verified constants: `ACCEPTED`, `DECLINED`, `DOWNLOADED`, `FAILED_DOWNLOAD`, `SUCCESSFULLY_LOADED`, `INVALID_URL`, `FAILED_RELOAD`, `DISCARDED`). `Player#getResourcePackStatus()` is the most recent status, nullable, **not per pack**: prefer the event with the UUID.
- Keep a per-player `Set<UUID>` / flag `hasCookiePack`: true on `SUCCESSFULLY_LOADED` for our UUID, false on `DECLINED`, `FAILED_*`, `INVALID_URL`, `DISCARDED`. Remove on quit.
- Use it for: HUD template (`hud-glyph.md`), the display item stack (`display-cookie.md`), icon materials (`world-panel.md`), and fallback sounds (`sounds.yml` vanilla fallbacks).
- On `SUCCESSFULLY_LOADED` re-send the item stacks of the viewer's displays (`setItemStack` with the item model) and refresh the HUD.
- Client responses are driven by the client: a modded client can lie or send statuses out of order (Paper docs). Only cosmetic decisions depend on them.
- Do not send the pack on every join if the client already has it: the client skips re-download when the hash is unchanged (it caches by hash), so repeating is cheap.

### Hosting
- A GitHub release asset URL works as the pack URL for the in-game test (commonly used; the URL redirects to the asset host). It must be a direct download of the zip. Verify with the first in-game test. Any https static host works.
- The zip must contain `pack.mcmeta` at its root, not inside a `resourcepack/` folder.
- Changing the zip means a new SHA-1: update the `sha1` line each time (a `mise` task for hashing is a nice-to-have for the coder or orchestrator).

### Coexisting with ItemsAdder's pack
- ItemsAdder generates and sends its own pack (`docs/integrations.md`). Its docs say that from 1.20.4 on multiple packs are possible, but that custom model data (and similar JSON) is not merged between packs; merging is needed in that case. ItemsAdder's own merge feature: copy our `assets/` into `plugins/ItemsAdder/contents/<name>/resourcepack/`, run `/iazip`, and set `contents-folders-priorities` in its `config.yml` (ItemsAdder wiki "Merge resourcepacks").
- Our assets use **item model definitions** (`items/<name>.json`) in our own namespace, fonts and sounds in our own namespace. No file is shared with ItemsAdder's pack, so stacking does not need merging.
- Stacked packs: each pack has its own UUID, so `replace(false)` keeps both loaded. Later packs in the stack override earlier ones for the same file path (vanilla behaviour); we never use a path that another pack could use.
- Risks (not verified on the actual server):
  1. ItemsAdder or another plugin sending its pack through the legacy single-pack method would clear ours. Mitigation: send ours with a short delay (for example 20 to 40 ticks after join), and on `PlayerResourcePackStatusEvent` for **another** UUID with `SUCCESSFULLY_LOADED` re-check once; if ours is not loaded, send it again once.
  2. ItemsAdder's newest release lists Paper support only up to 26.2: run `/version ItemsAdder` on the game server and record it in `docs/integrations.md` (an existing TODO).
  3. If stacking misbehaves: merge route above, with `resource-pack.enabled: false`.
- In the in-game guide: step "join with ItemsAdder installed: both packs show in the client's server-pack list (Options, Resource Packs); cookie model, glyph and sounds work."

## JDBC drivers for a Maven Paper plugin: `libraries:` vs shading

Latest versions on Maven Central (checked 2026-10-18): H2 `2.5.252`, MariaDB Connector/J `3.5.10`, HikariCP `7.1.0`.

### Option A (recommended): `plugin.yml` `libraries:`
```yaml
# src/main/resources/plugin.yml
libraries:
  - com.h2database:h2:2.5.252
  - org.mariadb.jdbc:mariadb-java-client:3.5.10
  - com.zaxxer:HikariCP:7.1.0        # optional; leave out until needed
```
Verified (PaperMC docs, `PluginDescriptionFile#getLibraries`): a YAML list of `group:artifact:version` coordinates, downloaded from Maven Central at startup into the plugin's classpath; "removes the need to shade and relocate". The Javadoc marks the feature as a preview. The download source is configurable by `PAPER_DEFAULT_CENTRAL_REPOSITORY` / `-Dorg.bukkit.plugin.java.LibraryLoader.centralURL`. Only Maven Central: no private repositories (those need a `paper-plugin.yml` loader).
`pom.xml` (compile only, not packaged):
```xml
<dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><version>2.5.252</version><scope>provided</scope></dependency>
<dependency><groupId>org.mariadb.jdbc</groupId><artifactId>mariadb-java-client</artifactId><version>3.5.10</version><scope>provided</scope></dependency>
<dependency><groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>7.1.0</version><scope>provided</scope></dependency>
```
`provided` matters: the existing `pom.xml` runs `maven-shade-plugin` with no configuration, which would otherwise pack any `compile` dependency into the jar unrelocated. Keep the versions of `plugin.yml` and `pom.xml` identical (put them in one Maven property, and let `plugin.yml` filtering insert it: `plugin.yml` is already filtered for `${version}`).
Pros: small jar, no relocation problems, drivers keep their `META-INF/services` files, one place to bump versions. Cons: first start needs internet (downloaded jars are cached in the server's `libraries/` folder); a preview feature; only Maven Central.

### Option B: shade and relocate (not recommended here)
- Jar grows by about 4 MB or more (H2 alone is several MB). Relocating JDBC drivers is fragile: they find classes by name and have `META-INF/services/java.sql.Driver` entries. The shade plugin needs `ServicesResourceTransformer` and `relocation` rules, and both drivers' behaviour after relocation is a known source of errors. Forum reports of "No suitable driver" after shading/relocating exist.
- Only worth it if the server has no internet at all. CLAUDE.md allows shading things that are not Paper/Adventure/ItemsAdder/ModelEngine, but A is simpler.

### Driver registration and classloader notes
- Each plugin has its own classloader (Paper), and `DriverManager` only auto-registers drivers it finds with the system class loader at startup, so **do not depend on auto-discovery**. Register explicitly from plugin code: `new org.h2.Driver()` / `new org.mariadb.jdbc.Driver()` (or `Class.forName(name, true, getClass().getClassLoader())`) before the first connection. `DriverManager.getConnection` called from plugin classes then accepts these drivers (source knowledge: the caller's loader must be able to load the driver class).
- HikariCP: set `driverClassName` (`org.h2.Driver` / `org.mariadb.jdbc.Driver`). It needs `slf4j-api`, which Paper provides.
- H2 embedded: `jdbc:h2:file:<absolute path under getDataFolder()>/data/cookieclicker`; `;MODE=MariaDB` makes one SQL dialect work for both (test it; keep SQL simple: standard `MERGE`/upsert differences are the usual trouble). Close the connection on disable and on `/bakery reload`; the H2 file is locked by a single process. H2 file formats can change between major versions: pin the version, do not float it, and plan an export/import before bumping.
- MariaDB: `jdbc:mariadb://host:3306/db`; the driver is LGPL.
- Pool sizing: H2 embedded needs 1 connection (no pool). MariaDB: HikariCP is optional; a small pool (2 to 4) is enough. All I/O is async (never on the main thread), as the brief demands; use one dedicated single-thread or small executor owned by the plugin and shut it down in `onDisable` after the final flush.

## Pitfalls
- Zipping the folder instead of its contents puts `pack.mcmeta` one level too deep: the pack is "invalid".
- Wrong or upper-case/stale SHA-1: download failure. Hash after every change.
- `sendResourcePacks` with `replace(true)` wipes other plugins' packs.
- `Player#setResourcePack` (legacy) also wipes them.
- Resource pack format mismatches only show a warning in the client's pack screen; the pack still loads (a 97.x pack on 26.3 is fine).
- Packs are cached on the client by hash. Same UUID plus a new hash is picked up on the next join.
- Do not put secrets (a private host URL with a token) in `config.yml` committed to git.
- Plugin reload (`/reload`) is unsupported by Paper; use `/bakery reload`.

## Sources
- Pack format table (26.3 = 97.1, 26.2 = 88.0): https://minecraft.wiki/w/Pack_format , https://minecraft.wiki/w/Java_Edition_26.3
- pack.mcmeta keys: https://minecraft.wiki/w/Pack.mcmeta
- Adventure resource packs (Paper docs): https://docs.papermc.io/adventure/resource-pack/
- Adventure Javadocs (4.24, same API): [ResourcePackRequest](https://jd.advntr.dev/api/4.24.0/net/kyori/adventure/resource/ResourcePackRequest.html), [ResourcePackInfo](https://jd.advntr.dev/api/4.24.0/net/kyori/adventure/resource/ResourcePackInfo.html)
- Paper 26.3 Javadocs: [Player](https://jd.papermc.io/paper/26.3/org/bukkit/entity/Player.html), [PlayerResourcePackStatusEvent](https://jd.papermc.io/paper/26.3/org/bukkit/event/player/PlayerResourcePackStatusEvent.html), [Status](https://jd.papermc.io/paper/26.3/org/bukkit/event/player/PlayerResourcePackStatusEvent.Status.html), [PlayerJoinEvent](https://jd.papermc.io/paper/26.3/org/bukkit/event/player/PlayerJoinEvent.html), [PluginDescriptionFile](https://jd.papermc.io/paper/26.3/org/bukkit/plugin/PluginDescriptionFile.html)
- Paper issue on `setResourcePack` clearing packs: https://github.com/PaperMC/Paper/issues/10027
- plugin.yml `libraries`: https://docs.papermc.io/paper/dev/plugin-yml/ ; paper-plugin.yml loader: https://docs.papermc.io/paper/dev/getting-started/paper-plugins/
- ItemsAdder merge guide: https://wiki.itemsadder.com/adding-content/merge-resourcepacks
- Versions: https://repo1.maven.org/maven2/com/h2database/h2/maven-metadata.xml , https://repo1.maven.org/maven2/org/mariadb/jdbc/mariadb-java-client/maven-metadata.xml , https://repo1.maven.org/maven2/com/zaxxer/HikariCP/maven-metadata.xml
- Project: `docs/integrations.md`, `resourcepack/pack.mcmeta`

## Hand-off

**model-designer** (pipeline: plain pack `resourcepack/`)
- Keep `resourcepack/pack.mcmeta` as it is (`min_format` 97, `max_format` 97, description "Cookie Empire resources" if you want to rename it). No `pack_format`, no `supported_formats`.
- Everything goes in `assets/cookieclicker/` only. No files in the `minecraft` namespace. PNGs RGBA 8-bit, lower-case file names with underscores.
- The pack must zip with `pack.mcmeta` at the root (the validation task should check that).

**sound-designer**
- `assets/cookieclicker/sounds.json` and `sounds/*.ogg` (mono Ogg Vorbis) live in the same pack, the same namespace. Sound events by the contract (`cookieclicker:ui.cookie_click` and so on). `sounds.yml` vanilla fallbacks are used for players without the pack (`hasCookiePack` false).

**vfx-designer**: nothing.

**plugin-coder**
- `config.yml` `resource-pack.enabled/url/sha1/required/prompt`; sending as above; per-player `hasCookiePack` flag from `PlayerResourcePackStatusEvent`.
- `plugin.yml` `libraries:` (H2, MariaDB, optional HikariCP) plus `provided` dependencies; explicit driver registration; one Maven property for versions.
- Never call `Player#setResourcePack`; never `replace(true)`.
- Note the shade plugin has no config in `pom.xml`: with `provided` drivers nothing extra is shaded.

CONTRACT CHANGES (proposed): add `resource-pack.enabled` (boolean) to the `config.yml` keys; add the status flag requirement (`hasCookiePack`) to the plugin-coder's pass 2 scope. Stable pack UUID derived from the name `cookieclicker:resourcepack` (no config key).
