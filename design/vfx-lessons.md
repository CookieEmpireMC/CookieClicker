# VFX lessons

One dated bullet per lesson. Promote recurring ones to `design/vfx-style-guide.md`.

- 2026-10-18: Rising sparkles must start at age 0 = the origin (`p = age / (life - 1)`), not `(age + 1) / life`; the first version left a visible gap between the ring and the first spark (preview iteration 1).
- 2026-10-18: Spread 0.4 inside a radius 1.0 ring read as a thin vase in the side view; 0.5 (half the ring radius) fills the ring better.
- 2026-10-18: Ease-out on rising motion bunches the last ticks at the top; that's the intended "hang", but it makes the top the densest spot. Keep counts low there.
- 2026-10-18: `BukkitScheduler#runTaskTimer(Plugin, Consumer<BukkitTask>, long, long)` returns `void` in 26.3, so it can't be tracked for shutdown. Use the `Runnable` overload, which returns the `BukkitTask`.
- 2026-10-18: `spawnParticle` with `count = 0` turns the offsets into a direction/velocity. To place a particle exactly on a precomputed point, use `count = 1`, zero offsets and speed 0.
- 2026-10-18: Golden-angle directions give an even, non-repeating spread for any count without randomness, so the preview is exactly what the game shows.
- 2026-10-18 (bakery-core): In 26.3 `org.bukkit.Particle` is still an enum with `getKey()` and `getDataType()`, so particle names can be resolved and validated in plain unit tests (no registry or server needed). `Material.matchMaterial` and `YamlConfiguration` also work in tests; `ItemStack.of` and `createBlockData` don't, so they're resolved in `Effects.load()`.
- 2026-10-18 (bakery-core): Emulating display interpolation for previews: at a tick where a new keyframe arrives, first advance the running interpolation to that tick, then start the new one from there. Doing it the other way round lost a tick and under-reported the pop-in peak (unit test caught it).
- 2026-10-18 (bakery-core): `emit-every: 2` on ease-out sparkles leaves visible "rows" in the side view (first gap is the biggest). With 6 sparks, emitting every tick (48 particles) reads as continuous trails and is still in budget.
- 2026-10-18 (bakery-core): A growing ring starting at radius 0.15 with 16 points is a dense blob on tick 0; start at ≥ 0.25 so every frame reads as a ring.
- 2026-10-18 (bakery-core): Repeated pops on the same display must reuse the rest scale captured by the first pop; reading `getTransformation().getScale()` mid-squash would compound the squash on fast clicking.
- 2026-10-18 (bakery-core): The preview plot needs approximate flight paths for launched particles (`~flight`, faint, small) or a burst looks like a dot cluster; but they must not count toward the particle total in the title.
- 2026-10-18 (bakery-core cleanup): Anything that runs per loaded entity server-wide (chunk-load sweep, `sweepLeftovers`) must reject foreign entities with the cheap scoreboard-tag lookup before touching the PDC; this only stays correct because `Spawner` always sets both, so keep that invariant.
- 2026-10-18 (bakery-core cleanup): Every public `Effects` method, not just play/pop, needs the main-thread guard; a rotating index (`jitter++ % n`) needs `Math.floorMod` or it goes negative after int overflow.
