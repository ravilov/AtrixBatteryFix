#! /system/bin/sh
set -e > /dev/null 2>&1 || :
if !(test -d /data/battd)
then
	echo "service data not found" >&2
	exit 1
fi
battd_user="`
	( ps 2>/dev/null || : ) | while read user pid ppid vsize rss wchan pc state name args
	do
		case "${name}" in
			battd|*/battd)
				echo "${user}"
				;;
			*)
				;;
		esac
	done
`"
if (test -n "${battd_user}")
then
	if (test -d /data/battd)
	then
		busybox chown -R "${battd_user}:${battd_user}" /data/battd > /dev/null 2>&1 || :
		busybox chmod 02770 /data/battd
	fi
	if (test -d /pds/public/battd)
	then
		busybox chown -R "${battd_user}:${battd_user}" /pds/public/battd > /dev/null 2>&1 || :
	fi
	if (test -e /system/bin/battd)
	then
		m=''
		( : > /system/bin/.test ) > /dev/null 2>&1
		if (test -e /system/bin/.test)
		then
			rm /system/bin/.test
		else
			busybox mount -oremount,rw /system
			m='yes'
		fi
		busybox chown "${battd_user}" /system/bin/battd > /dev/null 2>&1 || :
		busybox chmod 0755 /system/bin/battd
		if (test -n "${m}")
		then
			busybox mount -oremount,ro /system
		fi
	fi
fi
exit "${?}"
