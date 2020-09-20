#ifndef ROBOT_SRC_MAIN_JNI_INCLUDE_OCTOMAP_JNI_H
#define ROBOT_SRC_MAIN_JNI_INCLUDE_OCTOMAP_JNI_H

#include <jni.h>

#include <string>
#include <octomap/Pointcloud.h>
#include <octomap/OcTree.h>

namespace octomap_jni {

#ifdef __cplusplus
    extern "C" {
#endif

const static double default_m_res = 0.05;
const static double default_m_min_dis = 1.0;
const static double default_m_max_dis = 4.0;
const static double default_m_prob_hit = 0.7;
const static double default_m_prob_miss = 0.4;
const static double default_m_clamping_min = 0.12;
const static double default_m_clamping_max = 0.97;
const static double default_m_point_cloud_confidence = 0.7;
const static double default_device_height = 0.4;
const static double default_max_height_from_device = 1.0;
const static double default_max_height_from_floor = 0.15;

double m_res = default_m_res;         // Size of the voxels
double m_min_dis = default_m_min_dis;     // Minimal valid distance for points from the pointcloud
double m_max_dis = default_m_max_dis;     // Maximal valid distance for points from the pointcloud
double m_prob_hit = default_m_prob_hit;  // Probability of hit
double m_prob_miss = default_m_prob_miss; // Probability of miss
double m_clamping_min = default_m_clamping_min; // Octomap clamping minimal
double m_clamping_max = default_m_clamping_max; // Octomap clamping maximal
double m_point_cloud_confidence = default_m_point_cloud_confidence;// Minimal point cloud confidence
double m_device_height = default_device_height;  // Device height from the floor in meters
double m_max_height_from_device = default_max_height_from_device; // Max distance for point cloud
// data above the device
double m_max_height_from_floor = default_max_height_from_floor; // Min distance for point cloud
// data below the device

octomap::OcTree *g_octree;

/*
 * Class:     ai_cellbots_octomap_OctoMap
 * Method:    initNative
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_ai_cellbots_robot_tango_TangoOctoMapCostMap_initNative
        (JNIEnv *env, jobject thisObj);

/**
 * Class:     ai_cellbots_octomap_OctoMap
 * Method:    initNativeCustomSettings
 * Signature: (F;F;F;F;F;F;F;F;F;F;F)V
 */
JNIEXPORT void JNICALL Java_ai_cellbots_robot_tango_TangoOctoMapCostMap_initNativeCustomSettings
        (JNIEnv *env, jobject thisObj, jfloat resolution, jfloat min_dis, jfloat max_dis,
         jfloat prob_hit, jfloat prob_miss, jfloat clamping_min, jfloat clamping_max,
         jfloat point_cloud_confidence, jfloat device_height, jfloat max_height_from_device,
         jfloat max_height_from_floor);
/*
 * Class:     ai_cellbots_octomap_OctoMap
 * Method:    deleteNative
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_ai_cellbots_robot_tango_TangoOctoMapCostMap_deleteNative
        (JNIEnv *env, jobject thisObj);

/*
 * Class:     ai_cellbots_octomap_OctoMap
 * Method:    clearMapNative
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_ai_cellbots_robot_tango_TangoOctoMapCostMap_clearMapNative
        (JNIEnv *env, jobject thisObj);

/*
 * Class:     ai_cellbots_octomap_OctoMap
 * Method:    addPointCloudNative
 * Signature: (Lcom/google/atap/tangoservice/TangoPointCloudData;Lcom/google/atap/tangoservice/TangoPose;)V
 */
JNIEXPORT void JNICALL Java_ai_cellbots_robot_tango_TangoOctoMapCostMap_addPointCloudNative
        (JNIEnv *env, jobject thisObj, jobject tangoPointCloud, jobject tangoPose);

/*
 * Class:     ai_cellbots_octomap_OctoMap
 * Method:    addBumperNative
 * Signature: (Lcom/google/atap/tangoservice/TangoPointCloudData;Lcom/google/atap/tangoservice/TangoPose;)V
 */

/*
 * Class:     ai_cellbots_octomap_OctoMap
 * Method:    exportMapNative
 * Signature: (Ljava/lang/String)V
 */
JNIEXPORT jint JNICALL Java_ai_cellbots_robot_tango_TangoOctoMapCostMap_exportMapNative
        (JNIEnv *env, jobject thisObj, jstring exportPath);

/*
 * Class:     ai_cellbots_octomap_OctoMap
 * Method:    castRayNative
 * Signature: (Lcom/google/atap/tangoservice/TangoPose;Lcom/google/atap/tangoservice/TangoPose;)V
 */
JNIEXPORT jboolean JNICALL Java_ai_cellbots_robot_tango_TangoOctoMapCostMap_castRayNative
        (JNIEnv *env, jobject thisObj, jobject startPose, jobject endPose);

/*
 * Class:     ai_cellbots_octomap_OctoMap
 * Method:    castRayDistanceNative
 * Signature: (Lcom/google/atap/tangoservice/TangoPose;)V
 */
JNIEXPORT jdouble JNICALL Java_ai_cellbots_robot_tango_TangoOctoMapCostMap_castRayDistanceNative
        (JNIEnv *env, jobject thisObj, jobject startPose);

/*
 * Class:     ai_cellbots_octomap_OctoMap
 * Method:    castRaysDistancesNative
 * Signature: (Lcom/google/atap/tangoservice/TangoPose;FL;Ljava/util/List;)V
 */
JNIEXPORT jboolean JNICALL Java_ai_cellbots_robot_tango_TangoOctoMapCostMap_castRaysDistancesNative
        (JNIEnv *env, jobject thisObj, jobject startPose, jfloatArray anglesArray,
         jobject distancesList);

/*
 * Class:     ai_cellbots_octomap_OctoMap
 * Method:    getOctomapBinayNative
 * Signature: ()V
 */
JNIEXPORT jbyteArray JNICALL Java_ai_cellbots_robot_tango_TangoOctoMapCostMap_getOctomapBinaryNative
        (JNIEnv *env, jobject thisObj);

/*
 * Class:     ai_cellbots_octomap_OctoMap
 * Method:    getOctomapFullNative
 * Signature: ()V
 */
JNIEXPORT jbyteArray JNICALL Java_ai_cellbots_robot_tango_TangoOctoMapCostMap_getOctomapFullNative
        (JNIEnv *env, jobject thisObj);

/*
 * Class:     ai_cellbots_octomap_OctoMap
 * Method:    get_pointcloud_from_tango
 * Signature: ([Lcom/google/atap/tangoservice/TangoPointCloudData;octomap/Pointcloud)V
 */
void get_pointcloud_from_tango(JNIEnv *env, jobject tangoPose, jobject *tangoPointCloud,
                               octomap::Pointcloud *pointcloud);

/*
 * Class:     ai_cellbots_octomap_OctoMap
 * Method:    get_bumper_pointcloud
 * Signature: ([Lcom/google/atap/tangoservice/TangoPointCloudData;octomap/Pointcloud)V
 */
void get_bumper_pointcloud(JNIEnv *env, jobject *tangoPointCloud, octomap::Pointcloud *pointcloud);

/*
 * Class:     ai_cellbots_octomap_OctoMap
 * Method:    get_pose_from_tango
 * Signature: ([Lcom/google/atap/tangoservice/TangoPointCloudData;octomap/pose6d)V
 */
void get_pose_from_tango(JNIEnv *env, jobject tangoPose, octomap::pose6d *pose);

/*
 * Class:     ai_cellbots_octomap_OctoMap
 * Method:    get_point_from_tango
 * Signature: ([Lcom/google/atap/tangoservice/TangoPointCloudData;octomap/point3d)V
 */
void get_point_from_tango(JNIEnv *env, jobject tangoPose, octomap::point3d *point);

/*
 * Class:     ai_cellbots_octomap_OctoMap
 * Method:    get_orientation_from_tango
 * Signature: ([Lcom/google/atap/tangoservice/TangoPointCloudData;octomap/point3d)V
 */
void get_orientation_from_tango(JNIEnv *env, jobject tangoPose, octomap::point3d *point);

/*
 * Class:     ai_cellbots_octomap_OctoMap
 * Method:    set_point_to_tango
 * Signature: ([Lcom/google/atap/tangoservice/TangoPointCloudData;octomap/point3d)V
 */
void set_point_to_tango(JNIEnv *env, jobject *tangoPose, octomap::point3d *point);

/*
 * Class:     ai_cellbots_octomap_OctoMap
 * Method:    castRaysDistance
 * Signature: (octomap/pose6d;)V
 */
double castRayDistance(octomap::pose6d start_pose);

/*
 * Class:     ai_cellbots_octomap_OctoMap
 * Method:    castRaysDistancesNative
 * Signature: (octomap/pose6d;std/vector;[std/vector;)V
 */
bool castRaysDistances(octomap::pose6d start_pose, std::vector<float> angles_vector,
                       std::vector<float> *distances_vector_output);

octomap::OcTree *get_octree(void);

double get_resolution(void);

double get_max_height_from_device(void);

double get_device_height(void);

#ifdef __cplusplus
}
#endif

/*
 * Class:     ai_cellbots_octomap_OctoMap
 * Method:    get_point_from_java
 * Signature: (Ljava/lang/String)V
 */
std::string get_string(JNIEnv *env, jstring jString);

}  // namespace octomap_jni

#endif  // ROBOT_SRC_MAIN_JNI_INCLUDE_OCTOMAP_JNI_H
