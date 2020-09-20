#include "floor_object_detection/floor_object_detection.h"

#include <jni.h>
#include <memory>

static floor_object_detection::FloorObjectDetector detector;

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jfloatArray JNICALL
Java_ai_cellbots_robotlib_cv_FloorObjectDetectorJNINative_processDepthAndColorImages(
    JNIEnv* env, jobject, jdouble imageTimestamp, jfloatArray depthData, jbyteArray imageData,
    jint width, jint height, jint colorToDepthRatio) {
  jfloat*  depthBuffer = env->GetFloatArrayElements(depthData,0);
  int imageArraylen = env->GetArrayLength(imageData);
  std::unique_ptr<uint8_t[]> imageBuffer = std::unique_ptr<uint8_t[]>(new uint8_t[imageArraylen]);
  env->GetByteArrayRegion (imageData, 0, imageArraylen, reinterpret_cast<jbyte*>(imageBuffer.get()));
  float* bound_rects = detector.ProcessDepthAndColorImages(imageTimestamp, depthBuffer, imageBuffer.get(), width, height, colorToDepthRatio);
  if (bound_rects == nullptr) {
    return nullptr;
  } else {
    int size = 5 * bound_rects[0] + 1;
    jfloatArray result;
    result = env->NewFloatArray(size);
    env->SetFloatArrayRegion(result, 0, size, bound_rects);
    delete[] bound_rects;
    return result;
  }
}

#ifdef __cplusplus
}
#endif
