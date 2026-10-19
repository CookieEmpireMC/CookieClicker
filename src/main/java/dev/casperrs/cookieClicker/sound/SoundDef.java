package dev.casperrs.cookieClicker.sound;

/**
 * One event from {@code sounds.yml} (owned by the sound-designer).
 *
 * @param key      sound in our resource pack, e.g. {@code cookieclicker:ui.cookie_click}, or {@link #BLOCK_KEY}
 * @param fallback vanilla sound used when the player hasn't loaded the pack
 * @param source   lower-case sound source name ({@code master}, {@code ui}, {@code players}, …)
 */
public record SoundDef(String event, String key, String fallback, float volume,
                       float pitchMin, float pitchMax, String source) {

    /**
     * Special {@code key}: play the place sound of the block passed to
     * {@link SoundService#playBlock(org.bukkit.entity.Player, String, org.bukkit.Location, org.bukkit.block.data.BlockData)}
     * (its vanilla sound group), with this entry's volume, pitch and source. Needs no resource pack.
     */
    public static final String BLOCK_KEY = "@block";

    public boolean blockSound() {
        return BLOCK_KEY.equals(key);
    }
}
