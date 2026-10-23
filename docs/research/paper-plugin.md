# paper-plugin.yml migration and thread-safety (feature: tidy)

**Target version:** Paper 26.3 (`api-version: '26.3'`, Java 25). Researched 2026-10-23.

## Summary
- Replace `plugin.yml` with `paper-plugin.yml` plus a `PluginLoader` for the JDBC libraries. Paper picks `paper-plugin.yml` when both exist, so a leftover `plugin.yml` is dead weight: delete it.
- WorldGuard: `dependencies.server.WorldGuard: {load: BEFORE, required: true, join-classpath: true}` keeps today's behaviour (WG loaded, so `onLoad` can register flags before WG enables).
- Commands are already Brigadier (`LifecycleEvents.COMMANDS`), nothing to move. Permissions can stay declarative in `paper-plugin.yml` (see risk below).
- Almost nothing in the Bukkit API is documented as off-main safe. Do async moves only for pure computation/IO, and hop back to the main thread for every Bukkit/Adventure/WG-world call.

## Version notes
- The Paper docs page for Paper plugins is still marked **experimental** on 26.3 ("may be subject to change"; bootstrapper "highly experimental"). The `loader` is the supported way to add libraries; `libraries:` from `plugin.yml` has no `paper-plugin.yml` equivalent.
- Which file wins: Paper's `PluginFileType.VALUES = List.of(PAPER, SPIGOT)`; `guessType` returns the first match, so `paper-plugin.yml` wins and `plugin.yml` is ignored (Paper source, not in the docs; docs only say both may be in one jar).
- Reason to keep `plugin.yml`: only to run on Spigot/Bukkit servers. We target Paper only (CLAUDE.md), so no reason.

## paper-plugin.yml fields (schema, secondary source: schemastore; docs list no required/optional)
- Required: `name`, `version`, `main`, `api-version` (regex `^\d{1,2}\.\d{1,2}(\.\d{1,2})?$`).
- Optional: `description`, `authors`, `contributors`, `website`, `prefix`, `provides`, `load` (`STARTUP`|`POSTWORLD`, default POSTWORLD), `default-perm` (default `op`), `permissions`, `loader`, `bootstrapper`, `has-open-classloader`, `dependencies`.
- `dependencies` has only `bootstrap` and `server` groups; each entry: `load` (`BEFORE`|`AFTER`|`OMIT`, default OMIT = undefined order), `required` (default true), `join-classpath` (default true).
- `depend`/`softdepend`/`libraries`/`commands` do not exist here. Paper does not derive load order from the dependency list alone; use `load`. Cyclic loops stop the server.

Proposed file (keep `${version}` resource filtering as today):
```yaml
name: CookieClicker
version: '${version}'
main: dev.casperrs.cookieClicker.CookieClicker
loader: dev.casperrs.cookieClicker.CookieClickerLoader
api-version: '26.3'
load: POSTWORLD
description: Cookie Empire - a per-player cookie bakery.
dependencies:
  server:
    WorldGuard:
      load: BEFORE        # WG loaded first, so onLoad can register flags; WG enables after all onLoad calls
      required: true
      join-classpath: true  # default; needed to see com.sk89q.* classes
permissions:
  cookieempire.admin:
    description: Set up bakery plots and houses, and reload Cookie Empire.
    default: op
```
WorldEdit/FAWE are not declared today (reached through WorldGuard); keep it that way, or add `WorldEdit: {load: BEFORE, required: false}` only if code touches `com.sk89q.worldedit` directly. If it does, FAWE's `provides` name may differ, so test with both.

## Loader (libraries via Paper's Maven Central mirror)
```java
public final class CookieClickerLoader implements PluginLoader {
    @Override
    public void classloader(final PluginClasspathBuilder classpathBuilder) {
        final MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder(
                "central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());
        resolver.addDependency(new Dependency(new DefaultArtifact("com.h2database:h2:" + H2_VERSION), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.mariadb.jdbc:mariadb-java-client:" + MARIADB_VERSION), null));
        classpathBuilder.addLibrary(resolver);
    }
}
```
- Constant: `io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR` (configurable by env `PAPER_DEFAULT_CENTRAL_REPOSITORY` / system property `org.bukkit.plugin.java.LibraryLoader.centralURL`). The docs warn that using `repo.maven.apache.org` directly risks rate limits and logs a console warning. Imports: `io.papermc.paper.plugin.loader.{PluginLoader,PluginClasspathBuilder}`, `org.eclipse.aether.{artifact.DefaultArtifact,graph.Dependency,repository.RemoteRepository}` (confirm they resolve from `paper-api` on the compile classpath).
- Versions: the loader cannot read `${h2.version}` filtering. Hard-code two constants and add a small test asserting they equal the pom properties (or filter a tiny `libraries.properties` the loader reads; untested).
- Keep the JDBC jars out of the shaded jar as now. The loader runs before the plugin class exists, so no plugin API is usable inside it.

