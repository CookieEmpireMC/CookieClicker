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

## Other
- https://docs.papermc.io/paper/reference/spigot-configuration | moved-wrongly-threshold default 0.0625 | 2026-10-19 | stable
- https://ayakael.net/mirrors/papermc/commit/9271ee7643614663a6d93323a9e8db2d52123166 | Paper "Don't resend blocks on interactions (#9413)": rollback sends block+neighbour+tile updates on cancelled interactions | 2026-10-19 | stable

## paper-plugin.yml (checked 2026-10-23, see docs/research/paper-plugin.md)
- https://docs.papermc.io/paper/dev/getting-started/paper-plugins/ | fields, dependencies load BEFORE/AFTER/OMIT, MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR, experimental | stable
- https://www.schemastore.org/paper-plugin.json | required: name,version,main,api-version; permissions/default-perm supported | stable
- raw.githubusercontent.com/PaperMC/Paper/main/paper-server/.../provider/type/PluginFileType.java | paper-plugin.yml wins over plugin.yml | stable
- BukkitScheduler 26.3 Javadoc: "async tasks should never access any API in Bukkit"; Player Javadoc has no thread notes; WG managers page: save/load any thread only
- Dead ends: docs.papermc.io .../folia-support and .../getting-started/plugin-yml 404; PaperPluginMeta.java path 404

## Build quality research (2026-10-24, see docs/research/build-quality.md)
- https://westeroscraft.com/docs/building/fundamentals/{gradients,exteriors,layout-foundation,research-planning} | build-team rules: gradients, eaves, thatch >=45deg, depth must be structural | stable
- https://minecraft.wiki/w/Tutorial%3ARoof_construction_guidelines | pitches 63.4/45/22.5, roof 1-2 storeys, >15x15 split roof | stable
- https://www.conquestreforged.com/guides/gradients | gradient rules | stable
- https://thespike.gg/minecraft/house-ideas/{medieval-house,minecraft-farm-house} | credited builders + palettes | stable
- https://github.com/Philogag/deepslate-mcp (MIT, Node20+, headless-gl+Xvfb, .schem/.litematic/.nbt), https://github.com/chunky-dev/chunky (GPL-3, scene via GUI first), Sponge schematic-3 spec | volatile
- Dead ends: minecraft.net landscaping article fetch timed out (snippet only); minecraft.wiki Tutorial:Building_techniques 404; web search for medieval MC builds is mostly SEO spam
- Planet Minecraft: per-creator licences, no site-wide one

## Build quality pass 2 + review pipeline (2026-10-24)
- https://chunky-dev.github.io/docs/{reference/user_interface/chunky_launcher/headless,reference/technical/scene_format,support/minecraft_compatibility,getting_started/your_first_render}/ | headless flags (-render -scene-dir -threads -target -texture -download-mc -snapshot); scene JSON; stable 2.4.6 <=1.20.4, snapshot 2.5.0 <=26.2-snap8; camera conv: Y+1.62, yaw=90-mcyaw, pitch=mcpitch-90 | volatile
- github chunky-dev/chunky PRs #1870 (new dir layout dimensions/minecraft/overworld, dimension is string), #1915 (26.3 chunk format, merged 2026-10-13); API search endpoint api.github.com/search/issues works | volatile
- https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/latest | build 218 STABLE, jar url + sha256 | volatile
- minecraft.wiki: Server.properties (white-list default true since 26.3s2), Superflat (flat top Y=-60), Game_rule (advance_time, advance_weather, spawn_mobs, max_command_sequence_length 65536), Commands/setblock (strict mode 1.21.5), Chain (iron_chain rename 25w32a) | stable
- Mineflayer README: supports up to 26.1 only; minecraft-data 26.3 PR #1300 open | volatile
- Material javadoc jd.papermc.io/paper/26.3/org/bukkit/Material.html (431k chars): summary list in chunks offset 100000-300000; WebFetch rubber-stamps ids, so ask for the found list. CHAIN gone -> IRON_CHAIN
- Wiki Tutorials: Roof_types, Adding_beauty_to_constructions, Walls_and_buttresses, Settlement_guide, Architectural_terms; WC docs exteriors/interiors; forum tutorials (Rillian, xRILLIANx), piratemc.com houses-better-builds | stable
- Dead ends: chunkyupdate channel ids unknown (LauncherSettings/ReleaseChannel raw 404); minecraft.net landscaping timeout x2; web.archive.org blocked; wiki Tutorials has no mill/barn how-to; WebSearch for MC build guides = spam
- Fact: /bakery house place is player-only + WorldGuard plot region; headless review fills via RCON setblock strict

## Searches that worked
- "WorldEdit FAWE Minecraft 26.3 support release" (found Hangar WE beta)
- API endpoints beat HTML pages: Modrinth v2, Hangar v1 (WorldEdit only), GitHub API for IntellectualSites (EngineHub GitHub releases is empty)

## Dead ends
- https://hangar.papermc.io/api/v1/projects/EngineHub/WorldGuard/versions 404 (WG is not on Hangar)
- EngineHub GitHub releases/API empty; builds.enginehub.org and enginehub.org/worldguard have no usable versions
- worldguard developer/requirements/ and .../regions/flag-queries/ 404
