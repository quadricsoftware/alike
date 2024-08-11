#!/bin/bash

if [ "$#" -lt 4 ]; then
	echo "Not enough parameters"
	echo "Usage:"
	echo "$0 <cifs path> <ads or ods> <user> <pass> <optional domain>"
	echo "Ex:"
	echo "$0 \"//192.168.1.100/some share\" ads backupuser \"secret password\""
	exit 1
fi

SRV=$1
PATH="/mnt/$2"
USER=$3
PASS=$4
DOM=$5

if [ ! -d ${PATH} ]; then
	echo "Local path (${PATH}) not found."
	exit 1
fi

/usr/bin/sudo /bin/umount ${PATH} 2>/dev/null

if [ ! -z ${DOM} ]; then
	/usr/bin/sudo /bin/mount -t cifs -o username=${USER},password=${PASS},domain=${DOM},uid=1000 "${SRV}" ${PATH} 
else
	/usr/bin/sudo /bin/mount -t cifs -o username=${USER},password=${PASS},uid=1000 "${SRV}" ${PATH} 
fi

if [ $? -ne 0 ]; then
	echo "Failed to mount CIFS Share.";
	exit 1
fi

/bin/touch "${PATH}/test.wrt";

if [ $? -ne 0 ]; then
	echo "No write access to CIFS share!"
	sudo /bin/umount ${PATH}
	echo "Unmounted?"
	exit 1
fi


/bin/rm  "${PATH}/test.wrt";

FN="/usr/local/sbin/mount-${2}.sh"
echo "#!/bin/bash" > ${FN}
echo "#Automatically generated ${2} mount script" >> ${FN}

if [ ! -z ${DOM} ]; then
	echo  "/usr/bin/sudo /bin/mount -t cifs -o username=${USER},password=${PASS},domain=${DOM},uid=1000 \"${SRV}\" ${PATH}" >> ${FN}
else
	echo "/usr/bin/sudo /bin/mount -t cifs -o username=${USER},password=${PASS},uid=1000 \"${SRV}\" ${PATH}" >> ${FN}
fi
/usr/bin/sudo /bin/chmod 755 ${FN}
echo "Share mounted successfully!";
