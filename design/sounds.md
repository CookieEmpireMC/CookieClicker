# Sounds

Every sound event the plugin plays, what it should feel like, and where it comes from.
Config: `src/main/resources/sounds.yml` (event name -> pack key, vanilla fallback, volume, pitch range, source).
Pack: `resourcepack/assets/cookieclicker/sounds.json`, subtitles in `resourcepack/assets/cookieclicker/lang/en_us.json`.
Regenerate everything with `mise run sounds`; shared helpers in `design/sound-tools/synth.py`.

All files: mono Ogg Vorbis, 44.1 kHz, original procedural synthesis (numpy/scipy), no external sources.
Licence: our own work, part of this project. Measurements are from `mise run sound-check`.

## Special key `@block`

An event whose `key` is `"@block"` has no file of its own: the plugin plays the vanilla **place** sound
of the block the event is about (`BlockData#getSoundGroup().getPlaceSound()`, e.g. `minecraft:block.wood.place`
for planks; research: `docs/research/fake-blocks.md` Q8). Volume, pitch range and source still come from
`sounds.yml`; `fallback` is played if the block's sound can't be resolved. No `sounds.json` entry, no subtitle
of ours (vanilla's own subtitle shows), and it works with or without our resource pack. Only events that pass
a block can use it (currently `house_block_place`).

## bakery-core

| Event (sounds.yml) | Pack key | Files | Feel | Measured | Fallback | Status |
|---|---|---|---|---|---|---|
| `cookie_click` | `cookieclicker:ui.cookie_click` | `sounds/ui/cookie_click_1..4.ogg` | Crunchy, satisfying bite/tap: a soft pitch-dropping tap with a cluster of tiny crumb cracks. 4 variants (different crumb timing, ±6 % pitch); the plugin adds 0.92-1.08 pitch on top. Must stay pleasant on the 1000th click. | 120 ms each, peak -2.3/-2.7/-2.5/-3.0 dBFS, RMS -18.9/-18.0/-17.6/-19.1 dBFS | `minecraft:block.wood.hit` | placeholder (needs audition) |
| `cookie_crit` | `cookieclicker:ui.cookie_crit` | `sounds/ui/cookie_crit.ogg` | "Crispy crit": a deeper, longer crunch with more crumbs, then a quick rising golden sparkle (soft bells C6-E6-G6-C7, slightly detuned for shimmer). Clearly more rewarding than a click, not a fanfare. | 390 ms, peak -3.8 dBFS, RMS -18.1 dBFS | `minecraft:entity.experience_orb.pickup` | placeholder (needs audition) |
| `purchase` | `cookieclicker:ui.purchase` | `sounds/ui/purchase.ogg` | Bakery "ka-ching" without a cash register: a warm wooden counter knock with a pinch of crumbs ("ka"), then a small shop-door bell struck twice, G5 then D6 ("ching"). | 380 ms, peak -4.3 dBFS, RMS -17.9 dBFS | `minecraft:block.note_block.bell` | placeholder (needs audition) |
| `cant_afford` | `cookieclicker:ui.cant_afford` | `sounds/ui/cant_afford.ogg` | Soft "uh-uh": two muted low thuds, the second lower, with a quiet low-passed buzz. Dark, short, a touch quieter than the set so misclicks never grate. | 200 ms, peak -7.7 dBFS, RMS -18.8 dBFS | `minecraft:block.note_block.bass` | placeholder (needs audition) |

Scripts: `design/sound-scripts/cookie_click.py`, `cookie_crit.py`, `purchase.py`, `cant_afford.py` (parameters as constants at the top, fixed seeds).

## starter-house

The per-player build cinematic: dozens of landings and taps in 20-30 s, so these are short, soft, rounded and varied.

| Event (sounds.yml) | Pack key | Files | Feel | Measured | Fallback | Config | Status |
|---|---|---|---|---|---|---|---|
| `house_hammer` | `cookieclicker:house.hammer` | `sounds/house/hammer_1..4.ogg` | Light carpenter taps on wood and nails: a short wooden "tok" with a quickly damped nail "tink" and a tiny strike click. Variants: 2 single taps, a tap-tap, a light tap-tap-tap. Authored low, played at pitch 1.15-1.35 (shared with the anvil fallback, which then sounds small). | 150/150/260/330 ms, peak -4.1/-5.1/-4.1/-4.3 dBFS, RMS -18.4/-18.5/-18.9/-18.9 dBFS | `minecraft:block.anvil.place` (quiet, high) | vol 0.4, pitch 1.15-1.35, `block` | placeholder (needs audition) |
| `house_block_land` | `cookieclicker:house.block_land` | `sounds/house/block_land_1..4.ogg` | Soft "thunk" of a block landing on its spot: a round pitch-dropping thud (300 -> 175 Hz plus its 2nd harmonic), a short low-passed dust puff, then a smaller, slightly higher bounce 80-95 ms later (variants 3/4 add a tiny settle). Soft 2 ms attack: never a door knock. | 230 ms each, peak -3.2/-2.3/-3.1/-2.4 dBFS, RMS -17.8 dBFS | `minecraft:block.wood.place` | vol 0.5, pitch 0.9-1.1, `block` | placeholder (needs audition) |
| `house_block_place` | `@block` (see above) | none (vanilla) | The landed block's own vanilla place sound, slightly lower like vanilla placing. Layered with `house_block_land`. | n/a | `minecraft:block.stone.place` | vol 0.45, pitch 0.8-0.95, `block` | uses vanilla sounds |
| `house_fanfare` | `cookieclicker:house.fanfare` | `sounds/house/fanfare.ogg` | Cheerful "your bakery is open!": quick plucked pickup G4-C5-E5-G5, a short F major chord, then a ringing C major chord on a warm reed/horn-like pad with plucks, a pinch of shaker and three sparkle bells (E6-G6-C7, same family as the crit sparkle). Ends with a 300 ms fade. | 2800 ms, peak -4.3 dBFS, -16.0 LUFS | `minecraft:ui.toast.challenge_complete` | vol 0.8, pitch 1.0 (fixed), `ui` | placeholder (needs audition) |
| `house_chimney_crackle` | `cookieclicker:house.chimney_crackle` | `sounds/house/chimney_crackle.ogg` | Cosy, soft fire in the oven below the chimney: a low rumbling "fire breath" bed with scattered small crackles, a few little clusters and four soft wood pops. **Seamless 3.0 s loop**: built circularly (events wrap around, frequency-domain filtering), no crackles within 60 ms of the loop point, and the bed dips softly there, so a replay that starts slightly early or late doesn't click. Replay every 60 ticks on its own fixed timer; pitch is fixed at 1.0 (a random pitch changes the length 3.0/p s and drifts against the period). Intentionally ~3 dB under the -16 LUFS target: it's background. | 3000 ms, peak -3.9 dBFS, -19.1 LUFS; end-to-start jump -55 dBFS (smaller than a typical sample step) | `minecraft:block.campfire.crackle` | vol 0.5, pitch 1.0 (fixed), `ambient` | placeholder (needs audition) |

Scripts: `design/sound-scripts/house_hammer.py`, `house_block_land.py`, `house_fanfare.py`, `house_chimney_crackle.py`.
New shared helpers in `design/sound-tools/synth.py`: `gain_to_lufs`, `soft_limit`, `fft_filter` (circular, for loops), `place_circular`.

## factory-makeover

| Event (sounds.yml) | Pack key | Files | Feel | Measured | Fallback | Config | Status |
|---|---|---|---|---|---|---|---|
| `factory_ambient` | `cookieclicker:factory.ambient` | `sounds/factory/ambient.ogg` | Low, warm, cosy hum of a little cookie factory: a soft harmonic G2 (98 Hz) oven/machine drone with a second voice 0.25 Hz higher (one slow swell per loop), a quiet low rumble, a gentle 60 bpm machine "breath" with a soft muffled chuff on each beat and a very quiet wooden tick on the off-beat, plus five faint dull oven crackles. Low-passed at 3 kHz. **Seamless 4.0 s loop** (80 ticks), built circularly; the bar line sits on the loop point (hum dips there, the accented first chuff follows 70 ms later) so a slightly early/late replay is masked. Fixed pitch so overlapping replays never phase. Sits under `house_chimney_crackle`: file 0.4 dB quieter, config volume 0.3 vs 0.5 (~-5 dB), and almost no energy above 3 kHz where the crackle lives. Played at the first `points.steam` (or the house centre). | 4000 ms, peak -9.5 dBFS, -19.5 LUFS; end-to-start jump -58.7 dBFS (median sample step -52.8) | `minecraft:block.furnace.fire_crackle` | vol 0.3, pitch 1.0 (fixed), `ambient` | placeholder (needs audition) |

Script: `design/sound-scripts/factory_ambient.py`.

## conveyor-belt

| Event (sounds.yml) | Pack key | Files | Feel | Measured | Fallback | Config | Status |
|---|---|---|---|---|---|---|---|
| `belt_loop` | `cookieclicker:factory.belt_loop` | `sounds/factory/belt_loop.ogg` | Soft, cosy conveyor belt under the oven hum, not a drill: a thin motor hum on ~D2 (73.6 Hz, a fourth under the oven's G2, so the drones agree) with a second voice 0.2 Hz higher (one slow swell per loop), a quiet rubber-belt whir (noise 250-1100 Hz, gently pulsed per slat), and a light rattle of slats over the end roller, 5 per second, each a dull low thump + short muffled tick, every 4th a little stronger (a belt joint), with small timing/level jitter. Two faint low roller "tok"s per loop. Low-passed at 3 kHz (in game). **Authored for pitch 0.8** (shared with the fallback, which needs a low pitch): the 4.0 s file is the design sped up 1.25x, so at 0.8 it plays as a **seamless 5.0 s loop = 100 ticks**. Built circularly; no slat within 100 ms of the loop point, bed dips there. | 4000 ms file (5000 ms in game), peak -4.2 dBFS, -19.9 LUFS; end-to-start jump -63 dBFS (median sample step -44) | `minecraft:block.beehive.work` (pitch 0.8) | vol 0.2, pitch 0.8 (fixed), `ambient`; replay every 100 ticks (`belt.sound-every: 100`) | placeholder (needs audition) |

Script: `design/sound-scripts/factory_belt_loop.py` (design at 5.0 s, FFT-resampled to the 4.0 s file; `PLAY_PITCH` at the top).

## upgrade-holograms

`purchase` is reused for upgrade buys, unchanged.

| Event (sounds.yml) | Pack key | Files | Feel | Measured | Fallback | Config | Status |
|---|---|---|---|---|---|---|---|
| `upgrade_shelved` | `cookieclicker:ui.upgrade_shelved` | `sounds/ui/upgrade_shelved.ogg` | Soft "clink" as the icon lands on the shelf: a tiny wooden "tok" (the landing) and a short, low-passed (5.5 kHz) bell, E6 then a quiet B6 a few ms later. Cosy, not shrill. | 300 ms, peak -2.7 dBFS, RMS -18.1 dBFS | `minecraft:block.amethyst_block.chime` | vol 0.6, pitch 0.97-1.05, `ui` | placeholder (needs audition) |
| `bulk_toggle` | `cookieclicker:ui.bulk_toggle` | `sounds/ui/bulk_toggle_1..3.ogg` | Tiny dry wooden tick for cycling x1/x5/x10/Max: a short pitch-dropping pop with a faint crumb grain. 3 variants (pitch -8 %, 0, +8 %) so repeats don't machine-gun. | 90 ms each, peak -5.2..-5.4 dBFS, RMS -17.7..-17.8 dBFS | `minecraft:ui.button.click` | vol 0.5, pitch 0.97-1.05, `ui` | placeholder (needs audition) |

Scripts: `design/sound-scripts/upgrade_shelved.py`, `bulk_toggle.py`.

Review: is `upgrade_shelved` clearly a "clink", not a second purchase ding (it should be quieter and shorter than `purchase`)? Is `bulk_toggle` unobtrusive after 50 clicks, not clicky or shrill?

## Needs human review

Nobody has listened to these yet; every sound needs an audition (`mise run review` builds a page with players).
Absolute paths in the main checkout (the starter-house files land there after the merge; before that they are in the worktree `starter-house/sound-designer`, `/home/casper-rs/Documents/GitHub/CookieClicker/.claude/worktrees/agent-a1095fdd43d528461/…`):

- `/home/casper-rs/Documents/GitHub/CookieClicker/resourcepack/assets/cookieclicker/sounds/ui/cookie_click_1.ogg` (..`_4`)
- `/home/casper-rs/Documents/GitHub/CookieClicker/resourcepack/assets/cookieclicker/sounds/ui/cookie_crit.ogg`
- `/home/casper-rs/Documents/GitHub/CookieClicker/resourcepack/assets/cookieclicker/sounds/ui/purchase.ogg`
- `/home/casper-rs/Documents/GitHub/CookieClicker/resourcepack/assets/cookieclicker/sounds/ui/cant_afford.ogg`
- `/home/casper-rs/Documents/GitHub/CookieClicker/resourcepack/assets/cookieclicker/sounds/house/hammer_1.ogg` (..`_4`)
- `/home/casper-rs/Documents/GitHub/CookieClicker/resourcepack/assets/cookieclicker/sounds/house/block_land_1.ogg` (..`_4`)
- `/home/casper-rs/Documents/GitHub/CookieClicker/resourcepack/assets/cookieclicker/sounds/house/fanfare.ogg`
- `/home/casper-rs/Documents/GitHub/CookieClicker/resourcepack/assets/cookieclicker/sounds/house/chimney_crackle.ogg`
- `/home/casper-rs/Documents/GitHub/CookieClicker/resourcepack/assets/cookieclicker/sounds/factory/belt_loop.ogg` (before the merge: worktree `conveyor-belt/sound-designer`, `/home/casper-rs/Documents/GitHub/CookieClicker/.claude/worktrees/agent-a59158238e0ba9aaa/resourcepack/assets/cookieclicker/sounds/factory/belt_loop.ogg`)
- `/home/casper-rs/Documents/GitHub/CookieClicker/resourcepack/assets/cookieclicker/sounds/factory/ambient.ogg` (before the merge: worktree `factory-makeover/sound-designer`, `/home/casper-rs/Documents/GitHub/CookieClicker/.claude/worktrees/agent-a792ae8259bd6a8dd/resourcepack/assets/cookieclicker/sounds/factory/ambient.ogg`)

What to listen for:
- **cookie_click:** does it read as "crunchy cookie" rather than "static" or "wood block"? Spam-click 50 times: any variant that stands out or gets annoying? Is the crunch too loud relative to the tap?
- **cookie_crit:** obviously special next to a click, but not so long or bright that 5 % of clicks becomes tiring? The sparkle should feel golden, not like an alarm.
- **purchase:** reads as "bought something nice at a bakery"; the knock should be audible before the bell.
- **cant_afford:** soft and clear on laptop speakers (it was pitched up for that); not scolding.
- **Relative loudness in game:** all are levelled to about -18 dBFS RMS; cant_afford is mostly low frequencies and may sound quieter than the RMS suggests. Retune with `volume` in sounds.yml.
- **Fallbacks** (pack declined): `block.wood.hit`, `entity.experience_orb.pickup`, `block.note_block.bell`, `block.note_block.bass`. Are they acceptable stand-ins?
- **house_hammer:** at pitch ~1.25 does it read as "small hammer on a nail", not a beep or a woodblock? The nail "tink" should be short. Is the tap-tap-tap variant too busy when it overlaps landings?
- **house_block_land:** soft thunk + an audible little bounce, not a knock, not boomy on headphones. Layered with the block's own place sound (`house_block_place`): does the combination get muddy or too loud over a whole build? If so, lower `house_block_place` volume first.
- **Whole build (20-30 s):** play the full cinematic once. Fatiguing? Too many hammer taps? Retune with `volume`/pitch in sounds.yml or ask the coder to play hammer taps less often.
- **house_fanfare:** cheerful and "bakery", not a victory jingle from another game; the final chord shouldn't feel too long next to the title.
- **house_chimney_crackle:** cosy fire, not rain or static; loop it for a minute: any audible "seam" or recognisable repeating pop every 3 s? At volume 0.5 is it present but ignorable?
- **Fallbacks:** `block.anvil.place` at volume 0.4 / pitch 1.15-1.35 may still be clangy; if so, `block.wood.hit` is the softer stand-in. `ui.toast.challenge_complete` is long and grand for a fanfare stand-in.
- **factory_ambient:** a cosy oven/machine hum, not a fridge, transformer buzz or engine drone. Loop it for two minutes together with the chimney crackle: does the 1 Hz breath/chuff become a metronome, is the 4 s seam audible, and does it sit clearly under the crackle at 0.3 vs 0.5? On laptop speakers the hum should still be faintly there (its 200-800 Hz harmonics). The fallback `block.furnace.fire_crackle` at 0.3 is crackly rather than hummy; acceptable as a stand-in?
- **belt_loop:** a soft, cosy belt (gentle hum + light slat rattle), not a drill, sewing machine or helicopter. Loop it at pitch 0.8 every 100 ticks together with `factory_ambient` (0.3): does it sit clearly under the oven hum at 0.2, is the 5 s seam or the stronger every-4th slat audible as a repeat, and does the 5-per-second rattle get tiring? Fallback `block.beehive.work` at pitch 0.8 / volume 0.2: belt-like enough, or too buzzy/beelike?


## plot-polish (basket)

| Event (sounds.yml) | Pack key | Files | Feel | Measured | Fallback | Status |
|---|---|---|---|---|---|---|---|
| `basket_tier_up` | `cookieclicker:ui.basket_tier_up` | `sounds/ui/basket_tier_up.ogg` | Cookie basket reaches a higher tier (wicker, bushel, golden): soft rising whoosh, quick bell arpeggio C5-E5-G5-C6 landing on a ringing C6/E6/G6/C7 sparkle chord with coin-shimmer grains. Bigger and longer than `upgrade_shelved`, same bell family. Script `design/sound-scripts/basket_tier_up.py`. | 1200 ms, peak -4.9 dBFS, -16.0 LUFS (sound-check warns: UI sound > 400 ms, intended) | `minecraft:entity.player.levelup` | vol 0.8, pitch 1.0 (fixed, tuned chord), `ui` | placeholder (needs audition) |

Review: does the chime read as a clear "level up" without being shrill, and sit above `upgrade_shelved` in size without being louder than `house_fanfare`?

## building-roster

| Event (sounds.yml) | Pack key | Files | Feel | Measured | Fallback | Config | Status |
|---|---|---|---|---|---|---|---|
| `building_appear` | `cookieclicker:building.appear` | `sounds/building/appear_{1,2,3}.ogg` | A new building pops up on the plot: soft wooden plop with a pinch of crumb, then one small warm bell ding (A5 / B5 / D6 per variant). Pleasant on repeat. Script `design/sound-scripts/building_appear.py`. Original synthesis. | 390 ms, about -18 dBFS RMS | `minecraft:block.wood.place` | vol 0.6, pitch 0.95-1.08, `block` | placeholder (needs audition) |
| `building_milestone` | `cookieclicker:building.milestone` | `sounds/building/milestone.ogg` | A building type upgrades its look (25/100 owned): wooden thud, quick G-major bell arpeggio G5-B5-D6 landing on a G5/D6/G6/B6 chord with a soft pad and coin shimmer. Between `upgrade_shelved` and `basket_tier_up` in size, no whoosh. Script `design/sound-scripts/building_milestone.py`. Original synthesis. | 950 ms, -16 LUFS (sound-check warns: UI sound > 400 ms, intended) | `minecraft:ui.toast.challenge_complete` | vol 0.8, pitch 1.0 (fixed, tuned chord), `block` | placeholder (needs audition) |

Review: does appear stay pleasant when 3-4 buildings pop in quickly (bulk buy x10)? Does the milestone read as clearly bigger than appear but smaller than the basket tier-up? Both use `source: block` (positional, play at the building). The `challenge_complete` fallback is long for a milestone stand-in.

## ingredients

| Event (sounds.yml) | Pack key | Files | Feel | Measured | Fallback | Config | Status |
|---|---|---|---|---|---|---|---|
| `recipes_unlock` | `cookieclicker:recipe.unlock` | `sounds/recipe/unlock.ogg` | Recipe Book bought: short warm fanfare, plucked C5-E5-G5 pickup into a ringing C major chord with a low C4 and three sparkle bells (E6-G6-C7). Smaller than `house_fanfare`. Script `design/sound-scripts/recipe_book.py`. Original synthesis. | 1100 ms, -16 LUFS (UI > 400 ms warning, intended) | `minecraft:ui.toast.challenge_complete` | vol 0.8, pitch 1.0, `ui` | placeholder (needs audition) |
| `recipe_book_open` | `cookieclicker:recipe.open` | `sounds/recipe/open.ogg` | Soft page turn: gliding band-passed paper swish with two tiny flicks. Script as above. | 260 ms, about -19.5 dBFS RMS | `minecraft:item.book.page_turn` | vol 0.6, pitch 0.95-1.08, `ui` | placeholder (needs audition) |
| `recipe_buy` | `cookieclicker:recipe.buy` | `sounds/recipe/buy.ogg` | Recipe baked: quick page flip then a bright two-note chime G6-C7. Script as above. | 380 ms, about -18 dBFS RMS | `minecraft:entity.player.levelup` | vol 0.8, pitch 0.98-1.04, `ui` | placeholder (needs audition) |

Review: is the page turn paper-like and not hissy? Does the buy chime stay pleasant on repeated buys? Is the unlock clearly a bigger moment than the buy?
