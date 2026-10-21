---
name: conveyor-belt-facts
description: Verified spectator-camera and display-interpolation API facts on Paper 26.3, belt/camera contract details from Pat, ownership limit on tests (2026-10-21)
metadata:
  type: reference
---

Verified 2026-10-21 by javap + sources jar + clean compile (-Xlint:deprecation), paper-api 26.3.build.205-stable. Stable.
- `Player#setSpectatorTarget(@Nullable Entity)` throws IllegalStateException unless in SPECTATOR; `getSpectatorTarget()`.
  `com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent` (Cancellable, `getSpectatorTarget()`) and
  `PlayerStartSpectatingEntityEvent` exist. `PlayerTeleportEvent.TeleportCause.SPECTATE` exists.
- `Display#setTeleportDuration` only 0..59 (IllegalArgumentException); `setInterpolationDuration(int)` has no limit,
  so long smooth rides = one `setTransformation` with delay 0 + duration N, sent a tick after spawn.
- `PlayerGameModeChangeEvent#getCause()`; `Entity#setRotation(float,float)` not deprecated; `Bukkit.getDefaultGameMode()`.
- Not verified in game (design assumptions in house/CinematicCamera + belt/ConveyorBelts): setCamera's internal
  teleport fires PlayerTeleportEvent (wrapped in selfTeleport anyway); ItemDisplay works as a spectator target.

Contract (Pat, design/conveyor-belt.md, 2026-10-21): belt.yml = direction north, surface-height (int), item-model,
named `columns`, `first`/`last`/`middle` (cell i uses middle[(i-1)%len]); only belt cells y0..y0+h-1 change. Surface
display at (cell+0.5, y0+h, cell+0.5), NONE, model bottom at origin; cookies ride at +0.0625. layout.camera `at` is a
feet cell (eye +1.62), may be outside the plot.
Ownership: plugin-coder may NOT write src/test (tester-owned) even when asked for unit tests; smoke-test pure classes
in the scratchpad (javac -d out + java @argfile; argfile paths must be absolute) and list tests for Gandalf.
