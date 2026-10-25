package dev.casperrs.cookieClicker.farm;

import dev.casperrs.cookieClicker.plot.Plot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where the countryside and its places land in a region: sticking out, the needed size, world coordinates. */
class FarmPlanTest {

    private static FarmPlan plan() throws Exception {
        FarmPlan.Result r = FarmPlan.plan(FarmFixtures.layout(FarmFixtures.files()), FarmFixtures.REGION_MIN);
        assertNotNull(r.plan(), () -> "problems: " + r.problems());
        return r.plan();
    }

    @Test
    void aPlaceThatSticksOutOfTheCountrysideIsRefused() throws Exception {
        Map<String, Map<String, Object>> files = FarmFixtures.files();
        FarmFixtures.moveTo(files, "market", List.of(10, 0, 4)); // 4 wide from x 10 runs past the 12-wide countryside
        FarmPlan.Result r = FarmPlan.plan(FarmFixtures.layout(files), FarmFixtures.REGION_MIN);
        assertNull(r.plan());
        assertTrue(String.join(";", r.problems()).contains("place market"), r.problems().toString());
        assertTrue(String.join(";", r.problems()).contains("sticks out"));
    }

    @Test
    void neededSizeIsTheCountrysideBoxFromTheRegionMinimumOrNullWhenItFits() throws Exception {
        FarmPlan plan = plan();
        assertNull(plan.neededSize(FarmFixtures.REGION_MIN, new int[] {111, 66, 207}));
        // 12 x 8 across and 3 layers up from the region's minimum (the ground layers lie below the region)
        assertArrayEquals(new int[] {12, 3, 8}, plan.neededSize(FarmFixtures.REGION_MIN, new int[] {110, 66, 207}));
    }

    @Test
    void placePointsLandWhereTheRegionAndTheCountrysideCellPutThem() throws Exception {
        Farm.FarmPlace market = plan().places().get("market");
        assertEquals(new Plot.Viewpoint(109.5, 64.0, 207.5, 0f, 0f), market.viewpoint());
        assertEquals(new Plot.Point(109.5, 64.0, 205.5), market.recipeBook());
        assertEquals(new Farm.Yard(108, 204, 111, 205, 64), market.yard());
    }
}
