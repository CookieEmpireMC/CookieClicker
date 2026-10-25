---
name: paper-263-api-facts
description: Paper 26.3 / Adventure 5.2 / WorldGuard 7.0.20 API signatures verified by javap + clean compile (2026-10-18..22), plus where to javap them
metadata:
  type: reference
---

All verified against paper-api 26.3.build.205-stable by javap and a compile clean with -Xlint:deprecation. Stable
for this version; re-check only when the paper-api version in pom.xml changes.

**Lookups**
- javap: `mise exec -- javap -cp ~/.m2/repository/io/papermc/paper/paper-api/26.3.build.205-stable/paper-api-26.3.build.205-stable.jar <class>`
  (literal path; the guard refuses `$VAR`). Sources jar next to it. Adventure: `~/.m2/repository/net/kyori/adventure-api/5.2.0/`.
- Deprecation check: `rm -rf target/classes` then `mise exec -- mvn -B -q compile -Dmaven.compiler.showDeprecation=true -Dmaven.compiler.showWarnings=true` (empty = clean).

**Commands / plugin**
- Brigadier: `Commands.literal(..)`, `CommandSourceStack#getSender()/getExecutor()`, register via
  `getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, e -> e.registrar().register(node, "desc"))`.
- Plugin is a paper plugin (tidy, 2026-10-23): `paper-plugin.yml` + `CookieClickerLoader` (PluginLoader, drivers via
  `MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR`, versions from filtered `cookieclicker-libraries.properties`).
  Open JDBC via a `Driver` from `Class.forName(cls, true, ownClassLoader)`, not DriverManager.
- `org.jspecify.annotations.Nullable` is on the API classpath; on a nested type write `Plot.@Nullable Point`.

**World / blocks**
- `World#getKey().asString()` = `minecraft:overworld`; `Bukkit.getWorld(NamespacedKey|Key)`.
- `getHighestBlockYAt(int,int,HeightMap)`, `HeightMap.WORLD_SURFACE`, `Tag.LEAVES`, `Material#isSolid()`,
  `MultipleFacing`, `Wall#setHeight(BlockFace, Wall.Height.LOW)`, `Bukkit.createBlockData(String)`, `Block#getState(false)`.
- Chunk tickets (javap 2026-10-25): `World#addPluginChunkTicket(int,int,Plugin)` / `removePluginChunkTicket(int,int,Plugin)`
  (boolean), `removePluginChunkTickets(Plugin)`. `Entity#teleportAsync(Location, TeleportCause)` -> `CompletableFuture<Boolean>`.
- Structures: `Bukkit.getStructureManager()` createStructure/saveStructure(OutputStream,..)/loadStructure(InputStream);
  `Structure#fill(Location, BlockVector, boolean)`, `place(Location, boolean, StructureRotation, Mirror, int, float, Random)`.
- `Sign#getSide(Side.FRONT).line(int, Component)` + `update(true,false)`.
- Fake blocks: `Player#sendMultiBlockChange(Map<? extends Position, BlockData>)` (the `(Map, boolean)` overload is
  deprecated), `Position.block(x,y,z)`, `sendBlockUpdate(Location, TileState)`, `isChunkSent(long)`,
  `io.papermc.paper.event.packet.PlayerChunkLoadEvent`.

**Sound / pack**
- Adventure 5: `Sound.sound(Key, Source, float, float)`, `player.playSound(sound, Sound.Emitter.self())` or `(sound, x, y, z)`.
  `Key.key(String)` throws `InvalidKeyException`.
- `org.bukkit.Sound#key()` is deprecated for removal: use `Registry.SOUND_EVENT.getKey(sound)` (nullable);
  `Registry.SOUNDS` is @Obsolete. `BlockData#getSoundGroup().getPlaceSound()` returns `org.bukkit.Sound`.
- Pack: `ResourcePackRequest.resourcePackRequest().packs(info).prompt(c).required(b).replace(false).build()`,
  `ResourcePackInfo.resourcePackInfo(UUID, URI, String)`, `PlayerResourcePackStatusEvent#getID()`, Status
  SUCCESSFULLY_LOADED / ACCEPTED / DOWNLOADED (+ failure states).

**Entities / displays**
- `world.spawn(loc, Class, Consumer)` (set everything in the consumer), `Entity#getTrackedBy()`, `Player#showEntity(Plugin, Entity)`.
- Items: `ItemStack.of(Material)` + `setData(DataComponentTypes.ITEM_MODEL, Key)`;
  `CustomModelData.customModelData().addString(s).build()` with `DataComponentTypes.CUSTOM_MODEL_DATA`.
- `Display#setTeleportDuration` only 0..59 (IAE); `setInterpolationDuration(int)` unlimited (long ride = one
  `setTransformation` with delay 0 + duration N, a tick after spawn). `Color.fromARGB(int)`.
- Ray pick: `Entity#getBoundingBox().rayTrace(Vector, Vector, double)`.
- ItemDisplay renders the item turned 180 deg about Y vs. the model file (user test 2026-10-22; belt adds Math.PI in `belt/BeltSurface`).
- TextDisplay: front faces its yaw; text block centred on the entity (anchor bottom centre), width = widest wrapped
  line; translation is in the rotated frame, unscaled. Implemented in `text/TextWidth` (recalled vanilla renderer, not seen in game).
- Spectator: `Player#setSpectatorTarget(@Nullable Entity)` throws unless SPECTATOR; Paper
  `PlayerStartSpectatingEntityEvent` / `PlayerStopSpectatingEntityEvent` (Cancellable); `TeleportCause.SPECTATE`.

**Players / events**
- `Bukkit.getCurrentTick()` (int), `PrePlayerAttackEntityEvent#getAttacked()` (Cancellable), `PlayerMoveEvent#hasChangedBlock()`,
  `hidePlayer/showPlayer(Plugin, Player)`, `getTargetBlockExact(int)`, `PlayerGameModeChangeEvent#getCause()`,
  `Entity#setRotation(float,float)`, `Bukkit.getDefaultGameMode()`, `PlayerDeathEvent#getPlayer()`, `Bukkit.getOfflinePlayerIfCached(String)`.
- Attributes: `addTransientModifier`, `getModifier(Key)`, `removeModifier(Key)`, `new AttributeModifier(NamespacedKey, double, Operation)`,
  `Operation.MULTIPLY_SCALAR_1`, `Attribute.MOVEMENT_SPEED`, `JUMP_STRENGTH`.

**MiniMessage 5.2**: placeholders are NOT resolved inside click/insert args, but are inside `hover:show_text`;
gradient/rainbow/lang reshape inserted text (so `text/MarkerTemplate` falls back to a real parse for them).
`Component#insertion(String)` exists (used for clickable toggle spans).

**WorldGuard 7.0.20 / WorldEdit 7.4.6** (repo `https://maven.enginehub.org/repo/`, provided; jars under `~/.m2/repository/com/sk89q/`):
`FlagRegistry#register(Flag) throws FlagConflictException`, `new StringFlag(String)`; `RegionContainer#createQuery()`,
`get(weWorld)` (nullable); `RegionQuery#getApplicableRegions(weLocation)`; `ApplicableRegionSet` Iterable, `queryValue(..)`;
`ProtectedRegion` getId/getPriority/getFlag/getMinimumPoint/getMaximumPoint/getPoints/contains(int,int,int);
`BlockVector3#x()/y()/z()` (not getX); `BukkitAdapter.adapt(World|Location)`. Only `worldguard/WorldGuardHook` may import com.sk89q.
