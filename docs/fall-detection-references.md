# Layer 0 fall detection: where the thresholds come from

Written 2026-10-14 after the defense panel asked where the numbers come from. Every figure
below was read from the source named beside it. Where a figure is **not** in a published source,
the table says so.

## Verified references

1. **Bourke, A. K., O'Brien, J. V., & Lyons, G. M. (2007).** Evaluation of a threshold-based
   tri-axial accelerometer fall detection algorithm. *Gait & Posture, 26*(2), 194–199.
   https://doi.org/10.1016/j.gaitpost.2006.09.012
   (Bibliographic record checked on Crossref. The abstract names the method, a threshold on the
   resultant acceleration from a trunk-worn sensor, but gives no value; the values below are
   taken from reference 2, which reports them.)

2. **Bagalà, F., Becker, C., Cappello, A., Chiari, L., Aminian, K., Hausdorff, J. M.,
   Zijlstra, W., & Klenk, J. (2012).** Evaluation of accelerometer-based fall detection
   algorithms on real-world falls. *PLoS ONE, 7*(5), e37062.
   https://doi.org/10.1371/journal.pone.0037062
   (Read on the publisher's page. It re-implements the algorithms of Bourke and Kangas and
   states their thresholds.)

3. **Kangas, M., Konttila, A., Lindgren, P., Winblad, I., & Jämsä, T. (2008).** Comparison of
   low-complexity fall detection algorithms for body attached accelerometers. *Gait & Posture,
   28*(2), 285–291. https://doi.org/10.1016/j.gaitpost.2008.01.003
   (Bibliographic record checked on Crossref. Its thresholds are quoted from reference 2.)

4. **Li, Q., Stankovic, J. A., Hanson, M. A., Barth, A. T., Lach, J., & Zhou, G. (2009).**
   Accurate, fast fall detection using gyroscopes and accelerometer-derived posture information.
   *2009 Sixth International Workshop on Wearable and Implantable Body Sensor Networks*,
   138–143. https://doi.org/10.1109/BSN.2009.46
   (Full text read.)

5. **Sucerquia, A., López, J. D., & Vargas-Bonilla, J. F. (2017).** SisFall: A fall and movement
   dataset. *Sensors, 17*(1), 198. https://doi.org/10.3390/s17010198
   (Bibliographic record checked on Crossref; open access, CC BY 4.0. Cited as the public
   dataset to validate against, not for a threshold.)

## What the sources say, against what the app uses

Unit conversions: 1 g = 9.81 m/s²; 1 rad/s = 57.3 °/s.

| Gate | SEENior value | Published values | Verdict |
|---|---|---|---|
| **Free fall** (lower threshold) | below **3.0 m/s² = 0.31 g**, for 100–2000 ms | Bourke: lower fall threshold 0.41 g (original), 0.73 g (retuned by Bagalà), 0.65 g (waist variant). Kangas: start of fall when signal is below 0.6 g. (Refs 1–3) | **Stricter** than every published value. The 100–2000 ms duration window is not from a source. |
| **Impact** (upper threshold) | at least **25.0 m/s² = 2.55 g**; at most 32.5 m/s² = 3.31 g once tolerance has raised it | Bourke: upper fall threshold 3.52 g (original), 1.79 g (retuned), 2.8 g (waist variant). Li et al.: peak acceleration thresholds 3.0 g (chest) and 2.5 g (thigh). (Refs 1, 2, 4) | **Inside** the published range of about 1.8 to 3.5 g. |
| **Rotation** | at least **2.0 rad/s = 115 °/s** | Li et al.: peak angular rate thresholds 200 °/s (chest) and 340 °/s (thigh); a body at rest stays under 60 °/s. (Ref 4) | **More permissive** than Li's chest threshold, but above the resting level. See the caveat below. |
| **Stillness** after impact | at least **8 s**, counted after a 1.5 s settle period | Posture checked about 2 s after impact (Kangas); lying judged over the 1 to 3 s after impact (Bourke waist variant). (Refs 2, 3) | **Much longer** than published. **Not from a source.** It is a deliberate choice to keep false alarms down, because a phone cannot tell posture the way a worn sensor can. |
| Sampling rate | 50 Hz with hardware batching | Li et al.: 120 Hz. | Lower. Not validated against a published minimum. |

## Caveats to state out loud

* **These papers use body-worn sensors** on the trunk, waist or thigh. A phone in a pocket or
  hand sits differently, so published thresholds are a guide, not a guarantee.
* **Simulated falls are easier than real ones.** Bagalà et al. report that algorithms which
  worked on simulated falls "did not perform well" on real-world falls, with sensitivity
  falling to 57.0% (±27.3%). SEENior's own evidence is one real throw that confirmed and one
  soft-cushion drop that did not (2026-08-17).
* **Li et al. state that thresholds depend on the person** (height, weight, age) and that more
  work is needed. That is the reason fall tolerance adapts the impact and stillness gates.
* **The three Bourke figures** (3.52 / 0.41 g original; 1.79 / 0.73 g retuned; 2.8 / 0.65 g
  waist variant) are as reported by Bagalà et al., not read from Bourke's full text.

## What has not been done

The thresholds were **not** changed to match the literature. Doing so without validation could
trade one problem for another: loosening free fall to 0.6 g would make the detector fire more
easily, and we already had 15 false alarms in 14 days from the stillness gate. The honest
position is: impact sits inside the published range, rotation sits below Li's chest figure,
and free fall and stillness are stricter than the literature on purpose.

**To turn this into accuracy figures** replay the SisFall dataset (reference 5) through
`FallDetector` and report how many falls it confirms and how many everyday activities it
wrongly flags. Not yet done.
