#! /system/bin/sh
set -e
SVC='battd'
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
exit "${?}"
