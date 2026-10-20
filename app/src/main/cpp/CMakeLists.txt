cmake_minimum_required(VERSION 3.22.1)
project(sacuma C)

# Builds libsacuma.so; Gradle packs it into the APK under lib/arm64-v8a/.
add_library(sacuma SHARED vpad.c)

# liblog = logcat API (__android_log_print), libm = math (sqrtf).
target_link_libraries(sacuma log m)
