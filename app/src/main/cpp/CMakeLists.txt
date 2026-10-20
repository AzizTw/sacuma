cmake_minimum_required(VERSION 3.22.1)
project(gcadapter C)

# Builds libgcadapter.so; Gradle packs it into the APK under lib/arm64-v8a/.
add_library(gcadapter SHARED native-lib.c vpad.c)

# liblog = Android's logcat API (__android_log_print).
target_link_libraries(gcadapter log)
