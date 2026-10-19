# Review: starter-house/vfx-designer-outline (2026-10-19)

Target: Paper 26.x (unchanged). Reviewed `git diff f748411..HEAD` at e89d5fb.

- IDs `plot_outline`, `house_outline` match the contract (brief line 109).
- Presets: one `dust` particle, `force: true`, no entities; parser accepts them (shipped-config test `outlinePresetsAreOneForcedParticle` passes).
- No Java production change (only effects.yml, a test, design docs, lesson, memory).
- `mise run check` green, `mise run validate-assets` green (0 problems).
- Performance: ~120 plays per redraw per region every 20 ticks, 1 particle each (~240 particles/s per admin, admin-only). PERF-LOW: no action. Coder should keep it admin-only and cancel the redraw task on disconnect.

VERDICT: READY
