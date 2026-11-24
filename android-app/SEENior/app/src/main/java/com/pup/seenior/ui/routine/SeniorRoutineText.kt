package com.pup.seenior.ui.routine

import com.pup.seenior.ui.family.RoutineWording
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Every word on the senior's "My routine" screen, English and Filipino, as plain functions so
 * the wording can be unit-tested without Compose. The phrasing describes what the phone
 * measured; it never grades the senior and never calls a quieter day "low" or "behind".
 */
object SeniorRoutineText {

    // ---------------------------------------------------------------- chrome

    fun title(fil: Boolean) = if (fil) "Aking routine" else "My routine"
    fun profileRowSubtitle(fil: Boolean) =
        if (fil) "Tingnan ang natutunan ng telepono tungkol sa inyong araw"
        else "See what your phone has learned about your day"

    fun tabRoutine(fil: Boolean) = if (fil) "Routine" else "Routine"
    fun tabYesterday(fil: Boolean) = if (fil) "Kahapon" else "Yesterday"
    fun tabToday(fil: Boolean) = if (fil) "Ngayon" else "Today"
    fun tabChanges(fil: Boolean) = if (fil) "Pagbabago" else "Changes"

    fun loading(fil: Boolean) = if (fil) "Sandali lang po..." else "One moment..."

    // ---------------------------------------------------------------- blocks and figures

    fun blockName(block: String, fil: Boolean): String = RoutineWording.blockLabel(block, fil).let {
        // The family card calls the last block "Night" or "Madaling-araw / Tulog"; the senior sees the short one.
        if (block == "night" && fil) "Tulog" else it
    }

    fun clock(minute: Int): String {
        val m = ((minute % 1440) + 1440) % 1440
        val h24 = m / 60
        val h12 = if (h24 % 12 == 0) 12 else h24 % 12
        return "%d:%02d %s".format(h12, m % 60, if (h24 < 12) "AM" else "PM")
    }

    fun hours(window: SeniorRoutineModel.Window): String =
        "${clock(window.startMinute)} - ${clock(window.startMinute + window.durationMinutes)}"

    fun duration(seconds: Long, fil: Boolean): String = RoutineWording.duration(seconds.toDouble(), fil)

    fun labelStill(fil: Boolean) = if (fil) "Hindi gumagalaw nang hanggang" else "Still for up to"
    fun labelSteps(fil: Boolean) = if (fil) "Hakbang" else "Steps"
    fun labelUnlocks(fil: Boolean) = if (fil) "Pagbukas ng telepono" else "Phone unlocks"
    fun labelIdle(fil: Boolean) = if (fil) "Pinakamatagal na hindi ginagalaw ang telepono" else "Longest time phone untouched"

    fun times(n: Int, fil: Boolean) = if (fil) "$n beses" else if (n == 1) "1 time" else "$n times"
    fun usualSuffix(value: String, fil: Boolean) = if (fil) "karaniwan $value" else "usual $value"
    fun soFar(fil: Boolean) = if (fil) "sa ngayon" else "so far"

    // ---------------------------------------------------------------- routine tab

    fun learning(days: Int, fil: Boolean) =
        if (fil) "Natututo: araw $days ng 14" else "Learning: day $days of 14"
    fun learningDetail(fil: Boolean) =
        if (fil) "Mas maingat ang mga alerto hanggang araw 14."
        else "Alerts stay extra careful until day 14."
    fun learned(fil: Boolean) = if (fil) "Natutunan" else "Learned"
    fun setupAnswer(fil: Boolean) = if (fil) "Sagot sa setup" else "Setup answer"
    fun legend(fil: Boolean) =
        if (fil) "Natutunan = mula sa inyong mga araw. Sagot sa setup = hula pa mula sa inyong mga sagot."
        else "Learned = from your own days. Setup answer = still a guess from your answers."

    /** The one line under a block's name, from what the phone saw (or the setup guess). */
    fun usualHeadline(block: String, times: SeniorRoutineModel.Times, learned: Boolean, fil: Boolean): String {
        if (block == "night") return if (fil) "Nagpapahinga. Tahimik kami." else "Resting. We stay quiet."
        if (!learned) return if (fil) "Natututo pa sa bahaging ito ng araw." else "Still learning this part of your day."
        val at = when (block) {
            "morning" -> times.firstMinute
            else -> times.lastMinute
        } ?: return if (fil) "Natututo pa sa bahaging ito ng araw." else "Still learning this part of your day."
        return when (block) {
            "morning" -> if (fil) "Karaniwang gising ng mga ${clock(at)}" else "Usually up by about ${clock(at)}"
            "afternoon" -> if (fil) "Karaniwang aktibo hanggang mga ${clock(at)}" else "Usually active until about ${clock(at)}"
            else -> if (fil) "Karaniwang huling aktibo ng mga ${clock(at)}" else "Usually last active about ${clock(at)}"
        }
    }

