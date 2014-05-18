#!/bin/sh

VENDOR=xiaomi
DEVICE=HM2013022
BASE=../../../vendor/$VENDOR/$DEVICE/proprietary
#rm -rf $BASE/*
# export service depend binary and library
SRS=`adb shell ps | grep system/bin | cut -d 'S' -f2 | cut -d ' ' -f2 | dos2unix | sed 's/\r\n/ /g' | xargs`
PWD=`pwd`

GCC_PATH=../../../prebuilts/gcc/linux-x86/arm/arm-linux-androideabi-4.7/bin
echo "$CP"
for SR in $SRS; 
do
	if [ ! -f "../../../out/target/product/HM2013022$SR" ]; then
		echo "There is no \"out/target/product/HM2013022$SR\""
		CPFILE=`echo $SR |cut -d '/' -f4`
		echo "run adb pull $SR $BASE/bin/$CPFILE"
		adb pull $SR $BASE/bin/$CPFILE
		DEPLIBS=`$GCC_PATH/arm-linux-androideabi-readelf -a $BASE/bin/$CPFILE | \
			grep NEEDED | cut -d '[' -f2 | cut -d ']' -f1`
		for LIB in $DEPLIBS;
		do
			if [ ! -f "../../../out/target/product/HM2013022/system/lib/$LIB" ]; then
			  echo "adb pull /system/lib/$LIB $BASE/lib/$LIB"
				adb pull /system/lib/$SR $BASE/lib/$LIB
			fi
		done
	fi
done


echo "Pulling device files..."
for FILE in `cat proprietary-files.txt | grep -v ^# | grep -v ^$`; do
DIR=`dirname $FILE`
    if [ ! -d $BASE/$DIR ]; then
mkdir -p $BASE/$DIR
    fi
adb pull /system/$FILE $BASE/$FILE
done

#./setup-makefiles.sh
