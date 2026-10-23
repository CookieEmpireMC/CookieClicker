package dev.casperrs.cookieClicker.building;

import dev.casperrs.cookieClicker.house.design.BuildingSlots;
import dev.casperrs.cookieClicker.house.design.DesignFiles;
import dev.casperrs.cookieClicker.house.design.HouseDesign;
import dev.casperrs.cookieClicker.house.design.HouseDesignParser;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads {@code houses/building-slots.yml} and the default design whose layout cells the slots use. Small files,
 * read on enable and reload.
 */
final class BuildingSlotsLoader {

    private static final String SLOTS_FILE = DesignFiles.DIR + "/" + DesignFiles.BUILDING_SLOTS;
    private static final String DESIGN_FILE = DesignFiles.DIR + "/" + DesignFiles.DEFAULT_DESIGN + ".yml";

    /** The slots and their design; {@link #EMPTY} (no visible buildings) when either is missing or broken. */
    record Loaded(BuildingSlots slots, @Nullable HouseDesign design) {
        static final Loaded EMPTY = new Loaded(BuildingSlots.empty(), null);
    }

    private BuildingSlotsLoader() {
    }

    static Loaded load(Plugin plugin) {
        Logger log = plugin.getLogger();
        try {
            String slotsText = DesignFiles.read(plugin, DesignFiles.BUILDING_SLOTS);
            if (slotsText == null) {
                return Loaded.EMPTY;
            }
            BuildingSlots.Result r = BuildingSlots.parse(DesignFiles.parseYaml(slotsText));
            r.problems().forEach(p -> log.warning(SLOTS_FILE + ": " + p));
            String designText = DesignFiles.read(plugin, DesignFiles.DEFAULT_DESIGN + ".yml");
            if (designText == null) {
                log.warning(DESIGN_FILE + " not found; no visible buildings");
                return Loaded.EMPTY;
            }
            HouseDesignParser.Result d = HouseDesignParser.parse(DesignFiles.DEFAULT_DESIGN,
                    DesignFiles.parseYaml(designText));
            if (d.design() == null || d.design().layout() == null) {
                log.warning(DESIGN_FILE + " has no valid layout; no visible buildings");
                return Loaded.EMPTY;
            }
            return new Loaded(r.slots(), d.design());
        } catch (IOException | InvalidConfigurationException e) {
            log.log(Level.WARNING, "Could not read " + SLOTS_FILE + "; no visible buildings", e);
            return Loaded.EMPTY;
        }
    }
}
