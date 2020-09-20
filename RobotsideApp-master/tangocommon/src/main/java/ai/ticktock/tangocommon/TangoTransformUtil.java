package ai.cellbots.tangocommon;

import com.google.atap.tangoservice.TangoPoseData;

import ai.cellbots.common.Transform;

/**
 * Utilities to convert Cellbots transforms to and from Tango poses.
 */
public final class TangoTransformUtil {
    /**
     * Create a transform from TangoPoseData with a fixed timestamp.
     *
     * @param tt The tango pose data.
     * @param t  The timestamp.
     */
    public static Transform poseToTransform(TangoPoseData tt, double t) {
        if (tt.translation.length > 127) {
            throw new java.lang.Error("Translation length too big: " + tt.translation.length);
        }
        if (tt.rotation.length > 127) {
            throw new java.lang.Error("Rotation length too big: " + tt.rotation.length);
        }

        double[] position = new double[tt.translation.length];
        System.arraycopy(tt.translation, 0, position, 0, tt.translation.length);
        double[] rotation = new double[tt.rotation.length];
        System.arraycopy(tt.rotation, 0, rotation, 0, tt.rotation.length);

        return new Transform(position, rotation, t);
    }

    /**
     * Create a transform from TangoPoseData with a fixed timestamp.
     *
     * @param tt The tango pose data.
     */
    public static Transform poseToTransform(TangoPoseData tt) {
        return poseToTransform(tt, tt.timestamp);
    }

    /**
     * Convert transform to TangoPose
     *
     * @return Transform into a TangoPoseData object
     */
    public static TangoPoseData toTangoPose(Transform tf) {
        TangoPoseData poseData = new TangoPoseData();
        poseData.translation = new double[tf.getPosition().length];
        System.arraycopy(tf.getPosition(), 0, poseData.translation, 0, tf.getPosition().length);
        poseData.rotation = new double[tf.getRotation().length];
        System.arraycopy(tf.getRotation(), 0, poseData.rotation, 0, tf.getRotation().length);
        poseData.timestamp = tf.getTimestamp();
        return poseData;
    }
}
