package ai.cellbots.robot.vision;

import android.util.Log;

/**
 * Interfaces between C and Java.
 */
public final class FloorObjectDetectorJNINative {
  static {
    try {
      System.loadLibrary("floor_object_detect_jni");
    } catch (UnsatisfiedLinkError e) {
      final String TAG = FloorObjectDetectorJNINative.class.getSimpleName();
      Log.e(TAG, "Java interface could not load library" + e);
    }
  }

  /** Returns a float array of the bounding rectangles of floor objects represented as:
   * [# of bouding boxes, X1_1, Y1_1, Z1_1, X2_1, Y2_1, Z2_1, X1_2, Y1_2, Z1_2, X2_2, Y2_2, Z2_2, ...]
   * where (X1_i, Y1_i, Z1_i) and (X2_i, Y2_i, Z2_i) are the coordinates of the two bottom corners
   *       of rectangle i in the current world coordinates.
   * @param imageTimeStamp The timestamp that the color image was taken at.
   * @param depthImage The depth image data to be processed.
   * @param colorImage The YCRCB_420_SP color image data to be processed
   * @param depthImageSize An array containing depth image size [width, height]
   * @param colorImageSize An array containing color image size [width, height]
   * @param depthImageIntrinsics An array containing depth image intrinsics [fx, fy, cx, cy]
   * @param colorImageIntrinsics An array containing color image intrinsics [fx, fy, cx, cy]
   * @return a float array of bounding boxes information
   * The return value can be null if the data is sent at a higher frequency than the set fps (in the C++ layer)
   * It can also be null if the algorithm could not find enough points to estimate a floor plane
   */
  public static native float[] processDepthAndColorImages(double imageTimeStamp, float[] depthImage,
          byte[] colorImage, int[] depthImageSize, int[] colorImageSize,
          double[] depthImageIntrinsics, double[] colorImageIntrinsics);
}