# Chapter 4 and 5 outline: what the data can support (drafted 2026-11-16)

Source papers: `Group3_Chap 1-3 [FINAL REVISED PAPER] (1).pdf` (Chapters 1-3), CLAUDE.md, Supabase, `docs/`.
**Updated 2026-11-16 (evening).** The production database was cleaned after the first draft: the 181 alerts of the 32 removed seniors are gone, so the counts in 4.3 and 4.4 below were true *before* the cleanup and cannot be re-run. Use the CSV export if one was taken, or the figures as quoted in the 2026-11-16 session. What the cloud holds now is in section 4.11.

Rule for this file: every figure below comes from a source named next to it. Anything marked **GAP** does not exist yet and must be collected or stated as a limitation. Nothing here is invented.

---

## A. Fix Chapter 1-3 first (the paper contradicts the system)

A panelist who reads Chapter 3 and then Chapter 4 will find these. Either amend Chapter 3 or state them as deviations in Chapter 4.

| # | Chapter 3 says | The system actually does |
|---|---|---|
| 1 | All personal data and contact lists stay on the phone, "never uploaded" (Security Procedures; Ethical Considerations, third measure) | Contacts, pairing and alert metadata live in cloud PostgreSQL. Since 2026-10-19 the Routine Fingerprint syncs; since 11-14 a 30-day history of daily fingerprint summaries; 11-15 routine timing; 11-10 the 2-second `fall_trace`. `Sensor_Data` and `Daily_Aggregates` never leave. Justification: RA 10173 §12(c). (CLAUDE.md §11) |
| 2 | "Does not require a centralized web server" (Implementation Plan) | FastAPI backend on Render with Supabase PostgreSQL |
| 3 | SMS and push via Firebase and **Twilio** (Turnover) | **Semaphore PH** |
| 4 | Charging-related signal is part of the fingerprint and anomaly detection (Objective 1, Purpose) | Charging is context only. No `charging` alert has ever been produced: 0 of 214 alerts in the database. Detector signals are inactivity, movement, screen idle (CLAUDE.md §5; `MedianMadDetector.kt`) |
| 5 | Limitation 11 assumes an existing 24/7 barangay or emergency structure | The barangay tier is Monday-Friday, 8:00-17:00 Manila time. After hours alerts wait on the dashboard. The apps say so and no response time is promised (CLAUDE.md §7, §12) |
| 6 | Objective 6: one "unified contact app" | Family app (Android) and barangay dashboard (web) are separate |
| 7 | Evaluation Plan compares against **five** objectives; Table 48 is scrambled and contains a stray "Weighted mean 3.40 (Agree)" | Chapter 1 lists **six** objectives. Rebuild Table 48 |
| 8 | Alert location described only as "location associated with the alert" | A precise ~5 m geohash, not anonymous. State the §12(c) basis plainly. Never write "anonymous" or "cluster" (CLAUDE.md §11, §14) |
| 9 | Test plan "Actual Output" columns (Tables 36-43) are all filled "Pass", for example H1 "25 minutes", H2 "4 minutes 32 seconds" | Each Actual must be backed by a log, screenshot or database row. If it was written before it was run, rerun it or mark it as planned |
| 10 | Wellness prompt shown as "Are you safe?" (Scope) | Shipped wording is "Are you safe and well?" and "Ligtas po ba kayo? Maayos po ba kayo?" |
| 11 | Objectives measure accuracy only through survey and logs. There is no accuracy or false-positive test, although CLAUDE.md §10 sets targets of at least 85% accuracy and at most 15% false positives | Add a simulated-injection accuracy section (see Chapter 4, section 4.3) |

---

## B. Chapter 4 outline: Results and Discussion

### 4.1 Pilot setup and what actually ran
- Devices and roles: ~11 senior phones registered in the cloud; three real seniors in the go-live (confirm the number and names with the team, do not name them in the paper). Source: `seniors` table.
- Dates: go-live 2026-11-16. **The four-week pilot in Chapter 3 has not elapsed.** State the real observation window. Do not describe a four-week result.
- Deviations from Chapter 3 (table A above).

### 4.2 Objective 1: Routine Fingerprint (14-day baseline)
| Evidence | Value | Source |
|---|---|---|
| Phones with at least one learned (non-seed) cell | 1 of the phones checked on 2026-11-15 (Alfreda, 10 cells). Althea has 10 partly learned cells (morning and evening, 3 samples each). All others 0 | `routine_baseline`, queried 2026-11-15/16 |
| Daily copies of the fingerprint | 30-day history exists for some phones, all seed for the tested phone | `routine_baseline_history` |
| Why most phones are not learning | A block counts only at 80% sampling coverage and 3 or more days (`AggregateFeatures.kt:77`, `BaselineUpdater.kt:24`); phones go quiet for 1.5-2 h (heartbeat gaps) | code; `seniors.last_seen_at` |
**GAP:** "fingerprint stored by day 14" cannot be shown for most seniors. Report it as not met for most, with the cause (OS background restrictions) and the cure applied (battery exemption). Re-query after the nightly run before writing the final number.

