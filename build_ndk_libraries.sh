#!/bin/bash
set -e

# BlowTorch NDK Build Script (modernized for NDK r26+ / Clang)
#
# Requirements:
#   - ANDROID_HOME or NDK_HOME pointing to NDK
#   - gcc (host compiler for LuaJIT)
#   - make
#
# Usage: ./build_ndk_libraries.sh

if [ -n "$NDK_HOME" ]; then
    NDK="$NDK_HOME"
elif [ -n "$ANDROID_HOME" ]; then
    NDK_DIR=$(ls -d "$ANDROID_HOME/ndk/"* 2>/dev/null | sort -V | tail -1)
    if [ -n "$NDK_DIR" ]; then
        NDK="$NDK_DIR"
    fi
fi

if [ -z "$NDK" ] || [ ! -d "$NDK" ]; then
    echo "ERROR: NDK not found. Set NDK_HOME or ANDROID_HOME."
    exit 1
fi
echo "Using NDK: $NDK"

NDKAPI=24
LUAJIT_205="LuaJIT-2.0.5"
LUAJIT_21="LuaJIT-2.1"

# Detect host platform
case "$(uname -s)" in
    Linux*)  NDKHOST="linux-x86_64" ;;
    Darwin*) NDKHOST="darwin-x86_64" ;;
    *)       echo "Unsupported platform"; exit 1 ;;
esac

TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$NDKHOST"

if [ ! -d "$TOOLCHAIN" ]; then
    echo "ERROR: Toolchain not found at $TOOLCHAIN"
    exit 1
fi

if [ ! -d "$LUAJIT_21" ]; then
    echo "Cloning LuaJIT 2.1 (required for arm64-v8a)..."
    git clone --depth 1 --branch v2.1 https://github.com/LuaJIT/LuaJIT.git "$LUAJIT_21"
fi

echo "**********************************************"
echo "********* Cleaning prior builds. *************"
echo "**********************************************"

cd "$LUAJIT_205"
make clean || true
cd ..

if [ -d "$LUAJIT_21" ]; then
    cd "$LUAJIT_21"
    make clean || true
    cd ..
fi

cd BTLib
"$NDK/ndk-build" clean || true

rm -f ./jni/luajava/luaconf.h
rm -f ./jni/luajava/lualib.h
rm -f ./jni/luajava/luajit.h
rm -f ./jni/luajava/lua.h
rm -f ./jni/luajava/lauxlib.h
rm -f ./jni/luajava/libluajit-*.a
rm -f ./jni/luajava/libluajit-*.so
cd ..

echo "**********************************************"
echo "*************  STARTING BUILD ****************"
echo "**********************************************"

build_luajit() {
    local LUADIR="$1"
    local ABI="$2"
    local HOST_CC="$3"
    local TARGET_TRIPLE="$4"
    local TARGET_FLAGS="$5"

    echo ""
    echo "====== Building LuaJIT (${LUADIR}) for ${ABI} ======"
    cd "$LUADIR"
    make clean
    make \
        HOST_CC="$HOST_CC" \
        CROSS="$TOOLCHAIN/bin/llvm-" \
        STATIC_CC="$TOOLCHAIN/bin/${TARGET_TRIPLE}${NDKAPI}-clang" \
        DYNAMIC_CC="$TOOLCHAIN/bin/${TARGET_TRIPLE}${NDKAPI}-clang -fPIC" \
        TARGET_LD="$TOOLCHAIN/bin/${TARGET_TRIPLE}${NDKAPI}-clang" \
        TARGET_AR="$TOOLCHAIN/bin/llvm-ar rcus" \
        TARGET_STRIP="$TOOLCHAIN/bin/llvm-strip" \
        TARGET_SYS=Linux \
        TARGET_FLAGS="$TARGET_FLAGS"
    cp src/libluajit.a "../BTLib/jni/luajava/libluajit-${ABI}.a"
    cd ..
}

SYSROOT_FLAGS="--sysroot $TOOLCHAIN/sysroot -D__ANDROID_API__=${NDKAPI}"

# LuaJIT 2.0.5: stable 32-bit ARM build used historically by BlowTorch.
build_luajit "LuaJIT-2.0.5" armeabi-v7a "gcc -m32" "armv7a-linux-androideabi" \
    "$SYSROOT_FLAGS -march=armv7-a -mfloat-abi=softfp"

# LuaJIT 2.1: required for arm64-v8a (not supported in 2.0.5).
build_luajit "LuaJIT-2.1" arm64-v8a "gcc" "aarch64-linux-android" \
    "$SYSROOT_FLAGS -DLUAJIT_ENABLE_GC64=1"

echo ""
echo "Copying LuaJIT headers to BTLib/jni/luajava/ (from 2.0.5 for luajava compat)"
cp LuaJIT-2.0.5/src/lauxlib.h BTLib/jni/luajava/
cp LuaJIT-2.0.5/src/lua.h BTLib/jni/luajava/
cp LuaJIT-2.0.5/src/luaconf.h BTLib/jni/luajava/
cp LuaJIT-2.0.5/src/luajit.h BTLib/jni/luajava/
cp LuaJIT-2.0.5/src/lualib.h BTLib/jni/luajava/

echo "************************************************"
echo "********** STARTING ANDROID NDK BUILD **********"
echo "************************************************"

cd BTLib
"$NDK/ndk-build" NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=./jni/Android.mk NDK_APPLICATION_MK=./jni/Application.mk

echo ""
echo "**********************************************"
echo "********** BUILD COMPLETE ********************"
echo "**********************************************"
echo ""
echo "Native libraries built in BTLib/libs/"
ls -la libs/armeabi-v7a/ 2>/dev/null || echo "(no armeabi-v7a output)"
ls -la libs/arm64-v8a/ 2>/dev/null || echo "(no arm64-v8a output)"
