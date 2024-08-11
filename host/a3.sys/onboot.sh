#!/bin/bash
sysctl -w net.ipv4.ip_forward=1                                                                                                                                    
sysctl -w net.ipv6.conf.all.disable_ipv6=1
sysctl -w net.ipv6.conf.default.disable_ipv6=1
/sbin/modprobe fuse
/sbin/modprobe nfsd

export HOME=/root

echo Boot time: `date` > /tmp/boot.log

if [ ! -f /swapfile ]; then
	echo "Setting swap file" >> /tmp/boot.log
	/usr/local/sbin/set-swapfile.sh >> /tmp/boot.log
else
	echo "Found existing swapfile" >> /tmp/boot.log
fi

/usr/local/sbin/update_a3.sh -quiet >> /tmp/boot.log
                                                                                                                                                                   
BASE="/usr/local/sbin"
ADS="${BASE}/mount-ads.sh"
ODS="${BASE}/mount-ods.sh"

/usr/bin/xenstore-ls | grep unique-domain-id | awk '{print $3}' | tr -d '"' > /home/alike/configs/a3.vm.guid

echo Starting datashare mounts >> /tmp/boot.log
if [ -f  ${ODS} ]; then
	echo Mounting ODS... >> /tmp/boot.log
	eval $ODS >> /tmp/boot.log
	echo "Result: $? ">> /tmp/boot.log
fi  

if [ -f  ${ADS} ]; then
	echo Mounting ADS... >> /tmp/boot.log
	eval $ADS >> /tmp/boot.log
	echo "Result: $? ">> /tmp/boot.log
fi  
sleep 1


#ip=`sqlite3 /mnt/ads/prodDBs/nimbusdb.db "select val from settings where name='hostIP'"| tr -d '\n'`
sys=`ifconfig eth0| grep "inet addr" | tr -s ":" " " | awk {'print $3'}| tr -d '\n'`
bld=$(</usr/local/sbin/rev.num tr -d '\n')

if [ ! -f /home/alike/.env ]; then 
	echo "HOST_IP=${sys}" > /home/alike/.env
	echo "HOST_BLD=${bld}" >> /home/alike/.env
fi

chown alike.alike /home/alike/.env

FILE_PATH="/home/alike/docker-compose.yml"                                                                                                                
if ! grep -q "environment" "$FILE_PATH"; then                                                                                                                 
    line_number=$(awk '/cap_add:/ {print NR ; exit}' "$FILE_PATH")                                                                                    
    if [ -n "$line_number" ]; then                                                                                                                        
        sed -i "${line_number}i\ \ \ \ \ \ \ \ environment:" "$FILE_PATH"                                
    fi                                                                                                                                                    
fi 
if ! grep -q "HOST_IP" "$FILE_PATH"; then                                                                                                                 
    line_number=$(awk '/environment/ {print NR + 1; exit}' "$FILE_PATH")                                                                                    
    if [ -n "$line_number" ]; then                                                                                                                        
        sed -i "${line_number}i\ \ \ \ \ \ \ \ \ \ \ \ - HOST_IP=\$\{HOST_IP:?The HOST_IP environment variable is missing from your /home/alike/.env file\}" "$FILE_PATH"
    fi                                                                                                                                                    
fi 
if ! grep -q "HOST_BLD" "$FILE_PATH"; then
    line_number=$(awk '/environment/ {print NR + 1; exit}' "$FILE_PATH")
    if [ -n "$line_number" ]; then
        sed -i "${line_number}i\ \ \ \ \ \ \ \ \ \ \ \ - HOST_BLD=\$\{HOST_BLD:?The HOST_BLD environment variable is missing from your /home/alike/.env file\}" "$FILE_PATH"
    fi
fi


#if [ "$ip" != "$sys" ]; then
#        echo "Updating hostIP from: $ip to: $sys" >> /tmp/boot.log
#        sqlite3 /mnt/ads/prodDBs/nimbusdb.db "update settings set val=\"$sys\" where name='hostIP'"
#fi

echo "Copying timezone file to config"
cp -f /etc/localtime /home/alike/configs/localtime

