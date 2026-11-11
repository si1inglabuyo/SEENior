# Evidence note: the "was moving" cutoff for fall context (`FallContext.ACTIVE_MIN`)

Written 2026-11-11 after the defense panel challenged the 1.5 figure.

## What the number is (and is not)
`ACTIVE_MIN` is **an acceleration in m/s², not a walking speed.** `pre_dev` is the mean of
|acceleration − 1 g| over the 2 s before a fall's free-fall phase. The phone's accelerometer
cannot measure speed and the system never claims a speed. `active=1` only means the phone was
moving before the fall rather than lying still.

It is a **record, not a rule.** No detection gate and no `FallTolerance` calculation reads it
(a test asserts that), so this cutoff cannot change whether an alert fires.

## Why we checked
1.5 was an engineering starting point, not a measured value. The only test of it used
synthetic samples we generated ourselves, which proves nothing. The panel's concern, that
seniors walk slowly, was fair.

## Method (reproducible)
- Data: SisFall (Sucerquia, López & Vargas-Bonilla 2017, *Sensors* 17(1):198,
  doi:10.3390/s17010198), elderly subjects SE01–SE15, aged 60–75.
- Computation: exactly the app's `pre_dev`, over non-overlapping 2 s windows at 200 Hz.
  ADXL345 scale 32 g / 2¹³ per count.
- Walking: D01 "walking slowly" (775 windows), D02 "walking quickly" (789 windows).
- Rest: the dataset has no pure-rest class, so the quietest 2 s window of each of 670
  sit / lie / bend trials (D07–D16, which include "wait a moment") stands in for rest.
- Script: `docs/evidence/active_threshold_sisfall.py`. Chart: `active_threshold_sisfall.png`.
  Table: `active_threshold_table.md`.

## Result
| Threshold (m/s²) | Slow walk read as active | Quietest rest read as rest |
|---|---|---|
| 1.5 (old) | 55.9% | 100.0% |
| 1.2 | 78.3% | 99.1% |
| **1.0 (new)** | **90.2%** | **98.2%** |
| 0.8 | 97.4% | 96.0% |

At 1.5, 6 of 15 seniors' slow-walk medians fall below the cutoff (SE02, SE04, SE05, SE09, SE10,
SE15); at 1.0, 2 of 15 do. Fast walking was already 99% active at 1.5. Resting median is 0.14,
95th percentile 0.70, maximum 1.40.

## Decision
`ACTIVE_MIN` moves from 1.5 to **1.0 m/s²**: it keeps about 90% of senior slow-walking windows
on the "moving" side while 98% of at-rest windows stay on the other. Alerts written before the
change keep their own `thr=1.5` in `fall_context`, so each old row still states the cutoff it was
judged against.

## Limits (state these, do not hide them)
- SisFall's sensor is worn at the **waist**. A phone in a pocket, bag or hand reads differently,
  so this checks the idea on real senior gait, not this exact phone.
- The rest class is a proxy (quietest window per trial), not a recording of a senior lying still.
- The dataset gives no speed in m/s; it labels gait as slow or fast.
- SisFall's elderly subjects were healthy volunteers simulating daily activities; frail seniors
  may walk more slowly still.
- The cutoff is a single fixed number. Two of fifteen seniors still sit below 1.0 on slow walks.
- Next step if time allows: one slow-walk recording on the pilot phone to confirm the pocket case.
