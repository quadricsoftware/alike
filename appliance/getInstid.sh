ID=`/usr/bin/xenstore-read vm 2> /dev/null| sed -e 's/\/.*\///g' | awk '{print }'`
if [ ! -z $ID ]; then
echo $ID;
else
ID=`echo $(/usr/sbin/dmidecode -t 4 | grep ID | sed 's/.*ID://;s/ //g')  $(ifconfig | grep eth0 | awk '{print $NF}' | sed 's/://g') | md5sum | awk '{print $1}'`
echo $ID
fi

