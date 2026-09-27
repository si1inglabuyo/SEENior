package com.pup.seenior.ui.family

import androidx.compose.runtime.staticCompositionLocalOf
import com.pup.seenior.ui.wellness.WellnessMessages

/**
 * Family-app copy in the account's stored language ("en" / "fil" — the same two codes the
 * senior side already uses, `WellnessMessages.ENGLISH` / `.FILIPINO`; see backend migration
 * 0013 for why this lives on the cloud `users` row rather than a local table). Mirrors
 * `ui/SeniorStrings.kt`'s Copy-data-class + `forLanguage()` pattern rather than `strings.xml`,
 * for the same reason: language is a per-account *setting*, not the device locale, and it
 * must follow a family member across devices the moment they change it in Profile.
 *
 * **Phase 1 (2026-09-27).** Covers the bottom-nav tabs, Home, the Alerts tab in full, and
 * Profile's top-level chrome (including the new Language row itself). Deliberately NOT yet
 * covered, same staged rollout the senior side went through: the Contacts tab, Edit Profile /
 * change-password / delete-account sub-screens, and the Link/Connected pairing flow — those
 * stay English until a follow-up pass. Pre-auth screens (Login/SignUp/ForgotPassword/
 * CompletePhone) are English by necessity: there is no account yet to hold a preference.
 *
 * **These translations are an agent-written draft and have not been read by a native
 * speaker** — same caveat [[seenior-language]] carries for the senior side's copy.
 */
object FamilyStrings {

