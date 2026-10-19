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
| `house_chimney_crackle` | `cookieclicker:house.chimney_crackle` | `sounds/house/chimney_crackle.ogg` | Cosy, soft fire in the oven below the chimney: a low rumbling "fire breath" bed with scattered small crackles, a few little clusters and four soft wood pops. **Seamless 3.0 s loop**: built circularly (events wrap around, frequency-domain filtering), no crackles within 60 ms of the loop point, and the bed dips softly there, so a replay that starts slightly early or late doesn't click. Replay every 60 ticks (at pitch p it lasts 3.0/p s). Intentionally ~3 dB under the -16 LUFS target: it's background. | 3000 ms, peak -3.9 dBFS, -19.1 LUFS; end-to-start jump -55 dBFS (smaller than a typical sample step) | `minecraft:block.campfire.crackle` | vol 0.5, pitch 0.97-1.03, `ambient` | placeholder (needs audition) |

Scripts: `design/sound-scripts/house_hammer.py`, `house_block_land.py`, `house_fanfare.py`, `house_chimney_crackle.py`.
New shared helpers in `design/sound-tools/synth.py`: `gain_to_lufs`, `soft_limit`, `fft_filter` (circular, for loops), `place_circular`.

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
