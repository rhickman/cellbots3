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
// The maximum number of frames to process per second
const int kProcessImagesFPS = 1;
// TODO: fx and fy should come from Tango through JNI
const float fx = 1521.046710;
const float fy = 1518.999642;
const char* kColorDirectory = "/sdcard/datasets/tango/color/";
const char* kDepthDirectory = "/sdcard/datasets/tango/depth/";
}  // namespace

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

float* FloorObjectDetector::ProcessDepthAndColorImages(double timestamp, float* depth_data, uint8_t* image_data,
        int image_width, int image_height, int color_to_depth_ratio) {
    assert(timestamp > 0);
    assert(depth_data != nullptr);
    assert(image_data != nullptr);
    assert(image_width > 0);
    assert(image_height > 0);
    assert(color_to_depth_ratio > 0);
    char timestamp_str[128];
    sprintf(timestamp_str, "%f", timestamp * 1e9);
    if (timestamp < prev_image_timestamp_ + 1.0 / kProcessImagesFPS) {
        return nullptr;
    }
    prev_image_timestamp_ = timestamp;
    LOGI("FloorObjectDetectionApplication: Timestamp: %f ", timestamp);

    //TODO: adjust the code below to work when the depth is at a lower resolution than color
    if (color_to_depth_ratio != 1) {
        return nullptr;
    }

    float half_height = image_height / 2;
    cv::Mat color_mat_ycbcr_420sp = cv::Mat(image_height + half_height, image_width, CV_8UC1, image_data);
    cv::Mat color_mat = cv::Mat(image_height, image_width, CV_8UC3);
    cvtColor(color_mat_ycbcr_420sp, color_mat, CV_YUV420sp2RGB);

    cv::Mat depth_mat = cv::Mat(image_height, image_width, CV_32FC1, depth_data);
    const double max_threshold = 1.5;
    cv::Mat depth_filtered;
    cv::threshold(depth_mat, depth_filtered, max_threshold, 0.0, cv::THRESH_TOZERO_INV);

    //TODO: many of the parameters are hardcoded, these should be const variables
    //TODO: for loop below should be written in cleaner way
    //TODO: number of points used for plane fitting is currently around 650, could be reduced for speed
    std::vector<float> points_depth;
    points_depth.reserve(650);
    std::vector<cv::Point3f> points_location;
    points_location.reserve(650);
    for(int r = image_height - 160; r < image_height - 80; ++r) {
        const float* depth_row = depth_filtered.ptr<float>(r);
        cv::Mat labeled = cv::Mat::zeros(1, image_width, CV_32FC1);
        std::vector<int> labels_count;
        labels_count.reserve(20);
        bool component_active = false;
        int component_count = 0;
        int latest_label = 0;
        for (int c = 0; c < image_width; ++c) {
            float current_depth = depth_row[c];
            float component_depth;
            if (current_depth > 0) {
                if (component_active) {
                    if (fabs(current_depth - component_depth) < 0.03) {
                        labeled.at<float>(c) = latest_label;
                        component_depth = current_depth;
                        component_count += 1;
                    } else {
                        latest_label += 1;
                        labeled.at<float>(c) = latest_label;
                        component_depth = current_depth;
                        labels_count.push_back(component_count);
                        component_count = 1;
                    }
                } else {
                    latest_label += 1;
                    labeled.at<float>(c) = latest_label;
                    component_depth = current_depth;
                    labels_count.push_back(component_count);
                    component_count = 1;
                    component_active = true;
                }

            } else {
                component_active = false;
                labeled.at<float>(c) = 0;
            }
        }
        labels_count.push_back(component_count);
        auto max_labels_count_iter = std::max_element(std::begin(labels_count), std::end(labels_count));
        int max_labels_count_idx  = std::distance(std::begin(labels_count), max_labels_count_iter);
        int max_label_count = *max_labels_count_iter;
        if (max_label_count < 100) {
            continue;
        }
        std::vector<int> max_label_indxs;
        max_label_indxs.reserve(1500);
        // TODO: No need to loop over the whole width
        for (int i = 0; i < image_width; ++i) {
            if (labeled.at<float>(i) == max_labels_count_idx) {
                max_label_indxs.push_back(i);
            }
        }
        for(int j = max_label_count / 10; j < 8 * max_label_count / 10; j += max_label_count / 10) {
            points_depth.push_back(depth_row[max_label_indxs[j]]);
            cv::Point3f current_point;
            current_point.x = (max_label_indxs[j] - image_width / 2.0f) / fx;
            current_point.y = (r - image_height / 2.0f) / fy;
            current_point.z = 1;
            points_location.push_back(current_point);
        }
        labels_count.clear();
        max_label_indxs.clear();
    }
    if (points_depth.size() < 100 || points_location.size() < 100) {
        return nullptr;
    }

    cv::Mat left_hand_side = cv::Mat::zeros(points_location.size(), 3, CV_32FC1);
    cv::Mat right_hand_side = cv::Mat::zeros(points_depth.size(), 1, CV_32FC1);
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
    std::unique_ptr<float[]> cols_x = std::unique_ptr<float[]>(new float[image_width]);
    std::unique_ptr<float[]> rows_y = std::unique_ptr<float[]>(new float[image_height]);
    float half_width = image_width / 2;
    for (int i = 0; i < image_width; ++i) {
        cols_x[i] =  (i - half_width) / fx;
    }
    for (int j = 0; j < image_height; ++j) {
        rows_y[j] = (j - half_height) / fy;
    }
    cv::Mat cols_x_mat = cv::Mat(1, image_width, CV_32FC1, cols_x.get());
    cv::Mat rows_y_mat = cv::Mat(image_height, 1, CV_32FC1, rows_y.get());
    cv::Mat Xs, Ys;
    cv::repeat(cols_x_mat, image_height, 1, Xs);
    cv::repeat(rows_y_mat, 1, image_width, Ys);
    cv::Mat Xs_Zs = Xs.mul(depth_filtered);
    cv::Mat Ys_Zs = Ys.mul(depth_filtered);
    float a_plane = plane.at<float>(0, 0);
    float b_plane = plane.at<float>(1, 0);
    float c_plane = plane.at<float>(2, 0);
    float vec_plane_norm = sqrt(pow(a_plane, 2) + pow(b_plane, 2) + 1);
    cv::Mat diff = (a_plane * Xs_Zs + b_plane * Ys_Zs + c_plane - depth_filtered)/vec_plane_norm;
    cv::Mat depth_mask;
    cv::threshold(depth_filtered, depth_mask, 0, 255, cv::THRESH_BINARY);
    depth_mask.convertTo(depth_mask, CV_8UC1);
    cv::Mat diff_filtered;
    diff.copyTo(diff_filtered, depth_mask);

    char file_name[256];
    strcpy(file_name, kDepthDirectory);
    strcat(file_name, "diff-filtered.png");
    cv::imwrite(file_name, diff_filtered * 3000);
    cv::Mat components_image;
    cv::threshold(diff_filtered, components_image, 0.03, 255, cv::THRESH_BINARY);
    components_image.convertTo(components_image, CV_8UC1);
    std::vector<std::vector<cv::Point>> contours;
    std::vector<cv::Vec4i> hierarchy;
    findContours(components_image, contours, hierarchy, CV_RETR_EXTERNAL, CV_CHAIN_APPROX_SIMPLE, cv::Point(0, 0));
    std::vector<cv::Rect> bound_rects;
    bound_rects.reserve(contours.size());
    if (contours.size() > 0) {
        cv::Scalar color = cv::Scalar(0, 0, 255);
        //TODO: approximating contours might speed up the computation
        for (int i = 1; i < contours.size(); ++i) {
            double contourArea = cv::contourArea(contours[i]);
            if (contourArea >  1000) {
                cv::drawContours(color_mat, contours, i, color, 5);
                bound_rects.push_back(cv::boundingRect(cv::Mat(contours[i])));
            }
        }
    }

    std::unique_ptr<float[]> bound_rect_arr = std::unique_ptr<float[]>(new float[5 * bound_rects.size() + 1]);
    bound_rect_arr[0] = bound_rects.size();
    cv::Scalar color_rect = cv::Scalar(0, 255, 255);
    for (int j = 0; j < bound_rects.size(); ++j) {
        cv::Rect current_rect = bound_rects[j];
        cv::rectangle(color_mat, current_rect.tl(), current_rect.br(), color_rect, 2, 8, 0);
        cv::Mat rect_roi = depth_filtered(current_rect);
        cv::Mat rect_roi_nonzero = rect_roi > 0;
        double min_depth_val, max_depth_val;
        cv::Point min_depth_loc, max_depth_loc;
        cv::minMaxLoc(rect_roi, &min_depth_val, &max_depth_val, &min_depth_loc, &max_depth_loc, rect_roi_nonzero);
        bound_rect_arr[5 * j + 1] = (current_rect.tl().x - half_width) / fx;
        bound_rect_arr[5 * j + 2] = (current_rect.tl().y - half_height) / fy;
        bound_rect_arr[5 * j + 3] = (current_rect.br().x - half_width) / fx;
        bound_rect_arr[5 * j + 4] = (current_rect.br().y - half_height) / fy;
        bound_rect_arr[5 * j + 5] = min_depth_val;
    }

    strcpy(file_name, kColorDirectory);
    strcat(file_name, "contours.png");
    cv::imwrite(file_name, color_mat);
    return bound_rect_arr.release();
}
}  // floor_object_detection