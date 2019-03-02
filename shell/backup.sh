#!/bin/sh
#
# Copyright 2014 Coinport Inc. All Rights Reserved.
# Author: xiaolu@coinport.com (Wu Xiaolu)

day=`date +%Y%m%d`

count=`ls -l /var/bitway/backup/frontend | grep $day | wc -l`
if [ "$count" -eq "0" ];then
  dirname=$day
else
  dirname=$day"-"$count
fi
mkdir -p /var/bitway/backup/frontend/$dirname
echo "====================="
echo "create backup folder : "$dirname
cp /var/bitway/frontend/*.zip /var/bitway/backup/frontend/$dirname
