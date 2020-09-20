#include "floor_object_detection.h"

#include <iostream>
#include <cstdlib>
#include <memory>
#include <vector>

#include <android/log.h>
#include <assert.h>
#include <jni.h>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/features2d/features2d.hpp>
#include <opencv2/highgui/highgui.hpp>

namespace {
// The maximum number of frames to process per second.
// With Tango, the point cloud is available at 5 fps, setting
// kProcessImagesFPS to higher insures that the vision algorithm
// will always be triggered when a new point cloud is available.
const int kProcessImagesFPS = 10;
// This is a lower limit on the minimum depth, in meters, used to
// calculate floor plane. This value can be calculated from getDeviceZ
// in RobotModel and the camera orientation, once those are fixed.
// This value is just a lower limit and doesn't have to be exact.
// TODO(playerfour): get this from the RobotModel once the final camera/robot
// TODO(playerfour): position is fixed.
const double kFloorMinDepth = 0.65;
const char* kColorDirectory = "/sdcard/datasets/tango/color/";
const char* kDepthDirectory = "/sdcard/datasets/tango/depth/";
} // namespace

//TODO(playerfour) Add comments throughout the code.
namespace floor_object_detection {

void FloorObjectDetector::ConvertYCbCrtoRGB(uint8_t* rgb, uint8_t* ycbcr, int width, int height) {
    assert(rgb != nullptr);
    assert(ycbcr != nullptr);
    assert(width > 0);
    assert(height > 0);
    int num_pixels = width * height;
    for (int i = 0; i < height; ++i) {
        for (int j = 0; j < width; ++j) {
            float y = 1.164f * ((float) (ycbcr[i * width + j] & 0xff)) - 16.0f;
            float u = (float) (ycbcr[num_pixels + 2 * (j / 2) + (i / 2) * width] & 0xff) - 128.0f;
            float v = (float) (ycbcr[num_pixels + 2 * (j / 2) + 1 + (i / 2) * width] & 0xff) - 128.0f;

            int b = (int) (y + 1.596f * v);
            int g = (int) (y - 0.392f * u - 0.813f * v);
            int r = (int) (y + 2.017f * u);

            if (r < 0) {
                r = 0;
            } else if (r > 255) {
                r = 255;
            }

            if (g < 0) {
                g = 0;
            } else if (g > 255) {
                g = 255;
            }

            if (b < 0) {
                b = 0;
            } else if (b > 255) {
                b = 255;
            }

            rgb[3 * (i * width + j)] = (uint8_t) (r & 0xff);
            rgb[3 * (i * width + j) + 1] = (uint8_t) (g & 0xff);
            rgb[3 * (i * width + j) + 2] = (uint8_t) (b & 0xff);
        }
    }
    return;
}

float* FloorObjectDetector::ProcessDepthAndColorImages(double timestamp, float* depth_image, uint8_t* color_image, int* depth_image_size,
        int* color_image_size, double* depth_image_intrinsics, double* color_image_intrinsics) {
    assert(timestamp > 0);
    assert(depth_image != nullptr);
    assert(color_image != nullptr);
    assert(depth_image_size != nullptr);
    assert(color_image_size != nullptr);
    assert(depth_image_intrinsics != nullptr);
    assert(color_image_intrinsics != nullptr);
    assert(depth_image_size[0] > 0);
    assert(depth_image_size[1] > 0);
    assert(color_image_size[0] > 0);
    assert(color_image_size[1] > 0);
    assert(depth_image_intrinsics[0] > 0);
    assert(depth_image_intrinsics[1] > 0);
    assert(depth_image_intrinsics[2] > 0);
    assert(depth_image_intrinsics[3] > 0);
    assert(color_image_intrinsics[0] > 0);
    assert(color_image_intrinsics[1] > 0);
    assert(color_image_intrinsics[2] > 0);
    assert(color_image_intrinsics[3] > 0);

    char timestamp_str[128];
    sprintf(timestamp_str, "%f", timestamp * 1e9);
    if (timestamp < prev_image_timestamp_ + 1.0 / kProcessImagesFPS) {
        return nullptr;
    }
    prev_image_timestamp_ = timestamp;
    LOGV("FloorObjectDetectionApplication: Timestamp: %f ", timestamp);

    bool save_images = false;
    int color_width = color_image_size[0];
    int color_height = color_image_size[1];
    int half_color_height = color_height / 2;
    cv::Mat color_mat_ycbcr_420sp = cv::Mat(color_height + half_color_height, color_width, CV_8UC1, color_image);
    cv::Mat color_mat = cv::Mat(color_height, color_width, CV_8UC3);
    cvtColor(color_mat_ycbcr_420sp, color_mat, CV_YUV420sp2RGB);

    int depth_width = depth_image_size[0];
    int depth_height = depth_image_size[1];
    cv::Mat depth_mat = cv::Mat(depth_height, depth_width, CV_32FC1, depth_image);
    // Set depth image edges to zero.
    int edge_height = (int) (0.075 * depth_height);
    int edge_width = (int) (0.1 * depth_width);
    cv::Rect edge_roi = cv::Rect(0, 0, depth_width, edge_height);
    depth_mat(edge_roi).setTo(0.f);
    edge_roi = cv::Rect(0, depth_height - edge_height, depth_width, edge_height);
    depth_mat(edge_roi).setTo(0.f);
    edge_roi = cv::Rect(0, 0, edge_width, depth_height);
    depth_mat(edge_roi).setTo(0.f);
    edge_roi = cv::Rect(depth_width - edge_width, 0, edge_width, depth_height);
    depth_mat(edge_roi).setTo(0.f);
    const double max_threshold = 1.5;
    cv::Mat depth_filtered;
    cv::threshold(depth_mat, depth_filtered, max_threshold, 0.0, cv::THRESH_TOZERO_INV);

    // TODO(playerfour) many of the parameters are hardcoded, these should be const variables.
    // TODO(playerfour) for loop below should be written in cleaner way.
    // TODO(playerfour) this is set to work with our portrait setup, add a flag to switch between portrait and landscape.
    std::vector<float> points_depth;
    points_depth.reserve(300);
    std::vector<cv::Point3f> points_location;
    points_location.reserve(300);
    int col_lower_range = depth_width - (int) (0.2 * depth_width);
    int col_upper_range = depth_width - (int) (0.12 * depth_width);
    float depth_fx = (float) depth_image_intrinsics[0];
    float depth_fy = (float) depth_image_intrinsics[1];
    float depth_cx = (float) depth_image_intrinsics[2];
    float depth_cy = (float) depth_image_intrinsics[3];
    for(int col = col_lower_range; col < col_upper_range; ++col) {
        cv::Mat labeled = cv::Mat::zeros(depth_height, 1, CV_32SC1);
        std::vector<int> labels_count;
        labels_count.reserve(20);
        bool component_active = false;
        int component_count = 0;
        int latest_label = 0;
        for (int row = 0; row < depth_height; ++row) {
            float current_depth = depth_filtered.at<float>(row, col);
            float component_depth;
            if (current_depth > kFloorMinDepth) {
                if (component_active) {
                    if (fabs(current_depth - component_depth) < 0.03) {
                        labeled.at<int>(row, 0) = latest_label;
                        component_depth = current_depth;
                        component_count += 1;
                    } else {
                        latest_label += 1;
                        labeled.at<int>(row, 0) = latest_label;
                        component_depth = current_depth;
                        labels_count.push_back(component_count);
                        component_count = 1;
                    }
                } else {
                    latest_label += 1;
                    labeled.at<int>(row, 0) = latest_label;
                    component_depth = current_depth;
                    labels_count.push_back(component_count);
                    component_count = 1;
                    component_active = true;
                }

            } else {
                component_active = false;
                labeled.at<int>(row, 0) = 0;
            }
        }
        labels_count.push_back(component_count);
        auto max_labels_count_iter = std::max_element(std::begin(labels_count), std::end(labels_count));
        int max_labels_count_idx = std::distance(std::begin(labels_count), max_labels_count_iter);
        int max_label_count = *max_labels_count_iter;
        if (max_label_count < 20) {
            continue;
        }
        std::vector<int> max_label_indxs;
        max_label_indxs.reserve(depth_height);
        // TODO(playerfour) No need to loop over the whole height.
        for (int i = 0; i < depth_height; ++i) {
            if (labeled.at<int>(i, 0) == max_labels_count_idx) {
                max_label_indxs.push_back(i);
            }
        }
        for(int j = max_label_count / 10; j < 8 * max_label_count / 10; j += max_label_count / 20) {
            float current_point_depth = depth_filtered.at<float>(max_label_indxs[j], col);
            points_depth.push_back(current_point_depth);
            cv::Point3f current_point;
            current_point.x = (col - depth_cx) * current_point_depth / depth_fx;
            current_point.y = (max_label_indxs[j] - depth_cy) * current_point_depth / depth_fy;
            current_point.z = 1;
            points_location.push_back(current_point);
        }
        labels_count.clear();
        max_label_indxs.clear();
    }
    if (points_depth.size() < 50 || points_location.size() < 50) {
        return nullptr;
    }


    cv::Mat left_hand_side = cv::Mat::zeros((int) points_location.size(), 3, CV_32FC1);
    cv::Mat right_hand_side = cv::Mat::zeros((int) points_depth.size(), 1, CV_32FC1);
    for (int k = 0; k < points_depth.size(); ++k) {
        left_hand_side.at<float>(k, 0) = points_location[k].x;
        left_hand_side.at<float>(k, 1) = points_location[k].y;
        left_hand_side.at<float>(k, 2) = points_location[k].z;
        right_hand_side.at<float>(k, 0) = points_depth[k];
    }
    points_location.clear();
    points_depth.clear();
    cv::Mat left_hand_side_transpose = left_hand_side.t();
    cv::Mat plane = (((left_hand_side_transpose * left_hand_side).inv()) * left_hand_side_transpose) * right_hand_side;
    std::unique_ptr<float[]> cols_x(new float[depth_width]);
    std::unique_ptr<float[]> rows_y(new float[depth_height]);
    for (int i = 0; i < depth_width; ++i) {
        cols_x[i] =  (i - depth_cx) / depth_fx;
    }
    for (int j = 0; j < depth_height; ++j) {
        rows_y[j] = (j - depth_cy) / depth_fy;
    }
    cv::Mat cols_x_mat = cv::Mat(1, depth_width, CV_32FC1, cols_x.get());
    cv::Mat rows_y_mat = cv::Mat(depth_height, 1, CV_32FC1, rows_y.get());
    cv::Mat Xs, Ys;
    cv::repeat(cols_x_mat, depth_height, 1, Xs);
    cv::repeat(rows_y_mat, 1, depth_width, Ys);
    cv::Mat Xs_Zs = Xs.mul(depth_filtered);
    cv::Mat Ys_Zs = Ys.mul(depth_filtered);
    float a_plane = plane.at<float>(0, 0);
    float b_plane = plane.at<float>(1, 0);
    float c_plane = plane.at<float>(2, 0);
    float vec_plane_norm = (float) (sqrt(pow(a_plane, 2) + pow(b_plane, 2) + 1));
    cv::Mat diff = (a_plane * Xs_Zs + b_plane * Ys_Zs + c_plane - depth_filtered)/vec_plane_norm;
    cv::Mat depth_mask;
    cv::threshold(depth_filtered, depth_mask, 0, 255, cv::THRESH_BINARY);
    depth_mask.convertTo(depth_mask, CV_8UC1);
    cv::Mat diff_filtered;
    diff.copyTo(diff_filtered, depth_mask);

    if (save_images) {
        char file_name[256];
        strcpy(file_name, kDepthDirectory);
        strcat(file_name, timestamp_str);
        strcat(file_name, "-diff-filtered.png");
        cv::imwrite(file_name, diff_filtered * 3000);
    }
    cv::Mat components_image;
    cv::threshold(diff_filtered, components_image, 0.035, 255, cv::THRESH_BINARY);
    components_image.convertTo(components_image, CV_8UC1);
    std::vector<std::vector<cv::Point>> contours;
    std::vector<cv::Vec4i> hierarchy;
    findContours(components_image, contours, hierarchy, CV_RETR_EXTERNAL, CV_CHAIN_APPROX_SIMPLE, cv::Point(0, 0));
    std::vector<cv::Rect> bound_rects;
    bound_rects.reserve(contours.size());
    cv::Mat color_mat_downsampled;
    cv::Size color_downsampled_size(depth_width, depth_height);
    cv::resize(color_mat, color_mat_downsampled, color_downsampled_size);
    if (contours.size() > 0) {
        cv::Scalar color = cv::Scalar(0, 0, 255);
        // TODO(playerfour) approximating contours might speed up the computation.
        for (int i = 1; i < contours.size(); ++i) {
            double contourArea = cv::contourArea(contours[i]);
            if (contourArea >  150) {
                bound_rects.push_back(cv::boundingRect(cv::Mat(contours[i])));
                if (save_images) {
                    cv::drawContours(color_mat_downsampled, contours, i, color, 1);
                }
            }
        }
    }

    std::unique_ptr<float[]> bound_rect_arr(new float[6 * bound_rects.size() + 1]);
    bound_rect_arr[0] = bound_rects.size();
    for (int j = 0; j < bound_rects.size(); ++j) {
        cv::Rect current_rect = bound_rects[j];
        cv::Mat rect_roi = depth_filtered(current_rect);
        cv::Mat rect_roi_nonzero = rect_roi > 0;
        cv::Point current_rect_tr(current_rect.br().x, current_rect.tl().y);
        cv::Point current_rect_br(current_rect.br().x, current_rect.br().y);
        // Use the calculated plane equation to get the world position of the first corner.
        float denominator_tr = 1 - (current_rect_tr.x - depth_cx) * a_plane / depth_fx -
                               (current_rect_tr.y - depth_cy) * b_plane / depth_fy;
        if (denominator_tr == 0) {
            return nullptr;
        }
        float depth_tr = c_plane / denominator_tr;
        bound_rect_arr[6 * j + 1] = (current_rect_tr.x - depth_cx) * depth_tr / depth_fx;
        bound_rect_arr[6 * j + 2] = (current_rect_tr.y - depth_cy) * depth_tr / depth_fy;
        bound_rect_arr[6 * j + 3] = depth_tr;
        // Use the calculated plane equation to get the world position of the second corner.
        float denominator_br = 1 - (current_rect_br.x - depth_cx) * a_plane / depth_fx -
                               (current_rect_br.y - depth_cy) * b_plane / depth_fy;
        if (denominator_br == 0) {
            return nullptr;
        }
        float depth_br = c_plane / denominator_br;
        bound_rect_arr[6 * j + 4] = (current_rect_br.x - depth_cx) * depth_br/ depth_fx;
        bound_rect_arr[6 * j + 5] = (current_rect_br.y - depth_cy) * depth_br/ depth_fy;
        bound_rect_arr[6 * j + 6] = depth_br;
        if (save_images) {
            cv::rectangle(color_mat_downsampled, current_rect.tl(), current_rect.br(),
                          cv::Scalar(0, 255, 255), 2, 1, 0);
            cv::circle(color_mat_downsampled, current_rect_br, 3,
                       cv::Scalar(0, 255, 0), -1, 8);
            cv::circle(color_mat_downsampled, current_rect_tr, 3,
                       cv::Scalar(255, 0, 0), -1, 8);
        }
    }
    if (save_images) {
        char file_name[256];
        strcpy(file_name, kColorDirectory);
        strcat(file_name, timestamp_str);
        strcat(file_name, "-contours.png");
        cv::imwrite(file_name, color_mat_downsampled);
    }
    return bound_rect_arr.release();
}
}  // namespace floor_object_detection
