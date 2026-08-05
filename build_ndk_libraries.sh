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

# LuaJIT 2.1 has no releases; upstream ships it as a moving branch. Pin the exact
# commit so two builds of the same tag link the same interpreter — "branch v2.1"
# would give whatever HEAD happened to be that day. This is the commit the 2.2.4
# release was built from.
#
# Bumping it: change the SHA, run this script, and test on a device. Nothing
# reads the SHA at runtime.
LUAJIT_21_COMMIT="${LUAJIT_21_COMMIT:-3c4f9fe2052b8d08a917ac0d5f38563f0297b5a3}"
LUAJIT_21_URL="${LUAJIT_21_URL:-https://github.com/LuaJIT/LuaJIT.git}"

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

# An offline build (F-Droid buildserver, air-gapped CI) can hand us a tree that
# is already there — a srclib checkout, a submodule, an unpacked tarball — by
# pointing LUAJIT_21_DIR at it. Nothing is fetched in that case.
if [ -n "${LUAJIT_21_DIR:-}" ]; then
    if [ ! -d "$LUAJIT_21_DIR" ]; then
        echo "ERROR: LUAJIT_21_DIR=$LUAJIT_21_DIR does not exist." >&2
        exit 1
    fi
    if [ ! -e "$LUAJIT_21" ]; then
        ln -s "$LUAJIT_21_DIR" "$LUAJIT_21"
    fi
    echo "Using supplied LuaJIT 2.1 tree: $LUAJIT_21_DIR"
elif [ ! -d "$LUAJIT_21" ]; then
    echo "Fetching LuaJIT 2.1 at $LUAJIT_21_COMMIT (required for arm64-v8a)..."
    git init -q "$LUAJIT_21"
    git -C "$LUAJIT_21" remote add origin "$LUAJIT_21_URL"
    git -C "$LUAJIT_21" fetch -q --depth 1 origin "$LUAJIT_21_COMMIT"
    git -C "$LUAJIT_21" checkout -q FETCH_HEAD
else
    # Already on disk. Say so if it is not the pinned commit rather than
    # silently building something else; the tree is the developer's, not ours.
    have="$(git -C "$LUAJIT_21" rev-parse HEAD 2>/dev/null || echo unknown)"
    if [ "$have" != "$LUAJIT_21_COMMIT" ]; then
        echo "WARNING: $LUAJIT_21 is at $have, pinned commit is $LUAJIT_21_COMMIT." >&2
        echo "         Building it anyway. Delete the folder to get the pinned one." >&2
    fi
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
