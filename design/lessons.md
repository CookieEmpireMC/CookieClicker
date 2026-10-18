# Lessons (Pixel Pat)

One dated bullet per lesson: what worked, what looked wrong, how it was fixed.

## Technique notes (in my own words)
- Hue shifting: each ramp step towards the light moves the hue towards yellow, each step towards shadow moves it towards red/purple. Food stays warm and shadows don't turn muddy grey.
- Pillow shading means shading that follows the outline evenly. Avoid it by picking the light direction first and letting the shadow build up on the far side only.
- Round objects read as round when the shading bands are crescents shifted away from the light, not concentric rings.
- At 64 px, 4–5 value steps per material are enough; more steps give banding (stair-step stripes of near-equal colours).
- Clusters: group pixels of one colour into blobs of 3+ pixels. Lone pixels read as noise when the texture is shown small.

## Job log
- 2026-10-18 (cookie): Shading from a height field (rounded rim + dome + bumps) lit by one normal·light dot product, then quantised into a ramp, gives consistent top-left light for free. Repeatable for any round object.
- 2026-10-18 (cookie): Value noise as surface bumps made diagonal streaks that looked like wood grain. Gaussian "lumps" (round mounds) read as baked dough instead. Keep the lump amplitude low (0.5–0.9 against a 6 px rim), or overlapping back-sides form big dark smears that look like stains.
- 2026-10-18 (cookie): Chunks shaded with a 1 px edge rule looked flat. Giving each chunk its own small height field (distance transform → dome) and lighting it like the dough made them look like real 3D chocolate.
- 2026-10-18 (cookie): 1 px crack lines looked like scratches at 64 px, even with a lit lip. Dropped them; a clean face with soft lumps reads better. Cracks need 2+ px width or shouldn't be there at all.
- 2026-10-18 (cookie): The orphan cleanup on the shade map alone isn't enough: contact shadows added later make new lone pixels. Run a final cleanup on the finished image (no same colour among 8 neighbours → majority of the 4 neighbours), keeping diagonal 1 px lines and chocolate glints.
- 2026-10-18 (cookie): Checking 1× next to ×4 on light and dark backgrounds caught nothing this time, but it confirmed that the dark outline keeps the silhouette readable on dark backgrounds. Keep doing it.
- 2026-10-18 (cookie 3D): Fitting the slab stack by "maximum covered area with few holes" gave a 1-unit-tall centre slab with nubs sticking out. Minimising a cost (uncovered painted pixels + 3 × covered transparent pixels) over all boundary choices gave a round, symmetric stack; also forbid a top slab narrower than ~3.5 units or it becomes a nub. 3 iterations.
- 2026-10-18 (cookie 3D): The trimmed art lost its dark outline on the slab edges and looked sliced. A derived face texture (art masked to the slab silhouette + outline redrawn on its edge) fixed it; the source art stays untouched.
- 2026-10-18 (cookie 3D): The first edge texture (profile bands shifted per run) read as wood grain. Fixed lips at the faces, a flat golden middle and irregular light blister clusters read as baked crust.
- 2026-10-18 (icons): Height-field shading on flat rectangles (a head) makes a wide dark rim that reads as a wooden box. For boxy parts paint a flat base plus one lit and one shadow column by hand; keep height-field shading for round parts (hat puffs, grain ears).
- 2026-10-18 (icons): Thin rotated ellipses (grain ears 1.75 px wide) shattered into noise once outlined, and the sheaf looked like a torch. Ears 2+ px wide, each with its own outline ring painted before its fill (back to front), stay separate and readable. 1 px stalks/awns don't survive at 32 px.
- 2026-10-18 (icons): ×8 previews of a 32 px icon are too small to judge in the image viewer; use ×16 for icons, plus the 1×/2×/4× strip on light and dark.
- 2026-10-18 (tools): The worktree sandbox refuses Bash commands that use shell variables or loops, or pipe text mentioning the VCS tool into python. Use literal paths, the Edit tool, or a scratch Python script.
