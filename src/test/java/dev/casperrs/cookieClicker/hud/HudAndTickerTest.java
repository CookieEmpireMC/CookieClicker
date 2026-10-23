package dev.casperrs.cookieClicker.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.economy.EconomyConfig;
import dev.casperrs.cookieClicker.economy.EconomyConfigParser;
import dev.casperrs.cookieClicker.economy.PouchSnapshot;
import dev.casperrs.cookieClicker.text.RealMessages;
import dev.casperrs.cookieClicker.session.SessionManager;
import dev.casperrs.cookieClicker.storage.LoadedPouch;
import dev.casperrs.cookieClicker.storage.PlayerStore;
import dev.casperrs.cookieClicker.text.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class HudAndTickerTest {

    private static EconomyConfig config() throws Exception {
        return EconomyConfigParser.parse(
                Map.of("cost-growth", 1.15, "buildings", Map.of("baker", Map.of("base-cost", 15, "base-cps", 0.1))),
                Map.of("click", Map.of("base-power", 1))).config();
    }

    private static Messages messages() {
        Logger quiet = Logger.getAnonymousLogger();
        quiet.setLevel(Level.OFF);
        Messages m = new Messages(quiet);
        m.load(RealMessages.yaml());
        return m;
    }

    @Test
    void renderShowsCookiesAndCpsWithAndWithoutPack() throws Exception {
        Economy economy = new Economy(config(), new java.util.Random(1));
        Hud hud = new Hud(messages(), economy, p -> true);
        CookiePouch pouch = CookiePouch.fromSnapshot(new PouchSnapshot(1_500_000, 2_000_000, Map.of("baker", 10), Set.of()));
        String withPack = PlainTextComponentSerializer.plainText().serialize(hud.render(pouch, true));
        String noPack = PlainTextComponentSerializer.plainText().serialize(hud.render(pouch, false));
        assertTrue(withPack.contains("1.5M") && withPack.contains("1"), withPack);
        assertTrue(noPack.contains("1.5M"), noPack);
        // the cookie glyph (U+E000, cookieclicker:hud font) is only in the with-pack line
        assertTrue(withPack.indexOf('\uE000') >= 0, "with-pack variant carries the glyph: " + withPack);
        assertTrue(noPack.indexOf('\uE000') < 0, "no-pack variant must not show a box: " + noPack);
    }

    @Test
    void tickerProducesOneSecondOfCpsPerRunForLoadedPlayersOnly() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID ghost = UUID.randomUUID();
        PlayerStore store = new PlayerStore() {
            @Override
            public void init() {
            }

            @Override
            public Optional<LoadedPouch> load(UUID player) {
                return player.equals(alice)
                        ? Optional.of(new LoadedPouch(new PouchSnapshot(0, 0, Map.of("baker", 10), Set.of()), List.of()))
                        : Optional.empty();
            }

            @Override
            public void save(UUID player, PouchSnapshot snapshot) {
            }

            @Override
            public void close() {
            }
        };
        ExecutorService io = Executors.newSingleThreadExecutor();
        List<Runnable> main = new ArrayList<>();
        Logger quiet = Logger.getAnonymousLogger();
        quiet.setLevel(Level.OFF);
        SessionManager sessions = new SessionManager(store, io, main::add, quiet);
        sessions.start();
        sessions.join(alice);
        io.submit(() -> { }).get(5, TimeUnit.SECONDS);
        synchronized (main) {
            main.forEach(Runnable::run);
        }
        Economy economy = new Economy(config(), new java.util.Random(1));
        BakeryTicker ticker = new BakeryTicker(sessions, economy, new Hud(messages(), economy, p -> false), id -> null);
        ticker.setHudEverySeconds(0); // clamps to 1
        for (int i = 0; i < 5; i++) {
            ticker.run();
        }
        assertEquals(5.0, sessions.get(alice).orElseThrow().pouch(), 1e-9);
        assertEquals(5.0, sessions.get(alice).orElseThrow().baked(), 1e-9);
        assertTrue(sessions.get(ghost).isEmpty());
        io.shutdownNow();
    }
}
