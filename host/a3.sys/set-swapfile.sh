#!/bin/bash

u="$USER"
if [ "$u" == "alike" ]; then
        echo "This script must only be run by root/sudo"
        exit
fi


#default swapfile size: 1G
size=1024

if [ "$#" -eq 1 ]; then
	re='^[0-9]+$'
	if ! [[ $1 =~ $re ]] ; then
		echo "error: Not a number" >&2; exit 1
	else
		size=$1
	fi
fi

echo New swapfile size: ${size}M

if [ -f /swapfile ];then
	/sbin/swapoff /swapfile
	rm /swapfile
fi

/usr/bin/fallocate -l ${size}M /swapfile
/bin/dd if=/dev/zero of=/swapfile bs=1M count=${size}
/bin/chmod 600 /swapfile
/sbin/mkswap /swapfile
/sbin/swapon /swapfile

/bin/sed -i '/swapfile/d' /etc/fstab

echo "/swapfile	swap	swap	defaults 0 0" >> /etc/fstab
