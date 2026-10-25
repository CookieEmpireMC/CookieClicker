package dev.casperrs.cookieClicker.farm;

import dev.casperrs.cookieClicker.house.design.HouseDesign;
import dev.casperrs.cookieClicker.house.design.HouseDesignParser;

import java.util.List;

/**
 * Walks the non-keep cells of a {@link FarmPlan} in paste order (part by part, each bottom layer first), a batch at a
 * time, so a big paste can be spread over ticks. Pure; not thread-safe.
 */
public final class PasteCursor {

    /** Receives one cell in world coordinates with its palette character. */
    @FunctionalInterface
    public interface Sink {
        void set(int part, int x, int y, int z, char c);
    }

    private final List<FarmPlan.Part> parts;
    private int part;
    private int layer;
    private int z;
    private int x;

    public PasteCursor(FarmPlan plan) {
        this.parts = plan.parts();
    }

    public boolean done() {
        return part >= parts.size();
    }

    /** Hands at most {@code max} cells to {@code sink}; returns how many it handed. */
    public int next(int max, Sink sink) {
        int handed = 0;
        while (handed < max && part < parts.size()) {
            FarmPlan.Part p = parts.get(part);
            HouseDesign d = p.design();
            char c = d.grid()[layer][z][x];
            if (!HouseDesignParser.isKeep(c)) {
                int[] a = p.anchor();
                int[] o = d.origin();
                // HouseDesign.toWorld without the array per cell
                sink.set(part, a[0] + x - o[0], a[1] + layer - d.groundDepth() - o[1], a[2] + z - o[2], c);
                handed++;
            }
            advance(d);
        }
        return handed;
    }

    private void advance(HouseDesign d) {
        if (++x < d.sizeX()) {
            return;
        }
        x = 0;
        if (++z < d.sizeZ()) {
            return;
        }
        z = 0;
        if (++layer < d.sizeY()) {
            return;
        }
        layer = 0;
        part++;
    }
}
