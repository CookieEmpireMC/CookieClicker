# build-quality: re-review (house redesign, round 3)

Target: Paper 26.x, Java 25. Reviewed at the merge of `build-quality/model-designer` on `feature/build-quality` (2922ae8).

## Test change
`HouseDesignParserTest.theShippedDefaultDesignParsesAndIsWhatThePaletteSays`: size 9x7x7 -> 17x14x15, blocks 170 -> 590. Nothing else pinned (same scope as before). Values checked against `houses/default.yml` (size [17,14,15], ground-depth 1, 590 in header, 64 palette entries, points chimney [1,11,8] and door [6,1,6]). Note: the YAML chimney is y=11, not 12; points are in design y, the test only asserts the point exists. Gravel is still in the palette (`e`, used by the path edge), only the roof patch blocks are gone; not pinned.

## Tests
`mise run tools-test`: 81 tests, OK (1 skipped). `tools/test_house_design.py` now follows the redesigned generator: a `generate()` helper mirrors `house_default.main()` up to the file write (resolve, to_palette, to_layers with ground depth), the contract test pins [17,14,15] / ground-depth 1, and point lookups use layer index = design y + ground.
`mise run check-quiet`: 417 tests, 0 failures, BUILD SUCCESS.

## Integration review (houses vs plugin)
- default.yml and factory.yml parse via the shipped-file tests; factory is [19,18,14] with ground-depth 1, 75 palette entries.
- Factory contract unchanged in the diff: points (smoke, steam, belt_out [12,0,1], door [8,1,2]), the "Cookie Works" sign at [10,4,1], and the layout are not touched; only size y and ground-depth changed (points are design-y, so they do not shift).
- decor.yml stays within `Decor.MIN_Y` (-2); the well water source sits at y -1 and -2 enclosed by cobblestone on all four sides with cobblestone below, so it cannot flow. No non-persistent leaves in any of the four files.
- No Java changed in `src/main` since cbefa0f; no performance impact.

No findings.

VERDICT: READY
