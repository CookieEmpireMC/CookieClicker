---
name: worldguard-and-house-facts
description: WorldGuard 7.0.20 / WorldEdit 7.4.6 API signatures and Paper 26.3 structure/sign/sound-group APIs verified by javap + compile in starter-house pass 1 (2026-10-19)
metadata:
  type: reference
---

Verified 2026-10-19 by javap and a clean compile (-Xlint:deprecation, no warnings). Stable for these versions.

**WorldGuard 7.0.20 / WorldEdit 7.4.6** (jars: `~/.m2/repository/com/sk89q/worldguard/worldguard-core/7.0.20/`, `.../worldedit/worldedit-core/7.4.6/`, `.../worldedit-bukkit/7.4.6/`; javap with literal paths, classpath both cores):
- `FlagRegistry#register(Flag<?>) throws FlagConflictException`, `get(String)`; `new StringFlag(String)`.
- `RegionContainer#createQuery()`, `get(com.sk89q.worldedit.world.World)` (nullable); `RegionQuery#getApplicableRegions(com.sk89q.worldedit.util.Location)`.
- `ApplicableRegionSet` is `Iterable<ProtectedRegion>`, `isVirtual()`, `queryValue(RegionAssociable, Flag<V>)`.
- `ProtectedRegion`: `getId()`, `getPriority()`, `getFlag(T)`, `getMinimumPoint()/getMaximumPoint()` (BlockVector3), `getPoints()` (abstract on base: cuboids return 4 corners), `contains(int,int,int)`, `isPhysicalArea()`; `ProtectedPolygonalRegion` subclass.
- `BlockVector3/BlockVector2#x()/y()/z()` (use these, not getX).
- `BukkitAdapter.adapt(org.bukkit.World)`, `adapt(org.bukkit.Location)`.
- Maven: repo `https://maven.enginehub.org/repo/` resolves both; shaded jar contains 0 `com/sk89q` entries with `provided`.

**Paper 26.3** (paper-api 26.3.build.205-stable):
- `Bukkit.getStructureManager()`: `createStructure()`, `saveStructure(OutputStream, Structure)`, `loadStructure(InputStream)`; `Structure#fill(Location, BlockVector, boolean includeEntities)`, `place(Location, boolean, StructureRotation, Mirror, int palette, float integrity, Random)`.
- `BlockData#getSoundGroup().getPlaceSound()` returns `org.bukkit.Sound` (interface, `key()` gives the Adventure Key).
- `PlayerMoveEvent#hasChangedBlock()`; `Player#hidePlayer(Plugin, Player)` / `showPlayer(Plugin, Player)`; `getTargetBlockExact(int)`.
- `Sign#getSide(Side.FRONT).line(int, Component)` + `update(true, false)` compiles.
- `Block#getState(false)` (no snapshot) exists.
- Type-use @Nullable on a nested type must be written `Plot.@Nullable Point` (javac error otherwise).

Repo conventions learned:
- Worktree guard refuses `python3 - <<EOF` heredocs that edit files and multi-command sed scripts: use the Edit tool.
- Agent branches get merged into the feature branch while you work: read other agents' files via `git show feature/<name>:<path>`, not their branch name (branch is deleted after merge).
- JdbcPlayerStoreTest.freshDatabaseStartsAtVersionOne pins schema 1: bumping SCHEMA_VERSION needs the tester to update it.
- Effects.play may keep the Location for multi-tick jobs: pass a new Location per call.
- Review lessons (Gandalf, pass 1 round 1, 2026-10-19): any "grow until it fits" loop must terminate even when the
  limit is below the shape's minimum (Outline: stop once the step exceeds the longest edge); write undo backups to
  disk BEFORE changing the world; clear busy flags in try/finally (HousePlacer.guarded + handedOff pattern).
- Round 2 lesson: async multi-step operations need a cancellation token, not a boolean busy flag. reload/disable
  must invalidate in-flight IO callbacks (house/OperationGate: begin() token, valid(), end(token), reset() bumps the
  generation); check the token first in every main-thread continuation and only end your own token.
- Gandalf disables tests that reproduce open bugs (@Disabled); run them with the DisabledCondition deactivation flag
  (see paper-263-api-facts) to prove a fix, and tell Rick it's ready to un-disable.
