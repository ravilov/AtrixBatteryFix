#! /system/bin/sh
set -e > /dev/null 2>&1 || :

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

exit "${?}"
