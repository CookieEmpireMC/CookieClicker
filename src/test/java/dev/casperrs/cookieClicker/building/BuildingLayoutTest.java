package dev.casperrs.cookieClicker.building;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.config.BuildingVisualSettings;
import dev.casperrs.cookieClicker.config.YamlFiles;
import dev.casperrs.cookieClicker.house.BuildingSlots;
import dev.casperrs.cookieClicker.house.HouseDesign;
import dev.casperrs.cookieClicker.house.HouseDesignParser;
import dev.casperrs.cookieClicker.plot.Plot;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BuildingLayoutTest {

    private static final BuildingVisualSettings S = BuildingVisualSettings.defaults();

    @Test
    void newInstancesAppearUpToTheSlotCapAndNoFurther() {
        BuildingLayout.Change first = BuildingLayout.change(-1, 3, 10, S);
        assertEquals(0, first.shownBefore());
        assertEquals(3, first.shownAfter());
        assertTrue(first.appears());
        assertFalse(first.stageChanged(), "the first show is not a stage change");

        BuildingLayout.Change capped = BuildingLayout.change(3, 12, 10, S);
        assertEquals(10, capped.shownAfter());
        assertTrue(capped.appears());

        assertFalse(BuildingLayout.change(10, 12, 10, S).appears(), "the cap is reached: nothing new");
    }

    @Test
    void stageChangesAtTwentyFiveAndAHundred() {
        assertTrue(BuildingLayout.change(24, 25, 10, S).stageChanged());
        assertTrue(BuildingLayout.change(99, 100, 200, S).stageChanged());
        assertFalse(BuildingLayout.change(100, 101, 200, S).stageChanged());
        assertFalse(BuildingLayout.change(20, 24, 10, S).stageChanged());
    }

    @Test
    void theOriginIsTheFrameWorkedBackFromTheStoredHouseBox() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        try (InputStream in = getClass().getResourceAsStream("/houses/factory.yml")) {
            y.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        HouseDesign design = HouseDesignParser.parse("factory", YamlFiles.toMap(y)).design();
        assertNotNull(design);
        int[] frame = {100, 64, -40}; // where plot cell (0, 0, 0) of the layout lands in the world
        int[] at = design.layout().houseAt();
        int[] houseAt = {frame[0] + at[0], frame[1] + at[1], frame[2] + at[2]};
        int[] corner = design.toWorld(houseAt, 0, 0, 0); // what PlotSetup stores as the house box
        Plot.Box box = new Plot.Box(corner[0], corner[1], corner[2],
                corner[0] + design.sizeX() - 1, corner[1] + design.sizeY() - 1, corner[2] + design.sizeZ() - 1);
        Plot plot = new Plot("main", "minecraft:overworld", null, null, null, null, List.of(), List.of(), null,
                null, box);

        assertArrayEquals(frame, BuildingLayout.origin(plot, design));
        double[] floor = BuildingLayout.floorPoint(frame, new BuildingSlots.Slot(5, 0, 2, 0f));
        assertArrayEquals(new double[] {105.5, 64, -37.5}, floor);
        assertEquals(null, BuildingLayout.origin(Plot.empty("x", "minecraft:overworld"), design));
    }
}
