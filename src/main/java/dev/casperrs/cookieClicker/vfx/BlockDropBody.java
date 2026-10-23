package dev.casperrs.cookieClicker.vfx;

import dev.casperrs.cookieClicker.vfx.shape.DropTrack;
import org.bukkit.entity.BlockDisplay;

/** The real {@link DropJob.Body}: a {@link BlockDisplay} spawned by {@link Spawner}. */
final class BlockDropBody implements DropJob.Body {

    private final BlockDisplay display;

    BlockDropBody(BlockDisplay display) {
        this.display = display;
    }

    BlockDisplay display() {
        return display;
    }

    @Override
    public boolean valid() {
        return display.isValid();
    }

    @Override
    public void send(DropTrack.Keyframe k) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(k.duration());
        display.setTransformation(Transformations.of(k));
    }

    @Override
    public void remove() {
        if (display.isValid()) {
            display.remove();
        }
    }
}
