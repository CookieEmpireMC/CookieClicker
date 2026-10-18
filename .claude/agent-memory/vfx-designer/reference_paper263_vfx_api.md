---
name: reference-paper263-vfx-api
description: Paper 26.3 particle/display/scheduler API facts verified from the local sources jar, and what works in plain unit tests
metadata:
  type: reference
---

Verified 2026-10-18 against `~/.m2/repository/io/papermc/paper/paper-api/26.3.build.205-stable/paper-api-26.3.build.205-stable-sources.jar`
(unzip single files into the scratchpad and grep; faster and more exact than the web Javadocs).

- `org.bukkit.Particle` is still an **enum** (Keyed, `getKey()`, `getDataType()`), no deprecations. Data types:
  DUST→DustOptions(Color, size 0.01–4), DUST_COLOR_TRANSITION→DustTransition, ITEM→ItemStack, BLOCK/FALLING_DUST/
  DUST_PILLAR/BLOCK_CRUMBLE/BLOCK_MARKER→BlockData, ENTITY_EFFECT/FLASH/TINTED_LEAVES→Color, EFFECT/INSTANT_EFFECT→
  Spell(Color, power), DRAGON_BREATH/SCULK_CHARGE→Float, SHRIEK→Integer, VIBRATION/TRAIL/GEYSER* special.
- `Player#spawnParticle(Particle, x,y,z, count, ox,oy,oz, speed, T data)` exists; 26.x adds a per-axis
  `speedX/Y/Z + force + RandomizationType` overload (semantics unverified in game).
- `BukkitScheduler#runTaskTimer(Plugin, Runnable, long, long)` returns BukkitTask; Consumer overload returns void.
- `Transformation` constructor copies its vectors; getters return internal references.
- `TextDisplay#text(Component)` (String setText deprecated), `setTextOpacity(byte)`, `setBackgroundColor`,
  `setDefaultBackground`; `Color.fromARGB(int)` exists.
- Works in plain JUnit (no server): Particle enum, `Material.matchMaterial`, `YamlConfiguration.loadFromString`,
  `Color`, `NamespacedKey.fromString`. Needs a server: `ItemStack.of`, `Material#createBlockData`, `Bukkit.*`.

Related: [[project-effects-engine]]
