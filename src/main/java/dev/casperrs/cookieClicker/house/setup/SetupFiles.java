package dev.casperrs.cookieClicker.house.setup;

import dev.casperrs.cookieClicker.house.design.DesignFiles;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

/** What a plot setup reads before it starts: the design files and the plot's previous setup backup. IO thread. */
final class SetupFiles {

    /** Texts of the design files (null = missing) and the newest previous backup (null = none). */
    record Inputs(@Nullable String design, @Nullable String fence, @Nullable String belt, @Nullable String decor,
                  @Nullable String slots, BackupFiles.@Nullable Read old) {
    }

    private SetupFiles() {
    }

    static Inputs read(Plugin plugin, Path backupDir, String designName) throws IOException {
        BackupFiles.Read old = BackupFiles.readNewest(backupDir);
        return new Inputs(DesignFiles.read(plugin, designName + ".yml"),
                DesignFiles.read(plugin, DesignFiles.FENCE_STYLE), DesignFiles.read(plugin, DesignFiles.BELT_STYLE),
                DesignFiles.read(plugin, DesignFiles.DECOR), DesignFiles.read(plugin, DesignFiles.BUILDING_SLOTS), old);
    }

    /** Deletes the previous backup once it was restored (the world matches the state before it again). */
    static void deleteOld(Inputs in) throws IOException {
        if (in.old() != null) {
            BackupFiles.delete(in.old().yml());
        }
    }
}
