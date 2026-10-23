package dev.casperrs.cookieClicker.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.station.Station;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StationMigrationTest {

    private static Station st(String id, double x) {
        return new Station(id, "minecraft:overworld", x, 64, 0.5, 90f);
    }

    @Test
    void aStationInsideAPlotBecomesItsPedestal() {
        StationMigration.Plan plan = StationMigration.plan(List.of(st("station-1", 5.5)), s -> Optional.of("main"));
        assertEquals(1, plan.plots().size());
        Plot p = plan.plots().getFirst();
        assertEquals("main", p.id());
        assertEquals("minecraft:overworld", p.worldKey());
        assertEquals(new Plot.Pedestal(5.5, 64, 0.5, 90f), p.pedestal());
        assertTrue(plan.leftovers().isEmpty());
    }

    @Test
    void stationsOutsideEveryPlotAreLeftovers() {
        StationMigration.Plan plan = StationMigration.plan(List.of(st("station-1", 5.5), st("station-2", 99.5)),
                s -> s.x() < 10 ? Optional.of("main") : Optional.empty());
        assertEquals(List.of("main"), plan.plots().stream().map(Plot::id).toList());
        assertEquals(List.of("station-2"), plan.leftovers().stream().map(Station::id).toList());
    }

    @Test
    void theFirstStationOfAPlotWinsTheRestAreLeftovers() {
        StationMigration.Plan plan = StationMigration.plan(List.of(st("station-1", 1), st("station-2", 2), st("station-3", 3)),
                s -> Optional.of("main"));
        assertEquals(1, plan.plots().size());
        assertEquals(1.0, plan.plots().getFirst().pedestal().x());
        assertEquals(2, plan.leftovers().size());
    }

}
