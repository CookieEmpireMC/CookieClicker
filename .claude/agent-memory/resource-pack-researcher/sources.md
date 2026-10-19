---
name: sources
description: URLs fetched in earlier runs, what each confirmed, date checked, stable vs volatile, plus working searches and dead ends
metadata:
  type: reference
---

Rule: check memory first. Reuse STABLE rows without re-fetching; re-check VOLATILE rows only if older than ~14 days or the target version changed.

Format: URL | confirmed | checked | stable/volatile

## Paper 26.3 Javadocs (stable for 26.3)
- https://jd.papermc.io/paper/26.3/org/bukkit/entity/Player.html | sendBlockChange(Location,BlockData), sendBlockChanges(Collection<BlockState>) (per chunk section), sendMultiBlockChange(Map<? extends Position,BlockData>), sendBlockUpdate(Location,TileState); (Collection,boolean)/(Map,boolean) and sendSignChange deprecated; showEntity/hideEntity/canSee (bakery-core) | 2026-10-19 | stable. Page is 500k chars: use WebFetch offset (200000 and 300000 had the block methods)
- https://jd.papermc.io/paper/26.3/io/papermc/paper/math/Position.html | @Experimental; Position.block(x,y,z), block(Location), fine(...) | 2026-10-19 | stable
- https://jd.papermc.io/paper/26.3/org/bukkit/block/data/BlockData.html | getSoundGroup() not deprecated | 2026-10-19 | stable
- https://jd.papermc.io/paper/26.3/org/bukkit/SoundGroup.html | get{Break,Step,Place,Hit,Fall}Sound() -> Sound, getVolume/getPitch | 2026-10-19 | stable
- https://jd.papermc.io/paper/26.3/org/bukkit/Sound.html | Sound is an interface (OldEnum, Keyed x2, Sound.Type) | 2026-10-19 | stable
- https://jd.papermc.io/paper/26.3/org/bukkit/attribute/{AttributeInstance,AttributeModifier,Attribute}.html | addTransientModifier (not persisted), removeModifier(Key), AttributeModifier(NamespacedKey,double,Operation), Attribute.MOVEMENT_SPEED/JUMP_STRENGTH/GRAVITY | 2026-10-19 | stable
- https://docs.papermc.io/paper/dev/display-entities/ | interpolation recipe, teleport duration, first transformation 1 tick after spawn, BlockDisplay#setBlock | 2026-10-19 | stable
- bakery-core pages (Display, ItemDisplay, Interaction, Entity, DataComponentTypes, events): see paper-26-3-verified-api.md | 2026-10-18 | stable

## WorldGuard / WorldEdit (see worldguard-26-3.md)
- https://api.modrinth.com/v2/project/worldguard/version?limit=5 | WG 7.0.20 (2026-10-17, MC 26.3 only), 7.0.19, 7.0.18 ... with game_versions and changelogs. BEST source, WebFetch handles it | 2026-10-19 | VOLATILE
- https://dev.bukkit.org/projects/worldguard/files | same list, dates (7.0.20 = Oct 16) | 2026-10-19 | VOLATILE
- https://hangar.papermc.io/api/v1/projects/EngineHub/WorldEdit/versions?limit=6 | WE 7.4.6 (2026-10-15, Paper 1.21.4-26.3) | 2026-10-19 | VOLATILE
- https://api.github.com/repos/IntellectualSites/FastAsyncWorldEdit/releases?per_page=6 | FAWE 2.16.0 (2026-10-04, "26.3 Support"), 2.16.1 (2026-10-09) | 2026-10-19 | VOLATILE
- https://maven.enginehub.org/repo/com/sk89q/{worldguard/worldguard-bukkit,worldedit/worldedit-bukkit}/maven-metadata.xml | release WG 7.0.20, WE 7.4.6 | 2026-10-19 | VOLATILE
- https://worldguard.enginehub.org/en/latest/developer/{dependency,regions,regions/managers,regions/protected-region,regions/spatial-queries,regions/flag-calculation,regions/custom-flags,native-objects}/ and /regions/flags/ | developer API facts | 2026-10-19 | stable (docs are version-agnostic)
- https://builds.enginehub.org/job/{worldguard,worldedit} | CI builds with relative dates ("2 days ago") only | 2026-10-19 | volatile

## Other
- https://docs.papermc.io/paper/reference/spigot-configuration | moved-wrongly-threshold default 0.0625 | 2026-10-19 | stable
- https://ayakael.net/mirrors/papermc/commit/9271ee7643614663a6d93323a9e8db2d52123166 | Paper "Don't resend blocks on interactions (#9413)": rollback sends block+neighbour+tile updates on cancelled interactions | 2026-10-19 | stable

## Searches that worked
- "WorldEdit FAWE Minecraft 26.3 support release" (found Hangar WE beta)
- API endpoints beat HTML pages: Modrinth v2, Hangar v1 (WorldEdit only), GitHub API for IntellectualSites (EngineHub GitHub releases is empty)

## Dead ends
- https://hangar.papermc.io/api/v1/projects/EngineHub/WorldGuard/versions 404 (WG is not on Hangar)
- https://github.com/EngineHub/WorldGuard/releases (empty), api.github.com for EngineHub repos returned `[]`
- https://worldguard.enginehub.org/en/latest/developer/requirements/ and .../regions/flag-queries/ 404
- enginehub.org/worldguard page has no versions
- search "moved wrongly" gives generic netcode junk; spigot.yml docs are the useful hit
