#!/usr/bin/env bash
#
# Pulls one tester's local SQLite database off their phone and reports whether the data is
# worth keeping.
#
# MUST be run from Git Bash, never PowerShell: PowerShell's `>` re-encodes binary output as
# UTF-16 and silently corrupts the .db file. That failure is what motivated the whole
# laptop-side pull method.
#
#   ./scripts/pilot-health.sh                 # the only phone plugged in
#   ./scripts/pilot-health.sh 15818705CJ012394 [label]
#
# Pulled databases land in ./seenior-db/, which .gitignore already excludes -- they hold raw
# per-tester behavioural data and must never be committed (CLAUDE.md §11).
#
# Requires: adb on PATH, sqlite3 on PATH, USB debugging on the phone, and the "Allow USB
# debugging?" prompt accepted.

set -euo pipefail

SERIAL="${1:-}"
LABEL="${2:-}"
PKG="com.pup.seenior"
OUT_DIR="${SEENIOR_PULL_DIR:-./seenior-db}"

if [ -n "$SERIAL" ]; then ADB=(adb -s "$SERIAL"); else ADB=(adb); fi

DEVICES=$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | wc -l)
UNAUTH=$(adb devices | awk 'NR>1 && $2=="unauthorized" {print $1}' | wc -l)
OFFLINE=$(adb devices | awk 'NR>1 && $2=="offline" {print $1}' | wc -l)

if [ "$DEVICES" -eq 0 ] && [ "$UNAUTH" -gt 0 ]; then
  echo "Phone is plugged in but NOT AUTHORISED." >&2
  echo "  Unlock the screen and tap 'Allow' on the 'Allow USB debugging?' dialog." >&2
  echo "  No dialog? Unplug, replug, and watch the screen -- it appears once." >&2
  echo "  Still nothing? Developer options -> Revoke USB debugging authorisations, then replug." >&2
  exit 1
fi
if [ "$DEVICES" -eq 0 ] && [ "$OFFLINE" -gt 0 ]; then
  echo "Phone reports 'offline' -- usually a flaky cable or a charging-only one." >&2
  echo "  Try another cable and another port before anything else." >&2
  exit 1
fi
if [ "$DEVICES" -eq 0 ]; then
  echo "No phone connected. Plug it in, unlock it, and accept 'Allow USB debugging?'." >&2
  echo "  If the laptop never sees it at all, USB debugging is off:" >&2
  echo "  Settings -> System -> Developer options -> USB debugging." >&2
  exit 1
fi
if [ "$DEVICES" -gt 1 ] && [ -z "$SERIAL" ]; then
  echo "More than one phone is plugged in. Pass a serial:" >&2
  adb devices | awk 'NR>1 && $2=="device" {print "  " $1}' >&2
  exit 1
fi

