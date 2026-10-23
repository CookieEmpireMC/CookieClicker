package dev.casperrs.cookieClicker.house.cinematic;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * What a player gets back after the cinematic camera: their own game mode and where they stand afterwards. Stored on
 * the player (PDC string) while they are in the camera, so a quit, crash or disable mid-build is undone on the next
 * join. Pure Java; format {@code <MODE>;<world key>;<x>;<y>;<z>;<yaw>;<pitch>}. gameMode is never {@code SPECTATOR}
 * unless it was.
 */
public record CameraReturn(String gameMode, String worldKey, double x, double y, double z, float yaw, float pitch) {

    public CameraReturn {
        if (gameMode.isBlank() || gameMode.contains(";") || worldKey.isBlank() || worldKey.contains(";")) {
            throw new IllegalArgumentException("bad camera return " + gameMode + " / " + worldKey);
        }
    }

    public String encode() {
        return String.join(";", gameMode, worldKey, num(x), num(y), num(z), num(yaw), num(pitch));
    }

    /** The decoded value, or null when {@code raw} is not one (then it is ignored and removed). */
    public static @Nullable CameraReturn decode(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String[] p = raw.split(";", -1);
        if (p.length != 7) {
            return null;
        }
        try {
            double x = Double.parseDouble(p[2]);
            double y = Double.parseDouble(p[3]);
            double z = Double.parseDouble(p[4]);
            float yaw = Float.parseFloat(p[5]);
            float pitch = Float.parseFloat(p[6]);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw)
                    || !Float.isFinite(pitch)) {
                return null;
            }
            return new CameraReturn(p[0].trim().toUpperCase(Locale.ROOT), p[1], x, y, z, yaw, pitch);
        } catch (IllegalArgumentException e) { // NumberFormatException too
            return null;
        }
    }

    private static String num(double v) {
        return Double.toString(v);
    }
}