    // ---------------------------------------------------------------- yesterday and today

    fun dateLine(date: LocalDate, fil: Boolean): String =
        date.format(DateTimeFormatter.ofPattern("EEEE d MMM", if (fil) Locale("fil", "PH") else Locale.ENGLISH))

    fun yesterdayLine(date: LocalDate, fil: Boolean) =
        if (fil) "${dateLine(date, true)}, kumpara sa karaniwan" else "${dateLine(date, false)}, compared with your usual"
    fun todayLine(date: LocalDate, time: String, fil: Boolean) =
        if (fil) "${dateLine(date, true)}, sa ngayon ($time)" else "${dateLine(date, false)}, so far ($time)"

    fun noYesterday(fil: Boolean) =
        if (fil) "Wala pang kumpletong araw na naitatala. Lalabas dito ang kahapon bukas."
        else "No finished day is stored yet. Yesterday will appear here tomorrow."

    fun verdict(v: SeniorRoutineModel.Verdict, inProgress: Boolean, fil: Boolean): String? = when (v) {
        SeniorRoutineModel.Verdict.AS_USUAL ->
            if (inProgress) (if (fil) "Gaya ng karaniwan sa ngayon" else "As usual so far")
            else (if (fil) "Gaya ng karaniwan" else "As usual")
        SeniorRoutineModel.Verdict.QUIETER -> if (fil) "Medyo mas tahimik" else "A bit quieter"
        SeniorRoutineModel.Verdict.BUSIER -> if (fil) "Medyo mas aktibo" else "A bit busier"
        SeniorRoutineModel.Verdict.NOT_COMPARED -> null
    }

    fun notYet(fil: Boolean) = if (fil) "Hindi pa" else "Not yet"
    fun inProgress(fil: Boolean) = if (fil) "kasalukuyan" else "in progress"
    fun notCompared(fil: Boolean) = if (fil) "Hindi pa ikinukumpara" else "Not compared yet"

    fun yesterdayNote(fil: Boolean) =
        if (fil) "Ikinukumpara lang ang kahapon sa karaniwan. Hindi nito binabago ang mga alerto."
        else "Yesterday is only compared with your usual. It never changes alerts by itself."
    fun todayNote(fil: Boolean) =
        if (fil) "Hindi pa tapos ang araw, kaya wala pang pinal na paghahambing. Mamayang gabi, idadagdag ng telepono ang araw na ito sa natutunan nito."
        else "Today is not finished, so nothing is final yet. Tonight your phone adds today to what it has learned."

    fun upAt(minute: Int, usual: Int?, fil: Boolean): String =
        if (fil) "Gising ng ${clock(minute)}" + (usual?.let { " (karaniwan ${clock(it)})" } ?: "")
        else "Up at ${clock(minute)}" + (usual?.let { " (usually ${clock(it)})" } ?: "")
    fun activeUntil(minute: Int, usual: Int?, fil: Boolean): String =
        if (fil) "Aktibo hanggang ${clock(minute)}" + (usual?.let { " (karaniwan ${clock(it)})" } ?: "")
        else "Active until ${clock(minute)}" + (usual?.let { " (usually ${clock(it)})" } ?: "")
    fun lastActive(minute: Int, usual: Int?, fil: Boolean): String =
        if (fil) "Huling aktibo ${clock(minute)}" + (usual?.let { " (karaniwan ${clock(it)})" } ?: "")
        else "Last active ${clock(minute)}" + (usual?.let { " (usually ${clock(it)})" } ?: "")

    // ---------------------------------------------------------------- changes tab

