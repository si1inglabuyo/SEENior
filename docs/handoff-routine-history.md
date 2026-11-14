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

Inside "View routine", under the current fingerprint: one small line chart per time block of the
usual stillness, oldest day left, with the starting guess and today's value in a line below
("Started at a guess of 30 min, now 52 min"). Hide it until there are at least two days.

## Rules that still apply

- Log the open of this view to the access log the same way "View routine" is logged. It is a
  history of a person's routine, so it needs the same trail.
- Do not export it, and do not ask for more than 30 days; the server keeps no more.
- Read only. The phone is the only writer.
- Nothing here changes escalation. Do not read it in any alert logic.
