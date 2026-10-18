package dev.casperrs.cookieClicker.format;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;

/** Loads the shipped messages.yml from the classpath for tests. */
public final class RealMessages {

    private RealMessages() {
    }

    public static YamlConfiguration yaml() {
        try (InputStream in = RealMessages.class.getClassLoader().getResourceAsStream("messages.yml")) {
            if (in == null) {
                throw new IllegalStateException("messages.yml not on the classpath");
            }
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(r);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static NumberFormatter formatter() {
        dev.casperrs.cookieClicker.text.Messages m = new dev.casperrs.cookieClicker.text.Messages(java.util.logging.Logger.getAnonymousLogger());
        m.load(yaml());
        return m.numbers();
    }
}
