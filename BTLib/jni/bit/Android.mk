LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libbit
LOCAL_MODULE_FILENAME := libbit
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../luajava
LOCAL_SRC_FILES := ./bit.c
LOCAL_SHARED_LIBRARIES := lua
LOCAL_CFLAGS := -O3 -fpic -std=c99 -shared
include $(BUILD_SHARED_LIBRARY)
