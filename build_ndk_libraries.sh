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

# Absolute, because a LuaJIT tree can live outside this repo (see LUAJIT_21_DIR):
# once the build has cd'd into it, a relative "../BTLib" is resolved by cp and
# make against that tree's parent, not against the repo, and the static libs land
# somewhere else entirely.
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

LUAJIT_205="$ROOT/LuaJIT-2.0.5"
LUAJIT_21="$ROOT/LuaJIT-2.1"

# LuaJIT 2.1 has no releases; upstream ships it as a moving branch, so
# "clone --branch v2.1" gave whatever HEAD was that day and nothing recorded
# which. The sources are vendored in LuaJIT-2.1/ instead, exactly like 2.0.5, so
# a checkout of any tag builds the same interpreter offline and a source-only
# distributor (F-Droid) needs no external checkout.
#
# The commit they came from is below, for provenance and for the fetch fallback
# that runs only if the folder is missing. Updating LuaJIT means replacing the
# folder from that upstream commit and testing on a device; nothing reads the
# SHA at runtime.
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
    # Used where it stands. Symlinking it into the repo would put a "..", and so
    # the built libraries, on the wrong side of the link.
    LUAJIT_21="$(cd "$LUAJIT_21_DIR" && pwd)"
    echo "Using supplied LuaJIT 2.1 tree: $LUAJIT_21"
elif [ ! -d "$LUAJIT_21" ]; then
    echo "Fetching LuaJIT 2.1 at $LUAJIT_21_COMMIT (required for arm64-v8a)..."
    git init -q "$LUAJIT_21"
    git -C "$LUAJIT_21" remote add origin "$LUAJIT_21_URL"
    git -C "$LUAJIT_21" fetch -q --depth 1 origin "$LUAJIT_21_COMMIT"
    git -C "$LUAJIT_21" checkout -q FETCH_HEAD
elif [ -d "$LUAJIT_21/.git" ]; then
    # Somebody's own checkout rather than the vendored sources. Say so if it is a
    # different commit instead of silently building something else; the tree is
    # theirs, not ours. The vendored copy has no .git and is not checked here.
    have="$(git -C "$LUAJIT_21" rev-parse HEAD 2>/dev/null || echo unknown)"
    if [ "$have" != "$LUAJIT_21_COMMIT" ]; then
        echo "WARNING: $LUAJIT_21 is a git checkout at $have, not the vendored" >&2
        echo "         sources built from $LUAJIT_21_COMMIT. Building it anyway." >&2
    fi
fi

echo "**********************************************"
echo "********* Cleaning prior builds. *************"
echo "**********************************************"

cd "$LUAJIT_205"
make clean || true
cd "$ROOT"

if [ -d "$LUAJIT_21" ]; then
    cd "$LUAJIT_21"
    make clean || true
    cd "$ROOT"
fi

cd "$ROOT/BTLib"
"$NDK/ndk-build" clean || true

rm -f ./jni/luajava/luaconf.h
rm -f ./jni/luajava/lualib.h
rm -f ./jni/luajava/luajit.h
rm -f ./jni/luajava/lua.h
rm -f ./jni/luajava/lauxlib.h
rm -f ./jni/luajava/libluajit-*.a
rm -f ./jni/luajava/libluajit-*.so
cd "$ROOT"

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
    cp src/libluajit.a "$ROOT/BTLib/jni/luajava/libluajit-${ABI}.a"
    cd "$ROOT"
}

SYSROOT_FLAGS="--sysroot $TOOLCHAIN/sysroot -D__ANDROID_API__=${NDKAPI}"

# LuaJIT 2.0.5: stable 32-bit ARM build used historically by BlowTorch.
build_luajit "$LUAJIT_205" armeabi-v7a "gcc -m32" "armv7a-linux-androideabi" \
    "$SYSROOT_FLAGS -march=armv7-a -mfloat-abi=softfp"

# LuaJIT 2.1: required for arm64-v8a (not supported in 2.0.5).
build_luajit "$LUAJIT_21" arm64-v8a "gcc" "aarch64-linux-android" \
    "$SYSROOT_FLAGS -DLUAJIT_ENABLE_GC64=1"

echo ""
echo "Copying LuaJIT headers to BTLib/jni/luajava/ (from 2.0.5 for luajava compat)"
cp "$LUAJIT_205/src/lauxlib.h" BTLib/jni/luajava/
cp "$LUAJIT_205/src/lua.h" BTLib/jni/luajava/
cp "$LUAJIT_205/src/luaconf.h" BTLib/jni/luajava/
cp "$LUAJIT_205/src/luajit.h" BTLib/jni/luajava/
cp "$LUAJIT_205/src/lualib.h" BTLib/jni/luajava/

echo "************************************************"
echo "********** STARTING ANDROID NDK BUILD **********"
echo "************************************************"

cd "$ROOT/BTLib"
"$NDK/ndk-build" NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=./jni/Android.mk NDK_APPLICATION_MK=./jni/Application.mk

echo ""
echo "**********************************************"
echo "********** BUILD COMPLETE ********************"
echo "**********************************************"
echo ""
echo "Native libraries built in BTLib/libs/"
ls -la libs/armeabi-v7a/ 2>/dev/null || echo "(no armeabi-v7a output)"
ls -la libs/arm64-v8a/ 2>/dev/null || echo "(no arm64-v8a output)"