    fun usualChangedHeading(fil: Boolean) = if (fil) "Nagbago ang karaniwan ninyo" else "Your usual has changed"
    fun usualChangedSub(fil: Boolean) =
        if (fil) "Patuloy na natututo ang telepono sa mga huling araw ninyo."
        else "Your phone keeps learning from your recent days."
    fun waitsHeading(fil: Boolean) = if (fil) "Medyo mas matagal maghihintay ang telepono" else "Your phone waits a little longer"
    fun waitsSub(fil: Boolean) =
        if (fil) "Hindi ito pagbabago sa routine ninyo. Nililipat lang nito kung kailan magtatanong ang telepono."
        else "This is not a change to your routine. It only moves when your phone asks."
    fun mood(m: SeniorRoutineModel.Mood, fil: Boolean) = when (m) {
        SeniorRoutineModel.Mood.QUIETER -> if (fil) "Mas tahimik" else "Quieter"
        SeniorRoutineModel.Mood.BUSIER -> if (fil) "Mas aktibo" else "Busier"
        SeniorRoutineModel.Mood.DIFFERENT -> if (fil) "Iba na" else "Different"
    }
    fun whyBlock(m: SeniorRoutineModel.Mood, block: String, fil: Boolean): String {
        val name = blockName(block, fil).lowercase()
        return when (m) {
            SeniorRoutineModel.Mood.QUIETER ->
                if (fil) "Mas tahimik ang mga huling $name ninyo kaysa dati, kaya in-update ng telepono ang karaniwan ninyo."
                else "Your recent ${name}s were quieter than before, so your phone updated what is usual for you."
            SeniorRoutineModel.Mood.BUSIER ->
                if (fil) "Mas aktibo ang mga huling $name ninyo kaysa dati, kaya in-update ng telepono ang karaniwan ninyo."
                else "Your recent ${name}s were busier than before, so your phone updated what is usual for you."
            SeniorRoutineModel.Mood.DIFFERENT ->
                if (fil) "Iba ang mga huling $name ninyo kaysa dati, kaya in-update ng telepono ang karaniwan ninyo."
                else "Your recent ${name}s were different from before, so your phone updated what is usual for you."
        }
    }
    fun signalName(trigger: String, fil: Boolean) = when (trigger) {
        "inactivity" -> if (fil) "Hindi paggalaw" else "Stillness"
        "screen_idle" -> if (fil) "Hindi paggamit ng telepono" else "Phone untouched"
        else -> if (fil) "Galaw" else "Movement"
    }
    fun bigMinutes(minutes: Int) = "+$minutes min"
    fun bigLabel(minutes: Int?, fil: Boolean) =
        if (minutes != null && minutes >= 1) (if (fil) "mas matagal bago magtanong" else "longer before it asks")
        else (if (fil) "mas huli magtanong" else "asks a bit later")
    fun careSentence(count: Int, block: String, fil: Boolean): String {
        val name = blockName(block, fil).lowercase()
        return if (fil) "Sinabi ninyong ligtas kayo $count beses nang magtanong ang telepono tuwing $name, kaya medyo mas matagal na itong maghihintay doon."
        else "You said you were fine $count times when your phone asked in the $name, so it now waits a little longer there."
    }
    fun viewDetails(fil: Boolean) = if (fil) "Tingnan ang detalye" else "View details"
    fun hideDetails(fil: Boolean) = if (fil) "Itago ang detalye" else "Hide details"

    fun step1Title(fil: Boolean) = if (fil) "Ang mga sagot ninyong \"ligtas ako\"" else "Your \"I'm fine\" answers"
    fun step1Body(fil: Boolean) =
        if (fil) "Bawat isa ay may iskor kung gaano kalayo sa karaniwan ninyo nang magtanong ang telepono. Karaniwang nagtatanong mula 2.5."
        else "Each one scored how far past your usual it was when the phone asked. The phone normally asks from 2.5."
    fun step2Title(fil: Boolean) = if (fil) "Ang nasa gitna, dagdag ang maliit na margin" else "The middle one, plus a small margin"
    fun step2Body(median: String, fil: Boolean) =
        if (fil) "Ang nasa gitna ng mga iskor ay $median. May dagdag na maliit na margin na 0.25. Hindi ito lalampas sa 3.5, kaya laging magtatanong ang napakalaking pagbabago."
        else "The middle score is $median. A small margin of 0.25 is added. It never goes above 3.5, so a very big change always asks."
    fun step3Title(fil: Boolean) = if (fil) "Binabawi ng totoong alerto ang ilan" else "Real alerts take some back"
    fun step3Body(real: Int, keptPercent: Int, block: String, fil: Boolean): String {
        val name = blockName(block, fil).lowercase()
        return if (fil) {
            "Ang mga alertong totoo (kumilos ang pamilya o barangay) tuwing $name ay nagbabawas sa dagdag. " +
                if (real == 0) "0 ang bilang, kaya buo ang dagdag." else "$real ang bilang, kaya $keptPercent% ng dagdag ang natitira."
        } else {
            "Alerts in the $name that were real (family or barangay acted) cancel part of the raise. " +
                if (real == 0) "There were 0, so all of it stays." else "There were $real, so $keptPercent% of it stays."
        }
    }
    fun step4Title(fil: Boolean) = if (fil) "Ginawang minuto" else "Turned into minutes"
    fun step4Body(raise: String, spreadMinutes: Int, block: String, fil: Boolean): String {
        val name = blockName(block, fil).lowercase()
        return if (fil) "Ang dagdag ay $raise. Karaniwang nag-iiba ng $spreadMinutes min ang inyong karaniwan tuwing $name. Ang dagdag na iyon, kinaliskis ng 0.6745, ang karagdagang oras."
        else "The raise is $raise. What is usual for you in the $name normally varies by $spreadMinutes min. The raise of that spread, scaled by 0.6745, is the extra time."
    }
    fun resultMinutes(minutes: Int, block: String, fil: Boolean): String {
        val name = blockName(block, fil).lowercase()
        return if (fil) "Kaya $minutes min na mas matagal maghihintay ang telepono tuwing $name."
        else "So your phone waits $minutes min longer in the $name."
    }
    fun resultNoMinutes(block: String, fil: Boolean): String {
        val name = blockName(block, fil).lowercase()
        return if (fil) "Kaya medyo mas huli magtanong ang telepono tuwing $name." else "So your phone asks a little later in the $name."
    }