### 4.3 Objectives 2, 4, 5: detection accuracy (new section, add to Chapter 3 too)
Use simulated injection, as CLAUDE.md §10 requires.
- Layer 1: `AnomalySimulator` injects a known z-score; report detection against known ground truth. Note the 0.6745 scale change on 2026-11-13 and that the pilot-era thresholds were measured on the old scale.
- Layer 2: `ThresholdCalibrationTest`, `docs/isolation-forest-threshold.md`: about 90% of a long stillness flagged, about 1% of an ordinary day, over 200 simulated runs per scenario. **These are simulated rates, not validated accuracy.**
- Layer 3: fuzzy classifier unit tests.
- Real alerts: counts by status are in the table below (pre-cleanup). Now: 53 alerts (14 SOS, 23 falls, 16 anomalies), 8 marked `false_positive`. **Do not compute a false-positive rate from `status` alone.** "resolved" mixes drills, tests and real events, and only `false_positive` is an explicit label.

| trigger | resolved | false_positive | escalated | total |
|---|---|---|---|---|
| sos | 100 | 2 | 0 | 102 |
| fall_pattern | 67 | 6 | 1 | 74 |
| inactivity | 30 | 3 | 0 | 33 |
| screen_idle | 26 | 4 | 0 | 30 |
| movement | 3 | 0 | 0 | 3 |
| ml_flag | 1 | 0 | 0 | 1 |
(Supabase, 2026-11-16, **before** the cleanup; 244 alerts over 40 seniors.) **GAP:** to get an honest false-positive figure, label each real-person alert as true event, drill, or false alarm, with the responder's `remarks`, and separate test accounts.

### 4.4 Objective 3: fall detection
- Evidence: 74 fall alerts before the cleanup, 23 now; thresholds and sources in `docs/fall-detection-references.md`; 8-person walk test and SisFall comparison in `docs/evidence/`; `ACTIVE_MIN` moved 1.5 to 1.0 on 2026-11-12.
- Say plainly: thresholds are engineering starting points, not validated. 15 false positives in 14 days were seen in the earlier pilot (2026-09-19 note). Accuracy needs a dataset replay (SisFall), which has not been done.

### 4.5 Escalation and delivery (the strongest section)
- SOS drill 2026-11-10: barangay reached at +12.9 s (target 30 s met). Source: Supabase `escalation_steps` for that alert.
- Chain: senior 60 s (120 s under fall tolerance), family 600 s, barangay after hours waits for duty hours. Source: CLAUDE.md §7.
- Offline behavior: queued locally, delivered on reconnect (test G1-G2). **GAP:** attach a log of one real offline event.
- SMS fallback: working as of 2026-09-27.
- **Delivery time is `created_at - triggered_at`, not `created_at` alone.** `created_at` is when the server received the alert, after the senior's own window, so it hides that window; `triggered_at` is when the phone's detector fired. Before the cleanup the medians were: SOS 10.9 s, falls 60.9 s, routine anomalies about 600 s (the family window), with outliers of hours for alerts queued offline. All database times are UTC; Manila is +8.
- Compute delivery from `triggered_at` and `escalation_steps` with a query on the current rows; do not estimate. State where each time is measured (swipe to barangay, or server arrival).

### 4.6 Objective 6: family and barangay interfaces
- Features exist (acknowledge, escalate, false-positive, history, hall-closed days, after-hours notice, routine card). Cover with the UAT survey.
- **GAP:** no usability data. Collect it (4.8).

### 4.7 Performance and battery
- **Decision 2026-11-16: the survey is the only battery evidence.** Android gives an app no per-app battery figure, and the cloud keeps only the latest battery reading, so there is no measured drain. Report it as *perceived* battery impact (Performance Efficiency items, seniors and family), not as "under 10%". Change the Table 48 measurement method from "Battery Stats" to the survey, or say the 10% target was not measured directly. Earlier single-phone figure (0.097%, Infinix, 2026-09-02) may be quoted as an informal check only.
- Suggested items (Likert 1-5): the app did not make the phone's battery run out noticeably faster; I did not need to charge more often; the app ran in the background without slowing the phone. Add one open question about anything odd noticed.

