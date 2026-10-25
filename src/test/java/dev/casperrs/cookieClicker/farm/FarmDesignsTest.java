package dev.casperrs.cookieClicker.farm;

import dev.casperrs.cookieClicker.house.design.HouseDesignParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The countryside and place files: required points, bounds, the chimney rule and the ground limits. */
class FarmDesignsTest {

    @Test
    void theFixtureCountrysideLoadsWithItsPlacesAndOptionalPoints() throws Exception {
        FarmDesigns.Layout layout = FarmFixtures.layout(FarmFixtures.files());
        assertEquals(List.of("mill", "market"), List.copyOf(layout.places().keySet()));
        PlacePoints mill = layout.places().get("mill").points();
        PlacePoints market = layout.places().get("market").points();
        assertNotNull(mill.wheel());
        assertNull(market.smoke());
        assertNotNull(market.recipeBook());
        assertNotNull(market.noticeBoard());
    }

    @Test
    void aMissingRequiredPointIsReportedByPlaceAndKey() throws Exception {
        Map<String, Map<String, Object>> files = FarmFixtures.files();
        FarmFixtures.points(files, "mill").remove("ripe");
        FarmDesigns.Result r = FarmDesigns.load(files::get);
        assertNull(r.layout());
        assertEquals("mill", r.file());
        assertTrue(String.join(";", r.problems()).contains("points.ripe is missing"), r.problems().toString());
    }

    @Test
    void yardCornersAndTheWheelAxisAreBoundsChecked() throws Exception {
        Map<String, Map<String, Object>> yard = FarmFixtures.files();
        FarmFixtures.points(yard, "mill").put("yard", Map.of("from", List.of(0, 0), "to", List.of(5, 3)));
        assertTrue(String.join(";", FarmDesigns.load(yard::get).problems()).contains("is outside size x/z 5 x 4"));

        Map<String, Map<String, Object>> axis = FarmFixtures.files();
        FarmFixtures.points(axis, "mill").put("wheel", Map.of("at", List.of(2, 0, 0), "axis", "y"));
        assertTrue(String.join(";", FarmDesigns.load(axis::get).problems())
                .contains("points.wheel.axis must be x or z"));
    }

    @Test
    void aPlaceHasAtMostOneChimney() throws Exception {
        Map<String, Map<String, Object>> files = FarmFixtures.files();
        FarmFixtures.points(files, "mill").put("smoke", List.of(List.of(3, 1, 1), List.of(1, 1, 1)));
        assertTrue(String.join(";", FarmDesigns.load(files::get).problems()).contains("at most one cell"));
    }

    @Test
    void aFarmDesignMayGroundFourLayersButAHouseOnlyTwo() {
        Map<String, Object> flat = FarmFixtures.flat(5, 4);
        assertNotNull(HouseDesignParser.parse("mill", flat, HouseDesignParser.MAX_FARM_GROUND_DEPTH).design());
        HouseDesignParser.Result house = HouseDesignParser.parse("mill", flat);
        assertNull(house.design());
        assertTrue(String.join(";", house.problems()).contains("ground-depth must be a whole number from 0 to 2"));
    }
}
