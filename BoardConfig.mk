# Copyright (C) 2013 The CyanogenMod Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

DEVICE_FOLDER := device/xiaomi/HM2013022
VENDOR_FOLDER := vendor/xiaomi/HM2013022/proprietary

USE_CAMERA_STUB := true

# inherit from the proprietary version
-include vendor/xiaomi/HM2013022/BoardConfigVendor.mk
# Board
TARGET_BOARD_PLATFORM := MT6589
TARGET_ARCH := arm
TARGET_CPU_ABI := armeabi-v7a
TARGET_CPU_ABI2 := armeabi
TARGET_CPU_SMP := true
TARGET_ARCH_VARIANT := armv7-a-neon
TARGET_CPU_VARIANT := cortex-a7
ARCH_ARM_HAVE_TLS_REGISTER := true

# Override healthd HAL to use charge_counter for 1%
#BOARD_HAL_STATIC_LIBRARIES := libhealthd.mtk

#Bluetooth
BOARD_HAVE_BLUETOOTH := true

# Blob hacks
COMMON_GLOBAL_CFLAGS += -DMR1_AUDIO_BLOB -DDISABLE_HW_ID_MATCH_CHECK -DNEEDS_VECTORIMPL_SYMBOLS -DNEEDS_OLD_CALLSTACK_SYMBOLS -DTARGET_MTK -DTARGET_JB_TRACER
BOARD_HAVE_PRE_KITKAT_AUDIO_BLOB := true

# Power
TARGET_POWERHAL_VARIANT := cm

# Bootloader
TARGET_NO_BOOTLOADER := true
TARGET_BOOTLOADER_BOARD_NAME := HM2013022

#BOARD_CUSTOM_BOOTIMG_MK := $(DEVICE_FOLDER)/boot.mk

# EGL settings
BOARD_EGL_CFG := $(VENDOR_FOLDER)/lib/egl/egl.cfg
#TARGET_DISABLE_SURFACEFLINGER_PBUFFERS := true
#TARGET_DISABLE_SURFACEFLINGER_GLES2 := true
#TARGET_DISABLE_TRIPLE_BUFFERING := false
BOARD_ALLOW_EGL_HIBERNATION := true
USE_OPENGL_RENDERER := true

# Kernel
TARGET_PREBUILT_KERNEL := $(DEVICE_FOLDER)/kernel
BOARD_KERNEL_CMDLINE := console=ttyMT3,921600n1 vmalloc=530M slub_max_order=0 lcm=1-r61308_dsi_vdo fps=5363 pl_t=3710 lk_t=2388 printk.disable_uart=1 boot_reason=4
BOARD_KERNEL_BASE := 0x10008000

# Partition sizes get from /proc/emmc on a running device
BOARD_BOOTIMAGE_PARTITION_SIZE := 6291456 #6M
BOARD_SYSTEMIMAGE_PARTITION_SIZE := 681907200 #650M
BOARD_USERDATAIMAGE_PARTITION_SIZE := 2147483648 #2048M
BOARD_RECOVERYIMAGE_PARTITION_SIZE := 10485760 #6291456 6M
BOARD_KERNEL_PAGESIZE := 2048
BOARD_FLASH_BLOCK_SIZE := 131072 #128K
TARGET_USERIMAGES_USE_EXT4 := true

# Recovery
RECOVERY_FSTAB_VERSION := 1
RECOVERY_API_VERSION := 1
TARGET_RECOVERY_FSTAB := $(DEVICE_FOLDER)/recovery.fstab
TARGET_PREBUILT_RECOVERY_KERNEL := $(DEVICE_FOLDER)/kernel
BOARD_HAS_NO_SELECT_BUTTON := true
TARGET_RECOVERY_INITRC := $(DEVICE_FOLDER)/ramdisk/init.rc
BOARD_CUSTOM_RECOVERY_KEYMAPPING := ../../$(DEVICE_FOLDER)/recovery/recovery_keys.c
CWM_EMMC_BOOT_DEVICE_NAME := /dev/bootimg
CWM_EMMC_BOOT_DEVICE_SIZE := 6291456 #6M
CWM_EMMC_RECOVERY_DEVICE_NAME := /dev/recovery
CWM_EMMC_RECOVERY_DEVICE_SIZE := 10485760 #6291456 6M
TARGET_RECOVERY_PIXEL_FORMAT := "RGBX_8888"

# Ramdisk
TARGET_PROVIDES_INIT_RC := true

# Prebuilt image layouts
#DEVICE_BASE_RECOVERY_IMAGE := $(DEVICE_FOLDER)/mtk-tools/recovery.img

# wifi
WPA_SUPPLICANT_VERSION := VER_0_8_X
BOARD_WPA_SUPPLICANT_DRIVER := NL80211
BOARD_WPA_SUPPLICANT_PRIVATE_LIB := lib_driver_cmd_mtk

# Screen
DEVICE_RESOLUTION := 1280x720
TARGET_SCREEN_HEIGHT := 1280
TARGET_SCREEN_WIDTH := 720

# USB
TARGET_USE_CUSTOM_LUN_FILE_PATH := /sys/devices/platform/mt_usb/gadget/lun%d/file

# Telephony
BOARD_RIL_CLASS := ../../../$(DEVICE_FOLDER)/ril/

# Hardware tunables
BOARD_HARDWARE_CLASS := $(DEVICE_FOLDER)/cmhw/

# Release tool
TARGET_USE_SET_METADATA := false
#TARGET_PROVIDES_RELEASETOOLS := true
#TARGET_RELEASETOOL_OTA_FROM_TARGET_SCRIPT := build/tools/releasetools/ota_from_target_files --device_specific device/xiaomi/HM2013022/releasetools/ota_from_target_files.py
#TARGET_SYSTEMIMAGE_USE_SQUISHER := true