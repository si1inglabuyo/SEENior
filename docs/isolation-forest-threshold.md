# Isolation Forest threshold (Layer 2): where 0.64 comes from

Written 2026-10-14. Reproduce with `ThresholdCalibrationTest` (prints the table below) and
`IsolationForestTest` (asserts it).

## What was wrong
The nightly 22:05 run flagged three busy afternoons (10-10, 10-11, 10-12) as medium and the
senior cancelled each. Their z-scores: steps +3.2, unlocks +7.5, steps +9.3 (one evening +26.8).
The forest scores "unusual", and more activity is as unusual as less. Layer 2 now clips each
feature to the worrying side (movement, unlocks, steps: z <= 0; inactivity, screen idle: z >= 0).

## Why the old threshold (0.58) could not stay
It was picked from one draw per scenario on a simulator whose ordinary days varied by +/-5%
(about 0.1 z). Real ordinary blocks vary about 1.5 z (robust sd of the pilot's 2026-10-01..13
blocks: inactivity 2.0, idle 1.5, unlocks 1.5, steps 1.4, movement 0.5). The simulator now
takes that spread (`AggregateSimulator.ORDINARY_SPREAD`).

## Method
For each scenario: 200 independent runs, each training a fresh forest on 56 simulated block-days
(a fortnight) and scoring one new day. Share of runs at or above each threshold:

| Scenario | 0.58 | 0.60 | 0.62 | 0.64 | 0.66 | 0.68 |
|---|---|---|---|---|---|---|
| long stillness (should flag) | 99% | 97% | 94% | 90% | 70% | 46% |
| combination, mild (should flag) | 70% | 58% | 47% | 36% | 23% | 10% |
| mild total stillness (Layer 1's case) | 43% | 31% | 17% | 11% | 3% | 1% |
| ordinary day (should not) | 16% | 10% | 5% | 1% | 0% | 0% |
| lazy Sunday (should not) | 27% | 18% | 10% | 4% | 1% | 0% |
| energetic day, 3x steps (should not) | 10% | 4% | 2% | 1% | 0% | 0% |
| very energetic, 10x (should not) | 8% | 3% | 1% | 0% | 0% | 0% |

0.64 is the point where ordinary-day false flags are ~1% while the clear positive still flags 90%.
With four blocks a day, 0.62 would flag a normal block on roughly one night in five.

## Real-data check (sanity only, no labels)
Replaying the pilot's 83 usable blocks since 2026-09-20 (Python port, 8 seeds averaged): 5 blocks
at >= 0.64: 09-20 morning, 09-27 afternoon, 09-28 morning and afternoon, 10-11 morning. All are
stillness or idle (inactivity z +5.8 to +10.7). None of the 10-10..12 afternoons flag.

## Limits to state to the panel
Rates are on simulated days; no real emergency data exists, and collecting it would be unethical.
The threshold is an engineering choice backed by measured separation, not a validated accuracy.
A mild morning (steps -2.2 z, inactivity normal) is mostly invisible to Layer 2 by design.

## Does one threshold suit every senior?
Same method, three simulated seniors whose ordinary days vary by 0.75, 1.5 (the pilot) and 3.0 z.
Share flagged at 0.64:

| Scenario | Steady (0.75) | Pilot (1.5) | Irregular (3.0) |
|---|---|---|---|
| long stillness (should flag) | 98% | 90% | 53% |
| ordinary day (should not) | 2% | 1% | 1% |
| energetic day (should not) | 1% | 1% | 1% |
| lazy Sunday (should not) | 12% | 4% | 2% |

False flags stay at 1-2% for everyone, because the forest is trained on each senior's own days.
Detection of stillness falls for a very irregular senior (their own normal is wide). Lowering the
line for them would raise false flags (8% at 0.62), so a fixed 0.64 is kept. Layer 1, fall
detection and SOS still cover that senior.
