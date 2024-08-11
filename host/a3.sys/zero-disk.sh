#!/bin/bash

sudo dd if=/dev/zero of=/balloon bs=1M; sudo rm /balloon

echo "Disk zeroed"
