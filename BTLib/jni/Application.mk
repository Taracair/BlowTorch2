APP_ABI := armeabi-v7a arm64-v8a
APP_OPTIM := release
APP_CFLAGS := -O3
APP_MODULES := lua lsqlite3 sqlite3 bit marshal luabins
APP_PLATFORM := android-24

# 16 KB page support (Android 15+). A device booted in 16 KB mode will not load
# a library whose LOAD segments are aligned to 4 KB, and Android says so at
# install: "not 16 KB compatible, ELF check failed".
#
# This line is a belt, not the fix. What actually went wrong once (6 Aug 2026)
# was a stale BTLib/libs/: that folder is not in git, nobody had re-run
# build_ndk_libraries.sh in a while, and a test APK went out carrying libraries
# left over from an older NDK at "align 2**12". The production APK built the
# same week was already at 2**14, which is what gave the game away. Rebuilding
# with the flag *removed* also produces 2**14, so the flag was never what fixed
# it -- measured, not assumed.
#
# It stays because the script picks whichever NDK is newest on the machine. On
# r27+ 16 KB is the default and this changes nothing; on an older one it is the
# difference between a working phone and that dialog. Making the outcome the
# same either way is worth one line.
APP_LDFLAGS := -Wl,-z,max-page-size=16384
