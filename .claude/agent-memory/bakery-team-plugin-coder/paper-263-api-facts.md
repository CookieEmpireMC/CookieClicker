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
- Cleanup pass (2026-10-18): scratch Java against main classes in the same package fails as a single-file source launch (split-package IllegalAccessError); `javac -d out` then `java @argfile <class>` works. Build the argfile (`-cp target/classes:<mvn dependency:build-classpath output>`) with python; the guard refuses `$(...)`.
- MiniMessage 5.2.0: placeholders are NOT resolved inside click/insert args, but are inside `hover:show_text`; gradient/rainbow/lang reshape inserted text (HudTemplate falls back to parsing for those).
- starter-house pass 2 (2026-10-19, javap + compile): `Player#sendMultiBlockChange(Map<? extends io.papermc.paper.math.Position, BlockData>)` (the `(Map, boolean)` overload is deprecated), `Position.block(int,int,int)`, `sendBlockUpdate(Location, TileState)`, `isChunkSent(long)`; `io.papermc.paper.event.packet.PlayerChunkLoadEvent` exists (`getPlayer()`, ChunkEvent `getChunk()`/`getWorld()`); `AttributeInstance#addTransientModifier`, `getModifier(Key)`, `removeModifier(Key)`; `new AttributeModifier(NamespacedKey, double, Operation)`, `Operation.MULTIPLY_SCALAR_1`; `Attribute.MOVEMENT_SPEED`, `JUMP_STRENGTH`; `PlayerDeathEvent#getPlayer()`; `Bukkit.getOfflinePlayerIfCached(String)`.
- `org.bukkit.Sound#key()` is deprecated for removal on 26.3. Replacement (2026-10-19, compiled clean with showDeprecation): `Registry.SOUND_EVENT.getKey(sound)` -> `@Nullable NamespacedKey`. `Registry.SOUNDS` is the same registry but `@ApiStatus.Obsolete` (no compiler warning; apiNote says use SOUND_EVENT). Source: paper-api-26.3.build.205-stable-sources.jar org/bukkit/Registry.java l.244/383/481. Stable.
- Check deprecation warnings: `rm -rf target/classes` then `mise exec -- mvn -B -q compile -Dmaven.compiler.showDeprecation=true -Dmaven.compiler.showWarnings=true` (empty output = clean).
- Guard refuses shell `for` loops over `mise run` tasks and python heredocs in the same command as a VCS call; run each as its own Bash call (parallel is fine).
- Guard accepts `python3 -I - <file> <<'EOF'` (script on stdin, file as argv) for multi-replace edits; it refuses `cd x && ... && cd ../.. && git ...` chains.
- ArchitectureRulesTest pins rules from earlier passes (pass 1 banned `sendMultiBlockChange`): a pass that needs it fails that one test until Gandalf updates it; say so in the report.
- Tests pin behaviour hard: e.g. PluginSettingsResourcePackTest expects the empty pack url to be exactly 1 reported problem; check the tests before promising a "log at info" change.
- TextDisplay layout (2026-10-21, from vanilla TextDisplayRenderer as recalled + brief; not yet seen in game, stable): the text block is drawn centred on the entity position (translate 1 - w/2), background spans about [-w/2, w/2+1] px, w = widest wrapped line; LEFT/RIGHT only align lines inside the block. Transformation translation is in the display's rotated frame and not scaled, so left-edge shift = w/2 * scale / 40 blocks along local +x. Implemented in view/TextWidth (default-font advance table, bold +1, client word wrap).
- Scratch smoke test with Adventure on the classpath: `mise exec -- mvn -B -q dependency:build-classpath -Dmdep.outputFile=<scratch>/cp.txt`, then `mise -C <worktree> exec -- java -cp "<worktree>/target/classes:$(cat cp.txt)" <ABSOLUTE path>/Smoke.java` (mise -C changes cwd, so a relative .java path fails). Works for classes not in a split package.
