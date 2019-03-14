# Copyright (C) 2012 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests
LOCAL_SRC_FILES := $(call all-java-files-under, java)
LOCAL_PACKAGE_NAME := AdbJoinWifi
LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res
LOCAL_USE_AAPT2 := true
LOCAL_SDK_VERSION := 27
LOCAL_SDK_RES_VERSION := current
LOCAL_MIN_SDK_VERSION := 16

LOCAL_STATIC_ANDROID_LIBRARIES := \
    android-support-v7-appcompat \

include $(BUILD_PACKAGE)

