---
name: worldguard-26-3
description: Verified WorldGuard/WorldEdit/FAWE versions + API and fake-block/BlockDisplay/SoundGroup facts for Paper 26.3 (starter-house research, 2026-10-19)
metadata:
  type: reference
---

Verified 2026-10-19. Full notes: docs/research/worldguard-plot.md and docs/research/fake-blocks.md. Sources in [[sources]].

Versions (VOLATILE, recheck after ~2026-11-02 or on new MC): WorldGuard 7.0.20 (2026-10-16/17, "Requires MC 26.3+") is the 26.3 build; 7.0.19 lists 26.2+26.3. WorldEdit 7.4.6 (2026-10-15, Paper 1.21.4-26.3). FAWE 2.16.0 (2026-10-04) added 26.3, 2.16.1 latest. Not on Hangar for WG; get from Modrinth/dev.bukkit.
Maven: repo https://maven.enginehub.org/repo/ ; com.sk89q.worldguard:worldguard-bukkit:7.0.20 and com.sk89q.worldedit:worldedit-bukkit:7.4.6, scope provided (pull worldguard-core / worldedit-core transitively). Avoid 7.1.0/8.0.0-SNAPSHOT.
API: WorldGuard.getInstance().getFlagRegistry().register(flag) only in onLoad (locked after WG enables; FlagConflictException -> fetch existing, compare type). RegionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer(); createQuery().getApplicableRegions(BukkitAdapter.adapt(Location)); container.get(BukkitAdapter.adapt(world)) may be null; set.queryValue(null, flag)/queryState/testState; isVirtual() for disabled/failed-load. wrapPlayer = WorldGuardPlugin.inst().wrapPlayer(p). ProtectedPolygonalRegion.getPoints() -> List<BlockVector2> (min/max Y via region min/max point); addRegion replaces same id, so cache ids not objects. Non-members can't build by default; protection flags fall back to build; docs say leave them unset.

Fake blocks 26.3: Player#sendMultiBlockChange(Map<? extends Position,BlockData>) (Position = io.papermc.paper.math.Position, @Experimental, Position.block(x,y,z)), sendBlockChanges(Collection<BlockState>) (one packet per section), sendBlockChange(Location,BlockData), sendBlockUpdate(Location,TileState) (sign text; sendSignChange deprecated). Suppress-light overloads deprecated. Cancelled interactions make Paper resend blocks (loses fakes).
Keep-out: AttributeInstance#addTransientModifier (not persisted) with AttributeModifier(NamespacedKey,amount,Operation) on Attribute.MOVEMENT_SPEED / JUMP_STRENGTH; avoid setWalkSpeed (persists).
Sound: BlockData#getSoundGroup() and SoundGroup#getPlaceSound() NOT deprecated on 26.3; org.bukkit.Sound is an interface (Keyed): use sound.key().asString().
BlockDisplay: first transformation must be >= 1 tick after spawn (Paper docs); setTeleportDuration 0..59.
