package dev.casperrs.cookieClicker.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.plot.PlotRegions.RegionInfo;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlotResolverTest {

    private static RegionInfo region(String id, String plotFlag, String houseFlag) {
        return new RegionInfo(id, 0, plotFlag, houseFlag);
    }

    private static Plot plot(String plotRegion, String houseRegion) {
        return new Plot("main", "minecraft:overworld", plotRegion, houseRegion, null, null, null);
    }

    @Test
    void flagsResolveBothRegions() {
        List<String> problems = new ArrayList<>();
        PlotResolver.Resolved r = PlotResolver.resolve(plot(null, null),
                List.of(region("a", "main", null), region("b", null, "main"), region("c", "other", "other")), problems::add);
        assertEquals("a", r.plotRegion());
        assertEquals("b", r.houseRegion());
        assertTrue(problems.isEmpty());
    }

    @Test
    void flagsBeatTheNamesInPlotsYml() {
        PlotResolver.Resolved r = PlotResolver.resolve(plot("old_plot", "old_house"),
                List.of(region("old_plot", null, null), region("old_house", null, null), region("new_plot", "main", null),
                        region("new_house", null, "main")), p -> { });
        assertEquals("new_plot", r.plotRegion());
        assertEquals("new_house", r.houseRegion());
    }

    @Test
    void nameFallbackIsUsedWhenNoRegionCarriesTheFlag() {
        List<String> problems = new ArrayList<>();
        PlotResolver.Resolved r = PlotResolver.resolve(plot("Bakery_Plot", "bakery_house"),
                List.of(region("bakery_plot", null, null), region("bakery_house", null, null)), problems::add);
        assertEquals("bakery_plot", r.plotRegion(), "names compare case-insensitively");
        assertEquals("bakery_house", r.houseRegion());
        assertTrue(problems.isEmpty());
    }

    @Test
    void aFallbackNameThatDoesNotExistIsReportedAndLeavesTheAreaUnresolved() {
        List<String> problems = new ArrayList<>();
        PlotResolver.Resolved r = PlotResolver.resolve(plot("gone", null), List.of(region("x", null, null)), problems::add);
        assertNull(r.plotRegion());
        assertNull(r.houseRegion());
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("gone"));
    }

    @Test
    void duplicateFlagValuesAreAnErrorNamingBothRegionsAndResolveNothing() {
        List<String> problems = new ArrayList<>();
        PlotResolver.Resolved r = PlotResolver.resolve(plot("fallback", null),
                List.of(region("a", "main", null), region("b", "main", null), region("fallback", null, null)), problems::add);
        assertNull(r.plotRegion(), "ambiguous: do not guess, and do not fall back either");
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("a") && problems.getFirst().contains("b") && problems.getFirst().contains("bakery-plot"));
    }

    @Test
    void duplicateHouseFlagsDoNotAffectTheValidPlot() {
        List<String> problems = new ArrayList<>();
        PlotResolver.Resolved r = PlotResolver.resolve(plot(null, null),
                List.of(region("p", "main", null), region("h1", null, "main"), region("h2", null, "main")), problems::add);
        assertEquals("p", r.plotRegion());
        assertNull(r.houseRegion());
        assertEquals(1, problems.size());
    }

    @Test
    void oneRegionCannotBeBothPlotAndHouse() {
        List<String> problems = new ArrayList<>();
        PlotResolver.Resolved r = PlotResolver.resolve(plot(null, null), List.of(region("both", "main", "main")), problems::add);
        assertEquals("both", r.plotRegion());
        assertNull(r.houseRegion());
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("separate house region"));
    }

    @Test
    void multiplePlotsEachFindTheirOwnRegions() {
        List<RegionInfo> regions = List.of(region("p1", "one", null), region("h1", null, "one"), region("p2", "two", null),
                region("h2", null, "two"));
        Plot one = new Plot("one", "minecraft:overworld", null, null, null, null, null);
        Plot two = new Plot("two", "minecraft:overworld", null, null, null, null, null);
        assertEquals("h1", PlotResolver.resolve(one, regions, p -> { }).houseRegion());
        assertEquals("p2", PlotResolver.resolve(two, regions, p -> { }).plotRegion());
    }

    @Test
    void noRegionsAtAllIsAllNull() {
        PlotResolver.Resolved r = PlotResolver.resolve(plot(null, null), List.of(), p -> { });
        assertEquals(PlotResolver.Resolved.NONE, r);
    }
}
