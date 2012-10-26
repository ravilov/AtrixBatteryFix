#! /system/bin/sh
set -e > /dev/null 2>&1 || :

if (test -e /data/system/batterystats.bin)
then
	rm /data/system/batterystats.bin > /dev/null 2>&1 || :
fi

exit "${?}"
