APP_ABI := armeabi-v7a arm64-v8a
APP_OPTIM := release
APP_CFLAGS := -O3
APP_MODULES := lua lsqlite3 sqlite3 bit marshal luabins
APP_PLATFORM := android-24

# 16 KB page support (Android 15+). Devices booted in 16 KB mode refuse a
# library whose LOAD segments are aligned to 4 KB: the phone says "this app is
# not 16 KB compatible, ELF check failed" and the native side will not load.
#
# Measured on the btTest APK before this line existed: every arm64 library
# reported "align 2**12" from llvm-objdump -p, i.e. 4096. With the flag they
# report 2**14 (16384), which is what the loader wants and which is also
# backwards compatible -- a 16 KB aligned library still loads on a 4 KB device,
# it only costs a little padding in the file.
#
# NDK r27 makes this the default; this project builds against r26, where it has
# to be asked for. Keep the flag even after an NDK bump: it is harmless when it
# is already the default, and removing it silently regresses the phone.
APP_LDFLAGS := -Wl,-z,max-page-size=16384
