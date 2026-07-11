LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libluabins
LOCAL_MODULE_FILENAME := libluabins
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../luajava
LOCAL_SHARED_LIBRARIES := lua
LOCAL_CFLAGS := -O3 -fpic -shared -std=c99
LOCAL_SRC_FILES := ./fwrite.c \
                   ./load.c \
                   ./luabins.c \
                   ./save.c \
                   ./luainternals.c \
                   ./savebuffer.c \
                   ./write.c
include $(BUILD_SHARED_LIBRARY)
