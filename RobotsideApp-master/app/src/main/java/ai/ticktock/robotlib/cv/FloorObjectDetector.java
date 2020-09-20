package ai.cellbots.robotlib.cv;

import android.util.Log;

import com.projecttango.tangosupport.TangoSupport;

import java.util.ArrayList;
import java.util.List;

import ai.cellbots.common.Polygon;
import ai.cellbots.common.Transform;

/**
 * CV algorithm that detects objects on the floor
 */
public class FloorObjectDetector extends CVAlgorithm {

    public static final String TAG = CVManager.class.getSimpleName();

    /**
     * Called to execute the next CV algorithm update.
     * @param imageTimestamp The timestamp that the color image was taken at.
     * @param depthData The depth data to be processed.
     * @param imageData The YCRCB_420_SP color image.
     * @param imageWidth The width of the color image.
     * @param imageHeight The height of the color image.
     * @param colorToDepthRatio The ratio of color image width (or height) to depth image width (or height)
     *                          This value is always greater or equal to 1
     *                          If this value is 2, then the depth image size is (imageWidth/2, imageHeight/2)
     * @return The list of Polygon objects. Could be null if the vision algorithm fails.
     * TODO: the current polygons are lines connecting the top left corner to the bottom right, change to convex hull
     */

    public List<Polygon> execute(double imageTimestamp, float[] depthData,
            byte[] imageData, int imageWidth, int imageHeight, TangoSupport.TangoMatrixTransformData worldToCamera,
            int colorToDepthRatio) {
        String imageInfo = String.format("FloorObjectDetectionApplication ReceivingImage: %d, %d", imageWidth, imageHeight);
        Log.v(TAG, imageInfo);
        float[] bound_rects;
        bound_rects = FloorObjectDetectorJNINative.processDepthAndColorImages(imageTimestamp, depthData,
                imageData, imageWidth, imageHeight, colorToDepthRatio);
        ArrayList<Polygon> r = null;
        if (bound_rects == null) {
            Log.d(TAG, "Bounding rectangles array is null");
        } else if (bound_rects[0] == 0) {
            Log.d(TAG, "No bounding rectangle found");
        } else {
            Log.d(TAG, "Total number of bounding rectangles found: " + bound_rects[0]);
            r = new ArrayList<>((int) bound_rects[0]);
            for (int i = 0; i < bound_rects[0]; i++) {
                float X_tl = bound_rects[i * 5 + 1];
                float Y_tl = bound_rects[i * 5 + 2];
                float X_br = bound_rects[i * 5 + 3];
                float Y_br = bound_rects[i * 5 + 4];
                float depth = bound_rects[i * 5 + 5];
                float[] point_tl = {X_tl, Y_tl, depth};
                float[] point_br = {X_br, Y_br, depth};
                float[] point_tl_transformed = TangoSupport.transformPoint(worldToCamera.matrix, point_tl);
                float[] point_br_transformed = TangoSupport.transformPoint(worldToCamera.matrix, point_br);
                r.add(new Polygon(new Transform[]{
                        new Transform(point_tl_transformed[0], point_tl_transformed[1], point_tl_transformed[2], 0),
                        new Transform(point_br_transformed[0], point_br_transformed[1], point_br_transformed[2], 0)}));
            }
        }
        return r;
    }

    /**
     * Called to start the shutdown of the algorithm.
     */
    public void shutdown() {
    }

    /**
     * Called to wait for the algorithm to shutdown.
     */
    public void waitShutdown() {
    }
}
