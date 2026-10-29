#!/usr/bin/env bash
# Builds the trimmed OpenCV that ships in the app (module :opencv).
#
# The Maven artifact org.opencv:opencv bundles every OpenCV module in one 25 MB native library per CPU
# type. The app only uses core, imgproc and photo (plus the Java bindings), so this script builds just
# those, with the optional dependencies the app never touches switched off (image codecs belong to the
# excluded imgcodecs module, so they are off too; otherwise the install step looks for them). Result: about a fifth of
# the size with the same API for the calls the app makes.
#
# Usage:  scripts/opencv/build-opencv-android.sh <opencv-source-dir> <output-dir> [abi ...]
#   <opencv-source-dir>  a checkout of https://github.com/opencv/opencv at the tag in OPENCV_VERSION below
#   <output-dir>         receives sdk/native/libs/<abi>/libopencv_java4.so and sdk/java/src
#   abi                  defaults to arm64-v8a armeabi-v7a x86_64 (the ABIs the app builds for)
# Requires: cmake 3.22+, ninja, python3, and ANDROID_NDK (r27 or later) in the environment.
#
# Afterwards, copy the results into the repo (see scripts/opencv/README.md):
#   sdk/native/libs/<abi>/libopencv_java4.so  ->  opencv/src/main/jniLibs/<abi>/
#   sdk/java/src/org                          ->  opencv/src/main/java/org
set -euo pipefail

OPENCV_VERSION="4.14.0"
MIN_SDK=24

SRC=${1:?opencv source dir}
OUT=${2:?output dir}
shift 2
ABIS=("$@")
if [ ${#ABIS[@]} -eq 0 ]; then ABIS=(arm64-v8a armeabi-v7a x86_64); fi
: "${ANDROID_NDK:?set ANDROID_NDK to an NDK r27+ install}"

SRC=$(cd "$SRC" && pwd)
mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)

found=$(grep -E '^#define CV_VERSION_(MAJOR|MINOR|REVISION)' "$SRC/modules/core/include/opencv2/core/version.hpp" | awk '{print $3}' | paste -sd.)
if [ "$found" != "$OPENCV_VERSION" ]; then
  echo "Expected OpenCV $OPENCV_VERSION sources, found $found" >&2
  exit 1
fi

for abi in "${ABIS[@]}"; do
  build="$OUT/build-$abi"
  install="$OUT/install-$abi"
  rm -rf "$build" "$install"
  mkdir -p "$build"
  echo "===== $abi"
  case "$abi" in
    armeabi-v7a) extra=(-DANDROID_ABI="armeabi-v7a with NEON") ;;
    *) extra=(-DANDROID_ABI="$abi" -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON) ;;
  esac
  cmake -S "$SRC" -B "$build" -GNinja \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_PLATFORM="android-$MIN_SDK" \
    -DANDROID_STL=c++_shared \
    "${extra[@]}" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX="$install" \
    -DINSTALL_CREATE_DISTRIB=ON \
    -DCMAKE_SKIP_INSTALL_ALL_DEPENDENCY=ON \
    -DBUILD_LIST=core,imgproc,photo,java \
    -DBUILD_SHARED_LIBS=OFF \
    -DBUILD_ANDROID_EXAMPLES=OFF -DINSTALL_ANDROID_EXAMPLES=OFF \
    -DBUILD_EXAMPLES=OFF -DBUILD_TESTS=OFF -DBUILD_PERF_TESTS=OFF -DBUILD_DOCS=OFF -DBUILD_opencv_apps=OFF \
    -DBUILD_KOTLIN_EXTENSIONS=OFF \
    -DBUILD_JAVA=ON \
    -DWITH_TBB=OFF -DWITH_IPP=OFF -DWITH_KLEIDICV=OFF -DWITH_ITT=OFF -DWITH_OPENCL=OFF \
    -DWITH_PROTOBUF=OFF -DBUILD_PROTOBUF=OFF -DWITH_ANDROID_MEDIANDK=OFF \
    -DWITH_CAROTENE=ON \
    -DWITH_JPEG=OFF -DWITH_PNG=OFF -DWITH_TIFF=OFF -DWITH_WEBP=OFF -DWITH_OPENJPEG=OFF -DWITH_JASPER=OFF \
    -DWITH_OPENEXR=OFF -DWITH_IMGCODEC_HDR=OFF -DWITH_IMGCODEC_SUNRASTER=OFF -DWITH_IMGCODEC_PXM=OFF -DWITH_IMGCODEC_PFM=OFF \
    -DWITH_AVIF=OFF -DWITH_SPNG=OFF -DWITH_ZLIB=ON \
    -DCV_TRACE=OFF \
    -DOPENCV_ENABLE_ALLOCATOR_STATS=OFF
  # The modules, the JNI library (a separate target, not part of opencv_modules) and the generated Java
  # sources. The Gradle sub-project that packages OpenCV's own AAR is left out, since the app's :opencv
  # module takes the sources and the library directly.
  ninja -C "$build" opencv_modules opencv_java opencv_java_android_source_copy
  ninja -C "$build" install/strip
  ls -la "$install/sdk/native/libs/$abi/"
done

echo "Done. Copy sdk/native/libs/<abi>/libopencv_java4.so and sdk/java/src/org from the install-* folders."
