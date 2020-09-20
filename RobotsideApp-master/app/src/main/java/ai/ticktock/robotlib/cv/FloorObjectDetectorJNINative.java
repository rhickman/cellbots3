package ai.cellbots.robotlib.cv;

/**
 * Interfaces between C and Java.
 */
public final class FloorObjectDetectorJNINative {
  static {
    System.loadLibrary("floor_object_detection_jni");
  }

  /** Returns a float array of the bounding rectangles of floor objects represented as:
   * [# of bounding boxes, tl_X_1, tl_Y_1, br_X_1, br_Y_1, depth_1, tl_X_2, tl_Y_2, br_X_2, br_Y_2, depth_2, ..]
   * where tl_X_i and tl_Y_i are the top left corner coordinates of rectangle i in the current world coordinates
   *       br_X_i and br_Y_i are the bottom right coordinates of rectangle i in the current world coordinates
   *       depth_i is the minimum non-zero depth of rectangle i
   * @param imageTimeStamp The timestamp that the color image was taken at.
   * @param depthData The depth data to be processed.
   * @param imageData The YCRCB_420_SP color image.
   * @param imageWidth The width of the color image.
   * @param imageHeight The width of the color image.
   * @param colorToDepthRatio The ratio of color image width (or height) to depth image width (or height)
   *                          This value is always greater or equal to 1
   *                          If this value is 2, then the depth image size is (imageWidth/2, imageHeight/2)
   * @return a float array of bounding boxes information
   * The return value can be null if the data is sent at a higher frequency than the set fps (in the C++ layer)
   * It can also be null if the algorithm could not find enough points to estimate a floor plane
   */
  public static native float[] processDepthAndColorImages(double imageTimeStamp, float[] depthData,
          byte[] imageData, int imageWidth, int imageHeight, int colorToDepthRatio);
  //public static native void saveImages(float[] depthData);
}