---
name: paper-26-3-verified-api
description: Paper 26.3 API facts verified in Javadocs on 2026-10-18 (displays, visibility, events, resource packs, libraries); reuse instead of re-fetching
metadata:
  type: reference
---

Verified 2026-10-18 at https://jd.papermc.io/paper/26.3/ (details in docs/research/*.md of bakery-core).

- Display: `setInterpolationDuration/Delay(int)`, `setTeleportDuration(int)` range 0..59, `setTransformation(Transformation)`, `setBillboard(Display.Billboard FIXED|VERTICAL|HORIZONTAL|CENTER)`, `setBrightness(new Display.Brightness(block, sky))`, `setViewRange(float)`, `setGlowColorOverride(Color)`. `Transformation` ctors take (Vector3f, AxisAngle4f|Quaternionf, Vector3f, AxisAngle4f|Quaternionf); getters return Quaternionf.
- TextDisplay: `text(Component)`, `setBackgroundColor(Color)`, `setLineWidth(int px)`, `setAlignment(CENTER|LEFT|RIGHT)`, `setSeeThrough`, `setShadowed`, `setTextOpacity(byte)` (4..26 discarded by client, not interpolated), `setDefaultBackground`.
- ItemDisplay: `setItemStack`, `setItemDisplayTransform(NONE, GUI, FIXED, HEAD, GROUND, ...)`. Interaction: `setInteractionWidth/Height(float)`, `setResponsive`, `getLastAttack/getLastInteraction`.
- Visibility: `Entity#setVisibleByDefault`, `Player#showEntity/hideEntity(Plugin, Entity)`, `canSee`, `Entity#getTrackedBy()` (getTrackedPlayers deprecated). Spawn with `RegionAccessor#spawn(Location, Class, Consumer)` so config happens before tracking.
- Clicks on Interaction: right = `PlayerInteractAtEntityEvent` (`PlayerInteractEntityEvent` is @Obsolete); left = `EntityDamageByEntityEvent` (Paper #8980 closed 2023) + `PrePlayerAttackEntityEvent` (package io.papermc.paper.event.player; Javadoc silent on Interaction). Not confirmed in-game.
- Data components: `ItemStack.of(Material)`, `setData(DataComponentType.Valued<T>, T)`, `DataComponentTypes.ITEM_MODEL` is `Valued<Key>`.
- Resource packs: `ResourcePackInfo.resourcePackInfo(UUID, URI, String sha1hex)`, `ResourcePackRequest.resourcePackRequest().packs().prompt().required().replace()`, `Audience#sendResourcePacks`; legacy `Player#setResourcePack` String overloads deprecated and clears other packs (Paper #10027). `PlayerResourcePackStatusEvent` getID()/getStatus(); Status = ACCEPTED, DECLINED, DOWNLOADED, FAILED_DOWNLOAD, SUCCESSFULLY_LOADED, INVALID_URL, FAILED_RELOAD, DISCARDED.
- Pack formats: 26.1=84.0, 26.2=88.0, 26.3=97.1 (wiki). pack.mcmeta uses min_format/max_format (int = minor 0 / any minor), no pack_format.
- plugin.yml `libraries:` = Maven Central GAV list (preview feature). Maven Central latest 2026-10-18: H2 2.5.252, MariaDB 3.5.10, HikariCP 7.1.0.

Tool quirks: WebFetch on huge Javadoc pages (Player.html ~500k chars) needs `offset` (showEntity/hideEntity detail was at offset 300000). Some Javadoc class URLs 404 (PrePlayerAttackEntityEvent lives under io/papermc/paper/event/player/).
