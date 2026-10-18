# Sounds

Every sound event the plugin plays, what it should feel like, and where it comes from.
Config: `src/main/resources/sounds.yml` (event name -> pack key, vanilla fallback, volume, pitch range, source).
Pack: `resourcepack/assets/cookieclicker/sounds.json`, subtitles in `resourcepack/assets/cookieclicker/lang/en_us.json`.
Regenerate everything with `mise run sounds`; shared helpers in `design/sound-tools/synth.py`.

All files: mono Ogg Vorbis, 44.1 kHz, original procedural synthesis (numpy/scipy), no external sources.
Licence: our own work, part of this project. Measurements are from `mise run sound-check`.

## bakery-core

| Event (sounds.yml) | Pack key | Files | Feel | Measured | Fallback | Status |
|---|---|---|---|---|---|---|
| `cookie_click` | `cookieclicker:ui.cookie_click` | `sounds/ui/cookie_click_1..4.ogg` | Crunchy, satisfying bite/tap: a soft pitch-dropping tap with a cluster of tiny crumb cracks. 4 variants (different crumb timing, ±6 % pitch); the plugin adds 0.92-1.08 pitch on top. Must stay pleasant on the 1000th click. | 120 ms each, peak -2.3/-2.7/-2.5/-3.0 dBFS, RMS -18.9/-18.0/-17.6/-19.1 dBFS | `minecraft:block.wood.hit` | placeholder (needs audition) |
| `cookie_crit` | `cookieclicker:ui.cookie_crit` | `sounds/ui/cookie_crit.ogg` | "Crispy crit": a deeper, longer crunch with more crumbs, then a quick rising golden sparkle (soft bells C6-E6-G6-C7, slightly detuned for shimmer). Clearly more rewarding than a click, not a fanfare. | 390 ms, peak -3.8 dBFS, RMS -18.1 dBFS | `minecraft:entity.experience_orb.pickup` | placeholder (needs audition) |
| `purchase` | `cookieclicker:ui.purchase` | `sounds/ui/purchase.ogg` | Bakery "ka-ching" without a cash register: a warm wooden counter knock with a pinch of crumbs ("ka"), then a small shop-door bell struck twice, G5 then D6 ("ching"). | 380 ms, peak -4.3 dBFS, RMS -17.9 dBFS | `minecraft:block.note_block.bell` | placeholder (needs audition) |
| `cant_afford` | `cookieclicker:ui.cant_afford` | `sounds/ui/cant_afford.ogg` | Soft "uh-uh": two muted low thuds, the second lower, with a quiet low-passed buzz. Dark, short, a touch quieter than the set so misclicks never grate. | 200 ms, peak -7.7 dBFS, RMS -18.8 dBFS | `minecraft:block.note_block.bass` | placeholder (needs audition) |

Scripts: `design/sound-scripts/cookie_click.py`, `cookie_crit.py`, `purchase.py`, `cant_afford.py` (parameters as constants at the top, fixed seeds).

## Needs human review

Nobody has listened to these yet; every sound needs an audition (`mise run review` builds a page with players).
Absolute paths (worktree `bakery-core/sound-designer`; after the merge the same paths under the main checkout):

- `/home/casper-rs/Documents/GitHub/CookieClicker/.claude/worktrees/agent-ae9ee30d0e2fb85a7/resourcepack/assets/cookieclicker/sounds/ui/cookie_click_1.ogg`
- `/home/casper-rs/Documents/GitHub/CookieClicker/.claude/worktrees/agent-ae9ee30d0e2fb85a7/resourcepack/assets/cookieclicker/sounds/ui/cookie_click_2.ogg`
- `/home/casper-rs/Documents/GitHub/CookieClicker/.claude/worktrees/agent-ae9ee30d0e2fb85a7/resourcepack/assets/cookieclicker/sounds/ui/cookie_click_3.ogg`
- `/home/casper-rs/Documents/GitHub/CookieClicker/.claude/worktrees/agent-ae9ee30d0e2fb85a7/resourcepack/assets/cookieclicker/sounds/ui/cookie_click_4.ogg`
- `/home/casper-rs/Documents/GitHub/CookieClicker/.claude/worktrees/agent-ae9ee30d0e2fb85a7/resourcepack/assets/cookieclicker/sounds/ui/cookie_crit.ogg`
- `/home/casper-rs/Documents/GitHub/CookieClicker/.claude/worktrees/agent-ae9ee30d0e2fb85a7/resourcepack/assets/cookieclicker/sounds/ui/purchase.ogg`
- `/home/casper-rs/Documents/GitHub/CookieClicker/.claude/worktrees/agent-ae9ee30d0e2fb85a7/resourcepack/assets/cookieclicker/sounds/ui/cant_afford.ogg`

What to listen for:
- **cookie_click:** does it read as "crunchy cookie" rather than "static" or "wood block"? Spam-click 50 times: any variant that stands out or gets annoying? Is the crunch too loud relative to the tap?
- **cookie_crit:** obviously special next to a click, but not so long or bright that 5 % of clicks becomes tiring? The sparkle should feel golden, not like an alarm.
- **purchase:** reads as "bought something nice at a bakery"; the knock should be audible before the bell.
- **cant_afford:** soft and clear on laptop speakers (it was pitched up for that); not scolding.
- **Relative loudness in game:** all are levelled to about -18 dBFS RMS; cant_afford is mostly low frequencies and may sound quieter than the RMS suggests. Retune with `volume` in sounds.yml.
- **Fallbacks** (pack declined): `block.wood.hit`, `entity.experience_orb.pickup`, `block.note_block.bell`, `block.note_block.bass`. Are they acceptable stand-ins?
