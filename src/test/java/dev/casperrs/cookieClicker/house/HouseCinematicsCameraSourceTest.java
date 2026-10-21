package dev.casperrs.cookieClicker.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Source-level guard for the spectator camera (no server in unit tests): every way a cinematic run can end must end
 * the camera (game mode back), so a player is never left in spectator mode. Breaks when someone adds a new exit
 * from a run without ending the camera.
 */
class HouseCinematicsCameraSourceTest {

    private static final Path MAIN = Path.of("src/main/java/dev/casperrs/cookieClicker");
    private static final String CINEMATICS = read(MAIN.resolve("house/HouseCinematics.java"));
    private static final String CAMERA = read(MAIN.resolve("house/CinematicCamera.java"));

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The text of the method whose declaration line contains {@code signature}, braces matched. */
    private static String body(String src, String signature) {
        int at = src.indexOf(signature);
        assertTrue(at >= 0, "no method " + signature);
        int open = src.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return src.substring(open, i + 1);
            }
        }
        throw new AssertionError("unbalanced " + signature);
    }

    @Test
    void everyRunRemovalIsInCompleteOrAbort() {
        // `running.remove(` may only appear in complete(...) and the abort(...) implementation
        String complete = body(CINEMATICS, "private void complete(Run run, Player p)");
        String abort = body(CINEMATICS, "private boolean abort(UUID id, boolean online, boolean goBack)");
        String rest = CINEMATICS.replace(complete, "").replace(abort, "");
        assertEquals(List.of(), occurrences(rest, "running.remove("), "a run is removed outside complete/abort");
    }

    @Test
    void completionEndsTheCamera() {
        String complete = body(CINEMATICS, "private void complete(Run run, Player p)");
        assertTrue(complete.contains("camera.exit("), "complete() must end the camera");
        assertTrue(complete.indexOf("markSeen") < complete.indexOf("camera.exit("),
                "seen is stored before the camera teleports the player back into the plot");
    }

    @Test
    void abortEndsTheCameraEvenWhenAnotherStepThrows() {
        String abort = body(CINEMATICS, "private boolean abort(UUID id, boolean online, boolean goBack)");
        assertTrue(abort.contains("run.camera"));
        assertTrue(abort.contains("camera.exit(p, online, goBack)"));
        assertTrue(abort.matches("(?s).*guarded\\(\"end camera\".*"), "the camera step must not be skipped by a throw");
        // the other steps are guarded too, so none of them can throw before the camera step
        assertTrue(abort.indexOf("guarded(\"cancel effects\"") < abort.indexOf("camera.exit"));
    }

    @Test
    void everyAbortPathGoesThroughAbort() {
        assertTrue(body(CINEMATICS, "public void onQuit(PlayerQuitEvent").contains("abort(id, false)"), "quit/kick");
        assertTrue(body(CINEMATICS, "public void onDeath(PlayerDeathEvent").contains("abort(id, true)"), "death");
        assertTrue(body(CINEMATICS, "public void onWorldChange(PlayerChangedWorldEvent")
                .contains("abort(event.getPlayer().getUniqueId(), true)"), "world change");
        assertTrue(body(CINEMATICS, "public void abortAll()").contains("abort(id, true)"), "reload");
        assertTrue(body(CINEMATICS, "public void shutdown()").contains("abortAll()"), "disable");
        assertTrue(body(CINEMATICS, "public void shutdown()").contains("camera.exitAll()"), "disable safety net");
        assertTrue(body(CINEMATICS, "private void tick()").contains("abort(run.player, false)"), "player vanished");
        assertTrue(body(CINEMATICS, "private void tick()").contains("abort(run.player, true)"), "step failed");
        assertTrue(body(CINEMATICS, "public void onTeleport(PlayerTeleportEvent").contains("abort(id, true, false)"),
                "teleport away keeps their own game mode");
    }

    @Test
    void theCameraListenerIsRegisteredAndUnregisteredWithTheCinematics() {
        assertTrue(body(CINEMATICS, "public void start()").contains("registerEvents(camera, plugin)"));
        assertTrue(body(CINEMATICS, "public void shutdown()").contains("unregisterAll(camera)"));
    }

    @Test
    void theCameraStoresTheOriginalModeBeforeChangingIt() {
        String enter = body(CAMERA, "public boolean enter(Player player, Location camera, Location back)");
        int pdc = enter.indexOf("getPersistentDataContainer().set(");
        int mode = enter.indexOf("setMode(player, GameMode.SPECTATOR)");
        assertTrue(pdc > 0 && mode > pdc, "PDC first (crash-safe), then the game mode");
        assertTrue(enter.contains("restore(player, ret, true)"), "a failed enter restores at once");
    }

    @Test
    void everyPathOutOfTheCameraRestoresTheGameMode() {
        assertTrue(body(CAMERA, "private void restore(").contains("setMode(player, mode(back))"));
        assertTrue(body(CAMERA, "public boolean exit(").contains("restore(player, shot.back"));
        assertTrue(body(CAMERA, "public boolean exit(").contains("setMode(player, mode(shot.back))"), "quit");
        assertTrue(body(CAMERA, "public void onJoin(").contains("restore(p, back, true)"), "rejoin after a crash");
        assertTrue(body(CAMERA, "public void exitAll()").contains("exit(p, true, true)"));
        // the stored marker is removed only after the mode is back
        String restore = body(CAMERA, "private void restore(");
        assertTrue(restore.indexOf("setMode(") < restore.indexOf("getPersistentDataContainer().remove(key)"));
    }

    @Test
    void camerasAreNeverPersistentAndTagged() {
        String enter = body(CAMERA, "public boolean enter(");
        assertTrue(enter.contains("setPersistent(false)"));
        assertTrue(enter.contains("addScoreboardTag(StationViews.TAG)"));
        assertTrue(enter.contains("setVisibleByDefault(false)"), "per-player visibility");
    }

    @Test
    void noGameModeIsChangedOutsideTheCamera() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (var files = Files.walk(MAIN)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (p.getFileName().toString().equals("CinematicCamera.java")) {
                    continue;
                }
                if (read(p).contains("setGameMode(")) {
                    offenders.add(p.toString());
                }
            }
        }
        assertEquals(List.of(), offenders, "only CinematicCamera may change game modes (it stores the original)");
    }

    private static List<String> occurrences(String src, String needle) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile(Pattern.quote(needle)).matcher(src);
        while (m.find()) {
            out.add(needle + "@" + m.start());
        }
        return out;
    }
}
