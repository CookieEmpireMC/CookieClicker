package dev.casperrs.cookieClicker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * Cross-agent consistency of the shipped files: code-facing config (sounds.yml, effects.yml, buildings.yml,
 * messages.yml, StationInteractions names) against the resource pack. Plain file reads, no server.
 */
class ShippedContentConsistencyTest {

    private static final Path RES = Path.of("src/main/resources");
    private static final Path PACK = Path.of("resourcepack/assets/cookieclicker");

    private static YamlConfiguration yaml(String name) throws IOException {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(Files.readString(RES.resolve(name)));
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            throw new IOException(e);
        }
        return y;
    }

    private static JsonObject json(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }

    @Test
    void everySoundEventMapsToAPackSoundWithAnOggFile() throws IOException {
        YamlConfiguration sounds = yaml("sounds.yml");
        JsonObject soundsJson = json(PACK.resolve("sounds.json"));
        for (String event : List.of("cookie_click", "cookie_crit", "purchase", "cant_afford")) {
            assertTrue(sounds.isConfigurationSection(event), "sounds.yml lacks event " + event);
            String key = sounds.getString(event + ".key");
            assertTrue(key.startsWith("cookieclicker:"), key);
            String name = key.substring("cookieclicker:".length());
            assertTrue(soundsJson.has(name), "sounds.json lacks " + name);
            var files = soundsJson.getAsJsonObject(name).getAsJsonArray("sounds");
            assertFalse(files.isEmpty());
            files.forEach(f -> {
                String id = f.getAsString();
                assertTrue(Files.isRegularFile(PACK.resolve("sounds/" + id.substring("cookieclicker:".length()) + ".ogg")),
                        "missing ogg for " + id);
            });
            assertTrue(sounds.getString(event + ".fallback").startsWith("minecraft:"));
        }
        assertTrue(soundsJson.getAsJsonObject("ui.cookie_click").getAsJsonArray("sounds").size() >= 3,
                "brief: 3+ click variants");
    }

    @Test
    void javaEventNamesExistInEffectsAndSounds() throws IOException {
        String src = Files.readString(Path.of("src/main/java/dev/casperrs/cookieClicker/view/StationInteractions.java"));
        YamlConfiguration effects = yaml("effects.yml");
        YamlConfiguration sounds = yaml("sounds.yml");
        for (String c : List.of("CLICK", "CRIT", "PURCHASE", "CANT_AFFORD")) {
            Matcher m = Pattern.compile("static final String " + c + " = \"([a-z_]+)\"").matcher(src);
            assertTrue(m.find(), c);
            assertTrue(sounds.isConfigurationSection(m.group(1)), "sounds.yml lacks " + m.group(1));
            if (!c.equals("CANT_AFFORD")) {
                assertTrue(effects.isConfigurationSection(m.group(1)), "effects.yml lacks " + m.group(1));
            }
        }
        assertTrue(effects.isConfigurationSection("pop"), "Effects.POP preset");
    }

    @Test
    void everyItemModelHasDefinitionModelAndTextures() throws IOException {
        Set<String> models = new java.util.TreeSet<>(List.of("cookie"));
        YamlConfiguration buildings = yaml("buildings.yml");
        for (String id : buildings.getConfigurationSection("buildings").getKeys(false)) {
            models.add("building/" + id);
        }
        // effects.yml item-model values
        Matcher m = Pattern.compile("item-model: \"cookieclicker:([a-z_/]+)\"").matcher(Files.readString(RES.resolve("effects.yml")));
        while (m.find()) {
            models.add(m.group(1));
        }
        for (String model : models) {
            Path item = PACK.resolve("items/" + model + ".json");
            assertTrue(Files.isRegularFile(item), "no item definition for " + model);
            String target = json(item).getAsJsonObject("model").get("model").getAsString();
            assertTrue(target.startsWith("cookieclicker:"));
            Path modelFile = PACK.resolve("models/" + target.substring("cookieclicker:".length()) + ".json");
            assertTrue(Files.isRegularFile(modelFile), "no model for " + model);
            JsonObject textures = json(modelFile).getAsJsonObject("textures");
            for (var e : textures.entrySet()) {
                String tex = e.getValue().getAsString();
                if (tex.startsWith("#")) {
                    continue;
                }
                assertTrue(Files.isRegularFile(PACK.resolve("textures/" + tex.substring("cookieclicker:".length()) + ".png")),
                        "missing texture " + tex + " (" + model + ")");
            }
        }
    }

    @Test
    void everyBuildingHasNameAndFallbackIcon() throws IOException {
        YamlConfiguration buildings = yaml("buildings.yml");
        YamlConfiguration messages = yaml("messages.yml");
        YamlConfiguration config = yaml("config.yml");
        ConfigurationSection list = buildings.getConfigurationSection("buildings");
        for (String id : list.getKeys(false)) {
            assertTrue(messages.isString("buildings." + id + ".name"), "messages.yml buildings." + id + ".name");
            assertTrue(config.isString("station.panel.icons." + id + ".fallback-material"), "fallback icon for " + id);
        }
        List<String> panelOnly = new ArrayList<>(config.getConfigurationSection("station.panel.icons").getKeys(false));
        panelOnly.removeAll(list.getKeys(false));
        assertTrue(panelOnly.isEmpty(), "icon config for unknown buildings: " + panelOnly);
    }

    @Test
    void hudGlyphInMessagesIsTheFontCharacterInHudJson() throws IOException {
        JsonObject font = json(PACK.resolve("font/hud.json"));
        String glyph = font.getAsJsonArray("providers").get(0).getAsJsonObject().getAsJsonArray("chars").get(0).getAsString();
        assertEquals(1, glyph.codePointCount(0, glyph.length()));
        String bar = yaml("messages.yml").getString("hud.action-bar");
        assertTrue(bar.contains("<font:cookieclicker:hud>" + glyph + "</font>"), "action-bar glyph differs from font/hud.json");
        assertFalse(yaml("messages.yml").getString("hud.action-bar-no-pack").contains(glyph), "no-pack bar must not use the glyph");
        String file = font.getAsJsonArray("providers").get(0).getAsJsonObject().get("file").getAsString();
        assertTrue(Files.isRegularFile(PACK.resolve("textures/" + file.substring("cookieclicker:".length()))), file);
    }
}