## Plugin side
- Commands: already `LifecycleEvents.COMMANDS` registration; `paper-plugin.yml` has no `commands:`. Command aliases/descriptions go through `registrar.register(node, description, aliases)`.
- Permissions: declared in `paper-plugin.yml` (schema supports `permissions` + `default-perm`; Paper's `PluginMeta` exposes `getPermissions()` but carries a TODO "Do we even want this?"). Low-risk fallback if it ever stops registering: `getServer().getPluginManager().addPermission(new Permission("cookieempire.admin", PermissionDefault.OP))` in `onEnable`. Verify on a real server that `cookieempire.admin` appears (e.g. LuckPerms tab-complete or `PluginManager#getPermission`).
- `onLoad` flag registration keeps working: Paper loads all plugins (construct + `onLoad`) before enabling any. WG's flag registry is available once WG's `onLoad` has run, which `load: BEFORE` guarantees.

## Pitfalls
- Experimental: loader/bootstrapper APIs and the `PluginMeta` permission surface may change in later 26.x. Pin by testing each Paper bump.
- Classloader isolation: other Paper plugins cannot see our classes unless they depend on us (we expose none). WorldGuard is a Bukkit-style plugin; with `join-classpath: true` (default) our classloader can load its classes. Setting `join-classpath: false` would give `NoClassDefFoundError` on `com.sk89q.*`.
- `ConfigurationSerialization`: custom classes are not auto-registered in Paper plugins; call `registerClass` before `getObject(..., Class)` if we use serializable objects.
- `load: OMIT` has undefined ordering: always set `BEFORE` explicitly for WorldGuard.
- A missing required dependency removes the plugin from loading (logged), same as `depend`.
- Delete `plugin.yml` in the same commit; shipping both hides mistakes (the docs never state precedence).

## Thread-safety on 26.3
Rule from the BukkitScheduler Javadoc: "Asynchronous tasks should never access any API in Bukkit." There are no per-method annotations (SPIGOT-6784 open), so everything not listed as safe is "main only".

| Call | Off-main? | Basis |
|---|---|---|
| `runTaskAsynchronously` / `runTaskTimerAsynchronously` (scheduling itself) | Safe to call from any thread | Javadoc: tasks are queued; cancelling from other threads undocumented, treat as main-thread call |
| Code inside an async task | Pure Java/own data only | Javadoc warning above |
| `callSyncMethod` | Safe to call off-main; never `Future.get()` on main | Javadoc (about 10 ms latency) |
| Adventure `Audience.sendMessage` / `sendActionBar` on `Player` | **Unclear** (de facto only queues a packet, but the docs say nothing). Prefer: build the `Component` async, send on main | Javadoc `Player` page: no thread note |
| Display-entity metadata (`setText`, `setTransformation`, `setInterpolation*`, teleport) | **Main only** (mutates entity state, tracker) | General Bukkit rule; no exception documented |
| `Player#showEntity` / `hideEntity` / `canSee` | **Main only** (changes tracker/visibility state) | No thread note on Javadoc |
| WorldGuard `RegionQuery` / `RegionManager` reads | **Unclear.** WG docs only state that `save`/`load` may be called from any thread and block. Resolving the `Location`/`World` for a query touches Bukkit objects, so query on main unless profiled | WG docs (managers page) |
| `Bukkit` world/block/entity access, `Location#getBlock`, inventory | Main only | Javadoc rule |

Recommendation for async moves after the spark profile: only economy maths, serialization and JDBC calls (own thread pool, as `CookieClicker` already holds executors), then `Bukkit.getScheduler().runTask(plugin, ...)` for results.

## Sources
- https://docs.papermc.io/paper/dev/getting-started/paper-plugins/ (fields, dependencies, loader example, Maven Central mirror warning, experimental note)
- https://www.schemastore.org/paper-plugin.json (required fields, permissions shape, defaults; secondary source)
- https://raw.githubusercontent.com/PaperMC/Paper/main/paper-server/src/main/java/io/papermc/paper/plugin/provider/type/PluginFileType.java (paper-plugin.yml checked first)
- https://raw.githubusercontent.com/PaperMC/Paper/main/paper-server/src/main/java/io/papermc/paper/plugin/entrypoint/strategy/modern/ModernPluginLoadingStrategy.java (dependency validation, topological load)
- https://ayakael.net/mirrors/papermc/commit/23095683d0b0934ddf6d5272dafb4c69abd0ceca (PluginMeta permissions TODO)
- https://jd.papermc.io/paper/26.3/org/bukkit/scheduler/BukkitScheduler.html (async warning, callSyncMethod)
- https://jd.papermc.io/paper/26.3/org/bukkit/entity/Player.html (no thread notes)
- https://worldguard.enginehub.org/en/latest/developer/regions/managers/ (save/load any thread)
- https://hub.spigotmc.org/jira/browse/SPIGOT-6784 (no thread-safety annotations)

## Hand-off
- **plugin-coder:** add `paper-plugin.yml` (above) and `CookieClickerLoader`; remove `plugin.yml` and its `libraries:`; keep resource filtering for `${version}`; add a test tying loader version constants to the pom properties; test-start on Paper 26.3 with WorldGuard 7.0.20 and check flags register and `cookieempire.admin` exists. Moves off the main thread only for pure computation/IO.
- **model-designer / sound-designer / vfx-designer:** nothing.
- Proposed contract IDs: none beyond `dev.casperrs.cookieClicker.CookieClickerLoader` (class name) for the loader.
