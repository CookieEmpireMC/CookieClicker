package dev.casperrs.cookieClicker.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.economy.EffectType;
import dev.casperrs.cookieClicker.text.Messages;
import dev.casperrs.cookieClicker.text.RealMessages;
import dev.casperrs.cookieClicker.text.TextPadding;
import dev.casperrs.cookieClicker.view.panel.BulkMode;
import dev.casperrs.cookieClicker.view.panel.UpgradeRowState;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The compiled (marker-swap) panel rows must be exactly what the real MiniMessage parse gives. */
class PanelTextTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String[] COSTS = {"15", "1.5 million", "12,345.6", "9.99 decillion", "0", "<red>x", "&c"};

    private static Messages shipped() {
        Messages m = new Messages(Logger.getLogger("panel-text-test"));
        m.load(RealMessages.yaml());
        return m;
    }

    private static List<String> keys(String file, String section) throws Exception {
        try (InputStream in = PanelTextTest.class.getClassLoader().getResourceAsStream(file);
             InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return new ArrayList<>(YamlConfiguration.loadConfiguration(r).getConfigurationSection(section)
                    .getKeys(false));
        }
    }

    @Test
    void compiledBuildingRowsEqualTheRealParseInEveryBulkMode() throws Exception {
        Messages m = shipped();
        PanelText text = new PanelText(m);
        int checked = 0;
        for (String id : keys("buildings.yml", "buildings")) {
            for (String path : List.of("panel.row.affordable", "panel.row.unaffordable", "panel.row.locked")) {
                if (!m.has(path)) {
                    continue;
                }
                for (BulkMode mode : BulkMode.values()) {
                    // several rounds on one PanelText: the first call compiles, later ones swap values
                    for (int round = 0; round < COSTS.length; round++) {
                        int owned = round * 37;
                        int count = round + 1;
                        String cost = COSTS[round];
                        String gain = COSTS[(round + 2) % COSTS.length];
                        double needed = round * 1234.5;
                        Component fast = text.buildingRow(path, 1, id, m.number(owned), cost, gain,
                                m.number(needed), mode, count);
                        Component real = PanelText.buildingRow(m, path, 1, id, owned, cost, gain, needed,
                                PanelText.amount(m, mode, count));
                        assertEquals(real, fast, id + " " + path + " " + mode + " round " + round);
                        checked++;
                    }
                }
            }
        }
        assertTrue(checked > 100, "checked " + checked);
    }

    @Test
    void compiledClickRowsEqualTheRealParseForEveryStyleAndEffect() throws Exception {
        Messages m = shipped();
        PanelText text = new PanelText(m);
        List<String> ids = keys("upgrades.yml", "upgrades");
        List<String> buildings = keys("buildings.yml", "buildings");
        int checked = 0;
        for (String id : ids) {
            for (boolean repeatable : new boolean[] {true, false}) {
                for (UpgradeRowState.Style style : UpgradeRowState.Style.values()) {
                    for (EffectType type : EffectType.values()) {
                        for (String needBuilding : new String[] {null, buildings.get(0)}) {
                            int level = style == UpgradeRowState.Style.MAXED ? 50 : 3;
                            UpgradeRowState u = new UpgradeRowState(style, repeatable, level, 4, 1234.5, 6.5,
                                    type == EffectType.CLICK_CPS_FRACTION ? 0.01 : 2, type, 250, needBuilding, 7);
                            String path = PanelText.clickPath(u);
                            for (BulkMode mode : new BulkMode[] {BulkMode.ONE, BulkMode.TEN}) {
                                for (String cost : new String[] {"100", "1.5 million", "<b>"}) {
                                    Component fast = text.clickRow(path, 1, id, u, cost, "6.5", mode);
                                    Component real = realClickRow(m, path, id, u, cost, "6.5", mode);
                                    assertEquals(real, fast, id + " " + path + " " + type + " " + mode + " " + cost);
                                    checked++;
                                }
                            }
                        }
                    }
                }
            }
        }
        assertTrue(checked > 100, "checked " + checked);
    }

    @Test
    void aMessagesReloadDropsTheCompiledRows() {
        Messages m = shipped();
        PanelText text = new PanelText(m);
        String id = "villager_baker";
        String before = PLAIN.serialize(text.buildingRow("panel.row.affordable", 1, id, "1", "10", "1", "0",
                BulkMode.ONE, 1));
        YamlConfiguration changed = RealMessages.yaml();
        changed.set("panel.row.affordable", "CHANGED <cost>");
        m.load(changed);
        String after = PLAIN.serialize(text.buildingRow("panel.row.affordable", 1, id, "1", "10", "1", "0",
                BulkMode.ONE, 1));
        assertTrue(after.contains("CHANGED 10") && !before.contains("CHANGED"), after);
    }

    /** The click row as the panel builds it, parsed in full for these exact values (no template). */
    private static Component realClickRow(Messages m, String path, String id, UpgradeRowState u, String cost,
                                          String gain, BulkMode mode) {
        boolean amount = u.repeatable() && mode != BulkMode.ONE;
        String shortPath = "upgrades." + id + ".short";
        Component effect = m.has(shortPath)
                ? m.component(shortPath, m.number("value", u.shownValue()),
                        Placeholder.unparsed("level", Integer.toString(u.level())))
                : m.upgradeEffect(id, u.shownValue(), u.level());
        Component requirement = u.neededBuilding() != null
                ? m.component("panel.clicks.need-building", m.number("owned", u.neededOwned()),
                        Placeholder.unparsed("building", PLAIN.serialize(m.buildingName(u.neededBuilding()))))
                : m.component("panel.clicks.need-baked", m.number("needed", u.neededBaked()));
        return TextPadding.lines(m.raw(path), 1,
                Placeholder.unparsed("name", PLAIN.serialize(m.upgradeName(id))),
                Placeholder.unparsed("level", Integer.toString(u.level())),
                Placeholder.unparsed("cost", cost),
                Placeholder.unparsed("cpc", gain),
                Placeholder.component("amount", PanelText.amount(m, amount ? BulkMode.TEN : BulkMode.ONE, u.count())),
                Placeholder.component("effect", effect),
                Placeholder.component("requirement", requirement));
    }
}