    data class Copy(
        val language: String,

        // Bottom nav
        val tabHome: String,
        val tabLink: String,
        val tabAlerts: String,
        val tabContacts: String,
        val tabProfile: String,

        // Home
        val homeRoleBadge: String,
        val familyFallbackLabel: String,
        val sectionMySeniors: String,
        val sectionRecentAlerts: String,
        val seeAll: String,
        val loadingSeniors: String,
        val loadingRecentAlerts: String,
        val loadingHint: String,
        val couldNotLoadSeniorsTitle: String,
        val couldNotLoadAlertsTitle: String,
        val couldNotReachServer: String,
        val stillLinkedReassurance: String,
        val tryAgain: String,
        val noOneLinkedTitle: String,
        val noOneLinkedBody: String,
        val linkASeniorNow: String,
        val noAlertsYetTitle: String,
        val noAlertsYetBody: String,
        val riskLevelLabel: String,
        val alertsTodayLabel: String,
        val chargingLabel: String,
        val batteryLabel: String,
        val noCheckInYet: String,
        val phoneActive: String,
        val statusChecking: String,
        val statusAlert: String,
        val statusAllClear: String,

        // Alert status chips + trigger labels (shared between Home's recent-alerts row and
        // the Alerts tab's own detail screens)
        val statusPending: String,
        val statusAcknowledged: String,
        val statusEscalated: String,
        val statusResolved: String,
        val statusFalsePositive: String,
        val triggerInactivity: String,
        val triggerMovement: String,
        val triggerScreenIdle: String,
        val triggerCharging: String,
        val triggerSos: String,
        val triggerMlFlag: String,
        val triggerFallPattern: String,
        val triggerOther: String,
        val reasonInactivity: String,
        val reasonMovement: String,
        val reasonScreenIdle: String,
        val reasonCharging: String,
        val reasonSos: String,
        val reasonMlFlag: String,
        val reasonFallPattern: String,
        val reasonOther: String,

        // Relative time (relativeTimeAgo)
        val justNow: String,
        val minAgoSuffix: String,
        val hrAgoSuffix: String,
        val dAgoSuffix: String,
        // Short forms (no "ago"/"ang nakalipas") for list rows tight on width — mirrors the
        // old English-only code's `.removeSuffix(" ago")`, which cannot be done generically
        // once the suffix is translated.
        val minShortSuffix: String,
        val hrShortSuffix: String,
        val dShortSuffix: String,

        // Alerts tab
        val alertsHeader: String,
        val checkingStatus: String,
        val statusUnavailableTitle: String,
        val statusUnavailableReassurance: String,
        val couldNotReachInternet: String,
        val linkASeniorToStart: String,
        val noOngoingAlerts: String,
        val activeAlertHeader: String,
        val alertReasonLabel: String,
        val acknowledgeAlertButton: String,
        val escalationChainLabel: String,
        val escalationFamilyNotifiedPending: String,
        val lastKnownLocationLabel: String,
        val youAcknowledgedHeader: String,
        val alertAcknowledgedTitle: String,
        val locationLabel: String,
        val nextStepsLabel: String,
        val dispatchBarangayResponders: String,
        val callSeniorHeader: String,
        val contactInformationLabel: String,
        val phoneLabel: String,
        val homeAddressLabel: String,
        val callNowButton: String,
        val alertLocationHeader: String,
        val navigateHereButton: String,
        val navigateToHomeAddressButton: String,
        val dispatchBarangayHeader: String,
        val reasonForDispatchLabel: String,
        val dispatchReasonNoMovement: String,
        val dispatchReasonFallSuspected: String,
        val dispatchReasonMedicalEmergency: String,
        val dispatchReasonOther: String,
        val additionalNotesPlaceholder: String,
        val dispatchNowButton: String,
        val alertResolvedHeader: String,
        val incidentSummaryLabel: String,
        val summaryAlertId: String,
        val summaryTriggered: String,
        val summaryResolved: String,
        val summaryDuration: String,
        val summaryResolvedBy: String,
        val unknownDash: String,

        // Profile (top-level chrome only — see Phase 1 note above)
        val profileHeader: String,
        val defaultFamilyMemberName: String,
        val familyMemberRoleLabel: String,
        val myInfoLabel: String,
        val editProfileTitle: String,
        val editProfileSubtitle: String,
        val languageRowTitle: String,
        val helpInfoLabel: String,
        val aboutAppLabel: String,
        val howToUseLabel: String,
        val faqsLabel: String,
        val feedbackLabel: String,
        val logOutLabel: String,
        val deleteAccountTitle: String,
        val deleteAccountSubtitle: String,
        val logoutConfirmTitle: String,
        val logoutConfirmBody: String,
        val cancelButton: String,

        // Language picker (new — Profile -> Language)
        val languagePickerHeading: String,
        val languagePickerBodyEn: String,
        val languagePickerBodyFil: String,
        val englishOptionLabel: String,
        val filipinoOptionLabel: String,
    ) {
        fun greeting(firstName: String): String =
            if (language == WellnessMessages.FILIPINO) {
                if (firstName.isNotBlank()) "Kumusta, $firstName" else "Kumusta,"
            } else {
                if (firstName.isNotBlank()) "Hi there, $firstName" else "Hi there,"
            }

        val greetingCheckingOnSeniors: String
            get() = if (language == WellnessMessages.FILIPINO) "Tinitingnan ang iyong mga senior…" else "Checking on your seniors…"

        val greetingSomeoneNeedsAttention: String
            get() = if (language == WellnessMessages.FILIPINO) "May kailangan ng iyong atensyon" else "Someone needs your attention"

        val greetingAllSetToday: String
            get() = if (language == WellnessMessages.FILIPINO) "Ayos ang lahat ngayong araw" else "You're all set today"

        fun ongoingAlertMessage(names: List<String>): String {
            val subject = if (names.size == 1) {
                if (language == WellnessMessages.FILIPINO) "May bukas na alerto si ${names.first()}."
                else "${names.first()} has an ongoing alert."
            } else {
                if (language == WellnessMessages.FILIPINO) "May bukas na alerto ang ${names.size} senior."
                else "${names.size} seniors have ongoing alerts."
            }
            val cta = if (language == WellnessMessages.FILIPINO) "I-tap para buksan ang Alerts." else "Tap to open Alerts."
            return "$subject $cta"
        }

        fun lastCheckIn(relative: String): String =
            if (language == WellnessMessages.FILIPINO) "Huling check-in $relative" else "Last check-in $relative"

        fun monitoringLimitReached(max: Int): String =
            if (language == WellnessMessages.FILIPINO) "Naabot na ang limitasyon sa pagbantay ($max/$max)"
            else "Monitoring limit reached ($max/$max)"

        val monitoringLimitBody: String
            get() = if (language == WellnessMessages.FILIPINO) "Alisin ang isang naka-link na senior upang mabantayan ang bago."
            else "Remove a linked senior to monitor a new one."

        fun triggerShortLabel(triggerType: String): String = when (triggerType) {
            "inactivity" -> triggerInactivity
            "movement" -> triggerMovement
            "screen_idle" -> triggerScreenIdle
            "charging" -> triggerCharging
            "sos" -> triggerSos
            "ml_flag" -> triggerMlFlag
            "fall_pattern" -> triggerFallPattern
            else -> triggerOther
        }

        fun alertReasonText(triggerType: String): String = when (triggerType) {
            "inactivity" -> reasonInactivity
            "movement" -> reasonMovement
            "screen_idle" -> reasonScreenIdle
            "charging" -> reasonCharging
            "sos" -> reasonSos
            "ml_flag" -> reasonMlFlag
            "fall_pattern" -> reasonFallPattern
            else -> reasonOther
        }

        /**
         * Dispatch-reason button label, keyed by the fixed English sentence the button's
         * `onClick` sends on to `POST /alerts/{id}/dispatch` (`DISPATCH_REASON_CODES` in
         * FamilyAlertsScreen.kt). Deliberately keeps the *value* in English regardless of
         * this account's language — that string lands in `escalation_steps` and is read by
         * the barangay dashboard (a different lane's English-only React app), so only the
         * on-screen label may change, never what gets sent. Same "code vs. label" split the
         * account-deletion reason picker already uses.
         */
        fun dispatchReasonLabel(code: String): String = when (code) {
            "No movement / unresponsive" -> dispatchReasonNoMovement
            "Fall suspected" -> dispatchReasonFallSuspected
            "Medical emergency" -> dispatchReasonMedicalEmergency
            "Other" -> dispatchReasonOther
            else -> code
        }

        fun recentAlertChipLabel(status: String): String = when (status) {
            "pending" -> statusPending
            "acknowledged" -> statusAcknowledged
            "escalated" -> statusEscalated
            "resolved" -> statusResolved
            "false_positive" -> statusFalsePositive
            else -> status
        }

        fun relativeTimeAgo(minutes: Long): String = when {
            minutes < 1 -> justNow
            minutes < 60 -> "$minutes$minAgoSuffix"
            minutes < 60 * 24 -> "${minutes / 60}$hrAgoSuffix"
            else -> "${minutes / (60 * 24)}$dAgoSuffix"
        }

        /** For a list row tight on width (Home's RECENT ALERTS chip line) — same buckets,
         *  no "ago"/"ang nakalipas" tail. */
        fun relativeTimeAgoShort(minutes: Long): String = when {
            minutes < 1 -> justNow
            minutes < 60 -> "$minutes$minShortSuffix"
            minutes < 60 * 24 -> "${minutes / 60}$hrShortSuffix"
            else -> "${minutes / (60 * 24)}$dShortSuffix"
        }

        fun riskDetected(risk: String): String =
            if (language == WellnessMessages.FILIPINO) "Natukoy ang $risk Risk" else "$risk Risk Detected"

        fun mayNeedAttention(name: String): String =
            if (language == WellnessMessages.FILIPINO) "Maaaring kailangan ni $name ng iyong atensyon."
            else "$name may need your attention."

        fun detectedAt(time: String, relative: String): String =
            if (language == WellnessMessages.FILIPINO) "Natukoy $time · $relative" else "Detected $time · $relative"

        fun seniorPromptedAt(time: String): String =
            if (language == WellnessMessages.FILIPINO) "Tinanong ang senior sa $time" else "Senior prompted at $time"

        fun barangayWindow(minutes: Int): String =
            if (language == WellnessMessages.FILIPINO) "Barangay - $minutes minutong window"
            else "Barangay - $minutes mins window"

        fun barangayDispatchedBody(name: String): String =
            if (language == WellnessMessages.FILIPINO) "Ipinadala na ang mga barangay responder sa lokasyon ni $name."
            else "Barangay responders have been dispatched to $name's location."

        val barangayWillNotifyBody: String
            get() = if (language == WellnessMessages.FILIPINO) "Aabisuhan ang mga barangay responder kung hindi ito maresolba agad."
            else "Barangay responders will be notified if this isn't resolved soon."

        fun barangayNotifyWindow(minutes: Int): String =
            if (language == WellnessMessages.FILIPINO) "Aabisuhan ang barangay kung hindi maresolba sa loob ng $minutes minuto"
            else "Barangay will be notified if unresolved in $minutes minutes"

        fun callName(name: String): String = if (language == WellnessMessages.FILIPINO) "Tawagan si $name" else "Call $name"

        val navigateToSeniorLocation: String
            get() = if (language == WellnessMessages.FILIPINO) "Pumunta sa kanyang lokasyon" else "Navigate to her location"

        /** [pronoun] is "He" or "She" (from the senior's registered gender) -- meaningless in
         *  Filipino, where "siya" is gender-neutral, so the Filipino branch ignores it. */
        fun markResolvedSafe(pronoun: String): String =
            if (language == WellnessMessages.FILIPINO) "Markahang naresolba na. Ligtas na siya."
            else "Mark resolved. $pronoun's safe"

        fun dispatchExplainer(name: String): String =
            if (language == WellnessMessages.FILIPINO)
                "Ito ay humihiling ng opisyal na welfare check mula sa Barangay. Ipapadala ang isang responder sa lokasyon ni $name."
            else
                "This requests an official welfare check from the Barangay. A responder will be dispatched to $name's location."

        fun lastKnownLocationCaptured(time: String): String =
            if (language == WellnessMessages.FILIPINO) "Huling kilalang lokasyon. Nakuha noong $time."
            else "Last known location. Captured at $time"

        fun isSafe(name: String): String = if (language == WellnessMessages.FILIPINO) "Ligtas si $name" else "$name is Safe"

        fun closedByYou(time: String): String =
            if (language == WellnessMessages.FILIPINO) "Isinara mong alerto · $time" else "Alert closed by you · $time"

        val allNotifiedResolved: String
            get() = if (language == WellnessMessages.FILIPINO)
                "Naabisuhan na ang lahat ng miyembro ng pamilya at ang mga barangay responder na naresolba na ang sitwasyon."
            else
                "All family members and Barangay responders have been notified that the situation is resolved."

        fun durationMinutes(n: Long): String =
            if (language == WellnessMessages.FILIPINO) "$n minuto" else "$n minutes"

        val languageValueLabel: String
            get() = if (language == WellnessMessages.FILIPINO) filipinoOptionLabel else englishOptionLabel
    }

