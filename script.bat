@ECHO OFF
for /l %%x in (1, 1, %1) do (
	adb   shell am force-stop it.polito.mad.madmax
	sleep 1
	adb  shell am start-activity -W -n it.polito.mad.madmax/.MainActivity | grep "TotalTime" | cut -d ' ' -f 2
)