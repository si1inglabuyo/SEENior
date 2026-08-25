package com.pup.seenior.ui.wellness

/**
 * Every string the senior sees during a wellness check, in both supported languages.
 *
 * Kept out of `strings.xml` on purpose: the language is a per-senior setting stored in
 * `Senior_Onboarding.language_preference` (CLAUDE.md §7), not the device locale, so the
 * standard resource-qualifier mechanism would pick the wrong one.
 *
 * **The opening question is not free to reword.** CLAUDE.md §14 forbids "Are you okay?" — it was
 * flagged during panel defence as inappropriate informal slang for elderly Filipino users. The
 * mockup in `designs/senior/prompt_screen/` still shows the old wording; the panel's ruling wins.
 */
object WellnessMessages {

    const val ENGLISH = "en"
    const val FILIPINO = "fil"

    data class Copy(
        val headerTitle: String,
        val headerSubtitle: String,
        val question: String,
        val reason: String,
        val safeButton: String,
        val helpButton: String,
        val footer: String,
        val acknowledged: String,
        val alertSentTitle: String,
        val alertSentBody: String,
        /* Lets the senior close an alert that has already gone out. Until this existed, help
         * being on its way was a one-way door: a senior who got up unhurt had no way to say so,
         * and the alert sat in the family app until a relative happened to open it and resolve
         * it by hand. CLAUDE.md 1 promises the senior stays in the loop, not only until the
         * moment the alert leaves the phone. */
        val standDownButton: String,
        val standDownDone: String,
        val delivering: String,
        val returningHome: String,
        val returningHomeOne: String,
        val cancelButton: String,
        val sosHeaderTitle: String,
        val sosHeaderSubtitle: String,
        val sosQuestion: String,
        val sosSendingIn: String,
        val sosSendingInOne: String,
        val sosAutoNote: String,
        val sosWillAlert: String,
        val sosCancel: String,
        val sosAutoFooter: String,
        val sosBarangayRole: String,
        val sosNoContacts: String,
        /* The Home tab's standing report on an alert the senior has already raised. Separate
         * from alertSentBody, which is a one-off confirmation shown for five seconds on the way
         * out of the prompt: these two are what Home says for as long as the situation lasts. */
        val homePendingTitle: String,
        val homePendingBody: String,
        val homeDeliveredTitle: String,
        val homeDeliveredBody: String
    )

    fun forAlert(language: String, seniorFirstName: String, triggerType: String, timeBlock: String): Copy =
        if (language == FILIPINO) filipino(seniorFirstName, triggerType, timeBlock)
        else english(seniorFirstName, triggerType, timeBlock)

    // ---- English ----

    private fun english(name: String, triggerType: String, timeBlock: String) = Copy(
        headerTitle = "Check-In Required",
        headerSubtitle = englishTrigger(triggerType),
        // Panel-mandated phrasing. Do not change to "Are you okay?" (CLAUDE.md §14).
        question = "Are you safe and well,\n$name?",
        reason = englishReason(triggerType, timeBlock),
        safeButton = "I'M SAFE",
        helpButton = "I Need Help",
        footer = "If no response, your emergency contacts will be notified immediately.",
        acknowledged = "Response Acknowledged!",
        alertSentTitle = "Alert Sent!",
        // The mockup also promises "Your current location is being shared". It is not: location
        // is captured at alert-trigger time as an anonymous cluster id (CLAUDE.md §11) and no
        // cluster engine exists yet, so the alert goes up with no location at all. Telling the
        // senior otherwise would be a false reassurance in an emergency. Restore that line when
        // location capture is actually built.
        alertSentBody = "Your contacts have been notified.",
        standDownButton = "I'M FINE NOW",
        standDownDone = "We have told your family you are fine.",
        delivering = "Notifying your contacts now...",
        returningHome = "Returning to home in %d seconds...",
        returningHomeOne = "Returning to home in 1 second...",
        cancelButton = "CANCEL",
        sosHeaderTitle = "SOS Triggered",
        sosHeaderSubtitle = "Sending to your contacts",
        sosQuestion = "Sending help,\n$name",
        sosSendingIn = "Sending SOS in %d seconds",
        sosSendingInOne = "Sending SOS in 1 second",
        sosAutoNote = "Alert will go out automatically.\nCancel below if this was a mistake.",
        sosWillAlert = "Will Alert:",
        sosCancel = "Cancel — I'm Okay",
        sosAutoFooter = "If no action, SOS sends automatically",
        sosBarangayRole = "Barangay",
        sosNoContacts = "No family contacts linked yet. Your barangay will still be alerted.",
        homePendingTitle = "Waiting for Signal",
        homePendingBody = if (triggerType == "sos")
            "You pressed SOS. It will reach your family the moment this phone is back online."
        else
            "You asked for help. It will reach your family the moment this phone is back online.",
        homeDeliveredTitle = "Your Family Has Been Notified",
        homeDeliveredBody = if (triggerType == "sos")
            "Your SOS reached them. Help is on the way."
        else
            "Your request for help reached them. Help is on the way."
    )

    private fun englishTrigger(triggerType: String) = when (triggerType) {
        "inactivity" -> "Prolonged Inactivity Detected"
        "movement" -> "Unusual Movement Detected"
        "screen_idle" -> "Phone Unused Longer Than Usual"
        "charging" -> "Phone Off Charger Longer Than Usual"
        "fall_pattern" -> "Possible Fall Detected"
        "ml_flag" -> "Unusual Daily Routine"
        "sos" -> "Emergency Alert"
        else -> "Check-In Required"
    }