MODEL=$("${ADB[@]}" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
BRAND=$("${ADB[@]}" shell getprop ro.product.brand 2>/dev/null | tr -d '\r')
SERIAL_RESOLVED=$("${ADB[@]}" get-serialno | tr -d '\r')
NAME="${LABEL:-$(echo "$MODEL" | tr ' /' '__')}"
STAMP=$(date +%Y%m%d-%H%M)
DEST="$OUT_DIR/${NAME}-${STAMP}"
mkdir -p "$DEST"

echo "=============================================================="
echo " $BRAND $MODEL   ($SERIAL_RESOLVED)"
echo " pulled $(date '+%Y-%m-%d %H:%M')  ->  $DEST"
echo "=============================================================="

# The app must have been opened at least once, or there is no database to read.
if ! "${ADB[@]}" exec-out run-as "$PKG" ls databases/senior_app.db >/dev/null 2>&1; then
  echo
  echo "  NO DATABASE. Either SEENior was never opened on this phone, or this is not a"
  echo "  debug build (run-as only works on debuggable APKs). Nothing to check."
  exit 2
fi

# exec-out cat, not `run-as cp /data/local/tmp`: SELinux blocks the app UID writing there on
# some handsets (confirmed on the Infinix X6885). Nothing is written on-device this way.
for f in senior_app.db senior_app.db-wal senior_app.db-shm; do
  "${ADB[@]}" exec-out run-as "$PKG" cat "databases/$f" > "$DEST/$f" 2>/dev/null || true
done

DB="$DEST/senior_app.db"
[ -s "$DB" ] || { echo "  Pull produced an empty file. Are you in Git Bash?" >&2; exit 2; }

# Opening the db with the -wal/-shm siblings alongside folds them in on close.
sqlite3 "$DB" "PRAGMA integrity_check;" | head -1 | grep -q '^ok$' \
  || { echo "  DATABASE CORRUPT - pull it again." >&2; exit 2; }

sqlite3 "$DB" <<'SQL'
.mode column
.headers on

.print ''
.print '--- who ------------------------------------------------------'
SELECT s.first_name || ' ' || s.last_name AS senior,
       o.wake_time AS wake, o.sleep_time AS sleep,
       CASE WHEN o.has_nap = 1 THEN o.nap_time || ' +' || o.nap_duration_minutes || 'm'
            ELSE 'none' END AS nap,
       o.activity_level AS activity, o.language_preference AS lang,
       date(o.onboarding_completed_at/1000,'unixepoch','+8 hours') AS onboarded
FROM Seniors s LEFT JOIN Senior_Onboarding o ON o.senior_id = s.senior_id;

.print ''
.print '--- is the step counter alive? -------------------------------'
.print '(all zero = no sensor, or Physical activity was denied. Not fatal'
.print ' since v1.8, but it costs the witness that prevents false alarms.)'
SELECT MAX(step_count)      AS max_step_reading,
       MAX(total_steps)     AS max_block_steps,
       CASE WHEN MAX(step_count) > 0 OR MAX(total_steps) > 0
            THEN 'OK - reporting' ELSE 'SILENT - check the permission' END AS verdict
FROM (SELECT step_count, 0 AS total_steps FROM Sensor_Data
      UNION ALL SELECT 0, total_steps FROM Daily_Aggregates);

.print ''
.print '--- sampling today (raw rows, purged each night) --------------'
SELECT COUNT(*) AS rows_today,
       datetime(MIN(timestamp)/1000,'unixepoch','+8 hours') AS first_row,
       datetime(MAX(timestamp)/1000,'unixepoch','+8 hours') AS last_row,
       CAST((MAX(timestamp)-MIN(timestamp))/60000 AS INT) AS minutes_covered
FROM Sensor_Data;

.print ''
.print '--- block health (this is the answer) ------------------------'
.print 'healthy >=55   dozing 48-54   DISCARDED <48'
SELECT date, time_block, sample_count AS n,
       CASE WHEN sample_count >= 55 THEN 'healthy'
            WHEN sample_count >= 48 THEN 'dozing'
            ELSE 'DISCARDED' END AS verdict,
       total_inactivity_duration AS inact, total_steps AS steps
FROM Daily_Aggregates
ORDER BY date DESC,
         CASE time_block WHEN 'morning' THEN 1 WHEN 'afternoon' THEN 2
                         WHEN 'evening' THEN 3 ELSE 4 END;

.print ''
.print '--- summary --------------------------------------------------'
SELECT COUNT(*) AS blocks_recorded,
       SUM(sample_count >= 48) AS usable,
       SUM(sample_count < 48)  AS discarded,
       ROUND(100.0 * SUM(sample_count >= 48) / NULLIF(COUNT(*),0), 1) AS pct_usable
FROM Daily_Aggregates;

.print ''
.print '--- baseline written yet? ------------------------------------'
.print '(empty until the nightly worker has run - it fires ~09:09 and ~21:10)'
SELECT time_block, ROUND(median_value,1) AS med, ROUND(mad_value,1) AS mad,
       sample_count AS n, is_seed,
       datetime(last_updated/1000,'unixepoch','+8 hours') AS updated
FROM Baseline WHERE feature_name = 'inactivity_duration' ORDER BY time_block;

.print ''
.print '--- alerts raised --------------------------------------------'
SELECT COALESCE(datetime(triggered_at/1000,'unixepoch','+8 hours'),'-') AS at,
       trigger_type, risk_level, time_block,
       ROUND(deviation_score,2) AS z, status
FROM Alerts ORDER BY alert_id DESC LIMIT 10;
SQL

echo ''
echo '--------------------------------------------------------------'
echo " Any block under 48 means that phone froze the app. Redo step 4"
echo " of the setup checklist for that brand before the fortnight runs."
echo '--------------------------------------------------------------'
