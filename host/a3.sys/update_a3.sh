#!/bin/bash

if [ "$1" = "-h" ]; then
	echo "Usage:"
	echo "no arguments: update A3 software/scripts only"
	echo "-sys [updates the A3 system packages]"
	echo "-alike [updates the Alike docker images]"
	exit
fi

force=0
if [ "$1" = "-f" ]; then
	force=1
fi

if [ ${force} -eq 0 ]; then
	LOCAL_BLD=0
	if [[ -f /usr/local/sbin/rev.num ]];then
		LOCAL_BLD=`cat /usr/local/sbin/rev.num`
	fi
	CURRENT=`wget -qO - 'https://raw.githubusercontent.com/quadricsoftware/alike/main/host/a3.rev.num'`
	
	echo "Us: ${LOCAL_BLD} vs them: ${CURRENT}"
	if [ ${CURRENT} -gt ${LOCAL_BLD} ]; then
		echo We need to update!
	else
		echo "No need to update (rev: ${LOCAL_BLD})"
		exit 0
	fi
fi

wget -qO qs.sh 'https://raw.githubusercontent.com/quadricsoftware/alike/main/host/a3_update.sh' && bash qs.sh -quiet $@
if [[ -f qs.sh ]]; then
        rm qs.sh
fi

echo "Done locally"