    private fun englishReason(triggerType: String, timeBlock: String): String {
        val block = englishBlock(timeBlock)
        return when (triggerType) {
            "inactivity" ->
                "We noticed you have not moved for a while, during a time you are usually active in the $block."
            "movement" ->
                "Your movement this $block looks different from your usual routine."
            "screen_idle" ->
                "You have not used your phone for much longer than you usually do in the $block."
            "charging" ->
                "Your phone has been off its charger longer than usual this $block."
            "fall_pattern" ->
                "Your phone detected a sudden movement that looked like a fall."
            "ml_flag" ->
                "Your overall routine looked different from your usual pattern."
            "sos" ->
                "You sent an emergency alert."
            else ->
                "Something in your routine this $block looked different from usual."
        }
    }

    private fun englishBlock(timeBlock: String) = when (timeBlock) {
        "morning" -> "morning"
        "afternoon" -> "afternoon"
        "evening" -> "evening"
        else -> "night"
    }

    // ---- Filipino ----

    private fun filipino(name: String, triggerType: String, timeBlock: String) = Copy(
        headerTitle = "Kailangan ng Pagtugon",
        headerSubtitle = filipinoTrigger(triggerType),
        // Panel-approved Filipino equivalent, per CLAUDE.md §14.
        question = "Ligtas po ba kayo,\n$name?",
        reason = filipinoReason(triggerType, timeBlock),
        safeButton = "LIGTAS PO AKO",
        helpButton = "Kailangan Ko ng Tulong",
        footer = "Kung walang sagot, agad na maaabisuhan ang inyong mga emergency contact.",
        acknowledged = "Natanggap na po ang inyong sagot!",
        alertSentTitle = "Naipadala na ang Alerto!",
        alertSentBody = "Naabisuhan na po ang inyong mga contact.",
        standDownButton = "MAAYOS NA PO AKO",
        standDownDone = "Naipaalam na po namin sa inyong pamilya na maayos kayo.",
        delivering = "Inaabisuhan na po ang inyong mga contact...",
        returningHome = "Babalik sa home sa loob ng %d segundo...",
        returningHomeOne = "Babalik sa home sa loob ng 1 segundo...",
        cancelButton = "KANSELAHIN",
        sosHeaderTitle = "SOS Triggered",
        sosHeaderSubtitle = "Ipinapadala sa inyong mga contact",
        sosQuestion = "Ipinapadala ang tulong,\n$name",
        sosSendingIn = "Ipapadala ang SOS sa %d segundo",
        sosSendingInOne = "Ipapadala ang SOS sa 1 segundo",
        sosAutoNote = "Awtomatikong ipapadala ang alerto.\nKanselahin sa ibaba kung nagkamali po kayo.",
        sosWillAlert = "Aabisuhan:",
        sosCancel = "Kanselahin — Maayos po ako",
        sosAutoFooter = "Kung walang aksyon, awtomatikong ipapadala ang SOS",
        sosBarangayRole = "Barangay",
        sosNoContacts = "Wala pang naka-link na pamilya. Aabisuhan pa rin ang inyong barangay.",
        homePendingTitle = "Hinihintay ang Signal",
        homePendingBody = if (triggerType == "sos")
            "Napindot po ninyo ang SOS. Ipapadala ito sa inyong pamilya sa oras na bumalik ang signal."
        else
            "Humingi po kayo ng tulong. Ipapadala ito sa inyong pamilya sa oras na bumalik ang signal.",
        homeDeliveredTitle = "Naabisuhan na ang Inyong Pamilya",
        homeDeliveredBody = if (triggerType == "sos")
            "Naipadala na po ang inyong SOS. Paparating na po ang tulong."
        else
            "Naipadala na po ang inyong paghingi ng tulong. Paparating na po ang tulong."
    )

    private fun filipinoTrigger(triggerType: String) = when (triggerType) {
        "inactivity" -> "Matagal na Walang Paggalaw"
        "movement" -> "Hindi Pangkaraniwang Paggalaw"
        "screen_idle" -> "Matagal na Hindi Nagagamit ang Telepono"
        "charging" -> "Matagal nang Hindi Naka-charge"
        "fall_pattern" -> "Posibleng Pagkahulog"
        "ml_flag" -> "Hindi Pangkaraniwang Rutina"
        "sos" -> "Emergency Alert"
        else -> "Kailangan ng Pagtugon"
    }

    private fun filipinoReason(triggerType: String, timeBlock: String): String {
        val block = filipinoBlock(timeBlock)
        return when (triggerType) {
            "inactivity" ->
                "Napansin po naming matagal kayong hindi gumagalaw, sa oras na karaniwan kayong aktibo tuwing $block."
            "movement" ->
                "Ang inyong paggalaw ngayong $block ay iba sa inyong karaniwang rutina."
            "screen_idle" ->
                "Matagal po ninyong hindi nagagamit ang inyong telepono kumpara sa karaniwan tuwing $block."
            "charging" ->
                "Matagal na pong hindi naka-charge ang inyong telepono ngayong $block."
            "fall_pattern" ->
                "May natukoy pong biglaang paggalaw na parang pagkahulog."
            "ml_flag" ->
                "Ang inyong pangkalahatang rutina ay iba sa inyong karaniwang pattern."
            "sos" ->
                "Nagpadala po kayo ng emergency alert."
            else ->
                "May napansin po kaming kaibahan sa inyong rutina ngayong $block."
        }
    }

    private fun filipinoBlock(timeBlock: String) = when (timeBlock) {
        "morning" -> "umaga"
        "afternoon" -> "hapon"
        "evening" -> "gabi"
        else -> "madaling-araw"
    }
}
