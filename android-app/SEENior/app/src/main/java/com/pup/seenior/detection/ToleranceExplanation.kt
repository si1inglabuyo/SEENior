package com.pup.seenior.detection

/**
 * The working behind one block's false-alarm gate, as steps a presenter can read out. Every
 * number on screen is a [Fact] that says where it came from, so none of them is a bare figure
 * a panelist has to take on trust. No Android imports, so a JUnit test can drive it.
 */
object ToleranceExplanation {

    /** Where a number comes from. The three are different kinds of claim and are shown as such. */
    enum class Origin(val label: String) {
        /** Measured on this phone from this senior's own readings. */
        YOUR_DATA("from this senior's data"),

        /** Fixed by the system's design (the spec), not tuned for this senior. */
        DESIGN_RULE("design rule"),

        /** A value we picked. Not derived from anything; it is a tuning decision. */
        CHOSEN("chosen constant"),

        /** Plain arithmetic on the numbers above it. */
        ARITHMETIC("arithmetic")
    }

    data class Fact(val value: String, val meaning: String, val origin: Origin)

    data class Step(
        val title: String,
        /** The sum, with this block's numbers filled in. */
        val formula: String,
        /** Why the step exists, in a sentence. */
        val why: String,
        val facts: List<Fact>
    )

    /** The block's baseline as the explanation needs it. All times are seconds. */
    data class BaselineFacts(
        val medianSeconds: Double,
        val rawMadSeconds: Double,
        val madFloorSeconds: Double,
        val sampleCount: Int,
        val isSeed: Boolean
    ) {
        val effectiveMadSeconds: Double get() = maxOf(rawMadSeconds, madFloorSeconds)
    }

    private fun min(seconds: Double) = "%.1f min".format(seconds / 60.0)

    private fun z(value: Double) = "%.2f".format(value)

