#!/bin/bash

base="/tmp/wal-locker"
db="nimbusdb"
type=0
if [ $# -gt 0 ]; then
	if [ ${1} == "read" ]; then
		type=1
	fi
fi

full=${base}${db}.db

if [ ${type} == 0 ]; then
	flock -w 3 ${full} -c "cat ${full}; sleep 2 &" 
	echo $?
else
	flock -w 1 ${full} -c "cat ${full};" 
	echo $?;

fi