### 4.8 UAT survey (ISO/IEC 25010, Likert 1-5)
- **GAP:** 30 participants not surveyed. Chapter 3 fixes the instrument (eight characteristics) and the formulas (frequency, percentage, WM, AWM, thematic analysis).
- Minimum viable: survey the real seniors, their family contacts, and the responder this week. Report n honestly, for example "n = 9". Do not scale up to 30.

### 4.9 Privacy and security findings
- What leaves the phone and the §12(c) basis (list in A.1). Role scoping returns 404 for others. Crash reports carry no identity.
- Known gaps to disclose: a silent phone does not notify family (decided 2026-11-10, not built); a senior living alone with no family and no contact has nobody after hours.

### 4.10a What the cloud records now (v1.72-v1.74)
| Need | Where | Notes |
|---|---|---|
| Alerts that escalated | `alerts` (+ `escalation_steps`, `triggered_at`, `fall_evidence`, `outcome_reason`) | cancelled-in-window alerts are not here |
| Fall or anomaly the senior cancelled | `alert_outcomes` (type, risk, time block, fired-at, answer, `cancelled_after_seconds`) | needs v1.72+; the answer is required, so every new one lands here when the phone is online |
| A cancelled SOS (accidental swipe) | `alert_outcomes` with `sos_cancelled` and `cancelled_after_seconds` | needs v1.73+; no question is asked |
| The 5-minute cycle | `sampling_daily` (readings per block per day, about 288 a day) | needs v1.73+; a day appears after the nightly job; counts only |
| Fingerprint learned or not | `routine_baseline` (`is_seed`, `sample_count`) and `routine_baseline_history` | |
| Survey, UAT times, battery, accuracy by simulation | **not in the database** | paper survey; unit tests and `AnomalySimulator` |
Analysis of cancelled alerts reads two places: `alerts.outcome_reason` (escalated) and `alert_outcomes` (cancelled in the window). The privacy basis for `alert_outcomes` and `sampling_daily` is participant consent (CLAUDE.md section 11), so the consent form must mention both.

### 4.10 Discussion
Tie each finding back to the literature in Chapter 2, which I have only partly read. Read the rest of Chapter 2 before writing this section.

---

## C. Chapter 5 outline

### 5.1 Summary of findings
One paragraph per objective (1 to 6), each stating **met / partially met / not yet shown** with the number behind it. Suggested honest status:
1. Fingerprint: partially met (learning in a minority of phones).
2. Real-time detection every 5 min: met in operation; accuracy shown by simulation only.
3. Fall detection: built and running; accuracy not validated.
4. Isolation Forest: built; simulated rates only.
5. Fuzzy risk and escalation: met (SOS +12.9 s; chain proven).
6. Contact apps: built; usability not yet surveyed.

### 5.2 Conclusions
Only claims the evidence supports: the SOS and escalation path is reliable and fast; passive personalization is working in some phones and limited by OS background restrictions on others; detection thresholds are not yet validated.

### 5.3 Recommendations
- Run the full four-week pilot with the 30 participants, with a per-brand battery setup guide.
- Validate fall thresholds by SisFall replay and a senior walk test.
- Re-verify Layer 1 on the new 0.6745 scale.
- Notify family when a heartbeat goes missing (not built, deliberate).
- Make the barangay tier 24/7 or add an after-hours duty contact.
- Seed the baseline from observed times (blocks that follow the senior) (considered, not built).
- Expand beyond Barangay U.P. Campus.

### 5.4 Limitations (carry over Chapter 1 and add)
Android only; phone-left-at-home; not a diagnostic tool; barangay duty hours; sample size; simulated accuracy; thresholds not validated; OS background restrictions on some brands.

---

## D. What to collect before writing (ordered by value)
1. **Export the cloud tables now** (CSV from the SQL Editor): `alerts`, `alert_outcomes`, `sampling_daily`, `routine_baseline`. The cleanup showed rows can be lost; the export is the Chapter 4 evidence.
2. Re-run the fingerprint query after the nightly job (learned cells per phone), and `sampling_daily` for the coverage figure (total / 288 per day).
3. Delivery times from `triggered_at` on the current rows (see 4.5), and the cancelled-alert counts from `alert_outcomes`.
4. Label real alerts (true event, drill, false alarm) with the responders' remarks.
5. **Survey** of real participants (n reported as-is), including the battery items in 4.7. Battery is survey-only by decision.
6. Accuracy by simulated injection (`AnomalySimulator`, `ThresholdCalibrationTest`); record the pass count (Android 343, backend 337 as of 2026-11-16).
7. A log or screen recording of one offline-then-reconnect event.
8. Read the rest of Chapter 2 and the use case reports and data dictionary in Chapter 3, so the discussion cites them.
