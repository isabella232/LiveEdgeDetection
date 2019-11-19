LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := app
LOCAL_LDFLAGS := -Wl,--build-id
APP_ABI := arm64-v8a armeabi armeabi-v7a mips mips64 x86 x86_64
LOCAL_SRC_FILES := \
	/jniLibs/mips/libopencv_java3.so \
	/jniLibs/armeabi/libopencv_java3.so \
	/jniLibs/x86/libopencv_java3.so \
	/jniLibs/x86_64/libopencv_java3.so \
	/jniLibs/arm64-v8a/libopencv_java3.so \
	/jniLibs/mips64/libopencv_java3.so \
	/jniLibs/armeabi-v7a/libopencv_java3.so \

include $(BUILD_SHARED_LIBRARY)

