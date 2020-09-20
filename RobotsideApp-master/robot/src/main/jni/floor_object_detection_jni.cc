#include "vision/floor_object_detection/floor_object_detection.h"

#include <jni.h>
#include <memory>

static floor_object_detection::FloorObjectDetector detector;

#ifdef __cplusplus
extern "C" {
#endif

namespace vision {

JNIEXPORT jfloatArray JNICALL
Java_ai_cellbots_robot_vision_FloorObjectDetectorJNINative_processDepthAndColorImages(
        JNIEnv *env, jobject, jdouble image_timestamp, jfloatArray depth_image,
        jbyteArray color_image,
        jintArray depth_image_size, jintArray color_image_size,
        jdoubleArray depth_image_intrinsics,
        jdoubleArray color_image_intrinsics) {
    jfloat *depth_buffer = env->GetFloatArrayElements(depth_image, 0);
    int color_image_len = env->GetArrayLength(color_image);
    std::unique_ptr<uint8_t[]> color_buffer(new uint8_t[color_image_len]);
    env->GetByteArrayRegion(color_image, 0, color_image_len,
                            reinterpret_cast<jbyte *>(color_buffer.get()));
    jint *depth_buffer_size = env->GetIntArrayElements(depth_image_size, 0);
    jint *color_buffer_size = env->GetIntArrayElements(color_image_size, 0);
    jdouble *depth_buffer_intrinsics = env->GetDoubleArrayElements(depth_image_intrinsics, 0);
    jdouble *color_buffer_intrinsics = env->GetDoubleArrayElements(color_image_intrinsics, 0);

    float *bound_rects = detector.ProcessDepthAndColorImages(image_timestamp, depth_buffer,
                                                             color_buffer.get(),
                                                             depth_buffer_size, color_buffer_size,
                                                             depth_buffer_intrinsics,
                                                             color_buffer_intrinsics);
    if (bound_rects == nullptr) {
        return nullptr;
    } else {
        int size = (int) (6 * bound_rects[0] + 1);
        jfloatArray result;
        result = env->NewFloatArray(size);
        env->SetFloatArrayRegion(result, 0, size, bound_rects);
        delete[] bound_rects;
        return result;
    }
}

}  // namespace vision

#ifdef __cplusplus
}
#endif
