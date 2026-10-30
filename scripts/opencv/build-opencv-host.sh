#!/usr/bin/env bash
# Builds the same trimmed OpenCV (core, imgproc, photo) for a Linux x86_64 desktop JVM, so the imaging
# unit tests can run the real filters without a phone: app/src/test/resources/native/linux-x86_64.
#
# Usage:  scripts/opencv/build-opencv-host.sh <opencv-source-dir> <output-dir>
#   <opencv-source-dir>  checkout of https://github.com/opencv/opencv at tag 4.14.0 (same as the Android build)
# Requires: cmake, ninja, python3, a JDK (JAVA_HOME) with JNI headers, a C++ compiler.
# Afterwards copy <output-dir>/build/lib/libopencv_java*.so, stripped (strip --strip-unneeded), to
#   app/src/test/resources/native/linux-x86_64/opencv_java4.bin
# (".bin" because the Android Gradle plugin drops ".so" files from test resources; the tests load it by path).
set -euo pipefail

SRC=${1:?opencv source dir}
OUT=${2:?output dir}
: "${JAVA_HOME:?set JAVA_HOME to a JDK with include/jni.h}"
SRC=$(cd "$SRC" && pwd)
mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)
BUILD="$OUT/build"
rm -rf "$BUILD"

cmake -S "$SRC" -B "$BUILD" -GNinja \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_LIST=core,imgproc,photo,java \
  -DBUILD_SHARED_LIBS=OFF \
  -DBUILD_JAVA=ON -DBUILD_opencv_java=ON \
  -DBUILD_TESTS=OFF -DBUILD_PERF_TESTS=OFF -DBUILD_EXAMPLES=OFF -DBUILD_DOCS=OFF -DBUILD_opencv_apps=OFF \
  -DWITH_IPP=OFF -DWITH_TBB=OFF -DWITH_ITT=OFF -DWITH_OPENCL=OFF -DWITH_OPENCV_FLANN=OFF \
  -DWITH_PROTOBUF=OFF -DBUILD_PROTOBUF=OFF -DWITH_EIGEN=OFF -DWITH_LAPACK=OFF -DWITH_OPENEXR=OFF \
  -DWITH_JPEG=OFF -DWITH_PNG=OFF -DWITH_TIFF=OFF -DWITH_WEBP=OFF -DWITH_OPENJPEG=OFF -DWITH_JASPER=OFF \
  -DWITH_V4L=OFF -DWITH_GSTREAMER=OFF -DWITH_FFMPEG=OFF -DWITH_GTK=OFF -DWITH_QT=OFF \
  -DBUILD_ZLIB=ON \
  -DCV_TRACE=OFF \
  -DCMAKE_SHARED_LINKER_FLAGS="-static-libstdc++ -static-libgcc"
ninja -C "$BUILD" opencv_java
find "$BUILD/lib" -name 'libopencv_java*.so' -exec ls -la {} \;
