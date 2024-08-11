#!/bin/bash

if [ "$#" -lt 2 ]; then
	echo "Not enough parameters"
	echo "Usage:"
	echo "$0 <partition path> <ads or ods>"
	echo "Ex:"
	echo "$0 \"/dev/xvdb1\" ads"
	exit 1
fi

DEV=$1
PATH="/mnt/$2"

if [ ! -d ${PATH} ]; then
	echo "Local path (${PATH}) not found."
	exit 1
fi

/usr/bin/sudo /bin/umount ${PATH} 2>/dev/null

/usr/bin/sudo /bin/mount -o noatime,nodiratime,rw "${DEV}" ${PATH} 

if [ $? -ne 0 ]; then
        echo "Failed to mount partition ${DEV}.";
        exit 1
fi

/bin/touch "${PATH}/test.wrt";

if [ $? -ne 0 ]; then
        echo "No write access to volume!"
        sudo /bin/umount ${PATH}
        exit 1
fi

/bin/rm  "${PATH}/test.wrt";


FN="/usr/local/sbin/mount-${2}.sh"
echo "#!/bin/bash" > ${FN}
echo "#Automatically generated ${2} mount script" >> ${FN}
echo "/usr/bin/sudo /bin/mount -o noatime,nodiratime,user,rw \"${DEV}\" ${PATH} " >> ${FN}

/usr/bin/sudo /bin/chmod 755 ${FN}
echo "Share mounted successfully!"
