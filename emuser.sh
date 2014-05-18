#!/bin/bash
SRS=`adb shell ps | grep system/bin | cut -d 'S' -f2 | cut -d ' ' -f2 | dos2unix | sed 's/\r\n/ /g' | xargs`
PWD=`pwd`
VENDOR_PATH=vendor/xiaomi/HM2013022/proprietary
cd ../../../
GCC_PATH=prebuilts/gcc/linux-x86/arm/arm-linux-androideabi-4.7/bin
echo "$CP"
for SR in $SRS; 
do
	if [ ! -f "out/target/product/HM2013022$SR" ]; then
		echo "There is no \"out/target/product/HM2013022$SR\""
		CPFILE=`echo $SR |cut -d '/' -f4`
		echo "run adb pull $SR $VENDOR_PATH/bin/$CPFILE"
		adb pull $SR $VENDOR_PATH/bin/$CPFILE
		DEPLIBS=`$GCC_PATH/arm-linux-androideabi-readelf -a $VENDOR_PATH/bin/$CPFILE | \
			grep NEEDED | cut -d '[' -f2 | cut -d ']' -f1`
		for LIB in $DEPLIBS;
		do
			if [ ! -f "out/target/product/HM2013022/system/lib/$LIB" ]; then
			  echo "adb pull /system/lib/$LIB $VENDOR_PATH/lib/$LIB"
				adb pull /system/lib/$SR $VENDOR_PATH/lib/$LIB
			fi
		done
	fi
done
cd $PWD