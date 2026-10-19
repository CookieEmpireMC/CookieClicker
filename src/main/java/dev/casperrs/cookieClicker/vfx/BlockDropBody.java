package dev.casperrs.cookieClicker.vfx;

import dev.casperrs.cookieClicker.vfx.shape.DropTrack;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

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
        display.setTransformation(transformation(k));
    }

    @Override
    public void remove() {
        if (display.isValid()) {
            display.remove();
        }
    }

    static Transformation transformation(DropTrack.Keyframe k) {
        return new Transformation(
                new Vector3f((float) k.tx(), (float) k.ty(), (float) k.tz()),
                new AxisAngle4f(),
                new Vector3f((float) k.sx(), (float) k.sy(), (float) k.sz()),
                new AxisAngle4f());
    }
}
