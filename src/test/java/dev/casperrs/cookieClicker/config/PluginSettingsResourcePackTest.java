package dev.casperrs.cookieClicker.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PluginSettingsResourcePackTest {

    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";

    private static PluginSettings.ResourcePack pack(String yaml, List<String> problems) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(yaml);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return PluginSettings.from(y, Path.of("data"), problems::add).resourcePack();
    }

    private static String rp(String body) {
        return "resource-pack:\n" + body.indent(2);
    }

    @Test
    void validUrlAndHashIsSendable() {
        List<String> problems = new ArrayList<>();
        var p = pack(rp("enabled: true\nurl: \"https://example.com/pack.zip\"\nsha1: \"" + SHA + "\"\nrequired: true"), problems);
        assertTrue(p.sendable());
        assertTrue(p.required());
        assertEquals(List.of(), problems);
    }

    @Test
    void badHashIsDroppedAndReported() {
        for (String bad : new String[] {"abc", SHA + "0", "zz" + SHA.substring(2), "0123456789abcdef0123456789abcdef0123456"}) {
            List<String> problems = new ArrayList<>();
            var p = pack(rp("url: \"https://x/p.zip\"\nsha1: \"" + bad + "\""), problems);
            assertFalse(p.sendable(), bad);
            assertTrue(problems.stream().anyMatch(s -> s.contains("40 hex")), bad + " -> " + problems);
        }
    }

    @Test
    void nonHttpOrMalformedUrlsAreDropped() {
        for (String bad : new String[] {"ftp://x/p.zip", "file:///etc/passwd", "not a url", "x/p.zip", "javascript:alert(1)"}) {
            List<String> problems = new ArrayList<>();
            var p = pack(rp("url: \"" + bad + "\"\nsha1: \"" + SHA + "\""), problems);
            assertFalse(p.sendable(), bad);
            assertFalse(problems.isEmpty(), bad);
            assertEquals("", p.url(), bad);
        }
    }

    @Test
    void disabledIsSilentAndNeverSendable() {
        List<String> problems = new ArrayList<>();
        var p = pack(rp("enabled: false\nurl: \"https://x/p.zip\"\nsha1: \"" + SHA + "\""), problems);
        assertFalse(p.enabled());
        assertFalse(p.sendable());
        assertEquals(List.of(), problems);
        assertEquals(List.of(), problems);
        var garbage = pack(rp("enabled: false\nurl: \"ftp://bad\"\nsha1: \"zz\""), problems);
        assertEquals(List.of(), problems, "a disabled pack is not validated");
        assertFalse(garbage.sendable());
    }

}
