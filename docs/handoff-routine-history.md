# Handoff: routine history on the barangay dashboard

For the `dashboard` lane (`barangay-dashboard/**`). The backend and the family app are done on
`main`.

**Status 2026-11-14: the dashboard side is already written** (at the repo owner's request), on
branch `dashboard-routine-history` in a separate worktree, based on `origin/main`, **uncommitted
and not pushed**. Files: `src/routineHistory.js` (+ test), `src/components/RoutineTrend.jsx`,
the hook in `RoutineCard.jsx`, the `routine_history_view` access-log action in `audit.js`, and a
few CSS lines. Review it, commit it on your branch, and open the PR. The rest of this note is what
it was built from.

## What exists now

Migration 0024 adds `routine_baseline_history`: one copy of a senior's Routine Fingerprint per
calendar day, the last 30 days only. It is the same median and MAD per feature per time block as
`routine_baseline`, at most 20 rows a day, and it never holds a sensor reading.

```
GET /seniors/{sync_id}/routine/history     (bearer token, same access rule as /routine)
-> { "rows": [ { "day": "2026-11-14", "feature_name": "inactivity_duration",
                 "time_block": "morning", "median_value": 3120.0, "mad_value": 410.0,
                 "sample_count": 14, "is_seed": false, "is_estimate": false }, ... ] }
```

- A responder gets rows only for a senior in their barangay; anyone else gets 404, as with
  `/routine`.
- `feature_name` is one of `inactivity_duration`, `movement_score`, `screen_idle_duration`,
  `screen_unlock_count`, `step_count`. `time_block` is `morning`, `afternoon`, `evening`, `night`.
- Rows come back ordered by `day`, then block, then feature. Up to 600 rows.
- `inactivity_duration` is in seconds: the usual longest stillness in that block.

## What `is_estimate` means (say it in plain words, do not show the field name)

- `false`: the phone saved this copy on that day. It is the fingerprint it really held.
- `true`: the older fingerprints were overwritten and never saved, so the phone worked the day out
  afterwards from its stored daily summaries using today's rules. Close, but not what the detector
  used that day.

The family app draws saved days as a solid line and estimated days as a dotted one, with these
two sentences under the chart. Use the same wording so the two screens agree:

| | English | Filipino |
|---|---|---|
| Estimated days | Earlier days are an estimate worked out from the senior's saved daily activity. | Ang mga naunang araw ay tantiya lamang, batay sa naitalang pang-araw-araw na kilos. |
| Saved days | From [date], the app saved this each night. | Mula [petsa], ito ay naitala ng app tuwing gabi. |

(The family app says "your parent's" in the English line; the dashboard should say "the senior's".
Have a native speaker check the Filipino.)

## Suggested screen

No separate card (it duplicated the Daily Routine card). Inside each time block of "View
routine", under the existing "Still for up to ..." line: a small line chart of the usual
stillness, oldest day left, with "Started at a guess of 30 min, now 52 min" below it. Draw it
only for a block that real days have moved off the setup answer (some day with `sample_count`
above 0; seed cells carry 0), so a new senior sees the card unchanged. The two notes go once at
the bottom of the card, and only when at least one line was drawn.

## Rules that still apply

- Log the open of this view to the access log the same way "View routine" is logged. It is a
  history of a person's routine, so it needs the same trail.
- Do not export it, and do not ask for more than 30 days; the server keeps no more.
- Read only. The phone is the only writer.
- Nothing here changes escalation. Do not read it in any alert logic.

## Addendum 2026-11-15: block hours and "usually active" times (migration 0025)

Same lane, same rules. `GET /seniors/{sync_id}/routine` now also returns a `timing` object (null
until the senior's phone has sent one):

```
"timing": { "morning_start": 360, "afternoon_start": 660, "evening_start": 960, "night_start": 1260,
            "wake_minute": 490, "wake_days": 4, "evening_last_minute": 1120, "evening_last_days": 4 }
```

- The `*_start` figures are minutes after midnight, from the wake and sleep times the senior typed
  at setup. A block ends where the next one starts; night runs on to the next morning's start.
  Show them under each block name: "Morning, 6:00 AM - 11:00 AM". If `timing` is null, show the bare
  block name as today.
- `wake_minute` / `evening_last_minute` are what the phone actually saw: the usual time the senior
  was first active in the morning and last active in the evening, a median over up to 14 days. They
  are null until at least 3 days stand behind them. Say it plainly and with the day count: "Usually
  first active about 8:10 AM (based on 4 days)". Do **not** call it a wake or sleep time.
- Keep the two apart. The hours are the setup answers; the usual times are observed. The blocks do
  not move to follow the observed times, and nothing in detection reads either. Use the same wording
  as the family app (`ui/family/RoutineTimingWording.kt`), including the one-line note under the blocks.
- Limit worth saying if asked: the phone only sees readings inside a block, so someone who gets up
  earlier than the wake time they typed shows the typed time.
