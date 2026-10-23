package dev.casperrs.cookieClicker.sound;

/**
 * One event from {@code sounds.yml} (owned by the sound-designer). key is a pack sound or {@link #BLOCK_KEY}, fallback
 * the vanilla sound without the pack, source a lower-case name ({@code master}, {@code ui}, …).
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
