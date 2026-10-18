# Review: cookie-art / cookie-art/model-designer (branch review)

Target: Paper 26.3 (26.x, Java 25). Reviewer: Gandalf.

## Checks
- Commits `feature/bakery-core..HEAD`: exactly Pat's 4 commits, no others.
- `mise run validate-assets`: 0 problems (3 files).
- cookie.png: 64x64, 8-bit RGBA; alpha only 0/255; all four corners and every border pixel transparent; 9 opaque colours, matching the 9-colour palette in the spec. Visual check: round cookie with chocolate chunks, top-left light.
- Item model definition `cookieclicker:cookie` -> model `cookieclicker:item/cookie` -> texture `cookieclicker:item/cookie`: all resolve.
- pack.mcmeta: min_format 97 / max_format 97 (26.3 = 97.x), as the brief asked. The format number is not in our docs; it comes from the spec's cited wiki page, so I could not verify it offline.
- `mise run assets` reproduces the PNG; `git status` clean afterwards.
- ownership-check against feature/bakery-core: OK.

## Findings
1. Minor (Pat): the spec has no explicit originality note. It says "generated pixel art" (script with fixed seed, own palette), which implies originality, but add one line saying the art is original and not copied from vanilla or other packs.
2. Info (Rick/docs): the 97.x pack format is not recorded in docs/paper-26x-reference.md. Consider adding it so later reviews can verify it.

No blocking issues.

VERDICT: READY
