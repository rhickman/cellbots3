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
   * [# of bouding boxes, X1_1, Y1_1, Z1_1, X2_1, Y2_1, Z2_1, X1_2, Y1_2, Z1_2, X2_2, Y2_2, Z2_2, ...]
   * where (X1_i, Y1_i, Z1_i) and (X2_i, Y2_i, Z2_i) are the coordinates of the two bottom corners
   *       of rectangle i in the current world coordinates.
   * @param timestamp The timestamp that the color image was taken at.
   * @param depth_image The depth image data to be processed.
   * @param color_image The YCRCB_420_SP color image data to be processed
   * @param depth_image_size An array containing depth image size [width, height]
   * @param color_image_size An array containing color image size [width, height]
   * @param depth_image_intrinsics An array containing depth image intrinsics [fx, fy, cx, cy]
   * @param color_image_intrinsics An array containing color image intrinsics [fx, fy, cx, cy]
   * @return a pointer to the float array of bounding boxes information
   * The depth image should be a scaled version of the color image where the color to depth ratio is >= 1
   * The return value can be nullptr if the data is received at a higher frequency than the set fps
   * (see kProcessImagesFPS in floor_object_detection.cc)
   * It can also be nullptr if the algorithm could not find enough points to estimate a floor plane
   */
  float* ProcessDepthAndColorImages(double timestamp, float* depth_image, uint8_t* color_image, int* depth_image_size,
    int* color_image_size, double* depth_image_intrinsics, double* color_image_intrinsics);

 private:
  void ConvertYCbCrtoRGB(uint8_t* rgb, uint8_t* ycbcr, int width, int height);
  double prev_image_timestamp_ = 0;
};
}  // namespace floor object detection

#endif  // CPP_FLOOR_OBJECT_DETECTION_APPLICATION_H_