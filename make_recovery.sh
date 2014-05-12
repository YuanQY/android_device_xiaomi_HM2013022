TXT_BLUE='\e[0;36m'
TXT_RST='\e[0m'

make_source_image () {
# set up build environment
. build/envsetup.sh && lunch cm_HM2013022-eng

# clean build directory and make CWM recovery
make clobber && make -j4 recoveryimage
}

make_mtk_image () {
# copy MTK tools
rm out/target/product/HM2013022/CWM_recovery_Hongmi.img
rm -rf out/target/product/HM2013022/mtk-tools
cp -r device/xiaomi/HM2013022/mtk-tools out/target/product/HM2013022/mtk-tools

# MTK tools permissions
chmod 777 out/target/product/HM2013022/mtk-tools/mkbootimg out/target/product/HM2013022/mtk-tools/repack-MT65xx.pl out/target/product/HM2013022/mtk-tools/unpack-MT65xx.pl

# Prepare ramdisk
cd out/target/product/HM2013022/mtk-tools
./unpack-MT65xx.pl recovery.img 
rm -r recovery.img-ramdisk/sbin recovery.img-ramdisk/res/images

cp -r ../recovery/root/sbin recovery.img-ramdisk
cp -r ../recovery/root/res/images recovery.img-ramdisk/res/images
cp ../recovery/root/etc/recovery.fstab recovery.img-ramdisk/etc/recovery.fstab

ln -sf recovery.img-ramdisk/init recovery.img-ramdisk/sbin/ueventd
ln -sf recovery.img-ramdisk/init recovery.img-ramdisk/sbin/watchdogd

# Build MTK recovery image
cd ../../../../..
out/target/product/HM2013022/mtk-tools/repack-MT65xx.pl -recovery out/target/product/HM2013022/mtk-tools/recovery.img-kernel.img out/target/product/HM2013022/mtk-tools/recovery.img-ramdisk out/target/product/HM2013022/CWM_recovery_Hongmi.img
echo -e "${TXT_BLUE}Recovery image created: out/target/product/HM2013022/CWM_recovery_Hongmi.img${TXT_RST}"
}

# Specific arguments
show_argument_help () { 
echo 
echo "Hongmi recovery builder"
echo "By Redmaner"
echo 
echo "Usage: make_recovery.sh [option]"
echo 
echo " Options:"
echo "		-h, --help		This help"
echo "		-f, --full		Compile recovery from source and make MTK image"
echo "		-m, --mtk		Make MTK image"
echo "		-s, --source		Compile recovery from source (default)"
echo 
}

if [ $# -gt 0 ]; then
     if [ $1 == "-h" ] || [ $1 == "--help" ]; then
          show_argument_help
     elif [ $1 == "-f" ] || [ $1 == "--full" ]; then
            make_source_image; make_mtk_image
     elif [ $1 == "-m" ] || [ $1 == "--mtk" ]; then
            make_mtk_image
     elif [ $1 == "-s" ] || [ $1 == "--source" ]; then
            make_source_image
     else
            show_argument_help
     fi
else
     make_source_image
fi
