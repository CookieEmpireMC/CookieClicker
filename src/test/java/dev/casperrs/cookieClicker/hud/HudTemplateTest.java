package dev.casperrs.cookieClicker.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.casperrs.cookieClicker.format.RealMessages;
import dev.casperrs.cookieClicker.format.NumberFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.Test;

/** The compiled-template fast path must be indistinguishable from a real MiniMessage parse. */
class HudTemplateTest {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static List<String> values() {
        List<String> v = new ArrayList<>(List.of("0", "1", "-1", "1.5 million", "12,345.6", "1e45", "infinity", "NaN",
                "<red>", "</gold>", "<bold>x", "<click:run_command:/op me>", "&c", "§c", "\\<gold>", "'", "\"", "$1", "\\",
                "<cookies>", "<cps>", " ", "  lead", "trail  ", "\n", "\t", "\u0000", "﷐", "🍪", "<", ">", "<>", "%s", "{value}"));
        v.add("9".repeat(5000));
        NumberFormatter f = RealMessages.formatter();
        Random r = new Random(42);
        for (int i = 0; i < 400; i++) {
            double d = Math.pow(10, r.nextInt(80)) * r.nextDouble();
            v.add(f.format(d));
        }
        v.add(f.format(Double.MAX_VALUE));
        v.add(f.format(Double.POSITIVE_INFINITY));
        v.add(f.format(0.1));
        return v;
    }

    private static void assertSameAsParse(String template) {
        HudTemplate t = HudTemplate.compile(MM, template);
        assertNotNull(t, "should compile: " + template);
        List<String> vals = values();
        Random r = new Random(7);
        for (int i = 0; i < 3000; i++) {
            String a = vals.get(r.nextInt(vals.size()));
            String b = vals.get(r.nextInt(vals.size()));
            if (a.isEmpty() || b.isEmpty()) {
                continue;
            }
            assertEquals(HudTemplate.parse(MM, template, a, b), t.render(a, b), template + " with [" + a + "] [" + b + "]");
        }
    }

    @Test
    void shippedTemplatesCompileAndMatchAParseForManyValues() {
        var yaml = RealMessages.yaml();
        for (String path : new String[] {"hud.action-bar", "hud.action-bar-no-pack"}) {
            String raw = yaml.getString(path);
            assertNotNull(raw, path);
            assertSameAsParse(raw);
        }
    }

    @Test
    void hoverClickInsertAndRepeatedPlaceholdersMatchOrFallBackButNeverDiffer() {
        String[] templates = {
                "<hover:show_text:'You have <cookies>'><gold><cookies></gold></hover> <cps>/s <cps>",
                "<hover:show_text:'<red><cps></red> and <cookies>'>x</hover>",
                "<click:suggest_command:'/x <cookies>'><cookies></click>",
                "<click:open_url:'https://x/<cookies>'>go</click>",
                "<insert:<cookies>>text <cps></insert>",
                "<cookies><cookies><cps><cps>",
                "<bold><cookies></bold><italic><cps></italic>",
                "<hover:show_text:'<hover:show_text:\"<cps>\">nested</hover>'><cookies></hover>",
                "<lang:chat.type.text:<cookies>:<cps>>",
                "<lang:chat.type.text:'<cookies>':'<cps>'> <cookies>",
                "no placeholders at all",
                "<cookies>",
        };
        List<String> vals = values();
        Random r = new Random(3);
        for (String template : templates) {
            HudTemplate t = HudTemplate.compile(MM, template);
            for (int i = 0; i < 1500; i++) {
                String a = vals.get(r.nextInt(vals.size()));
                String b = vals.get(r.nextInt(vals.size()));
                if (a.isEmpty() || b.isEmpty()) {
                    continue;
                }
                var expected = HudTemplate.parse(MM, template, a, b);
                var actual = t == null ? expected : t.render(a, b); // null = Hud parses instead
                assertEquals(expected, actual, template + " with [" + a + "] [" + b + "]");
            }
        }
    }

    @Test
    void textReshapingTagsFallBackToFullParsing() {
        assertNull(HudTemplate.compile(MM, "<gradient:red:blue><cookies> cookies</gradient>"));
        assertNull(HudTemplate.compile(MM, "<rainbow><cps></rainbow>"));
        assertNull(HudTemplate.compile(MM, "<gradient:red:blue><cookies></gradient> <cps>"));
    }

    @Test
    void templatesContainingTheMarkerCharacterAreNeverCompiled() {
        assertNull(HudTemplate.compile(MM, "﷐ <cookies>"));
    }

    @Test
    void emptyValuesAreCallersJob() {
        // Hud.render parses for empty values; the template itself must still agree for a normal pair
        HudTemplate t = HudTemplate.compile(MM, "<gold><cookies></gold> <cps>");
        assertNotNull(t);
        assertEquals(HudTemplate.parse(MM, "<gold><cookies></gold> <cps>", "1", "2"), t.render("1", "2"));
    }
}
