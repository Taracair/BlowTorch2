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
LUAJIT="LuaJIT-2.0.5"

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

echo "**********************************************"
echo "********* Cleaning prior builds. *************"
echo "**********************************************"

cd "$LUAJIT"
make clean || true
cd ..

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

cd "$LUAJIT"

# armeabi-v7a (32-bit ARM)
echo ""
echo "====== Building LuaJIT for armeabi-v7a ======"
make clean
make \
    HOST_CC="gcc -m32" \
    CROSS="$TOOLCHAIN/bin/llvm-" \
    STATIC_CC="$TOOLCHAIN/bin/armv7a-linux-androideabi${NDKAPI}-clang" \
    DYNAMIC_CC="$TOOLCHAIN/bin/armv7a-linux-androideabi${NDKAPI}-clang" \
    TARGET_LD="$TOOLCHAIN/bin/armv7a-linux-androideabi${NDKAPI}-clang" \
    TARGET_AR="$TOOLCHAIN/bin/llvm-ar rcus" \
    TARGET_STRIP="$TOOLCHAIN/bin/llvm-strip" \
    TARGET_SYS=Linux \
    TARGET_FLAGS="--sysroot $TOOLCHAIN/sysroot -march=armv7-a -mfloat-abi=softfp"

cp src/libluajit.a src/libluajit-armeabi-v7a.a

echo ""
echo "Copying LuaJIT output to BTLib/jni/luajava/"
cp src/libluajit-armeabi-v7a.a ../BTLib/jni/luajava/

echo "Copying LuaJIT headers to BTLib/jni/luajava/"
cp src/lauxlib.h ../BTLib/jni/luajava/
cp src/lua.h ../BTLib/jni/luajava/
cp src/luaconf.h ../BTLib/jni/luajava/
cp src/luajit.h ../BTLib/jni/luajava/
cp src/lualib.h ../BTLib/jni/luajava/

echo "************************************************"
echo "********** STARTING ANDROID NDK BUILD **********"
echo "************************************************"

cd ../BTLib
"$NDK/ndk-build" NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=./jni/Android.mk NDK_APPLICATION_MK=./jni/Application.mk

echo ""
echo "**********************************************"
echo "********** BUILD COMPLETE ********************"
echo "**********************************************"
echo ""
echo "Native libraries built in BTLib/libs/"
ls -la libs/armeabi-v7a/ 2>/dev/null || echo "(no output yet)"
