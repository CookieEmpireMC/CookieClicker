---
name: ledger-vfx
description: review ledger entry for the vfx package (starter-house vfx branch)
metadata:
  type: reference
---
- vfx package (Effects, EffectTicker, Job, DropJob, LoopJob, ViewerJobs, EffectHandle, shape/DropTrack, config parser) verified at 2bcf65a on starter-house/vfx-designer (2026-10-19): contract signatures, callback rules, per-viewer hidden spawn, pending buffering, caps, perf, ArchitectureRulesTest. READY. Re-review with `git diff 2bcf65a..HEAD -- src/main/java/dev/casperrs/cookieClicker/vfx src/main/resources/effects.yml`.
- Test trick: EffectTicker is testable without MockBukkit/Mockito via java.lang.reflect.Proxy for Plugin/Server/BukkitScheduler/BukkitTask (EffectTickerTest). No Mockito in pom.
- Test trick (upgrade-holograms, 2026-10-21): PopJob is testable without MockBukkit: a `java.lang.reflect.Proxy` Display that keeps `getTransformation`, records `setTransformation` and `setInterpolationDuration`, and returns null/true for the rest. Drive steps with `job.step()` after setting the fake server tick; `job.cancel()` runs the cut-short cleanup. Tests in PopMotionTest.
- Probe costs: a throwaway test that prints `EffectsConfigParser.parse(EffectsConfigParserTest.shipped(), null).preset(id).particleCost()` (method must `throws Exception`) gives exact per-play particle counts; run with `mise exec -- mvn -q test -Dtest=<Probe>`, then delete it.
