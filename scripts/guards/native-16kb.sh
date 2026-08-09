#!/usr/bin/env bash
# Every native library in BTLib/libs is aligned for 16 KB pages.
#
# Why this is a guard and not a comment: BTLib/libs is not in git, and it is
# built by a separate script nobody remembers to re-run. On 6 Aug 2026 a test
# APK shipped libraries left over from an older NDK, aligned to 4 KB, and the
# phone refused them with "not 16 KB compatible, ELF check failed". Nothing in
# the build noticed; the production APK of the same week was fine, which is the
# only reason it was caught at all.
#
# Android 15+ needs LOAD segments at 2**14. A 16 KB library still loads on a
# 4 KB device, so there is never a reason to ship 2**12.
#
# arm64-v8a only, deliberately. 16 KB pages are a 64-bit feature and Android
# enforces this on arm64; a recent NDK aligns arm64 to 16 KB by default and
# leaves armeabi-v7a at 4 KB, which is correct and must not fail this check.
# (APP_LDFLAGS in Application.mk raises both, which is harmless but is not what
# is being asserted here.)
#
# Skips cleanly when there is nothing to check (a fresh clone has no libs) or
# when llvm-objdump cannot be found, because this must not break CI on a machine
# without an NDK. It fails only when it can see a library and that library is
# wrong.

set -uo pipefail
cd "$(dirname "$0")/../.."

LIBDIR="BTLib/libs/arm64-v8a"
if [ ! -d "$LIBDIR" ]; then
  echo "no $LIBDIR (natives not built here) - skipped"
  exit 0
fi

mapfile -t SOS < <(find "$LIBDIR" -name '*.so' 2>/dev/null | sort)
if [ "${#SOS[@]}" -eq 0 ]; then
  echo "no .so under $LIBDIR - skipped"
  exit 0
fi

OBJDUMP=""
for candidate in \
  "${NDK_HOME:-}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-objdump" \
  "${ANDROID_HOME:-$HOME/Android/Sdk}"/ndk/*/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-objdump \
  "$(command -v llvm-objdump 2>/dev/null)" \
  "$(command -v objdump 2>/dev/null)"; do
  if [ -n "$candidate" ] && [ -x "$candidate" ]; then
    OBJDUMP="$candidate"
    break
  fi
done

if [ -z "$OBJDUMP" ]; then
  echo "no llvm-objdump/objdump found - skipped"
  exit 0
fi

bad=0
for so in "${SOS[@]}"; do
  align=$("$OBJDUMP" -p "$so" 2>/dev/null \
    | grep -m1 -A1 'LOAD' \
    | grep -oE 'align 2\*\*[0-9]+' \
    | head -1)
  if [ -z "$align" ]; then
    echo "  ?? $so (could not read alignment)"
    continue
  fi
  exp="${align##*\*\*}"
  if [ "$exp" -lt 14 ]; then
    echo "  BAD  $so -> $align (want 2**14 or more)"
    bad=1
  fi
done

if [ "$bad" -ne 0 ]; then
  echo
  echo "Native libraries are not 16 KB aligned. Android 15+ refuses these."
  echo "Re-run ./build_ndk_libraries.sh — the usual cause is a stale BTLib/libs"
  echo "left over from an older NDK, not a missing flag."
  exit 1
fi

echo "ok (${#SOS[@]} libraries at 2**14 or better)"
exit 0
