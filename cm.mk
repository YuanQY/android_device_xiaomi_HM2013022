## Specify phone tech before including full_phone
$(call inherit-product, vendor/cm/config/gsm.mk)

# Release name
PRODUCT_RELEASE_NAME := HM2013022

# Inherit some common CM stuff.
$(call inherit-product, vendor/cm/config/common_full_phone.mk)

# Inherit device configuration
$(call inherit-product, device/xiaomi/HM2013022/device_HM2013022.mk)

## Device identifier. This must come after all inclusions
PRODUCT_DEVICE := HM2013022
PRODUCT_NAME := cm_HM2013022
PRODUCT_BRAND := xiaomi
PRODUCT_MODEL := HM2013022
PRODUCT_MANUFACTURER := xiaomi
