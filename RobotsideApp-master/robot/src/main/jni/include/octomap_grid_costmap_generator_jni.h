#ifndef ROBOT_SRC_MAIN_JNI_INCLUDE_OCTOMAP_GRID_COSTMAP_GENERATOR_JNI_H
#define ROBOT_SRC_MAIN_JNI_INCLUDE_OCTOMAP_GRID_COSTMAP_GENERATOR_JNI_H

#include <jni.h>
#include <string>
#include "octomap_jni.h"
#include <octomap/octomap.h>
#include <octomap/OcTreeKey.h>
#include <octomap/OcTree.h>

/* Header for class ai_cellbots_octomap_DistanceGrid */

namespace octomap_jni {

#ifdef __cplusplus
extern "C" {
#endif

    octomap::OcTreeKey m_update_BBX_min;
    octomap::OcTreeKey m_update_BBX_max;
    octomap::OcTreeKey m_padded_min_key;
    unsigned m_multires2DScale;

    float m_x_size;               // With in meters of the grid around the robot
    float m_y_size;               // Height in meters of the grid around the robot
    float m_center_x;             // Center of the grid in x in world coordinates
    float m_center_y;             // Center of the grid in y in world coordinates
    float m_cell_size;            // Size of the grid
    int m_x_cells;                // Amount of cells in X
    int m_y_cells;                // Amount of cells in Y
    long m_capacity;
    unsigned m_treeDepth;
    unsigned m_max_tree_depth;
    int8_t *g_costmap_buffer;
    octomap::point3d m_min_point;
    octomap::point3d m_max_point;
    octomap::OcTree *g_octomap_octree;

    const static uint8_t OCCUPIED_CELL_VALUE = 127;
    const static uint8_t FREE_CELL_VALUE = 0;

/// Test if key is within update area of map (2D, ignores height)
    inline bool isInUpdateBBX(const octomap::OcTree::iterator &it) {
        // 2^(tree_depth-depth) voxels wide:
        unsigned voxel_width = (1 << (m_max_tree_depth - it.getDepth()));
        octomap::OcTreeKey key = it.getIndexKey(); // lower corner of voxel
        return (key[0] + voxel_width >= m_update_BBX_min[0]
                && key[1] + voxel_width >= m_update_BBX_min[1]
                && key[0] <= m_update_BBX_max[0]
                && key[1] <= m_update_BBX_max[1]);
    }


    inline unsigned mapIndexes(int i, int j) {
        return m_x_cells * j + i;
    }

    inline unsigned mapIdx(const octomap::OcTreeKey &key) {
        m_multires2DScale = 1 << (m_treeDepth - m_max_tree_depth);
        return mapIndexes((key[0] - m_padded_min_key[0]) / m_multires2DScale,
                          (key[1] - m_padded_min_key[1]) / m_multires2DScale);

    }

    inline static void updateMinKey(const octomap::OcTreeKey &in, octomap::OcTreeKey &min) {
        for (unsigned i = 0; i < 3; ++i)
            min[i] = std::min(in[i], min[i]);
    };

    inline static void updateMaxKey(const octomap::OcTreeKey &in, octomap::OcTreeKey &max) {
        for (unsigned i = 0; i < 3; ++i)
            max[i] = std::max(in[i], max[i]);
    };

    bool isSpeckleNode(const octomap::OcTreeKey &tree_key);

    bool updateBBX(octomap::point3d center_pose);

    void update2DMap(const octomap::OcTree::iterator &it, bool occupied);


/*
 * Class:     ai_cellbots_robot_tango_TangoOctoMapCostMap
 * Method:    initCostmapNative
 * Signature: (D;D;D)V
 */
    JNIEXPORT jboolean JNICALL Java_ai_cellbots_robot_tango_OctomapGridCostmapGenerator_initCostmapNative
            (JNIEnv *env, jobject thisObj, jdouble cellSize, jdouble xSize, jdouble ySize);

/*
 * Class:     ai_cellbots_robot_tango_TangoOctoMapCostMap
 * Method:    updateCostmapNative
 * Signature: (Ljava/nio/Buffer;Lcom/google/atap/tangoservice/TangoPose)V
 */
    JNIEXPORT jboolean JNICALL Java_ai_cellbots_robot_tango_OctomapGridCostmapGenerator_updateCostmapNative
            (JNIEnv *env, jobject thisObj, jobject buffer, jobject startPose);

#ifdef __cplusplus
}
#endif

}  // namespace octomap_jni

#endif  // ROBOT_SRC_MAIN_JNI_INCLUDE_OCTOMAP_GRID_COSTMAP_GENERATOR_JNI_H
