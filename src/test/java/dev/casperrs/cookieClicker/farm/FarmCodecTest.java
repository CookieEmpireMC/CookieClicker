package dev.casperrs.cookieClicker.farm;

import dev.casperrs.cookieClicker.config.YamlFiles;
import dev.casperrs.cookieClicker.plot.Plot;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code plots.yml farms:} as text: a farm with every optional point survives a save and a load. */
class FarmCodecTest {

    private static Farm farm(String id, boolean optional) {
        Plot.Viewpoint view = new Plot.Viewpoint(120.5, 65.0, 230.5, 180f, 10f);
        Farm.Yard yard = new Farm.Yard(115, 225, 125, 231, 65);
        Farm.FarmPlace place = optional
                ? new Farm.FarmPlace(view, yard, new Plot.Point(118.5, 66.0, 226.5), new Plot.Pedestal(120.5, 65.0, 228.5, 90f),
                new Plot.Point(122.5, 65.0, 229.5), List.of(new Plot.Point(121.5, 70.0, 227.5)),
                new Farm.Wheel(new Plot.Point(119.5, 67.5, 226.5), PlacePoints.Axis.X),
                new Plot.Point(117.5, 65.0, 230.5), new Plot.Pedestal(116.5, 65.0, 229.5, 90f))
                : new Farm.FarmPlace(view, yard, new Plot.Point(118.5, 66.0, 226.5), new Plot.Pedestal(120.5, 65.0, 228.5, 0f),
                new Plot.Point(122.5, 65.0, 229.5), List.of(), null, null, null);
        return new Farm(id, "minecraft:overworld", "countryside", new Plot.Cell(100, 64, 200),
                new Farm.Box(100, 60, 200, 249, 103, 319), Map.of("mill", place, "market", place));
    }

    @Test
    void aFarmWithItsOptionalPointsSurvivesSaveAndLoad() throws Exception {
        Farm full = farm("farm1", true);
        YamlConfiguration written = new YamlConfiguration();
        written.createSection(FarmCodec.SECTION, FarmCodec.toMap(List.of(full, farm("farm2", false))));
        YamlConfiguration read = new YamlConfiguration();
        read.loadFromString(written.saveToString());

        List<String> problems = new ArrayList<>();
        List<Farm> loaded = FarmCodec.fromMap(YamlFiles.toMap(read.getConfigurationSection(FarmCodec.SECTION)),
                problems::add);
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(List.of(full, farm("farm2", false)), loaded);
    }

    @Test
    void aBrokenFarmIsSkippedAndReportedWhileTheOthersLoad() {
        Map<String, Object> farms = new LinkedHashMap<>();
        farms.put("farm1", FarmCodec.toMap(List.of(farm("farm1", true))).get("farm1"));
        farms.put("farm2", Map.of("world", "minecraft:overworld", "region", "countryside")); // no origin, box or places
        List<String> problems = new ArrayList<>();
        List<Farm> loaded = FarmCodec.fromMap(farms, problems::add);
        assertEquals(1, loaded.size());
        assertEquals("farm1", loaded.getFirst().id());
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("farms.farm2"), problems.toString());
    }
}
