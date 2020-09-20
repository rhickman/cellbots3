cmake_minimum_required(VERSION 3.4.1)

# Sets lib_build_DIR to the path of the desired output directory.
set(lib_build_DIR ${CMAKE_CURRENT_SOURCE_DIR}/lib/${ANDROID_ABI})
file(MAKE_DIRECTORY ${lib_build_DIR})

# Define external output folder for libraries
set(lib_out_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../libs/${ANDROID_ABI})
file(MAKE_DIRECTORY ${lib_out_DIR})

# Uncomment if we want wrpper libraries in the same location than external libraries
#set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${lib_out_DIR})

set(OPENCV_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/opencv_android_sdk_331)

# Set C++ standard to C++11
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")

# Declare external headers
include_directories(${OPENCV_DIRECTORY}/sdk/native/jni/include)

# Declare project wrappers headers
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

# Add wrappers libraries
add_library(floor_object_detection_jni SHARED floor_object_detection_jni.cc floor_object_detection/floor_object_detection.cc)
add_library(lib_opencv SHARED IMPORTED)

set_target_properties(lib_opencv PROPERTIES IMPORTED_LOCATION ${OPENCV_DIRECTORY}/sdk/native/libs/${ANDROID_ABI}/libopencv_java3.so)

# Link project wrappers to external libraries
target_link_libraries(floor_object_detection_jni log android lib_opencv)

# Copy external compiled libraries so they can be included into the APK (hack?)
add_custom_command(TARGET floor_object_detection_jni POST_BUILD
                   COMMAND ${CMAKE_COMMAND} -E copy_if_different
                       ${OPENCV_DIRECTORY}/sdk/native/libs/${ANDROID_ABI}/libopencv_java3.so
                       ${lib_out_DIR}/libopencv_java3.so
                  )
