#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# DevTerm Node.js Cross-Compilation Script
# ============================================================
# Builds Node.js for Android ARM64 (aarch64)
# Intended to run in GitHub Actions CI (ubuntu-latest)
# ============================================================

NODE_VERSION="${1:-24.8.0}"
OUTPUT_DIR="${2:-app/src/main/assets/nodejs}"
BUILD_DIR="/tmp/node-build"
NDK_VERSION="r27c"
NDK_URL="https://dl.google.com/android/repository/android-ndk-${NDK_VERSION}-linux.zip"

echo "=== DevTerm Node.js Cross-Compilation ==="
echo "Node.js version: ${NODE_VERSION}"
echo "Output directory: ${OUTPUT_DIR}"

# --- Install dependencies ---
sudo apt-get update -qq
sudo apt-get install -y -qq curl zip build-essential python3

# --- Download and extract NDK ---
if [ ! -d "${BUILD_DIR}/android-ndk-${NDK_VERSION}" ]; then
    echo "Downloading NDK ${NDK_VERSION}..."
    curl -fsSL "${NDK_URL}" -o /tmp/ndk.zip
    mkdir -p "${BUILD_DIR}"
    unzip -q /tmp/ndk.zip -d "${BUILD_DIR}"
    rm /tmp/ndk.zip
fi

export ANDROID_NDK="${BUILD_DIR}/android-ndk-${NDK_VERSION}"

# --- Determine toolchain paths ---
TOOLCHAIN="${ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64"
export PATH="${TOOLCHAIN}/bin:${PATH}"

export AR="${TOOLCHAIN}/bin/llvm-ar"
export CC="${TOOLCHAIN}/bin/aarch64-linux-android21-clang"
export CXX="${TOOLCHAIN}/bin/aarch64-linux-android21-clang++"
export LINK="${CXX}"
export CC_host="gcc"
export CXX_host="g++"

# --- Download Node.js source ---
NODE_SRC="${BUILD_DIR}/node-v${NODE_VERSION}"
if [ ! -d "${NODE_SRC}" ]; then
    echo "Downloading Node.js v${NODE_VERSION} source..."
    curl -fsSL "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}.tar.gz" -o /tmp/node-src.tar.gz
    tar xzf /tmp/node-src.tar.gz -C "${BUILD_DIR}"
    rm /tmp/node-src.tar.gz
fi

cd "${NODE_SRC}"

# --- Configure for Android ARM64 ---
echo "Configuring for Android ARM64..."
./android-configure \
    --dest-cpu=arm64 \
    --dest-os=android \
    --without-npm \
    --without-snapshot \
    --openssl-no-asm \
    ../../android-ndk-${NDK_VERSION} 21 aarch64

# --- Build ---
echo "Building Node.js (this will take a while)..."
make -j"$(nproc)" binary

# --- Extract binary from archive ---
echo "Extracting build artifacts..."
BUILT_ARCHIVE=$(ls node-*.tar.gz 2>/dev/null | head -1)
if [ -z "${BUILT_ARCHIVE}" ]; then
    echo "Error: no build archive found"
    exit 1
fi

WORK_DIR=$(mktemp -d)
tar xzf "${BUILT_ARCHIVE}" -C "${WORK_DIR}"
EXTRACTED_DIR=$(ls "${WORK_DIR}")
mkdir -p "${OUTPUT_DIR}/bin" "${OUTPUT_DIR}/lib" "${OUTPUT_DIR}/include"

cp "${WORK_DIR}/${EXTRACTED_DIR}/bin/node" "${OUTPUT_DIR}/bin/node"
chmod +x "${OUTPUT_DIR}/bin/node"

# Copy shared libraries
if [ -d "${WORK_DIR}/${EXTRACTED_DIR}/lib" ]; then
    cp -r "${WORK_DIR}/${EXTRACTED_DIR}/lib/"* "${OUTPUT_DIR}/lib/"
fi

echo "=== Build complete ==="
echo "Node.js binary: ${OUTPUT_DIR}/bin/node"
ls -lh "${OUTPUT_DIR}/bin/node"

# Verify
file "${OUTPUT_DIR}/bin/node"
