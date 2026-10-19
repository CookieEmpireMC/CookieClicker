package dev.casperrs.cookieClicker.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.sound.Sound;
import org.junit.jupiter.api.Test;

class SoundConfigParserTest {

    private static Map<String, Object> spec(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void parsesAFullEntry() {
        List<String> problems = new ArrayList<>();
        Map<String, SoundDef> out = SoundConfigParser.parse(Map.of("cookie_click", spec(
                "key", "cookieclicker:ui.cookie_click", "fallback", "minecraft:block.stone.hit",
                "volume", 0.8, "pitch-min", 0.9, "pitch-max", 1.1, "source", "UI")), problems::add);
        SoundDef d = out.get("cookie_click");
        assertEquals("cookieclicker:ui.cookie_click", d.key());
        assertEquals("minecraft:block.stone.hit", d.fallback());
        assertEquals(0.8f, d.volume());
        assertEquals(0.9f, d.pitchMin());
        assertEquals(1.1f, d.pitchMax());
        assertEquals("ui", d.source());
        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    void defaultsAreFilledIn() {
        List<String> problems = new ArrayList<>();
        SoundDef d = SoundConfigParser.parse(Map.of("e", spec("key", "a:b", "fallback", "c:d")), problems::add).get("e");
        assertEquals(1f, d.volume());
        assertEquals(1f, d.pitchMin());
        assertEquals(1f, d.pitchMax());
        assertEquals("master", d.source());
        assertTrue(problems.isEmpty());
    }

    @Test
    void badEntriesAreSkippedOthersLoad() {
        List<String> problems = new ArrayList<>();
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("good", spec("key", "a:b", "fallback", "c:d"));
        yaml.put("nokey", spec("fallback", "c:d"));
        yaml.put("badkey", spec("key", "UPPER Case:x", "fallback", "c:d"));
        yaml.put("notasection", "text");
        yaml.put("blankkey", spec("key", "  "));
        Map<String, SoundDef> out = SoundConfigParser.parse(yaml, problems::add);
        assertEquals(List.of("good"), List.copyOf(out.keySet()));
        assertEquals(4, problems.size(), problems.toString());
    }

    @Test
    void missingFallbackWarnsAndUsesTheKey() {
        List<String> problems = new ArrayList<>();
        SoundDef d = SoundConfigParser.parse(Map.of("e", spec("key", "a:b")), problems::add).get("e");
        assertEquals("a:b", d.fallback());
        assertEquals(1, problems.size());
    }

    @Test
    void badNumbersAndSourceAreRepaired() {
        List<String> problems = new ArrayList<>();
        SoundDef d = SoundConfigParser.parse(Map.of("e", spec("key", "a:b", "fallback", "c:d", "volume", -1,
                "pitch-min", 1.5, "pitch-max", 0.5, "source", "nonsense")), problems::add).get("e");
        assertEquals(1f, d.volume());
        assertEquals(0.5f, d.pitchMin());
        assertEquals(1.5f, d.pitchMax());
        assertEquals("master", d.source());
        assertEquals(3, problems.size(), problems.toString());
    }

    @Test
    void nanVolumeFallsBackToDefault() {
        SoundDef d = SoundConfigParser.parse(Map.of("e", spec("key", "a:b", "fallback", "c:d", "volume", Double.NaN)), p -> { }).get("e");
        assertEquals(1f, d.volume());
    }

    @Test
    void sourceAliasesMapToAdventureSources() {
        assertEquals(Sound.Source.RECORD, SoundService.source("records"));
        assertEquals(Sound.Source.BLOCK, SoundService.source("blocks"));
        assertEquals(Sound.Source.PLAYER, SoundService.source("players"));
        assertEquals(Sound.Source.UI, SoundService.source("ui"));
        assertEquals(Sound.Source.MASTER, SoundService.source("???"));
    }

    @Test
    void everySourceNameTheParserAcceptsMapsToARealSource() {
        for (String s : List.of("master", "music", "record", "records", "weather", "block", "blocks", "hostile",
                "neutral", "player", "players", "ambient", "voice", "ui")) {
            SoundService.source(s); // must not throw
        }
        assertEquals(Sound.Source.VOICE, SoundService.source("voice"));
    }

    @Test
    void atBlockKeyIsAcceptedWithOrWithoutFallback() {
        List<String> problems = new ArrayList<>();
        Map<String, SoundDef> out = SoundConfigParser.parse(Map.of(
                "with", spec("key", "@block", "fallback", "minecraft:block.stone.place", "source", "block"),
                "without", spec("key", "@block")), problems::add);
        assertTrue(problems.isEmpty(), problems.toString());
        assertTrue(out.get("with").blockSound());
        assertEquals("minecraft:block.stone.place", out.get("with").fallback());
        assertTrue(out.get("without").blockSound());
        assertEquals(SoundDef.BLOCK_KEY, out.get("without").fallback(), "no fallback: unresolved blocks stay silent");
    }

    @Test
    void otherAtKeysAreStillRejected() {
        List<String> problems = new ArrayList<>();
        Map<String, SoundDef> out = SoundConfigParser.parse(Map.of("x", spec("key", "@other", "fallback", "minecraft:block.stone.place")),
                problems::add);
        assertTrue(out.isEmpty());
        assertEquals(1, problems.size());
    }

    @Test
    void normalEventsAreNotBlockSounds() {
        Map<String, SoundDef> out = SoundConfigParser.parse(Map.of("c", spec("key", "cookieclicker:ui.c", "fallback", "minecraft:block.wood.hit")),
                p -> { });
        assertTrue(!out.get("c").blockSound());
    }
}
