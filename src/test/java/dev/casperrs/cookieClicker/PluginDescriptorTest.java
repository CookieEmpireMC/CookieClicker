package dev.casperrs.cookieClicker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.command.BakeryCommand;
import dev.casperrs.cookieClicker.command.FarmCommand;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** paper-plugin.yml and the loader: what the server reads before our code runs, so no other test would notice. */
class PluginDescriptorTest {

    private static YamlConfiguration descriptor() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        try (var in = PluginDescriptorTest.class.getClassLoader().getResourceAsStream("paper-plugin.yml")) {
            assertNotNull(in, "paper-plugin.yml missing from the jar");
            y.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return y;
    }

    private static String pomProperty(String name) throws Exception {
        Matcher m = Pattern.compile("<" + Pattern.quote(name) + ">([^<]+)</").matcher(Files.readString(Path.of("pom.xml")));
        assertTrue(m.find(), "pom.xml has no property " + name);
        return m.group(1).trim();
    }

    @Test
    void loaderDownloadsExactlyTheDriverVersionsOfThePom() throws Exception {
        assertEquals(List.of("com.h2database:h2:" + pomProperty("h2.version"),
                "com.zaxxer:HikariCP:" + pomProperty("hikaricp.version"),
                "org.mariadb.jdbc:mariadb-java-client:" + pomProperty("mariadb.version")),
                CookieClickerLoader.libraries());
    }

    @Test
    void descriptorPointsAtOurClassesAndFiltersTheVersion() throws Exception {
        YamlConfiguration y = descriptor();
        assertEquals(CookieClicker.class.getName(), y.getString("main"));
        assertEquals(CookieClickerLoader.class.getName(), y.getString("loader"));
        assertFalse(y.getString("version").contains("${"), "version was not filtered by Maven");
        assertEquals("26.3", y.getString("api-version"));
        assertFalse(Files.exists(Path.of("src/main/resources/plugin.yml")), "a leftover plugin.yml hides mistakes");
    }

    @Test
    void worldGuardLoadsBeforeUsSoOnLoadCanRegisterTheFlags() throws Exception {
        YamlConfiguration y = descriptor();
        assertEquals("BEFORE", y.getString("dependencies.server.WorldGuard.load"));
        assertTrue(y.getBoolean("dependencies.server.WorldGuard.required"));
        assertTrue(y.getBoolean("dependencies.server.WorldGuard.join-classpath"), "needed to see com.sk89q.*");
    }

    @Test
    void theDeclaredPermissionIsTheOneTheCommandChecks() throws Exception {
        YamlConfiguration y = descriptor();
        assertTrue(y.isConfigurationSection("permissions." + BakeryCommand.PERMISSION),
                "paper-plugin.yml does not declare " + BakeryCommand.PERMISSION);
        assertEquals("op", y.getString("permissions." + BakeryCommand.PERMISSION + ".default"));
    }

    @Test
    void theTeleportPermissionIsDeclaredAndOpenToEveryone() throws Exception {
        YamlConfiguration y = descriptor();
        assertTrue(y.isConfigurationSection("permissions." + FarmCommand.TELEPORT_PERMISSION),
                "paper-plugin.yml does not declare " + FarmCommand.TELEPORT_PERMISSION);
        assertTrue(y.getBoolean("permissions." + FarmCommand.TELEPORT_PERMISSION + ".default"));
    }
}
