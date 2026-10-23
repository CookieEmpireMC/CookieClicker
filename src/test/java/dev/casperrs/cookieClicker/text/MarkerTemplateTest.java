package dev.casperrs.cookieClicker.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.format.RealMessages;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class MarkerTemplateTest {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static String row(String key) {
        return RealMessages.yaml().getString(key).replace("<amount>", "");
    }

    @Test
    void shippedPanelRowsRenderExactlyLikeARealParse() {
        for (String key : new String[] {"panel.row.affordable", "panel.row.unaffordable"}) {
            MarkerTemplate t = MarkerTemplate.of(MM, row(key), "name", "owned", "cost", "cps");
            assertTrue(t.isFast(), key + " should use the fast path");
            String[][] cases = {
                {"Villager Baker", "0", "15", "0.1"},
                {"Extra Conveyor Belt", "1,234", "1.5 million", "12,345.6"},
                {"<red>Tag</red>", "7", "<bold>9", "&c1"},
                {"x", "", "3", "4"}};
            for (String[] values : cases) {
                assertEquals(t.parse(values), t.render(values), key + " " + String.join("|", values));
            }
        }
    }

    @Test
    void playerTextWithTagsStaysLiteral() {
        MarkerTemplate t = MarkerTemplate.of(MM, row("panel.row.affordable"), "name", "owned", "cost", "cps");
        String plain = PlainTextComponentSerializer.plainText().serialize(t.render("<red>Evil</red>", "1", "2", "3"));
        assertTrue(plain.contains("<red>Evil</red>"), plain);
    }

    @Test
    void reshapingTagsFallBackToARealParse() {
        MarkerTemplate t = MarkerTemplate.of(MM, "<gradient:red:blue><v></gradient>", "v");
        assertFalse(t.isFast());
        assertEquals(PlainTextComponentSerializer.plainText().serialize(t.parse("hello")),
                PlainTextComponentSerializer.plainText().serialize(t.render("hello")));
    }

    @Test
    void wrongValueCountIsRejected() {
        MarkerTemplate t = MarkerTemplate.of(MM, "<a> <b>", "a", "b");
        assertThrows(IllegalArgumentException.class, () -> t.render("only one"));
    }
}
