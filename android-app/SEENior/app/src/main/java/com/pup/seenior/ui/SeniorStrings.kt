package com.pup.seenior.ui

import com.pup.seenior.ui.wellness.WellnessMessages

/**
 * Every string on the senior's everyday screens — the bottom tabs and Home — in both supported
 * languages.
 *
 * Kept out of `strings.xml` for the same reason as [WellnessMessages], and its reasoning is worth
 * repeating rather than rediscovering: the language is a **per-senior setting** stored in
 * `Senior_Onboarding.language_preference`, not the device locale. Android's resource qualifiers
 * resolve against the handset, so a phone a relative set up in English would silently override the
 * senior's own answer on exactly the screens they use unaccompanied.
 *
 * Filipino here follows the register [WellnessMessages] established and the panel accepted: formal,
 * "po" throughout, and comfortable with the loanwords Filipino speakers actually use — that file
 * already writes "inyong mga contact", so "Mga Contact" on a tab is the same voice, not a lapse
 * into English.
 *
 * **These translations are a draft pending a native speaker's review.** The panel has already
 * rejected one phrasing as too informal; nothing here should reach a defence unread.
 */
object SeniorStrings {

    data class Copy(
        val tabHome: String,
        val tabInvite: String,
        val tabContacts: String,
        val tabProfile: String,
        val roleLabel: String,
        val allSetToday: String,
        val youAreSafe: String,
        val monitoringAtRisk: String,
        val monitoringActive: String,
        val chargeToContinue: String,
        val batteryLow: String,
        val batteryGood: String,
        val emergencyAlert: String,
        val swipeToSend: String,
        val reachTitle: String,
        val reachBody: String,
        val openSettings: String,
        val notNow: String,
    ) {
        fun greeting(name: String): String = if (this === FILIPINO_COPY) "Kumusta po, $name" else "Hi, $name"

        /** The barangay name is a proper noun and stays as written in both languages. */
        fun alertsFamilyAndBarangay(barangay: String): String = when {
            this === FILIPINO_COPY && barangay.isBlank() ->
                "Aabisuhan po ang inyong pamilya at barangay kung walang sagot"
            this === FILIPINO_COPY ->
                "Aabisuhan po ang inyong pamilya at Barangay " + barangay + "\nkung walang sagot"
            barangay.isBlank() -> "Alerts family and your barangay if no response"
            else -> "Alerts family and Barangay " + barangay + "\nif no response"
        }
    }

    private val ENGLISH_COPY = Copy(
        tabHome = "Home",
        tabInvite = "Invite",
        tabContacts = "Contacts",
        tabProfile = "Profile",
        roleLabel = "Senior",
        allSetToday = "You're all set today",
        youAreSafe = "You're Safe",
        monitoringAtRisk = "Monitoring At Risk",
        monitoringActive = "Monitoring is active",
        chargeToContinue = "Charge your phone to continue\nemergency monitoring",
        batteryLow = "Battery - Low",
        batteryGood = "Battery - Good",
        emergencyAlert = "EMERGENCY ALERT",
        swipeToSend = "Swipe to send alert",
        reachTitle = "Let SEENior reach you",
        reachBody = "To wake your screen and show a check-in over other apps, SEENior needs two " +
            "settings turned on. Without them a check-in only appears as a small banner, " +
            "which is easy to miss.",
        openSettings = "Open settings",
        notNow = "Not now",
    )

    private val FILIPINO_COPY = Copy(
        tabHome = "Home",
        tabInvite = "Imbitasyon",
        tabContacts = "Mga Contact",
        tabProfile = "Profile",
        roleLabel = "Senior",
        allSetToday = "Ayos po ang lahat ngayong araw",
        youAreSafe = "Ligtas Po Kayo",
        monitoringAtRisk = "May Problema sa Pagbantay",
        monitoringActive = "Aktibo po ang pagbantay",
        chargeToContinue = "I-charge po ang inyong telepono\nupang magpatuloy ang pagbantay",
        batteryLow = "Baterya - Mababa",
        batteryGood = "Baterya - Maayos",
        emergencyAlert = "ALERTONG EMERGENCY",
        swipeToSend = "I-swipe po upang magpadala ng alerto",
        reachTitle = "Upang marating po kayo ng SEENior",
        reachBody = "Upang mabuksan ang inyong screen at maipakita ang check-in kahit may " +
            "ibang app na nakabukas, kailangan pong i-on ang dalawang setting. Kung hindi, " +
            "lilitaw lamang ang check-in bilang maliit na banner na madaling hindi mapansin.",
        openSettings = "Buksan ang settings",
        notNow = "Hindi muna",
    )

    fun forLanguage(language: String): Copy =
        if (language == WellnessMessages.FILIPINO) FILIPINO_COPY else ENGLISH_COPY
}
