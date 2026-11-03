#!/bin/bash
# Measures CarMeter's CPU, memory and launch time on the Pleos emulator.
# Method and results: docs/performance.md.
#
# Before running:
#   - the emulator is booted and the app is installed for the driver user and has consent
#   - adb forward tcp:8088 tcp:8088   (Virtual Console, used to drive vehicle events)
#
# CPU comes from /proc/<pid>/stat (utime + stime) sampled every 2 s. "avg" is the whole
# scenario, "peak" the busiest 2 s window. Both are given against one core and against
# all cores, since the Pleos criterion does not say which one it means. "events" counts
# the vehicle changes the Vehicle Service actually delivered to the app, so a scenario
# whose events never arrived is visible instead of reading as 0% CPU.
#
# The Virtual Console only accepts changes while a browser session is open (it expires
# after 3 s), so the script keeps its own session alive for the whole run.
set -euo pipefail
cd "$(dirname "$0")/.."
. scripts/env.sh
exec </dev/null

PKG=com.garam.hackathon
ACTIVITY=$PKG/.ui.MainActivity
VC=http://127.0.0.1:8088
TICKS_PER_SEC=100
CORES=$(adb shell nproc | tr -d '\r')
INTERVAL=2

vc() {
    local code
    code=$(curl -s -o /dev/null -w '%{http_code}' -X PUT "$VC/vehicle/$1")
    if [ "$code" != 200 ]; then echo "Virtual Console refused $1 (HTTP $code)" >&2; exit 1; fi
}

CLIENT=$(python3 -c 'import uuid; print(uuid.uuid4())')
keep_session() { while true; do curl -s -o /dev/null -X POST "$VC/browser/session?client=$CLIENT"; sleep 1; done; }
keep_session &
SESSION_PID=$!
# wait reports the killed loop as 143; under set -e that would end the trap before the session is closed.
trap 'kill $SESSION_PID 2>/dev/null; wait $SESSION_PID 2>/dev/null || true; curl -s -o /dev/null -X POST "$VC/browser/session?client=$CLIENT&closing=1" || true' EXIT
sleep 2
delivered() { adb logcat -d | grep -c "broadcastPropertyCallback() called with: applicationId = $PKG" || true; }
app_pid() { adb shell pidof $PKG | tr -d '\r'; }
cpu_ticks() { adb shell cat /proc/"$1"/stat | awk '{print $14 + $15}'; }
pss_mb() { adb shell dumpsys meminfo "$1" | awk '/TOTAL PSS:/ {printf "%.0f", $3 / 1024; exit}'; }
now_ms() { python3 -c 'import time; print(int(time.time() * 1000))'; }
show_app() { adb shell am start --user current -n $ACTIVITY >/dev/null 2>&1; sleep 2; }
hide_app() { adb shell am start --user current -a android.settings.SETTINGS >/dev/null 2>&1; sleep 2; }

# One vehicle change per call, alternating seat heat levels (including ventilation) and
# air conditioning, so the collector handles a steady stream of listener events.
STEP=0
vehicle_event() {
    case $((STEP % 6)) in
        0) vc "hvac?driver_seat=1" ;;
        1) vc "hvac?ac=1" ;;
        2) vc "hvac?driver_seat=3" ;;
        3) vc "hvac?ac=0" ;;
        4) vc "hvac?driver_seat=-2" ;;
        5) vc "hvac?driver_seat=0" ;;
    esac
    STEP=$((STEP + 1))
}

# Taps "이전 달" where the wide layout puts it; each tap loads and renders an older month.
ui_event() { adb shell input tap 906 146; }

# measure <label> <seconds> <event function or "none">
measure() {
    local label=$1 seconds=$2 events=$3
    local pid start prev peak=0 end total
    pid=$(app_pid)
    adb logcat -c
    start=$(now_ms)
    prev=$(cpu_ticks "$pid")
    local first=$prev last_ms=$start
    while [ $(( $(now_ms) - start )) -lt $((seconds * 1000)) ]; do
        [ "$events" != none ] && $events
        sleep $INTERVAL
        local ticks ms
        ticks=$(cpu_ticks "$pid"); ms=$(now_ms)
        peak=$(python3 -c "print(max($peak, ($ticks - $prev) / $TICKS_PER_SEC / (($ms - $last_ms) / 1000) * 100))")
        prev=$ticks; last_ms=$ms
    done
    end=$(now_ms)
    total=$(python3 -c "print(($prev - $first) / $TICKS_PER_SEC / (($end - $start) / 1000) * 100)")
    python3 -c "
avg, peak, cores, cpu_s = $total, $peak, $CORES, ($prev - $first) / $TICKS_PER_SEC
print('| $label | $(delivered) | %.2f%% (%.3f%%) | %.2f%% (%.3f%%) | %.2f s | %s MB |' % (avg, avg / cores, peak, peak / cores, cpu_s, '$(pss_mb "$pid")'))"
}

echo "Device: $(adb shell getprop ro.product.model | tr -d '\r'), Android $(adb shell getprop ro.build.version.release | tr -d '\r'), $CORES cores"
echo "Build: $(adb shell dumpsys package $PKG | awk -F= '/versionName/ {print $2; exit}' | tr -d '\r'), debuggable=$(adb shell dumpsys package $PKG | grep -c 'DEBUGGABLE' || true)"
echo
echo "| Scenario | Events delivered | CPU avg, one core (all cores) | CPU peak 2 s, one core (all cores) | CPU time | PSS |"
echo "|---|---|---|---|---|---|"

vc "gear?gear=Park"; vc "controls?speed=0&accelerator=0&brake=0&steering=0"
hide_app
measure "Parked, app in background, no events (60 s)" 60 none
measure "Parked, app in background, event every 2 s (60 s)" 60 vehicle_event

vc "gear?gear=Drive"; vc "controls?speed=15&accelerator=30&brake=0&steering=0"
measure "Driving, app in background, event every 2 s (120 s)" 120 vehicle_event
show_app
measure "Driving, driving summary on screen, event every 2 s (60 s)" 60 vehicle_event

vc "controls?speed=0&accelerator=0&brake=0&steering=0"; vc "gear?gear=Park"
sleep 2
measure "Parked, bill on screen, idle (30 s)" 30 none
measure "Parked, bill on screen, month change every 2 s (30 s)" 30 ui_event
vc "hvac?driver_seat=0"; vc "hvac?ac=0"

echo
echo "Launch (am start -W TotalTime):"
for i in 1 2 3; do
    adb shell am force-stop $PKG
    sleep 2
    echo "  cold $i: $(adb shell am start -W --user current -n $ACTIVITY | awk -F': ' '/TotalTime/ {print $2}' | tr -d '\r') ms"
done
for i in 1 2 3; do
    hide_app
    echo "  warm $i: $(adb shell am start -W --user current -n $ACTIVITY | awk -F': ' '/TotalTime/ {print $2}' | tr -d '\r') ms"
done
