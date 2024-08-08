#!/bin/bash
if [ $# -ne 4 ]
        then
        echo "Incorrect arguments supplied"                                                            
        echo "Usage: $0 [device num] [src file path] [dest device] [block size (KB)]" 
        exit
fi                                                                                                                                                                                            
devNum=$1
src=$2
dst=/dev/${3}
bs=$4
if [ ! -b "$dst" ]; then
        echo "Couldn't find device: $dst"
        exit
fi
set -e  # Exit immediately if a command exits with a non-zero status
cnt=0                                    
bcnt=0                                   
blankfile="/tmp/${devNum}.blanks"
syncfile="/tmp/${devNum}.sync"
progfile="/tmp/${devNum}.prog"
errorfile="/tmp/${devNum}.errors"
totLoops=$(wc -l < "$syncfile")
blocknumfile="/tmp/${devNum}.blocknum"
export src dst bs total_blocks blocknumfile                                                                                                                                
echo "testing blanks ${blankfile} and ${dst}"                                                                                                                              
if [ -f "$blankfile" ]; then                                                                                                                                               
    cat "$blankfile" | xargs -n 1 -P 4 -I {} dd if=/dev/zero of="${dst}" seek={} bs="${bs}K" count=1 conv=noerror status=none                                              
    bcnt=$(wc -l < "$blankfile")                                                                                                                                           
fi                                                                                                                                                                         
echo "testing blocks ${syncfile} ${src} -> ${dst}"                                                                                                                         
if [ -f "$syncfile" ]; then                                                                                                                                                
        total_blocks=$(wc -l < "$syncfile")                                                                                                                                
        echo "Total blocks: ${total_blocks}"                                                                                                                               
	block_num=0
        echo 0 > "$blocknumfile"  
    cat "$syncfile" | xargs -n 1 -P 4 -I {} sh -c '                                                                                                                        
        block_num=$(cat "$blocknumfile")                                                                                                                                   
        dd if="$1" of="$2" seek="$0" skip="$0" bs="${bs}K" count=1 conv=noerror status=none 2>> "$3"                                                                       
        block_num=$((block_num + 1))                                                                                                                                       
        progress=$((${block_num} * 100 / ${total_blocks}))                                                                                                                 
        echo "$progress%" > "$4"                                                                                                                                           
        echo "$block_num" > "$blocknumfile"                                                                                                                                
    ' {} "${src}" "${dst}" "${errorfile}" "${progfile}"                                                                                                                    
    cnt=$(wc -l < "$syncfile")                                                                                                                                             
    echo "Done. Synced $cnt blocks. Zeroed $bcnt blocks."                                                                                                                  
        echo "100%" > "${progfile}"                                                                                                                                        
fi                                                                                                                                                                         
exit 0   
