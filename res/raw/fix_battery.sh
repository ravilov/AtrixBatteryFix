#! /system/bin/sh
set -e > /dev/null 2>&1 || :

#-- delete battd data files
if (test -d /data/battd)
then
	:
else
	echo "/data/battd does not exist" >&2
	exit 1
fi
for i in cc_data cc_data_old powerup
do
	test -e "/data/battd/${i}" || continue
	rm "/data/battd/${i}" > /dev/null 2>&1 || :
done

#-- delete battery stats
# DISABLED
#if (test -e /data/system/batterystats.bin)
#then
#	rm /data/system/batterystats.bin > /dev/null 2>&1 || :
#fi

exit "${?}"
