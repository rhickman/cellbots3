#ifndef FLOOR_OBJECT_DETECTOR_H_
#define FLOOR_OBJECT_DETECTOR_H_

#include <vector>

#include <android/log.h>
#include <assert.h>
#include <jni.h>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/features2d/features2d.hpp>
#include <opencv2/highgui/highgui.hpp>


#define LOGV(...) \
  __android_log_print(ANDROID_LOG_VERBOSE, "floor_object_detection", __VA_ARGS__)
#define LOGD(...) \
  __android_log_print(ANDROID_LOG_DEBUG, "floor_object_detection", __VA_ARGS__)
#define LOGI(...) \
  __android_log_print(ANDROID_LOG_INFO, "floor_object_detection", __VA_ARGS__)
#define LOGW(...) \
  __android_log_print(ANDROID_LOG_WARN, "floor_object_detection", __VA_ARGS__)
#define LOGE(...) \
  __android_log_print(ANDROID_LOG_ERROR, "floor_object_detection", __VA_ARGS__)

namespace floor_object_detection {

class FloorObjectDetector {
 public:
  /** Returns a pointer to a float array of the bounding rectangles of floor objects represented as:
   * [# of bouding boxes, tl_X_1, tl_Y_1, br_X_1, br_Y_1, depth_1, tl_X_2, tl_Y_2, br_X_2, br_Y_2, depth_2, ..]
   * where tl_X_i and tl_Y_i are the top left corner coordinates of rectangle i in the current world coordinates
   *       br_X_i and br_Y_i are the bottom right coordinates of rectangle i in the current world coordinates
   *       depth_i is the minimum non-zero depth of rectangle i
   * @param timestamp The timestamp that the color image was taken at.
   * @param depth_data The depth data to be processed.
   * @param image_data The YCRCB_420_SP color image.
   * @param image_width The width of the image.
   * @param image_height The width of the image.
   * @param color_to_depth_ratio The ratio of color image width (or height) to depth image width (or height)
   *                             This value is always greater or equal to 1
   *                             If this value is 2, then the depth image size is (image_width/2, image_height/2)
   * @return a pointer to the float array of bounding boxes information
   * The return value can be nullptr if the data is received at a higher frequency than the set fps
   * (see kProcessImagesFPS in floor_object_detection.cc)
   * It can also be nullptr if the algorithm could not find enough points to estimate a floor plane
   */
  float* ProcessDepthAndColorImages(double timestamp, float* depth_data, uint8_t* image_data,
          int image_width, int image_height, int color_to_depth_ratio);

 private:
  void ConvertYCbCrtoRGB(uint8_t* rgb, uint8_t* ycbcr, int width, int height);
  double prev_image_timestamp_ = 0;
};
}  // namespace floor object detection

#endif  // CPP_FLOOR_OBJECT_DETECTION_APPLICATION_H_