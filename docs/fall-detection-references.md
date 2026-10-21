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

6. **Gimpel, T., Kiertscher, S., Lindemann, A., Schnor, B., & Vogel, P. (2015).** Evaluation of
   threshold-based fall detection on Android smartphones. *Proceedings of the 8th International
   Conference on Health Informatics (HEALTHINF 2015)*, SCITEPRESS.
   (Authors, title, year, thresholds and results read through a fetch summary of the open PDF
   on scitepress.org, 2026-10-21, not from the full text. **Page range and DOI still to be
   added from the SCITEPRESS page before this goes in the paper.**) Phone accelerometer worn
   at the hip or in a belt bag. Best configuration ("Gimpel"): free fall 0.75 g for 30 ms,
   impact 2 g within 500 ms, stillness variation 0.2 g over a 3.5 s window, orientation change
   66 degrees; 44 of 52 simulated falls detected (about 85%) and 2 false positives over 297 h of
   ADL recordings. Falls were simulated by three volunteers aged 23, 29 and 55; ADLs came from
   two people aged 55 and 72 (11 h from the older). No angular-velocity threshold. Sampling
   rate not stated for its own recordings; it cites 20 to 100 Hz as suitable.

7. **Luque, R., Casilari, E., Morón, M.-J., & Redondo, G. (2014).** Comparison and
   characterization of Android-based fall detection systems. *Sensors, 14*(10), 18543–18574.
   https://doi.org/10.3390/s141018543 (open access, PMC4239945). Read through a fetch summary of
   the first ~100,000 of ~147,000 characters of the PMC page on 2026-10-21, not the full text;
   re-check the quotes before they go in the paper. It supports three things SEENior does:
   (a) the posture or activity before a fall matters to the threshold ("the posture of the user
   before the fall must be taken into account to optimize the threshold", reporting Viet et
   al.); (b) fall detectors on phones are configured per user through a profile (thresholds,
   sampling rate) and some set parameters from age, sex and BMI; (c) a dropped phone is a known
   false-alarm source, which iFall handles by ignoring a detection if the dropped phone is picked
   up, and which the authors criticise in a commercial app that dialled an emergency number the
   moment the phone was dropped. Its own experiments used fixed thresholds, so it is not a source
   for SEENior's numbers. Phone worn at the waist or in a trouser pocket.

   Against SEENior: impact 2 g sits just below our 2.55 g; its free fall (0.75 g) and
   stillness (3.5 s window) are far looser than ours (0.31 g, 8 s); 50 Hz sampling is inside
   the range it cites.

8. **Kwon, Y. J., Aung, T., Synovec, S. M., Oberle, A. D., Hanton, C. R., Whittington, J.,
   Goulding, E. H., Witbrodt, B. C., Bonasera, S. J., & Schenk, A. K. (2017).** Classifying
   smartphone-based accelerometer data to obtain validated measures of subject activity status,
   step count, and gait speed [Preprint]. *bioRxiv*. https://doi.org/10.1101/160317
   Added 2026-10-21 as the only source found for the `active` flag in `fall_context`. **Not read
   in full:** bioRxiv returned 403, so the figures come from a search summary. Reported there:
   adults over 55, RMS acceleration of about 0.094 to 0.156 g for walking, at or above 0.156 g
   for stair climbing. Re-check the numbers and the participant ages before citing. A 2017 JMIR
   mHealth record (vol. 5, no. 10, e104) may be the peer-reviewed version; unconfirmed, cite the
   journal version if so. It is outside the 2020 to 2026 range and it is RMS, not our mean
   distance from 1 g, so the numbers are comparable, not identical.

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

## Suggested wording for the paper (drafted 2026-10-21)

Use this in place of any sentence saying the adaptive rules come from the literature. The
defense answer on 2026-10 said the references "were highlighted in the Review of Related
Literature"; that holds for the gate ranges only, not for the rules built on top of them.

