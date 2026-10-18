---
name: paper-263-api-facts
description: Paper 26.3 API + build facts verified by compiling (no deprecation warnings with -Xlint:deprecation) in bakery-core pass 1, 2026-10-18
metadata:
  type: reference
---

Verified 2026-10-18 against paper-api 26.3.build.205-stable (compiled, -Xlint:deprecation clean):
- Brigadier: `io.papermc.paper.command.brigadier.Commands.literal(..)`, `CommandSourceStack#getSender()/getExecutor()`, register via `getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, e -> e.registrar().register(node, "desc"))`.
- `World#getKey().asString()` gives `minecraft:overworld`; `Bukkit.getWorld(NamespacedKey)` and `getWorld(Key)` exist.
- Adventure 5: `Sound.sound(Key, Sound.Source, float, float)`, `player.playSound(sound, Sound.Emitter.self())`, `player.playSound(sound, x, y, z)`, `Key.key(String)` throws `InvalidKeyException`.
- `PlayerResourcePackStatusEvent.Status`: SUCCESSFULLY_LOADED, ACCEPTED, DOWNLOADED (+ failure/declined/discarded).
- `org.jspecify.annotations.Nullable` is on the paper-api classpath.
- plugin.yml `libraries:` downloads from Maven Central (docs.papermc.io/paper/dev/plugin-yml); pom filters plugin.yml so `${h2.version}` works there. H2 2.5.252 (2026-09-26), MariaDB Connector/J 3.5.10 latest on Central as of 2026-10-18.
- Open JDBC via `Driver` instance from `Class.forName(cls, true, ownClassLoader)`, not DriverManager (library loader).
- Worktree isolation guard rejects bash with brace expansion, `for` loops over git, `$var` sed args and long multi-command scripts: write scratch files with Write, run plain single commands.
- Pass 2 (2026-10-18, compiled clean): Adventure 5.2.0 `ResourcePackRequest.resourcePackRequest().packs(info).prompt(c).required(b).replace(false).build()`, `ResourcePackInfo.resourcePackInfo(UUID, URI, String)`; `PlayerResourcePackStatusEvent#getID()`; `PrePlayerAttackEntityEvent#getAttacked()` (Cancellable); `Bukkit.getCurrentTick()` (int); `Color.fromARGB(int)`; `Bukkit.createBlockData(String)`; `ItemStack.of(Material)` + `setData(DataComponentTypes.ITEM_MODEL, Key)`; `world.spawn(loc, Class, Consumer)`; `Entity#getTrackedBy()`.
- javap the API: `mise exec -- javap -cp ~/.m2/repository/io/papermc/paper/paper-api/26.3.build.205-stable/paper-api-26.3.build.205-stable.jar <class>` (literal path; `$VAR` in the command is refused by the guard). Adventure jar: `~/.m2/repository/net/kyori/adventure-api/5.2.0/`.
- Text display front faces the entity's yaw direction (wiki: "visible from ... the direction the text display is rotated towards"); anchor = bottom centre of the text. Item display FIXED facing still unverified in game.
- Guard also refuses `python3 - <<EOF` that edits files: write the script to the scratchpad and run `python3 -I script.py <file>`.
- Tests are tester-owned and pin message keys (`hud.action-bar`, `hud.action-bar-no-pack` in MessagesTest); MessagesTest scans Java for literal `"panel.…"` etc. paths, so never build message paths by string concatenation.
- Run a `@Disabled` test without editing it: `mise exec -- mvn -B -q test -Dtest=X '-Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition'`.
- Scratch smoke test: `mise exec -- java -cp target/classes:~/.m2/.../h2-2.5.252.jar Smoke.java` (single-file source launch) works for pure classes.