    private val ENGLISH_COPY = Copy(
        language = WellnessMessages.ENGLISH,
        tabHome = "Home",
        tabLink = "Link",
        tabAlerts = "Alerts",
        tabContacts = "Contacts",
        tabProfile = "Profile",
        homeRoleBadge = "Family",
        familyFallbackLabel = "Family",
        sectionMySeniors = "MY SENIORS",
        sectionRecentAlerts = "RECENT ALERTS",
        seeAll = "See all",
        loadingSeniors = "Loading your seniors…",
        loadingRecentAlerts = "Loading recent alerts…",
        loadingHint = "This can take a moment if the server is waking up.",
        couldNotLoadSeniorsTitle = "Could not load your seniors",
        couldNotLoadAlertsTitle = "Could not load recent alerts",
        couldNotReachServer = "Could not reach the server.",
        stillLinkedReassurance = "They are still linked to your account.",
        tryAgain = "Try again",
        noOneLinkedTitle = "No one linked yet",
        noOneLinkedBody = "Link your senior family member so you can keep an eye on them and receive alerts.",
        linkASeniorNow = "Link a senior now",
        noAlertsYetTitle = "No alerts yet",
        noAlertsYetBody = "Alerts about your senior will show up here.",
        riskLevelLabel = "Risk Level",
        alertsTodayLabel = "Alerts Today",
        chargingLabel = "Charging",
        batteryLabel = "Battery",
        noCheckInYet = "No check-in yet",
        phoneActive = "Phone active",
        statusChecking = "Checking",
        statusAlert = "Alert",
        statusAllClear = "All clear",
        statusPending = "Pending",
        statusAcknowledged = "Acknowledged",
        statusEscalated = "Escalated",
        statusResolved = "Resolved",
        statusFalsePositive = "False alarm",
        triggerInactivity = "No movement",
        triggerMovement = "Unusual movement",
        triggerScreenIdle = "Phone idle",
        triggerCharging = "Charging unusually long",
        triggerSos = "SOS pressed",
        triggerMlFlag = "Unusual daily pattern",
        triggerFallPattern = "Possible fall",
        triggerOther = "Unusual pattern",
        reasonInactivity = "No movement for a while during their usual active hours. No response to the check-in prompt.",
        reasonMovement = "Movement pattern looks unusual compared to their normal routine.",
        reasonScreenIdle = "Phone hasn't been used in longer than usual for this time of day.",
        reasonCharging = "Device has been charging far longer than expected with no normal activity.",
        reasonSos = "They pressed the SOS button.",
        reasonMlFlag = "Today's overall activity pattern looks unusual compared to their routine.",
        reasonFallPattern = "A possible fall was detected.",
        reasonOther = "An unusual pattern was detected in their routine.",
        justNow = "just now",
        minAgoSuffix = " min ago",
        hrAgoSuffix = " hr ago",
        dAgoSuffix = " d ago",
        minShortSuffix = " min",
        hrShortSuffix = " hr",
        dShortSuffix = " d",
        alertsHeader = "Alerts",
        checkingStatus = "Checking your senior's status…",
        statusUnavailableTitle = "Status unavailable",
        statusUnavailableReassurance = "We could not check on your senior — this does not mean anything is wrong.",
        couldNotReachInternet = "Could not reach the server. Check your internet connection.",
        linkASeniorToStart = "Link a senior to start receiving alerts.",
        noOngoingAlerts = "No ongoing alerts.",
        activeAlertHeader = "Active Alert",
        alertReasonLabel = "Alert reason",
        acknowledgeAlertButton = "Acknowledge Alert",
        escalationChainLabel = "ESCALATION CHAIN",
        escalationFamilyNotifiedPending = "You're notified - pending acknowledgement",
        lastKnownLocationLabel = "LAST KNOWN LOCATION",
        youAcknowledgedHeader = "You Acknowledged",
        alertAcknowledgedTitle = "Alert Acknowledged",
        locationLabel = "LOCATION",
        nextStepsLabel = "NEXT STEPS",
        dispatchBarangayResponders = "Dispatch barangay responders",
        callSeniorHeader = "Call Senior",
        contactInformationLabel = "CONTACT INFORMATION",
        phoneLabel = "Phone",
        homeAddressLabel = "Home address",
        callNowButton = "Call now",
        alertLocationHeader = "Alert Location",
        navigateHereButton = "Navigate here",
        navigateToHomeAddressButton = "Navigate to home address",
        dispatchBarangayHeader = "Dispatch Barangay",
        reasonForDispatchLabel = "REASON FOR DISPATCH",
        dispatchReasonNoMovement = "No movement / unresponsive",
        dispatchReasonFallSuspected = "Fall suspected",
        dispatchReasonMedicalEmergency = "Medical emergency",
        dispatchReasonOther = "Other",
        additionalNotesPlaceholder = "Additional notes for responder…",
        dispatchNowButton = "Dispatch now",
        alertResolvedHeader = "Alert Resolved",
        incidentSummaryLabel = "INCIDENT SUMMARY",
        summaryAlertId = "Alert ID",
        summaryTriggered = "Triggered",
        summaryResolved = "Resolved",
        summaryDuration = "Duration",
        summaryResolvedBy = "Resolved by",
        unknownDash = "—",
        profileHeader = "Profile",
        defaultFamilyMemberName = "Family Member",
        familyMemberRoleLabel = "Family Member",
        myInfoLabel = "MY INFO",
        editProfileTitle = "Edit profile",
        editProfileSubtitle = "Full name, mobile number, password",
        languageRowTitle = "Language",
        helpInfoLabel = "HELP & INFORMATION",
        aboutAppLabel = "About this app",
        howToUseLabel = "How to use",
        faqsLabel = "FAQs",
        feedbackLabel = "Feedback & requests",
        logOutLabel = "Log Out",
        deleteAccountTitle = "Delete account",
        deleteAccountSubtitle = "Permanently remove your account and unlink your seniors",
        logoutConfirmTitle = "Log out?",
        logoutConfirmBody = "You'll need to sign in again to see your linked seniors.",
        cancelButton = "Cancel",
        languagePickerHeading = "Choose your language",
        languagePickerBodyEn = "The app will use this language.",
        languagePickerBodyFil = "Gagamitin ng app ang wikang ito.",
        englishOptionLabel = "English",
        filipinoOptionLabel = "Filipino / Tagalog",
    )

