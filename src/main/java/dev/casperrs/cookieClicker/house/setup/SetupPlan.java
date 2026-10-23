package dev.casperrs.cookieClicker.house.setup;

import dev.casperrs.cookieClicker.house.design.HouseDesign;
import dev.casperrs.cookieClicker.plot.Plot;

import java.util.List;

/**
 * A planned setup, ready to apply once {@link #backup} and {@link #structure} are on disk. houseAt is the design
 * origin's world position.
 */
record SetupPlan(HouseDesign design, int[] houseAt, int groundY, List<HouseBackup.Change> changes, Plot updated,
                 SetupBackup backup, byte[] structure) {

    SetupPlan {
        changes = List.copyOf(changes);
    }
}
