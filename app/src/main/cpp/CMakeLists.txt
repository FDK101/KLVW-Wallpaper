cmake_minimum_required(VERSION 3.22.1)
project("klvw_vulkan")

# Find Vulkan
find_library(VULKAN_LIB vulkan)
find_library(ANDROID_LIB android)
find_library(LOG_LIB log)

add_library(
    klvw_vulkan
    SHARED
    vulkan_renderer.cpp
    vulkan_utils.cpp
)

target_compile_options(klvw_vulkan PRIVATE
    -std=c++17
    -O3
    -ffast-math
    -DVK_USE_PLATFORM_ANDROID_KHR
)

target_include_directories(klvw_vulkan PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(
    klvw_vulkan
    ${VULKAN_LIB}
    ${ANDROID_LIB}
    ${LOG_LIB}
)

# 16 KB page size alignment — required for Android 15+ / Google Play
target_link_options(klvw_vulkan PRIVATE "-Wl,-z,max-page-size=16384")
