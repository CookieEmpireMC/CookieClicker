package dev.casperrs.cookieClicker.sound;

/**
 * One event from {@code sounds.yml} (owned by the sound-designer).
 *
 * @param key      sound in our resource pack, e.g. {@code cookieclicker:ui.cookie_click}
 * @param fallback vanilla sound used when the player hasn't loaded the pack
 * @param source   lower-case sound source name ({@code master}, {@code ui}, {@code players}, …)
 */
public record SoundDef(String event, String key, String fallback, float volume,
                       float pitchMin, float pitchMax, String source) {
}
