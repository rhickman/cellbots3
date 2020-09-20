#ifndef ROBOTSIDEAPP_COSTMAP_INFLATION_H
#define ROBOTSIDEAPP_COSTMAP_INFLATION_H

#include <android/log.h>

#include <cstdio>
#include <assert.h>
#include <math.h>
#include <string.h>

// TODO: Add this pre-processor macros into a common cellbots_jni.h
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, "costmap_inflation", __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "costmap_inflation", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "costmap_inflation", __VA_ARGS__)

class CostMapInflation {
public:
    jbyte *inflate(int8_t *, jsize, double, double, int *);

private:
    const int8_t LETHAL_OBSTACLE = 127;
    const int8_t INSCRIBED_INFLATED_OBSTACLE = 120;
    const int8_t MIN_COST = 0;
    const int8_t MAX_COST = 127;
    int8_t *costmap_copy;
    int costmap_x_lower_limit;
    int costmap_x_upper_limit;
    int costmap_y_lower_limit;
    int costmap_y_upper_limit;

    int8_t get_cost(int, int, int8_t *);

    int8_t get_highest_cost_in_region(int, int, int, int, int8_t *);

    int8_t compute_inflated_costmap(int8_t);
};

/**
 * Inflates a CostMap by mutating the data.
 *
 * @param grid The cost map grid.
 * @param length The length of the 1D cost map grid.
 * @param radius The radius of the robot.
 * @param resolution The grid resolution in meters/cell.
 * @param limits The limits of the grid {x_min, x_max, y_min, y_max}
 * @return The inflated cost map grid.
 */
jbyte *CostMapInflation::inflate(int8_t *grid, jsize length, double radius, double resolution,
                                 int *limits) {
    int inscribed_radius = static_cast<int>(ceil(radius / resolution));
    LOGV("Inscribed radius: %d", inscribed_radius);
    // Cost map limits
    costmap_x_lower_limit = limits[0];
    costmap_x_upper_limit = limits[1];
    costmap_y_lower_limit = limits[2];
    costmap_y_upper_limit = limits[3];
    // Cost map width
    int costmap_width = costmap_x_upper_limit - costmap_x_lower_limit;

    if (inscribed_radius <= 0) {
        LOGI("Inscribed radius is less than or equal to zero, so we ignore");
        return grid;
    }

    // Copy entire cost map data - this will be the cost map data to be returned
    costmap_copy = new int8_t[length];
    for (int i = 0; i < length; i++) {
        costmap_copy[i] = grid[i];
    }

    // Go through all cost map cells
    for (int x = costmap_x_lower_limit; x < costmap_x_upper_limit; x++) {
        for (int y = costmap_y_lower_limit; y < costmap_y_upper_limit; y++) {
            // If it's an obstacle, don't modify it
            if (get_cost(x, y, grid) == MAX_COST) continue;
            // Take the neighbor limits of each cell
            int lower_x_limit = x - inscribed_radius;
            int lower_y_limit = y - inscribed_radius;
            int upper_x_limit = x + inscribed_radius;
            int upper_y_limit = y + inscribed_radius;
            // Find the neighbor with higher cost
            int8_t higher_cost = get_highest_cost_in_region(
                    // If any neighbor is outside the cost map, it will be omitted
                    lower_x_limit < costmap_x_lower_limit ? costmap_x_lower_limit : lower_x_limit,
                    lower_y_limit < costmap_y_lower_limit ? costmap_y_lower_limit : lower_y_limit,
                    upper_x_limit > costmap_x_upper_limit ? costmap_x_upper_limit : upper_x_limit,
                    upper_y_limit > costmap_y_upper_limit ? costmap_y_upper_limit : upper_y_limit,
                    grid
            );
            // Update the cost based on the neighbor with highest cost
            costmap_copy[(y - costmap_y_lower_limit) * costmap_width + x -
                         costmap_x_lower_limit] = compute_inflated_costmap(higher_cost);
        }
    }
    LOGI("Inflated cost map: ");
    for (int i = 0; i < length; i++) {
        LOGI("%d", costmap_copy[i]);
    }
    return costmap_copy;
}

int8_t CostMapInflation::get_cost(int x, int y, int8_t *grid) {
    return grid[(y - costmap_y_lower_limit) * (costmap_x_upper_limit - costmap_x_lower_limit) + x -
                costmap_x_lower_limit];
}

/**
 * Gets the highest CostMap cost in a region.
 *
 * @param xStart The start x coordinate in the CostMap coordinates.
 * @param yStart The start y coordinate in the CostMap coordinates.
 * @param xEnd   The end x coordinate in the CostMap coordinates.
 * @param yEnd   The end y coordinate in the CostMap coordinates.
 * @return The highest CostMap cost in a region.
 */
int8_t CostMapInflation::get_highest_cost_in_region(int x_start, int y_start, int x_end, int y_end,
                                                    int8_t *grid) {
    assert(x_start <= x_end);
    assert(y_start <= y_end);
    if (x_start == x_end) {
        return MIN_COST;
    } else if (y_start == y_end) {
        return MIN_COST;
    }
    int8_t cost = MIN_COST;
    for (int x = x_start; x < x_end; x++) {
        for (int y = y_start; y < y_end; y++) {
            cost = static_cast<int8_t >(fmax(cost, get_cost(x, y, grid)));
            if (cost == MAX_COST) {
                return cost;
            }
        }
    }
    return cost;
}

/**
 * Computes the new cost of the cell given the maximum cost of its neighbors.
 *
 * @param cost Maximum cost of its neighbors.
 * @return Updated cost value of the cell.
 */
int8_t CostMapInflation::compute_inflated_costmap(int8_t cost) {
    if (cost == MAX_COST) {
        // It's an obstacle
        return LETHAL_OBSTACLE;
    } else if (cost >= INSCRIBED_INFLATED_OBSTACLE) {
        return INSCRIBED_INFLATED_OBSTACLE;
    } else {
        // Proportional relationship with the cost
        return cost;
    }
}

#endif //ROBOTSIDEAPP_COSTMAP_INFLATION_H