    fun steps(
        signalName: String,
        timeBlock: String,
        breakdown: FalseAlarmTolerance.Breakdown,
        baseline: BaselineFacts?
    ): List<Step> {
        val base = FalseAlarmTolerance.BASE_THRESHOLD
        val cap = FalseAlarmTolerance.CAP
        val margin = FalseAlarmTolerance.MARGIN
        val steps = mutableListOf<Step>()

        // ---- 1. Which alerts count
        val scoresText = if (breakdown.evidence.isEmpty()) "none" else breakdown.evidence.joinToString { z(it) }
        steps += Step(
            title = "1. Which false alarms count",
            formula = "Counted for the $timeBlock block: ${breakdown.evidence.size} (z = $scoresText)",
            why = "Only alerts closed as \"I'm fine now\" or marked false by family or the barangay, in this " +
                "signal and this time block. One false alarm proves nothing, so at least " +
                "${FalseAlarmTolerance.MIN_EVIDENCE} are needed before anything changes.",
            facts = listOf(
                Fact("${FalseAlarmTolerance.WINDOW_DAYS} days", "how far back an alert still counts; the same window the baseline uses, so the two forget together", Origin.DESIGN_RULE),
                Fact("${FalseAlarmTolerance.MIN_EVIDENCE}", "false alarms needed in one block before the gate moves", Origin.CHOSEN),
                Fact(z(base), "an alert below this z is not counted: no alert would have been raised for it", Origin.DESIGN_RULE),
                Fact(z(cap), "an alert at or above this z is not counted: a reading this extreme must always alert, so it can never teach the system to stay quiet", Origin.DESIGN_RULE),
                Fact(scoresText, "each counted alert's z, saved when it fired: |reading - median| / MAD", Origin.YOUR_DATA)
            )
        )

        // ---- 2. This block's normal
        if (baseline != null) {
            val sourceNote = if (baseline.isSeed) "still the onboarding estimate, not yet real readings" else "of this block's readings"
            steps += Step(
                title = "2. This block's normal",
                formula = "median ${min(baseline.medianSeconds)}, MAD ${min(baseline.effectiveMadSeconds)}",
                why = "The system only ever compares a reading with this senior's own usual $signalName for this " +
                    "time of day. The median is the middle value; the MAD is the typical distance from it.",
                facts = listOf(
                    Fact(min(baseline.medianSeconds), "median $signalName in the $timeBlock block, the middle of ${baseline.sampleCount} days $sourceNote", Origin.YOUR_DATA),
                    Fact(min(baseline.rawMadSeconds), "MAD: the middle of how far each day sat from that median", Origin.YOUR_DATA),
                    Fact(min(baseline.madFloorSeconds), "smallest MAD allowed, so a very regular senior doesn't make every small change look huge", Origin.CHOSEN),
                    Fact(min(baseline.effectiveMadSeconds), "the MAD actually used: the larger of the two above", Origin.ARITHMETIC)
                )
            )
        }

        // ---- 3. Typical false alarm
        if (breakdown.medianZ == null) {
            steps += Step(
                title = "3. Not enough false alarms yet",
                formula = "${breakdown.evidence.size} counted, ${FalseAlarmTolerance.MIN_EVIDENCE} needed",
                why = "Until the pattern is there, nothing is added and the gate stays at ${z(base)}.",
                facts = listOf(Fact(z(base), "the normal gate every block starts at", Origin.DESIGN_RULE))
            )
            return steps
        }

        steps += Step(
            title = "3. How unusual they were, typically",
            formula = "median of ${scoresText} = ${z(breakdown.medianZ)}",
            why = "The middle value, not the average, so one odd score can't drag the result. If the false " +
                "alarms were barely over ${z(base)}, the gate rises a little; if they were nearer ${z(cap)}, it rises more.",
            facts = listOf(Fact(z(breakdown.medianZ), "the typical z of the counted false alarms", Origin.ARITHMETIC))
        )

        // ---- 4. Margin
        steps += Step(
            title = "4. Add the margin",
            formula = "${z(breakdown.medianZ)} + ${z(margin)} = ${z(breakdown.gateBeforeRealAlerts)}",
            why = "Without a margin, a reading exactly as unusual as the false alarms would sit on the edge " +
                "and flip between asking and not asking. The margin leaves it a little room. The result is " +
                "held between ${z(base)} and ${z(cap)}, so the gate can never reach the extreme line.",
            facts = listOf(
                Fact(z(margin), "the margin. It is not calculated from the data; it is a small value we picked", Origin.CHOSEN),
                Fact(z(breakdown.gateBeforeRealAlerts), "gate if no real alert has happened here", Origin.ARITHMETIC)
            )
        )

        // ---- 5. Real alerts
        val f = breakdown.evidence.size
        val r = breakdown.realAlertCount
        steps += Step(
            title = "5. Real alerts take some back",
            formula = if (r == 0) "($f - 0) / $f = ${z(breakdown.keptShare)}" else "($f - $r) / $f = ${z(breakdown.keptShare)}",
            why = "A real alert in this same block means this hour can matter. Each one cancels one false " +
                "alarm's worth of the raise; when real alerts match the false alarms, the raise is gone.",
            facts = listOf(
                Fact("$r", "real alerts in this block and signal in the last ${FalseAlarmTolerance.WINDOW_DAYS} days (family acknowledged, barangay involved, or resolved)", Origin.YOUR_DATA),
                Fact("$f", "false alarms counted in step 1", Origin.YOUR_DATA),
                Fact("%.0f%%".format(breakdown.keptShare * 100), "share of the raise that is kept", Origin.ARITHMETIC)
            )
        )

        // ---- 6. Gate
        steps += Step(
            title = "6. The new gate",
            formula = "${z(base)} + (${z(breakdown.gateBeforeRealAlerts)} - ${z(base)}) x ${z(breakdown.keptShare)} = ${z(breakdown.gate)}",
            why = "Start from the normal gate, add the raise, scaled by how much of it the real alerts left.",
            facts = listOf(
                Fact(z(base), "the normal gate", Origin.DESIGN_RULE),
                Fact(z(breakdown.raise), "how far the gate has been raised, in z", Origin.ARITHMETIC),
                Fact(z(breakdown.gate), "a reading must reach this z before the senior is asked", Origin.ARITHMETIC)
            )
        )

        if (baseline == null) return steps

        // ---- 7. In minutes
        val mad = baseline.effectiveMadSeconds
        val addedMinutes = breakdown.extraMinutes(mad)
        steps += Step(
            title = "7. What that is in minutes",
            formula = "${z(breakdown.raise)} z x ${min(mad)} = +${"%.1f".format(addedMinutes)} min",
            why = "A z-score counts how many MADs a reading is from the median. So one unit of z is exactly " +
                "one MAD of $signalName for this block, and the raise converts straight to minutes.",
            facts = listOf(
                Fact(z(breakdown.raise), "the raise from step 6", Origin.ARITHMETIC),
                Fact(min(mad), "one MAD, from step 2", Origin.YOUR_DATA),
                Fact("60", "seconds in a minute, to show the answer in minutes", Origin.ARITHMETIC),
                Fact("+${"%.1f".format(addedMinutes)} min", "extra $signalName now tolerated in the $timeBlock block only", Origin.ARITHMETIC)
            )
        )

        // ---- 8. Result
        val before = (baseline.medianSeconds + base * mad) / 60.0
        val after = (baseline.medianSeconds + breakdown.gate * mad) / 60.0
        steps += Step(
            title = "8. Result",
            formula = "before ${"%.1f".format(before)} min  ->  now ${"%.1f".format(after)} min",
            why = "The point where the senior is asked, written as median + gate x MAD.",
            facts = listOf(
                Fact("${"%.1f".format(before)} min", "${min(baseline.medianSeconds)} + ${z(base)} x ${min(mad)}", Origin.ARITHMETIC),
                Fact("${"%.1f".format(after)} min", "${min(baseline.medianSeconds)} + ${z(breakdown.gate)} x ${min(mad)}", Origin.ARITHMETIC)
            )
        )
        return steps
    }
}