    private val FILIPINO_COPY = ENGLISH_COPY.copy(
        language = WellnessMessages.FILIPINO,
        tabHome = "Home",
        tabLink = "Link",
        tabAlerts = "Alerts",
        tabContacts = "Contacts",
        tabProfile = "Profile",
        homeRoleBadge = "Pamilya",
        familyFallbackLabel = "Pamilya",
        sectionMySeniors = "MGA SENIOR KO",
        sectionRecentAlerts = "MGA RESIYENTENG ALERTO",
        seeAll = "Tingnan lahat",
        loadingSeniors = "Ikinakarga ang iyong mga senior…",
        loadingRecentAlerts = "Ikinakarga ang mga resiyenteng alerto…",
        loadingHint = "Maaaring tumagal ito kung ginigising pa ang server.",
        couldNotLoadSeniorsTitle = "Hindi ma-load ang iyong mga senior",
        couldNotLoadAlertsTitle = "Hindi ma-load ang mga resiyenteng alerto",
        couldNotReachServer = "Hindi maabot ang server.",
        stillLinkedReassurance = "Naka-link pa rin sila sa iyong account.",
        tryAgain = "Subukan ulit",
        noOneLinkedTitle = "Walang naka-link pa",
        noOneLinkedBody = "I-link ang iyong senior na kapamilya upang mabantayan mo sila at matanggap ang mga alerto.",
        linkASeniorNow = "I-link ang senior ngayon",
        noAlertsYetTitle = "Walang alerto pa",
        noAlertsYetBody = "Lalabas dito ang mga alerto tungkol sa iyong senior.",
        riskLevelLabel = "Antas ng Panganib",
        alertsTodayLabel = "Alerto Ngayong Araw",
        chargingLabel = "Naka-charge",
        batteryLabel = "Baterya",
        noCheckInYet = "Walang check-in pa",
        phoneActive = "Aktibo ang telepono",
        statusChecking = "Sinusuri",
        statusAlert = "Alerto",
        statusAllClear = "Ayos naman",
        statusPending = "Naghihintay",
        statusAcknowledged = "Kinumpirma",
        statusEscalated = "Naipasa sa barangay",
        statusResolved = "Naresolba",
        statusFalsePositive = "Maling alarma",
        triggerInactivity = "Walang galaw",
        triggerMovement = "Kakaibang galaw",
        triggerScreenIdle = "Hindi ginagamit ang telepono",
        triggerCharging = "Matagal nang naka-charge",
        triggerSos = "Pinindot ang SOS",
        triggerMlFlag = "Kakaibang gawi sa araw",
        triggerFallPattern = "Posibleng nadapa",
        triggerOther = "Kakaibang pattern",
        reasonInactivity = "Matagal na hindi gumagalaw sa oras na dapat siyang aktibo. Walang sagot sa check-in prompt.",
        reasonMovement = "Kakaiba ang galaw kumpara sa normal niyang gawi.",
        reasonScreenIdle = "Matagal nang hindi nagagamit ang telepono para sa oras na ito.",
        reasonCharging = "Matagal nang naka-charge ang device na walang normal na aktibidad.",
        reasonSos = "Pinindot niya ang SOS button.",
        reasonMlFlag = "Kakaiba ang kabuuang gawi ngayong araw kumpara sa kanyang routine.",
        reasonFallPattern = "May natukoy na posibleng pagkadapa.",
        reasonOther = "May natukoy na kakaibang pattern sa kanyang routine.",
        justNow = "ngayon lang",
        minAgoSuffix = " minuto ang nakalipas",
        hrAgoSuffix = " oras ang nakalipas",
        dAgoSuffix = " araw ang nakalipas",
        minShortSuffix = " min",
        hrShortSuffix = " oras",
        dShortSuffix = " araw",
        alertsHeader = "Alerts",
        checkingStatus = "Sinusuri ang katayuan ng iyong senior…",
        statusUnavailableTitle = "Hindi available ang katayuan",
        statusUnavailableReassurance = "Hindi namin nasuri ang iyong senior — hindi ito nangangahulugang may mali.",
        couldNotReachInternet = "Hindi maabot ang server. Suriin ang iyong internet connection.",
        linkASeniorToStart = "I-link ang isang senior upang makatanggap ng alerto.",
        noOngoingAlerts = "Walang kasalukuyang alerto.",
        activeAlertHeader = "Aktibong Alerto",
        alertReasonLabel = "Dahilan ng alerto",
        acknowledgeAlertButton = "Kumpirmahin ang Alerto",
        escalationChainLabel = "TALAAN NG ESCALATION",
        escalationFamilyNotifiedPending = "Naabisuhan ka - naghihintay ng kumpirmasyon",
        lastKnownLocationLabel = "HULING KILALANG LOKASYON",
        youAcknowledgedHeader = "Kinumpirma Mo Na",
        alertAcknowledgedTitle = "Nakumpirma ang Alerto",
        locationLabel = "LOKASYON",
        nextStepsLabel = "SUSUNOD NA HAKBANG",
        dispatchBarangayResponders = "Ipadala ang mga barangay responder",
        callSeniorHeader = "Tawagan ang Senior",
        contactInformationLabel = "IMPORMASYON NG KONTAK",
        phoneLabel = "Telepono",
        homeAddressLabel = "Tirahan",
        callNowButton = "Tawagan ngayon",
        alertLocationHeader = "Lokasyon ng Alerto",
        navigateHereButton = "Pumunta dito",
        navigateToHomeAddressButton = "Pumunta sa tirahan",
        dispatchBarangayHeader = "Ipadala sa Barangay",
        reasonForDispatchLabel = "DAHILAN PARA SA PAGPAPADALA",
        dispatchReasonNoMovement = "Walang galaw / hindi tumutugon",
        dispatchReasonFallSuspected = "Pinaghihinalaang nadapa",
        dispatchReasonMedicalEmergency = "Medical emergency",
        dispatchReasonOther = "Iba pa",
        additionalNotesPlaceholder = "Karagdagang tala para sa responder…",
        dispatchNowButton = "Ipadala ngayon",
        alertResolvedHeader = "Naresolbang Alerto",
        incidentSummaryLabel = "BUOD NG INSIDENTE",
        summaryAlertId = "Alert ID",
        summaryTriggered = "Nag-trigger",
        summaryResolved = "Naresolba",
        summaryDuration = "Tagal",
        summaryResolvedBy = "Naresolba ni",
        unknownDash = "—",
        profileHeader = "Profile",
        defaultFamilyMemberName = "Miyembro ng Pamilya",
        familyMemberRoleLabel = "Miyembro ng Pamilya",
        myInfoLabel = "MGA IMPORMASYON KO",
        editProfileTitle = "I-edit ang profile",
        editProfileSubtitle = "Buong pangalan, numero ng mobile, password",
        languageRowTitle = "Wika",
        helpInfoLabel = "TULONG AT IMPORMASYON",
        aboutAppLabel = "Tungkol sa app na ito",
        howToUseLabel = "Paano gamitin",
        faqsLabel = "Mga Tanong",
        feedbackLabel = "Puna at kahilingan",
        logOutLabel = "Mag-log Out",
        deleteAccountTitle = "Burahin ang account",
        deleteAccountSubtitle = "Permanenteng alisin ang iyong account at i-unlink ang mga senior mo",
        logoutConfirmTitle = "Mag-log out?",
        logoutConfirmBody = "Kailangan mo ulit mag-sign in upang makita ang mga naka-link na senior mo.",
        cancelButton = "Kanselahin",
        languagePickerHeading = "Pumili ng wika",
        languagePickerBodyEn = "The app will use this language.",
        languagePickerBodyFil = "Gagamitin ng app ang wikang ito.",
        englishOptionLabel = "English",
        filipinoOptionLabel = "Filipino / Tagalog",
    )

    fun forLanguage(language: String): Copy =
        if (language == WellnessMessages.FILIPINO) FILIPINO_COPY else ENGLISH_COPY
}

/**
 * Provided in `FamilyDashboard`, sourced from the logged-in account's `UserDto.languagePreference`
 * (default "en" until that fetch lands) — see [FamilyStrings] for why this is per-account rather
 * than per-device.
 */
val LocalFamilyCopy = staticCompositionLocalOf {
    FamilyStrings.forLanguage(WellnessMessages.ENGLISH)
}
