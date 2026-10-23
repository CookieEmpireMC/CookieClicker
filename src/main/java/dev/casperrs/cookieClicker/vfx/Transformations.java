package dev.casperrs.cookieClicker.vfx;

import dev.casperrs.cookieClicker.vfx.shape.DisplayTrack;
import dev.casperrs.cookieClicker.vfx.shape.DropTrack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Builds display {@link Transformation}s from track keyframes. */
final class Transformations {

    private Transformations() {
    }

    /** Translation, rotation around an axis (degrees) and a uniform scale. */
    static Transformation of(DisplayTrack.Keyframe k) {
        float s = (float) k.scale();
        return new Transformation(
                new Vector3f((float) k.tx(), (float) k.ty(), (float) k.tz()),
                new AxisAngle4f((float) Math.toRadians(k.angleDeg()), (float) k.ax(), (float) k.ay(), (float) k.az()),
                new Vector3f(s, s, s),
                new AxisAngle4f());
    }

    /** Translation and a per-axis scale, no rotation. */
    static Transformation of(DropTrack.Keyframe k) {
        return new Transformation(
                new Vector3f((float) k.tx(), (float) k.ty(), (float) k.tz()),
                new AxisAngle4f(),
                new Vector3f((float) k.sx(), (float) k.sy(), (float) k.sz()),
                new AxisAngle4f());
    }

    /** {@code cur} with a new scale; translation and both rotations are kept. */
    static Transformation withScale(Transformation cur, Vector3f scale) {
        return new Transformation(cur.getTranslation(), cur.getLeftRotation(), scale, cur.getRightRotation());
    }

    /** {@code cur} with a new translation, left rotation and scale; the right rotation is kept. */
    static Transformation with(Transformation cur, Vector3f translation, Quaternionf leftRotation, Vector3f scale) {
        return new Transformation(translation, leftRotation, scale, cur.getRightRotation());
    }
}
