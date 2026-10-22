package dev.casperrs.cookieClicker.text;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.format.RealMessages;
import dev.casperrs.cookieClicker.view.TextWidth;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessagesTest {

    /** Every placeholder name any messages.yml entry may use (the plugin fills these in). */
    private static final List<String> PLACEHOLDERS = List.of("prefix", "cookies", "cps", "name", "owned", "cost",
            "needed", "amount", "id", "x", "y", "z", "world", "distance", "radius", "count", "problem");

    private static final MiniMessage STRICT = MiniMessage.builder().strict(true).build();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private Messages messages;
    private YamlConfiguration yaml;

    @BeforeEach
    void setUp() {
        Logger quiet = Logger.getAnonymousLogger();
        quiet.setLevel(Level.OFF);
        messages = new Messages(quiet);
        yaml = RealMessages.yaml();
        messages.load(yaml);
    }

    private static TagResolver allPlaceholders() {
        List<TagResolver> list = new ArrayList<>();
        for (String p : PLACEHOLDERS) {
            list.add(Placeholder.unparsed(p, "<" + p + ">"));
        }
        return TagResolver.resolver(list);
    }

    @Test
    void everyMiniMessageValueParsesStrictly() {
        int checked = 0;
        for (String path : yaml.getKeys(true)) {
            if (!yaml.isString(path) || path.startsWith("numbers.")) {
                continue; // numbers.* are plain patterns ({value} {suffix}), not MiniMessage
            }
            String raw = yaml.getString(path);
            // building names deliberately leave their colour tag open ("<#e8b46a>Villager Baker"): lenient parse
            MiniMessage mm = path.endsWith(".name") ? MiniMessage.miniMessage() : STRICT;
            assertDoesNotThrow(() -> mm.deserialize(raw, allPlaceholders()), path);
            checked++;
        }
        assertTrue(checked >= 20, "expected many messages, checked " + checked);
    }

    @Test
    void panelRowTextsAreOneLineUnderTheTitleAndFitTheRowWidth() {
        // a locked row is "???" plus one line; the requirement line must fit the row width (120 less padding)
        TagResolver values = TagResolver.resolver(Placeholder.unparsed("needed", "250M"),
                Placeholder.unparsed("owned", "10"), Placeholder.unparsed("building", "Wheat Farm"));
        for (String path : List.of("panel.clicks.need-baked", "panel.clicks.need-building")) {
            assertEquals(1, TextWidth.lineWidths(TextWidth.runs(messages.component(path, values)), 116).size(), path);
        }
        assertEquals(2, TextWidth.lineWidths(TextWidth.runs(messages.component("panel.row.locked", values)), 116)
                .size(), "a locked row is two lines");
    }

    @Test
    void prefixIsPresentAndParses() {
        assertTrue(yaml.isString("prefix"));
        assertDoesNotThrow(() -> STRICT.deserialize(yaml.getString("prefix")));
    }

    @Test
    void numberSettingsListsAreStringsAndNotEmpty() {
        assertFalse(yaml.getStringList("numbers.suffixes").isEmpty());
        assertEquals(33, yaml.getStringList("numbers.suffixes").size()); // K … Tg, UTg, DTg (10^3 … 10^99)
        assertEquals("DTg", yaml.getStringList("numbers.suffixes").get(32));
    }

    @Test
    void everyPathUsedInSourceExistsInMessagesYml() throws Exception {
        java.nio.file.Path root = java.nio.file.Path.of("src/main/java");
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"((?:commands|storage|hud|panel|click|resource-pack|plot|house)\\.[a-z0-9.-]+)\"");
        List<String> missing = new ArrayList<>();
        try (var files = java.nio.file.Files.walk(root)) {
            for (java.nio.file.Path f : files.filter(x -> x.toString().endsWith(".java") && !x.endsWith("PluginSettings.java") && !x.endsWith("PlotSettings.java") && !x.endsWith("HouseSettings.java")).toList()) {
                var m = p.matcher(java.nio.file.Files.readString(f));
                while (m.find()) {
                    // resource-pack.prompt in config.yml is a messages path pointer too; both live in messages.yml
                    if (!yaml.isString(m.group(1))) {
                        missing.add(f.getFileName() + ": " + m.group(1));
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "message paths missing from messages.yml: " + missing);
    }

    @Test
    void playerInputInPlaceholdersShowsLiterally() {
        String evil = "<red><click:run_command:/op me>HI</click></red><b>";
        Component c = messages.component("plot.pedestal-set", Placeholder.unparsed("id", evil));
        String text = PLAIN.serialize(c);
        assertTrue(text.contains(evil), text);
        assertDoesNotThrowAnyClick(c);
    }

    private static void assertDoesNotThrowAnyClick(Component c) {
        assertTrue(c.children().stream().noneMatch(ch -> ch.clickEvent() != null), "player input must not create a click event");
        assertEquals(null, c.clickEvent());
        // also nested
        c.iterable(net.kyori.adventure.text.ComponentIteratorType.DEPTH_FIRST)
                .forEach(x -> assertEquals(null, x.clickEvent(), "no click events anywhere"));
    }

    @Test
    void reloadProblemTextWithTagsIsShownLiterally() {
        Component c = messages.component("commands.reload.problem", Placeholder.unparsed("problem", "buildings.yml: <bad> tag"));
        assertTrue(PLAIN.serialize(c).contains("<bad> tag"));
    }

    @Test
    void sendAddsThePrefixAndKeepsInputLiteral() {
        List<Component> sent = new ArrayList<>();
        Audience audience = new Audience() {
            @Override
            public void sendMessage(Component message) {
                sent.add(message);
            }
        };
        messages.send(audience, "plot.chimney-no-target", Placeholder.unparsed("radius", "<gold>8"));
        assertEquals(1, sent.size());
        String text = PLAIN.serialize(sent.get(0));
        assertTrue(text.contains("Cookie Empire"), text);
        assertTrue(text.contains("<gold>8"), text);
    }

    @Test
    void missingPathReturnsThePathAndDoesNotThrow() {
        assertEquals("nope.nothing", messages.raw("nope.nothing"));
        assertEquals("nope.nothing", PLAIN.serialize(messages.component("nope.nothing")));
    }

    @Test
    void buildingNamesComeFromMessagesWithIdFallback() {
        assertEquals("Baker's Apprentice", PLAIN.serialize(messages.buildingName("villager_baker")));
        assertEquals("unknown_id", PLAIN.serialize(messages.buildingName("unknown_id")));
    }

    @Test
    void numbersAreInsertedUnparsed() {
        Component c = messages.component("hud.action-bar-no-pack", messages.number("cookies", 1_234_567), messages.number("cps", 0.1));
        String text = PLAIN.serialize(c);
        assertTrue(text.contains("1.2M"), text);
        assertTrue(text.contains("0.1"), text);
    }

    @Test
    void hudWithPackUsesTheHudFont() {
        String raw = yaml.getString("hud.action-bar");
        assertTrue(raw.contains("<font:cookieclicker:hud>"));
        assertFalse(yaml.getString("hud.action-bar-no-pack").contains("<font"));
    }
}
