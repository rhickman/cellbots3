package ai.cellbots.arcompanion.utils;

import android.opengl.Matrix;

import com.google.atap.tangoservice.TangoCameraIntrinsics;

import ai.cellbots.arcompanion.R;

/**
 * A utility class containing boilerplate code for Google Tango operations.
 *
 * Created by playerthree on 8/21/17.
 */

public final class TangoUtils {

    private static final float NEAR = 0.1f;
    private static final float FAR = 100.0f;

    private TangoUtils() {
        throw new AssertionError(R.string.assertion_utility_class_never_instantiated);
    }

    /**
     * Use Tango camera intrinsics to calculate the projection Matrix for the Rajawali scene.
     */
    public static float[] CameraIntrinsicsToProjection(TangoCameraIntrinsics intrinsics) {
        // Uses frustumM to create a projection matrix taking into account calibrated camera
        // intrinsic parameter.
        // Reference: http://ksimek.github.io/2013/06/03/calibrated_cameras_in_opengl/
        double cx = intrinsics.cx;
        double cy = intrinsics.cy;
        double fx = intrinsics.fx;
        double fy = intrinsics.fy;
        double width = intrinsics.width;
        double height = intrinsics.height;

        double xScale = NEAR / fx;
        double yScale = NEAR / fy;

        double halfWidth = width / 2.0;
        double halfHeight = height / 2.0;

        double xOffset = (cx - halfWidth) * xScale;
        // Color camera's coordinates has y pointing downwards so we negate this term.
        double yOffset = -(cy - halfHeight) * yScale;

        float m[] = new float[16];
        Matrix.frustumM(
                m,
                0,
                (float) (xScale * -halfWidth - xOffset),
                (float) (xScale * halfWidth - xOffset),
                (float) (yScale * -halfHeight - yOffset),
                (float) (yScale * halfHeight - yOffset),
                NEAR,
                FAR
        );
        return m;
    }
}
