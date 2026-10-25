package dev.casperrs.cookieClicker.farm;

import dev.casperrs.cookieClicker.house.design.HouseDesign;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The paste walk: every cell lands where {@link HouseDesign#toWorld} puts it, and batches add up to one pass. */
class PasteCursorTest {

    private static String cell(int part, int x, int y, int z, char c) {
        return part + " " + x + " " + y + " " + z + " " + c;
    }

    @Test
    void everyNonKeepCellLandsWhereTheDesignPutsItInTheWorld() throws Exception {
        FarmPlan plan = FarmPlan.plan(FarmFixtures.layout(FarmFixtures.files()), FarmFixtures.REGION_MIN).plan();
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < plan.parts().size(); i++) {
            FarmPlan.Part p = plan.parts().get(i);
            HouseDesign d = p.design();
            for (HouseDesign.Cell c : d.cells()) {
                int[] w = d.toWorld(p.anchor(), c.x(), c.y(), c.z());
                char ch = d.grid()[c.y() + d.groundDepth()][c.z()][c.x()];
                expected.add(cell(i, w[0], w[1], w[2], ch));
            }
        }
        List<String> actual = collect(plan, Integer.MAX_VALUE);
        Collections.sort(expected);
        Collections.sort(actual);
        assertEquals(expected, actual);
        assertEquals(plan.cellCount(), actual.size());
    }

    @Test
    void batchesHandOutTheSameCellsInTheSameOrderAsOnePass() throws Exception {
        FarmPlan plan = FarmPlan.plan(FarmFixtures.layout(FarmFixtures.files()), FarmFixtures.REGION_MIN).plan();
        List<String> batched = new ArrayList<>();
        PasteCursor cursor = new PasteCursor(plan);
        while (!cursor.done()) {
            cursor.next(7, (part, x, y, z, c) -> batched.add(cell(part, x, y, z, c)));
        }
        assertEquals(collect(plan, Integer.MAX_VALUE), batched);
    }

    private static List<String> collect(FarmPlan plan, int max) {
        List<String> out = new ArrayList<>();
        new PasteCursor(plan).next(max, (part, x, y, z, c) -> out.add(cell(part, x, y, z, c)));
        return out;
    }
}
