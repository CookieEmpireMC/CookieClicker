package dev.casperrs.cookieClicker.house;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

}
