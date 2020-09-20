package ai.cellbots.robotlib.cv;

import com.projecttango.tangosupport.TangoSupport;

import java.util.List;

import ai.cellbots.common.Polygon;

/**
 * A computer vision algorithm.
 */
public abstract class CVAlgorithm {

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
     */
    
    public abstract List<Polygon> execute(double imageTimestamp, float[] depthData,
            byte[] imageData, int imageWidth, int imageHeight, TangoSupport.TangoMatrixTransformData worldToCamera,
            int colorToDepthRatio);
  
    /**
     * Called to start the shutdown of the algorithm. Maybe called during an execute() call.
     */
    public abstract void shutdown();

    /**
     * Called to wait for the algorithm to shutdown. Will be called after the last execute() call.
     */
    public abstract void waitShutdown();
}
