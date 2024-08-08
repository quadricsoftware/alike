#!/bin/bash
src=$1
dst=$2
output=$3
blank=$4

if [ $# -ne 4 ]; then
	echo "Insufficient arguments"
	echo Usage: $0 [current file] [prev file] [output file] [blank print]
	echo "Produces a bkl file"
	echo "current: current hcl"
	echo "previous: previous hcl"
	exit
fi

if [ -z "$src" ]; then 
	echo "No src given"
	exit
elif [ -z "$dst" ]; then
	echo "No dst given"
	exit
elif [ -z "$output" ]; then
	echo "No output file given"
	exit
fi

if [ ! -f $src ]; then
	echo "Fail $src doesnt exist"
elif [ ! -f $dst ]; then
	echo "Fail $dst doesnt exist"
else
	if [ -f $output ]; then
		rm $output
	fi
	blanks=$output".blanks"
	awk -v srccy="$src" '{ getline x< srccy } $0!=x && $0 == "'${blank}'" { print NR-1 }' $dst > $blanks
	awk -v srccy="$src" '{ getline x< srccy } $0!=x && $0 != "'${blank}'" { print NR-1 }' $dst > $output
#	These do not work, so don't be tempted to try them
#	sdiff $src $dst | sed -n '/|\|</=' > $output
#	diff -yZ $src $dst | grep -n \| |tr ':' ' ' | awk {'print $1'} > $output

	cat $output | wc -l

	exit 0
fi

exit 0