    fun learnedHeading(fil: Boolean) = if (fil) "1 · Ang natutunan ng telepono" else "1 · What your phone learned"
    fun careHeading(fil: Boolean) = if (fil) "2 · Gaano kaingat ang mga alerto" else "2 · How careful alerts are"

    fun noRoutineChange(fil: Boolean) =
        if (fil) "Walang nagbago. Hindi nagbago ang karaniwan ninyo nitong nakaraang 7 araw."
        else "Nothing has changed. What is usual for you has stayed about the same for the last 7 days."
    fun learnedWhyQuieter(fil: Boolean) =
        if (fil) "Bakit: mas tahimik ang mga huling araw ninyo kaysa dati."
        else "Why: your recent days were quieter than before."
    fun learnedWhyBusier(fil: Boolean) =
        if (fil) "Bakit: mas aktibo ang mga huling araw ninyo kaysa dati."
        else "Why: your recent days were busier than before."
    fun learnedWhyOther(fil: Boolean) =
        if (fil) "Bakit: iba ang mga huling araw ninyo kaysa dati."
        else "Why: your recent days were different from before."
    fun estimateNote(fil: Boolean) =
        if (fil) "Ang ilang naunang araw ay kinuwenta na lang pagkatapos, kaya sinusunod nila ang takbo pero maaaring iba sa ginamit noon."
        else "Some earlier days were worked out afterwards, so they follow the trend but may differ from what was used then."

    fun feature(feature: String, fil: Boolean): String = when (feature) {
        SeniorRoutineModel.STEPS -> labelSteps(fil)
        SeniorRoutineModel.UNLOCKS -> labelUnlocks(fil)
        SeniorRoutineModel.STILL -> labelStill(fil)
        else -> labelIdle(fil)
    }

    fun value(feature: String, v: Double, fil: Boolean): String = when (feature) {
        SeniorRoutineModel.STEPS -> "%,d".format(Math.round(v))
        SeniorRoutineModel.UNLOCKS -> times(Math.round(v).toInt(), fil)
        else -> duration(Math.round(v), fil)
    }

    fun noCare(fil: Boolean) =
        if (fil) "Walang pagbabago. Kapag nagtanong ang telepono at sinabi ninyong ligtas kayo nang dalawang beses sa parehong oras, mas matagal itong maghihintay doon."
        else "Nothing has changed. If your phone asks twice at the same hour and you say you are fine, it will wait a little longer there."
    fun careIntro(fil: Boolean) =
        if (fil) "Sinabi ninyong ligtas kayo nang magtanong ang telepono, kaya medyo mas matagal itong maghihintay sa mga oras na ito."
        else "You said you were fine when your phone asked, so it waits a little longer in these hours."
    /** "+12 min" for the stillness signals; a words chip when there is no time to give. */
    fun careChip(minutes: Int?, fil: Boolean): String =
        if (minutes != null && minutes >= 1) "+$minutes min" else if (fil) "mas huli magtanong" else "asks a bit later"
    fun careFine(fil: Boolean) = if (fil) "Ligtas kayo" else "You were fine"
    fun careToday(fil: Boolean) = if (fil) "Ngayon" else "Today"
    fun careNote(fil: Boolean) =
        if (fil) "Ang nakaraang 14 araw lang ang binibilang. Ang malaking pagbabago ay laging magtatanong. Hindi nagbabago ang routine sa itaas."
        else "Only the last 14 days count. A big change always asks. Your routine is not changed."

    /** Month and day only, no weekday: "Nov 19". */
    fun shortDate(date: LocalDate, fil: Boolean): String =
        date.format(DateTimeFormatter.ofPattern("MMM d", if (fil) Locale("fil", "PH") else Locale.ENGLISH))
}
