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
