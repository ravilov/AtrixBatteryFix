#! /system/bin/sh
set -e
SVC='battd'
if (test -z "`getprop "init.svc.${SVC}"`")
then
	echo "Service does not exist" >&2
	exit 1
fi
setprop 'ctl.stop' "${SVC}"
timeout=10
while (test "`getprop "init.svc.${SVC}"`" = 'running')
do
	timeout=$(( timeout - 1 ))
	(test "${timeout}" -le 0) && break
	sleep 1
done
sleep 1
setprop 'ctl.start' "${SVC}"
timeout=10
while (test "`getprop "init.svc.${SVC}"`" != 'running')
do
	timeout=$(( timeout - 1 ))
	(test "${timeout}" -le 0) && break
	sleep 1
done
if (test "`getprop "init.svc.${SVC}"`" != 'running')
then
	echo "Unable to restart battd" >&2
	exit 1
fi
exit "${?}"
