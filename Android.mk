LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)
#LOCAL_STATIC_JAVA_LIBRARIES := \
#        android-support-v4 \

LOCAL_PACKAGE_NAME := SprdGeneralSecurity
LOCAL_CERTIFICATE := platform
LOCAL_PRIVATE_PLATFORM_APIS := true
LOCAL_USE_AAPT2 := true
LOCAL_PRODUCT_MODULE := true

LOCAL_RESOURCE_DIR := \
        $(LOCAL_PATH)/res

include frameworks/base/packages/SettingsLib/common.mk
include $(BUILD_PACKAGE)

# Use the following include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
