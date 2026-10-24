---
name: review-pipeline-facts
description: Local Paper review server + Chunky render pipeline (tools/review/, mise run review-render) - verified command lines and quirks (2026-10-24)
metadata:
  type: reference
---

Verified 2026-10-24 by running `mise run review-render factory --at 200 -60 0` end to end (stable unless the setup moves).

- Layout outside the repo: `~/bakery-review/{server,chunky,scenes,shots}`; server = Paper 26.3 flat world, RCON
  127.0.0.1:25575 pw `review`; the orchestrator runs it as `java -Xmx3G -jar paper.jar --nogui` (check with
  `rcon.reachable` before starting one). Flat world: first air layer y -60 (grass -61, dirt -62/-63, bedrock -64).
- Chunky: `java -Dchunky.home=~/bakery-review/chunky/home -jar ChunkyLauncher.jar -texture <home/resources/minecraft.jar>
  -reload-chunks -render <abs scene.json> -f`; an absolute scene path makes its folder the scene dir; output is
  `<scene dir>/snapshots/<name>-<spp>.png`. Scene needs yClipMin/yMin -64 (else empty). 1280x720 @64 spp ~8-18 s.
- Before Chunky reads the world: `save-all flush` over RCON. Chunks: `forceload add`, then poll
  `execute if loaded x y z` (reply contains "passed").
- `setblock ... strict` replies "Changed the block" / "Could not set"; fill replies "Successfully filled" /
  "No blocks were filled".
- mise passes extra args to a single-command task without a `usage` field (`mise run review-render factory --shots eye`),
  but `mise run <task> --help` is swallowed by mise ("does not accept any arguments").
- `tools/review/plot_plan.py` mirrors the Java planner (HouseDesignParser ground-depth, DecorKeepOut incl. ground
  keep-out, FenceStyle spacing, FenceLayout): change both together.
