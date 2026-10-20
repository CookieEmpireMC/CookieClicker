---
name: ledger-vfx
description: review ledger entry for the vfx package (starter-house vfx branch)
metadata:
  type: reference
---
- vfx package (Effects, EffectTicker, Job, DropJob, LoopJob, ViewerJobs, EffectHandle, shape/DropTrack, config parser) verified at 2bcf65a on starter-house/vfx-designer (2026-10-19): contract signatures, callback rules, per-viewer hidden spawn, pending buffering, caps, perf, ArchitectureRulesTest. READY. Re-review with `git diff 2bcf65a..HEAD -- src/main/java/dev/casperrs/cookieClicker/vfx src/main/resources/effects.yml`.
- Test trick: EffectTicker is testable without MockBukkit/Mockito via java.lang.reflect.Proxy for Plugin/Server/BukkitScheduler/BukkitTask (EffectTickerTest). No Mockito in pom.
