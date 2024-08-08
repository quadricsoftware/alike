#!/bin/bash
file=$1
src=$2
dst=$3
prg="/tmp/restore.$4"
bs=$5

if [ $# -ne 5 ]; then
	echo "Insufficient arguments"
	echo Usage: $0 [blkfile] [src] [dst] [position] [blocksize]
	exit
fi

if [ -z "$src" ]; then 
	echo "No src given"
	exit
elif [ -z "$dst" ]; then
	echo "No dst given"
	exit
elif [ -z "$4" ]; then
	echo "No device postition given"
	exit
fi

if [ ! -f $file ]; then
	echo "Fail $file doesnt exist"
	exit
fi

if [ -f $prg ]; then
	rm $prg
fi

total=`cat $file| wc -l`
errcnt=0
loop=0
echo $loop/$total > $prg


makePipes(){
	mkfifo pipe-$$
	exec 3<>pipe-$$
	rm pipe-$$
	local i=$1
	for((;i>0;i--)); do
		printf %s 000 >&3
	done
}
lockedRun(){
    local x
    read -u 3 -n 3 x && ((0==x)) || exit $x
    (
     ( "$@"; )
	printf '%.3d' $? >&3
    )&
}

blockCopy(){
	dd status=noxfer if=$1 of=/dev/$2 seek=$3 skip=$3 bs=${4}K count=1 conv=noerror> /dev/null 2>&1
	#dd status=noxfer if=$1 of=/dev/$2 seek=$3 skip=$3 bs=2048K count=1 conv=noerror> /dev/null 2>&1
	rc=$?; 
	if [[ $rc != 0 ]]; then 
		errcnt=$(( $errcnt + 1))
	fi
}


blankfile=$file".blanks"
if [ -f $blankfile ]; then
	for i in $(cat $blankfile); do
		dd status=noxfer if=/dev/null of=/dev/$3 seek=$i bs=${bs}K count=1 conv=noerror> /dev/null 2>&1
		#dd status=noxfer if=/dev/null of=/dev/$3 seek=$i bs=2048K count=1 conv=noerror> /dev/null 2>&1
	done
fi



N=5
makePipes $N
for i in $(cat $file); do
	lockedRun blockCopy $src $dst $i $bs
	echo $loop/$total > $prg
	loop=$(( $loop + 1))
	f=$(($loop % 1000))
	if [ "$f" -eq 0 ]; then
		echo -n "."
	fi
done
wait
sync
if [[ $errcnt -ne 0 ]]; then
	echo "$errcnt error(s) transferring data to device"
fi
exit 0

