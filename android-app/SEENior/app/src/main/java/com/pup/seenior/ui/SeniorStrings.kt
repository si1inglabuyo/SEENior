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
 * Filipino here is comfortable with the loanwords Filipino speakers actually use (e.g. "cellphone"
 * rather than "telepono"). **"po" was removed from this file's copy after a native-speaker review
 * on 2026-09-14** — see the tag/history for [[seenior-language]] — except where it quotes the
 * wellness prompt itself ([WellnessMessages]), which CLAUDE.md §14 pins verbatim and this file
 * does not touch.
 *
 * **These translations are a draft pending a further native speaker's review.** The panel has
 * already rejected one phrasing as too informal; nothing here should reach a defence unread.
 */
object SeniorStrings {

    data class Copy(
        val tabHome: String,
        val tabInvite: String,
        val tabAlerts: String,
        val tabContacts: String,
        val tabProfile: String,
        val roleLabel: String,
        val alertsCurrentHeader: String,
        val alertsNoActive: String,
        val alertsHistoryHeader: String,
        val alertsNoHistory: String,
        val alertStatusPending: String,
        val alertStatusFamilyNotified: String,
        val alertStatusBarangayNotified: String,
        val alertStatusResolved: String,
        val alertStatusSelfCancelled: String,
        val alertStatusFalsePositive: String,
        val riskHigh: String,
        val riskMedium: String,
        val riskLow: String,
        val allSetToday: String,
        val youAreSafe: String,
        val monitoringAtRisk: String,
        val monitoringActive: String,
        val chargeToContinue: String,
        /* Shown on the same status card, in the same amber, when there is a HIGH-risk alert
         * still open and unresolved -- outranks the battery message below, and specifically
         * does not say "you're safe", which was the bug this pair of strings fixes. */
        val helpPendingTitle: String,
        val helpPendingBody: String,
        val batteryLow: String,
        val batteryGood: String,
        val emergencyAlert: String,
        val swipeToSend: String,
        val reachTitle: String,
        val reachBody: String,
        val openSettings: String,
        val notNow: String,
        /* The Home-screen banner for a permission monitoring needs and no longer has. Unlike
         * reachTitle/reachBody above, which offer an improvement, these describe something
         * already broken -- so the wording says what has stopped working, not what could be
         * better, and the banner stays up until it is fixed. */
        val permissionLostTitle: String,
        val permissionLostBody: String,
        val permissionLostCta: String,
    ) {
        fun greeting(name: String): String = if (this === FILIPINO_COPY) "Kumusta, $name" else "Hi, $name"

        /** The barangay name is a proper noun and stays as written in both languages. */
        fun alertsFamilyAndBarangay(barangay: String): String = when {
            this === FILIPINO_COPY && barangay.isBlank() ->
                "Aabisuhan ang iyong pamilya at barangay kung walang sagot"
            this === FILIPINO_COPY ->
                "Aabisuhan ang iyong pamilya at Barangay " + barangay + "\nkung walang sagot"
            barangay.isBlank() -> "Alerts family and your barangay if no response"
            else -> "Alerts family and Barangay " + barangay + "\nif no response"
        }
    }

    private val ENGLISH_COPY = Copy(
        tabHome = "Home",
        tabInvite = "Invite",
        tabAlerts = "Alerts",
        tabContacts = "Contacts",
        tabProfile = "Profile",
        roleLabel = "Senior",
        alertsCurrentHeader = "Current Alert",
        alertsNoActive = "You have no active alert right now.",
        alertsHistoryHeader = "Alert History — Last 14 Days",
        alertsNoHistory = "No alerts in the past 14 days.",
        alertStatusPending = "Waiting for a response",
        alertStatusFamilyNotified = "Family notified",
        alertStatusBarangayNotified = "Barangay notified",
        alertStatusResolved = "Resolved",
        alertStatusSelfCancelled = "You said you were fine",
        alertStatusFalsePositive = "False alarm",
        riskHigh = "High",
        riskMedium = "Medium",
        riskLow = "Low",
        allSetToday = "You're all set today",
        youAreSafe = "You're Safe",
        monitoringAtRisk = "Monitoring At Risk",
        monitoringActive = "Monitoring is active",
        chargeToContinue = "Charge your phone to continue\nemergency monitoring",
        helpPendingTitle = "Help Request Still Open",
        helpPendingBody = "Your family hasn't confirmed yet. Tap below if you're okay now.",
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
        permissionLostTitle = "SEENior cannot watch over you right now",
        permissionLostBody = "A permission SEENior needs has been turned off, so it cannot " +
            "check on you properly. Tap below to turn it back on.",
        permissionLostCta = "Turn it back on",
    )

    private val FILIPINO_COPY = Copy(
        tabHome = "Home",
        tabInvite = "Imbitasyon",
        tabAlerts = "Alerts",
        tabContacts = "Contacts",
        tabProfile = "Profile",
        roleLabel = "Senior",
        alertsCurrentHeader = "Kasalukuyang Alerto",
        alertsNoActive = "Wala kang aktibong alerto ngayon.",
        alertsHistoryHeader = "Kasaysayan ng Alerto — Nakaraang 14 na Araw",
        alertsNoHistory = "Walang alerto sa nakaraang 14 na araw.",
        alertStatusPending = "Naghihintay ng sagot",
        alertStatusFamilyNotified = "Naabisuhan ang pamilya",
        alertStatusBarangayNotified = "Naabisuhan ang barangay",
        alertStatusResolved = "Naresolba na",
        alertStatusSelfCancelled = "Sinabi mong ayos ka na",
        alertStatusFalsePositive = "Maling alarma",
        riskHigh = "Mataas",
        riskMedium = "Katamtaman",
        riskLow = "Mababa",
        allSetToday = "Ayos ang lahat ngayong araw",
        youAreSafe = "Ligtas Kayo",
        monitoringAtRisk = "May Problema sa Pagbantay",
        monitoringActive = "Aktibo ang pagbantay",
        chargeToContinue = "I-charge ang iyong cellphone\nupang magpatuloy ang pagbantay",
        helpPendingTitle = "Bukas Pa ang Kahilingan ng Tulong",
        helpPendingBody = "Hindi pa kumpirmado ng iyong pamilya. Pindutin sa ibaba kung ayos na kayo.",
        batteryLow = "Baterya - Mababa",
        batteryGood = "Baterya - Maayos",
        emergencyAlert = "EMERGENCY ALERT",
        swipeToSend = "I-swipe upang magpadala ng alerto",
        reachTitle = "Upang marating kayo ng SEENior",
        reachBody = "Upang mabuksan ang iyong screen at maipakita ang Safety Confirmation Prompt " +
            "kahit may ibang app na nakabukas, kailangan i-on ang dalawang setting. Kung hindi, " +
            "lilitaw lamang ang Safety Confirmation Prompt bilang maliit na banner na madaling " +
            "hindi mapansin.",
        openSettings = "Buksan ang settings",
        notNow = "Hindi muna",
        permissionLostTitle = "Hindi kayo mabantayan ng SEENior ngayon",
        permissionLostBody = "May pahintulot na kailangan ang SEENior na nakapatay, kaya hindi " +
            "kayo nito mabantayan nang maayos. Pindutin sa ibaba upang ibalik ito.",
        permissionLostCta = "Ibalik ito",
    )

    fun forLanguage(language: String): Copy =
        if (language == WellnessMessages.FILIPINO) FILIPINO_COPY else ENGLISH_COPY
}
