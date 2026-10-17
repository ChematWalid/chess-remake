#!/usr/bin/env bash
set -e

BUILD_DIR="build"

if [ ! -d "$BUILD_DIR" ]; then
    echo ">>> Configuring CMake..."
    cmake -S . -B "$BUILD_DIR" -DCMAKE_BUILD_TYPE=Debug
fi

echo ">>> Building..."
cmake --build "$BUILD_DIR" --parallel "$(nproc)"

echo ""
echo "✓ Build complete. Binaries are in $BUILD_DIR/bin/"
