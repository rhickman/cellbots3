#ifdef __cplusplus
extern "C" {
#endif

#include <jni.h>
#include "costmap_inflation.h"

static CostMapInflation inf;

JNIEXPORT jbyteArray JNICALL
Java_ai_cellbots_robot_costmap_InflatorJNINative_inflate(
        JNIEnv *env, jclass type, jbyteArray obj, jdouble radius, jdouble resolution,
        jintArray limits) {

    // Step 1: Convert the incoming JNI jbyteArray to C's jbyte*
    // Get a pointer to the array
    jbyte *grid = env->GetByteArrayElements(obj, NULL);
    jint *_limits = env->GetIntArrayElements(limits, NULL);
    // do some exception null checking
    if (grid == NULL || limits == NULL) return NULL;
    jsize length = env->GetArrayLength(obj);
    // The limits have to be 4
    if (env->GetArrayLength(limits) != 4) return NULL;

    // Step 2: Perform its intended operations
    jbyte *result = inf.inflate(grid, length, radius, resolution, _limits);

    // release the memory so java can take it again
    env->ReleaseByteArrayElements(obj, grid, 0);
    env->ReleaseIntArrayElements(limits, _limits, 0);

    // Step 3: Convert the C's Native jbyte* to JNI jbyteArray, and return
    jbyteArray jArray = env->NewByteArray(length); // allocate
    if (jArray == NULL) return NULL;
    env->SetByteArrayRegion(jArray, 0, length, result);

    return jArray;
}

#ifdef __cplusplus
}
#endif
