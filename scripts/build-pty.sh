#!/bin/bash

set -e

if [ -z "$CC" ]; then
    CC=$(which aarch64-linux-gnu-gcc 2>/dev/null)
    if [ -z "$CC" ]; then
        echo "ERROR: aarch64-linux-gnu-gcc not found"
        echo "Please install: sudo apt install gcc-aarch64-linux-gnu"
        exit 1
    fi
fi

echo "Using compiler: $CC"

SRC_DIR="app/src/main/cpp"
OUT_DIR="app/src/main/jniLibs/arm64-v8a"

mkdir -p "$OUT_DIR"

$CC \
    -shared \
    -fPIC \
    -o "$OUT_DIR/libpty.so" \
    "$SRC_DIR/pty.c" \
    -I"$JAVA_HOME/include" \
    -I"$JAVA_HOME/include/linux" \
    -ldl \
    -llog \
    -Wall \
    -Wextra \
    -O2 \
    -DANDROID

echo "Success! libpty.so created at: $OUT_DIR/libpty.so"
echo ""
echo "To verify:"
echo "  file $OUT_DIR/libpty.so"
echo "  readelf -h $OUT_DIR/libpty.so | grep Machine"