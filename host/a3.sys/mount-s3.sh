#!/bin/bash

if [ "$#" -lt 4 ]; then
	echo "Not enough parameters"
	echo "Usage:"
	echo "$0 <bucket name> <endpoint url> <access key> <secret access key>"
	echo "Ex:"
	echo "$0 mybucket s3.us-east-1.amazonaws.com access-key super-secret-key"
	exit 1
fi

BUCKET=$1
URL=$2
KEY=$3
SECRET=$4
PATH=/mnt/ods

if [ ! -d ${PATH} ]; then
	echo "Local path (${PATH}) not found."
	exit 1
fi

echo "[default]" > s3.creds
echo "aws_access_key_id = ${KEY}" >> s3.creds
echo "aws_secret_access_key = ${SECRET}" >> s3.creds

/usr/bin/sudo /bin/mv -f s3.creds /root/.aws/credentials

/usr/bin/sudo /bin/umount ${PATH} 2>/dev/null

/usr/bin/sudo /sbin/modprobe fuse
/usr/bin/sudo /usr/local/sbin/goofys --endpoint https://${URL} -o allow_other --uid 1000 ${BUCKET} ${PATH}

if [ $? -ne 0 ]; then                                                                                  
        echo "Failed to mount S3 Bucket.";
	echo "run: sudo tail /var/log/messages for details.";
        exit 1
fi
                                           
/bin/touch "${PATH}/test.wrt";
  
if [ $? -ne 0 ]; then
        echo "No write access to S3 storage!"
        sudo /bin/umount ${PATH}
        echo "Unmounted?"
        exit 1                               
fi                              
                         
              
/bin/rm  "${PATH}/test.wrt";


FN="/usr/local/sbin/mount-ods.sh"
echo "#!/bin/bash" > ${FN}
echo "#Automatically generated ODS mount script" >> ${FN}
echo "/usr/bin/sudo /sbin/modprobe fuse " >> ${FN}
echo "/usr/bin/sudo /usr/local/sbin/goofys --endpoint https://${URL} -o allow_other --uid 1000 ${BUCKET} ${PATH} " >> ${FN}
/usr/bin/sudo /bin/chmod 755 ${FN}
echo "Share mounted successfully!"


