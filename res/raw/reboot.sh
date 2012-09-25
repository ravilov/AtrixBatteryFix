#! /system/bin/sh
export PATH="/sbin:/system/sbin:/system/bin:/system/xbin:/data/local/bin"
reboot="`which reboot 2> /dev/null`"
if test -z "${reboot}"
then
	echo "reboot: command not found" >&2
	exit 1
fi
exec "${reboot}" "${@}"
exit "${?}"
