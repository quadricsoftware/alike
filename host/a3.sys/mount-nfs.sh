#!/bin/bash

if [ "$#" -lt 2 ]; then
        echo "Not enough parameters"
        echo "Usage:"
        echo "$0 <nfs path> <ads or ods> <3 or 4>"
        echo "Ex:"
        echo "$0 \"192.168.1.100:/share\" ads "
        exit 1
fi

SRV=$1
PATH="/mnt/$2"
VERS=4
if [ -n "$3" ]; then
    VERS="$3"
fi

if [ ! -d ${PATH} ]; then
        echo "Local path (${PATH}) not found."
        exit 1
fi
/usr/bin/sudo /bin/umount ${PATH} 2>/dev/null

# echo /usr/bin/sudo /bin/mount -o nolock,noatime,nodiratime,user,vers=${VERS} "${SRV}" ${PATH}
/usr/bin/sudo /bin/mount -o vers=${VERS},soft,intr,timeo=100,retrans=5,nolock,noatime,nodiratime,user "${SRV}" ${PATH}

if [ $? -ne 0 ]; then
        echo "Failed to mount NFS Share.";
        exit 1
fi

/bin/touch "${PATH}/test.wrt";

if [ $? -ne 0 ]; then
        echo "No write access to NFS share!"
        sudo /bin/umount ${PATH}
        echo "Unmounted?"
        exit 1
fi

/bin/rm  "${PATH}/test.wrt";


FN="/usr/local/sbin/mount-${2}.sh"
echo "#!/bin/bash" > ${FN}
echo "#Automatically generated ${2} mount script" >> ${FN}
echo "/usr/bin/sudo /bin/mount -o nolock,noatime,nodiratime,user,rw \"${SRV}\" ${PATH} " >> ${FN}

/usr/bin/sudo /bin/chmod 755 ${FN}
echo "Share mounted successfully!"