> **Source of the Layer 0 thresholds.** The fall detector confirms a fall only when four
> phases occur in order: free fall, impact, body rotation and post-impact stillness. The
> impact threshold (25.0 m/s², 2.55 g) lies inside the range of upper fall thresholds reported
> for threshold-based detectors, about 1.8 to 3.5 g (Bourke, O'Brien, & Lyons, 2007; Li et
> al., 2009; Gimpel et al., 2015), and the 50 Hz sampling rate lies within the 20 to 100 Hz
> range reported as suitable (Gimpel et al., 2015). The free-fall threshold (0.31 g) and the
> stillness duration (8 s) are deliberately stricter than published values (free fall 0.41 to
> 0.75 g; stillness judged over 1 to 3.5 s), chosen to reduce false alarms from a phone that
> cannot judge body posture as a worn sensor can. The stillness duration is a design choice
> and is not taken from a published source. The cited studies used body-worn sensors and
> simulated falls, so their values guide the design and do not validate it.

> **Adaptive rules.** SEENior does not classify situations such as a sleeping senior dropping
> the phone or an active senior falling while walking. It adapts to each senior's own history
> instead. After two or more fall alerts in 14 days are closed as false alarms, the impact and
> stillness thresholds rise by 5% per false alarm up to 30%, and the senior receives 120
> seconds to respond instead of 60. An anomaly in inactivity or movement is logged without a
> prompt when the screen was on within the last five minutes. Repeated false alarms in one
> signal and time block loosen that block's trigger for 14 days, never reaching the extreme
> threshold, and alerts that were acted on take the loosening back. These rules and their
> parameters (5%, 30%, 120 s, five minutes, 14 days) are the authors' engineering design. They
> were checked by simulation and unit tests and have not been validated on real falls.

> **Limitations.** A first dropped phone still raises a High-risk alert with a 60-second
> window, because tolerance applies only after repeated false alarms. A hard drop onto a floor
> followed by stillness can satisfy all four phases. Detection accuracy has not been measured
> on a public dataset; a replay of SisFall (Sucerquia, López, & Vargas-Bonilla, 2017) is
> planned.

## What the system records about the situation (added 2026-10-21)

Each fall alert now stores `fall_context` (local `Alerts`, migration 5->8 on the phone; cloud
`alerts.fall_context`, migration 0019): `pre_dev` (mean distance of the phone's acceleration from
1 g over the two seconds before the free fall), `active` (1 when `pre_dev` >= 1.5 m/s2) and
`night` (1 in the senior's night block). A dropped phone at rest reads `pre_dev=0.1;active=0;night=1`;
a fall while walking reads `pre_dev=2.8;active=1;night=0`. It is a **record, not a rule**: no
gate and no tolerance step reads it, a test asserts that. The 1.5 m/s2 cut is an engineering
provisional starting value. It was chosen before any source was checked, and is not derived from
one. It sits at the top of the band reported for walking in a smartphone study of adults over 55
(about 0.094 to 0.156 g RMS, roughly 0.9 to 1.5 m/s2; Kwon et al. 2017 preprint, reference 8,
a different measure from ours, so only comparable). Say "provisional, consistent with published
walking ranges, calibration pending", never "taken from" a paper. Literature for the idea that
pre-fall activity matters and that a dropped phone is a known false alarm: Luque et al. 2014
(reference 7).

Query for the panel (cloud shows only falls that escalated; the phone's `Alerts` shows all):

```sql
SELECT created_at, status, risk_level, fall_context, fall_tolerance, fall_evidence
FROM alerts WHERE trigger_type = 'fall_pattern' ORDER BY created_at DESC;
```

**Reference list entries** for the paper: references 1, 2, 4, 5, 6, 7 and 8 above (Kangas et al.
2008 only if it is cited in the text).

**To turn this into accuracy figures** replay the SisFall dataset (reference 5) through
`FallDetector` and report how many falls it confirms and how many everyday activities it
wrongly flags. Not yet done.
