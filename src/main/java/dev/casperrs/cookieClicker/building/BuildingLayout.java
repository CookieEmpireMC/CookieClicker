package dev.casperrs.cookieClicker.building;

import dev.casperrs.cookieClicker.config.BuildingVisualSettings;
import dev.casperrs.cookieClicker.house.design.BuildingSlots;
import dev.casperrs.cookieClicker.house.design.HouseDesign;
import dev.casperrs.cookieClicker.house.design.HouseLayout;
import dev.casperrs.cookieClicker.plot.Plot;
import org.jspecify.annotations.Nullable;

/**
 * Pure maths of the visible buildings: where a plot's layout frame starts in the world and
 * what one building type shows for an owned count.
 */
public final class BuildingLayout {

    private BuildingLayout() {
    }

    /**
     * What one building type shows before and after a change of its owned count. Stages are 1..3, or 0 when nothing
     * is shown; {@code shownAfter = min(owned, slots)}.
     */
    public record Change(int shownBefore, int shownAfter, int stageBefore, int stageAfter) {

        /** New instances appear (index {@code shownBefore} up to {@code shownAfter - 1}). */
        public boolean appears() {
            return shownAfter > shownBefore;
        }

        /** Shown instances were there before and now get a new look. */
        public boolean stageChanged() {
            return stageBefore != 0 && stageAfter != 0 && stageAfter != stageBefore;
        }
    }

    /** {@code ownedBefore} is -1 when this type was not shown yet; {@code slots} is the visual cap. */
    public static Change change(int ownedBefore, int ownedAfter, int slots, BuildingVisualSettings s) {
        int before = ownedBefore < 0 ? 0 : Math.min(ownedBefore, slots);
        int after = Math.max(0, Math.min(ownedAfter, slots));
        return new Change(before, after, before == 0 ? 0 : s.stage(ownedBefore), after == 0 ? 0 : s.stage(ownedAfter));
    }

    /**
     * The world block of plot cell {@code (0, 0, 0)} of {@code design}'s layout (x/z: the layout's min corner, y: the
     * first air layer above the ground), worked back from the house box {@code /bakery plot setup} stored; null when
     * the plot has no house box or the design no layout.
     */
    public static int @Nullable [] origin(Plot plot, HouseDesign design) {
        Plot.Box house = plot.house();
        HouseLayout layout = design.layout();
        if (house == null || layout == null) {
            return null;
        }
        int[] at = layout.houseAt();
        int[] o = design.origin();
        return new int[] {house.minX() - at[0] + o[0], house.minY() - at[1] + o[1], house.minZ() - at[2] + o[2]};
    }

    /** The floor point of {@code slot} (cell centre on its layer): the model's bottom centre. */
    public static double[] floorPoint(int[] origin, BuildingSlots.Slot slot) {
        return new double[] {origin[0] + slot.x() + 0.5, origin[1] + slot.y(), origin[2] + slot.z() + 0.5};
    }
}
