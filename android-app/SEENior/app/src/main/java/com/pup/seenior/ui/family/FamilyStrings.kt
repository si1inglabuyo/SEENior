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
        val contactSupportLabel: String,
        val termsLabel: String,
        val privacyLabel: String,
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

        // Contacts tab
        val contactsSearchPlaceholder: String,
        val noSeniorsLinkedYet: String,
        val unlinkSeniorButton: String,
        val unlinkConfirmTitle: String,
        val unlinkButton: String,

        // Edit profile
        val fullNameLabel: String,
        val mobileNumberLabel: String,
        val invalidPhoneError: String,
        val changePasswordButton: String,
        val setAPasswordButton: String,
        val savingEllipsis: String,
        val saveChangesButton: String,

        // Change/set password dialog
        val passwordSetTitle: String,
        val passwordChangedTitle: String,
        val passwordSetBody: String,
        val passwordChangedBody: String,
        val currentPasswordLabel: String,
        val newPasswordLabel: String,
        val confirmNewPasswordLabel: String,
        val passwordsDontMatch: String,
        val doneButton: String,
        val saveButton: String,
        val savingDots: String,

        // Delete account
        val deleteAccountWarning: String,
        val tellUsWhyRequired: String,
        val deleteReasonSeniorNoLongerNeeds: String,
        val deleteReasonNotCaregiver: String,
        val deleteReasonDuplicate: String,
        val deleteReasonPrivacy: String,
        val deleteReasonNotUseful: String,
        val deleteReasonOther: String,
        val tellUsMoreLabel: String,
        val tellUsMoreError: String,
        val deletingEllipsis: String,
        val deleteMyAccountButton: String,
        val deleteConfirmTitle: String,
        val deleteConfirmBody: String,
        val deleteButton: String,

        // Link (enter code)
        val linkToYourSeniorHeading: String,
        val enterInviteCodeBody: String,
        val inviteCodeLabel: String,
        val lookingUpCode: String,
        val verifyCodeButton: String,
        val seniorMustGenerateHint: String,
        val manageLinkedSeniors: String,

        // Connected (relationship picker)
        val connectedTitle: String,
        val youAreSeniorsLabel: String,
        val relationshipDaughter: String,
        val relationshipSon: String,
        val relationshipGrandchild: String,
        val relationshipCaregiver: String,
        val relationshipHusband: String,
        val relationshipWife: String,
        val otherRelationshipPlaceholder: String,
        val connectingEllipsis: String,
        val goToHomeButton: String,
        val addAnotherSenior: String,
        val linkedSuccessTitle: String,
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

        fun unlinkConfirmBody(name: String): String =
            if (language == WellnessMessages.FILIPINO)
                "Sigurado ka bang gusto mong i-unlink si $name? Hindi ka na makakatanggap ng mga " +
                    "alerto at update mula sa senior na ito."
            else
                "Are you sure you want to unlink $name? You will no longer receive alerts and " +
                    "updates from this senior."

        /** [email] is shown so the family member knows which sign-in identity a new password
         *  would attach to -- meaningless to translate, since it's their own literal address. */
        fun googleSignupNotice(email: String): String =
            if (language == WellnessMessages.FILIPINO)
                "Nag-sign up ka gamit ang Google. Magdagdag ng password upang makapag-sign in ka " +
                    "rin gamit ang $email."
            else
                "You signed up with Google. Add a password to also sign in with $email."

        /** Dispatch-reason-style code/label split: [code] is the stable string the app already
         *  sends as `deletion_reason` (AccountDeletionRequest.reason in DELETE_REASONS below) --
         *  never translated, since the backend stores it verbatim for audit. */
        fun deleteReasonLabel(code: String): String = when (code) {
            "senior_no_longer_needs" -> deleteReasonSeniorNoLongerNeeds
            "not_caregiver" -> deleteReasonNotCaregiver
            "duplicate" -> deleteReasonDuplicate
            "privacy" -> deleteReasonPrivacy
            "not_useful" -> deleteReasonNotUseful
            else -> deleteReasonOther
        }

        /** [code] is one of the fixed English values in RELATIONSHIPS (ConnectedScreen.kt),
         *  stored verbatim as `Contact.relationship_label` and shown on the SENIOR's own
         *  Contacts screen regardless of the family member's language -- so, same reasoning as
         *  the dispatch/delete reasons, only the on-screen chip label may vary; the stored value
         *  never does, or a Filipino chip pick would show untranslated on an English-reading
         *  senior's screen and vice versa. */
        fun relationshipLabel(code: String): String = when (code) {
            "daughter" -> relationshipDaughter
            "son" -> relationshipSon
            "grandchild" -> relationshipGrandchild
            "caregiver" -> relationshipCaregiver
            "husband" -> relationshipHusband
            "wife" -> relationshipWife
            else -> code.replaceFirstChar { it.uppercase() }
        }

        fun connectingHint(code: String): String =
            if (language == WellnessMessages.FILIPINO)
                "Kumokonekta sa server ng SEENior upang i-verify ang $code. Siguraduhing may " +
                    "internet ka."
            else
                "Connecting to SEENior's server to verify $code. Make sure you have internet."

        fun nowMonitoring(name: String): String =
            if (language == WellnessMessages.FILIPINO)
                "Binabantayan mo na ngayon si $name. Maaabisuhan ka kung may matukoy na kakaiba."
            else
                "You are now monitoring $name. You'll receive alerts if anything unusual is detected."

        fun linkedSuccessBody(name: String): String =
            if (language == WellnessMessages.FILIPINO)
                "Binabantayan mo na ngayon si $name. Maaabisuhan ka agad kung may matukoy na " +
                    "kakaiba."
            else
                "You are now monitoring $name. You'll be alerted right away if anything unusual " +
                    "is detected."

        // -- Error/status messages for the ViewModels below. ViewModels are plain classes, not
        // Composables, so they cannot read LocalFamilyCopy themselves -- each stores a
        // FamilyError describing WHAT went wrong (see FamilyError.kt), and the screen calls one
        // of these to render it in the account's language, same split as nowMonitoring()/
        // linkedSuccessBody() above already use for ViewModel-sourced data.
        fun couldNotLoadAlerts(code: Int): String =
            if (language == WellnessMessages.FILIPINO) "Hindi ma-load ang mga alerto (server error $code)."
            else "Could not load alerts (server error $code)."

        fun couldNotAcknowledge(code: Int): String =
            if (language == WellnessMessages.FILIPINO) "Hindi ma-acknowledge (server error $code)."
            else "Could not acknowledge (server error $code)."

        fun couldNotDispatchBarangay(code: Int): String =
            if (language == WellnessMessages.FILIPINO) "Hindi maipadala sa barangay (server error $code)."
            else "Could not dispatch barangay (server error $code)."

        fun couldNotResolve(code: Int): String =
            if (language == WellnessMessages.FILIPINO) "Hindi maresolba (server error $code)."
            else "Could not resolve (server error $code)."

        fun couldNotLoadProfile(code: Int): String =
            if (language == WellnessMessages.FILIPINO) "Hindi ma-load ang iyong profile (server error $code)."
            else "Could not load your profile (server error $code)."

        fun couldNotSaveProfile(code: Int): String =
            if (language == WellnessMessages.FILIPINO) "Hindi ma-save (server error $code)."
            else "Could not save (server error $code)."

        fun couldNotChangePassword(code: Int): String =
            if (language == WellnessMessages.FILIPINO) "Hindi mabago ang password (server error $code)."
            else "Could not change password (server error $code)."

        fun couldNotSetPassword(code: Int): String =
            if (language == WellnessMessages.FILIPINO) "Hindi maitakda ang password (server error $code)."
            else "Could not set a password (server error $code)."

        fun couldNotDeleteAccount(code: Int): String =
            if (language == WellnessMessages.FILIPINO) "Hindi ma-delete ang iyong account (server error $code). Subukan ulit."
            else "Could not delete your account (server error $code). Please try again."

        fun couldNotLoadSeniors(code: Int): String =
            if (language == WellnessMessages.FILIPINO) "Hindi ma-load ang iyong mga naka-link na senior (server error $code)."
            else "Could not load your linked seniors (server error $code)."

        fun couldNotUnlink(code: Int): String =
            if (language == WellnessMessages.FILIPINO) "Hindi ma-unlink (server error $code)."
            else "Could not unlink (server error $code)."

        fun couldNotLoadHomeActivity(code: Int): String =
            if (language == WellnessMessages.FILIPINO) "Hindi ma-load ang aktibidad ng alerto (server error $code)."
            else "Could not load alert activity (server error $code)."

        fun couldNotVerifyCode(code: Int): String =
            if (language == WellnessMessages.FILIPINO) "Hindi ma-verify (server error $code)."
            else "Could not verify (server error $code)."

        fun couldNotConnect(code: Int): String =
            if (language == WellnessMessages.FILIPINO) "Hindi makakonekta (server error $code)."
            else "Could not connect (server error $code)."

        val currentPasswordIncorrect: String
            get() = if (language == WellnessMessages.FILIPINO) "Mali ang kasalukuyang password." else "Current password is incorrect."

        val accountAlreadyHasPassword: String
            get() = if (language == WellnessMessages.FILIPINO) "Ang account na ito ay may password na. Gamitin ang Baguhin ang Password."
            else "This account already has a password. Use Change Password."

        val invalidOrExpiredCode: String
            get() = if (language == WellnessMessages.FILIPINO) "Hindi valid o expired na ang code. Hilingin sa senior na gumawa ng bago."
            else "Invalid or expired code. Ask the senior to generate a new one."

        val codeExpiredOrLimitReached: String
            get() = if (language == WellnessMessages.FILIPINO) "Naubos na ang code, o naabot mo na ang limitasyong 3 senior."
            else "That code just expired, or you're already at the 3-senior limit."

        val couldNotReachServerCheckConnection: String
            get() = if (language == WellnessMessages.FILIPINO) "Hindi maabot ang server. Suriin ang iyong internet connection."
            else "Could not reach the server. Check your internet connection."

        val couldNotReachServerTryAgain: String
            get() = if (language == WellnessMessages.FILIPINO) "Hindi maabot ang server. Suriin ang koneksyon at subukan ulit."
            else "Could not reach the server. Check your connection and try again."

        val couldNotReachServerHasInternet: String
            get() = if (language == WellnessMessages.FILIPINO) "Hindi maabot ang server. Siguraduhing may internet ka."
            else "Could not reach the server. Make sure you have internet."

        val sessionExpiredMessage: String
            get() = if (language == WellnessMessages.FILIPINO) "Nag-expire ang iyong session. Mag-log in ulit."
            else "Your session expired. Please log in again."

        val backContentDescription: String
            get() = if (language == WellnessMessages.FILIPINO) "Bumalik" else "Back"

        val mapPreviewUnavailable: String
            get() = if (language == WellnessMessages.FILIPINO) "Hindi available ang preview ng mapa" else "Map preview unavailable"

        val yourEmailFallback: String
            get() = if (language == WellnessMessages.FILIPINO) "iyong email" else "your email"

        val theSeniorFallback: String
            get() = if (language == WellnessMessages.FILIPINO) "ang senior" else "the senior"

        // -- AlertLocationMap.kt captions. That file draws the map shared by the Active/
        // Acknowledged/Resolved alert screens above but has no Composable-scoped `copy` of its
        // own, so it takes one as a parameter instead.
        fun currentLocationKnown(place: String): String =
            if (language == WellnessMessages.FILIPINO) "Kasalukuyang lokasyon: $place" else "Current location: $place"

        val currentLocationUnknownPlace: String
            get() = if (language == WellnessMessages.FILIPINO)
                "Nakuha ang kasalukuyang lokasyon noong itinaas ang alerto (nasa mapa)."
            else "Current location captured when the alert was raised (shown on the map)."

        val locationCapturedOnceNote: String
            get() = if (language == WellnessMessages.FILIPINO)
                "Nakuha nang isang beses lang, sa sandaling iyon — hindi sinusubaybayan ng SEENior ang lokasyon sa anumang oras."
            else "Captured once, at that moment only — SEENior does not track location at any other time."

        fun approximateAreaNote(metres: Int): String =
            if (language == WellnessMessages.FILIPINO) "Tinatayang lugar — humigit-kumulang $metres m ang lawak."
            else "Approximate area — about $metres m across."

        val noLiveLocationCaptured: String
            get() = if (language == WellnessMessages.FILIPINO) "Walang nakuhang live location para sa alertong ito."
            else "No live location was captured for this alert."

        fun homeAddressLine(address: String): String = "$homeAddressLabel: $address"
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
        contactSupportLabel = "Contact support",
        termsLabel = "Terms & conditions",
        privacyLabel = "Privacy policy",
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

        contactsSearchPlaceholder = "Search seniors…",
        noSeniorsLinkedYet = "No seniors linked yet.",
        unlinkSeniorButton = "Unlink Senior",
        unlinkConfirmTitle = "Unlink this senior?",
        unlinkButton = "Unlink",

        fullNameLabel = "Full name",
        mobileNumberLabel = "Mobile number",
        invalidPhoneError = "Enter a valid PH mobile number (09XXXXXXXXX or +639XXXXXXXXX)",
        changePasswordButton = "Change Password",
        setAPasswordButton = "Set a Password",
        savingEllipsis = "SAVING…",
        saveChangesButton = "SAVE CHANGES",

        passwordSetTitle = "Password set",
        passwordChangedTitle = "Password changed",
        passwordSetBody = "You can now sign in with your email and this password, or keep using Google.",
        passwordChangedBody = "Your password was updated successfully.",
        currentPasswordLabel = "Current password",
        newPasswordLabel = "New password",
        confirmNewPasswordLabel = "Confirm new password",
        passwordsDontMatch = "Passwords don't match",
        doneButton = "Done",
        saveButton = "Save",
        savingDots = "Saving…",

        deleteAccountWarning = "This removes your account and unlinks every senior you monitor. They will no " +
            "longer send alerts to you, and you will need to sign up again to use the app. " +
            "This cannot be undone.",
        tellUsWhyRequired = "Please tell us why (required)",
        deleteReasonSeniorNoLongerNeeds = "The senior I monitored no longer needs this",
        deleteReasonNotCaregiver = "I'm no longer a caregiver for this senior",
        deleteReasonDuplicate = "I made this account by mistake or it's a duplicate",
        deleteReasonPrivacy = "Privacy concerns",
        deleteReasonNotUseful = "It didn't work the way I expected",
        deleteReasonOther = "Another reason",
        tellUsMoreLabel = "Tell us more",
        tellUsMoreError = "Please tell us your reason",
        deletingEllipsis = "DELETING…",
        deleteMyAccountButton = "DELETE MY ACCOUNT",
        deleteConfirmTitle = "Delete your account?",
        deleteConfirmBody = "Your account is removed and every senior is unlinked. This cannot be undone.",
        deleteButton = "Delete",

        linkToYourSeniorHeading = "Link to Your Senior",
        enterInviteCodeBody = "Enter the invite code from a senior's SEENior app to connect.",
        inviteCodeLabel = "Invite code:",
        lookingUpCode = "Looking up code…",
        verifyCodeButton = "→  Verify code",
        seniorMustGenerateHint = "The senior must generate a code first from their SEENior app.",
        manageLinkedSeniors = "Manage linked seniors",

        connectedTitle = "Connected",
        youAreSeniorsLabel = "You are the senior's:",
        relationshipDaughter = "Daughter",
        relationshipSon = "Son",
        relationshipGrandchild = "Grandchild",
        relationshipCaregiver = "Caregiver",
        relationshipHusband = "Husband",
        relationshipWife = "Wife",
        otherRelationshipPlaceholder = "Other (e.g. niece, neighbor)",
        connectingEllipsis = "CONNECTING…",
        goToHomeButton = "Go to Home",
        addAnotherSenior = "Add another senior",
        linkedSuccessTitle = "Linked successfully",
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
        contactSupportLabel = "Makipag-ugnayan sa support",
        termsLabel = "Mga tuntunin at kondisyon",
        privacyLabel = "Privacy Policy",
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

        contactsSearchPlaceholder = "Maghanap ng senior…",
        noSeniorsLinkedYet = "Walang naka-link na senior pa.",
        unlinkSeniorButton = "I-unlink ang Senior",
        unlinkConfirmTitle = "I-unlink ang senior na ito?",
        unlinkButton = "I-unlink",

        fullNameLabel = "Buong pangalan",
        mobileNumberLabel = "Numero ng mobile",
        invalidPhoneError = "Ilagay ang wastong PH mobile number (09XXXXXXXXX o +639XXXXXXXXX)",
        changePasswordButton = "Baguhin ang Password",
        setAPasswordButton = "Magtakda ng Password",
        savingEllipsis = "SINE-SAVE…",
        saveChangesButton = "I-SAVE ANG PAGBABAGO",

        passwordSetTitle = "Naitakda ang password",
        passwordChangedTitle = "Nabago ang password",
        passwordSetBody = "Maaari ka na ngayong mag-sign in gamit ang iyong email at password na ito, o patuloy gamitin ang Google.",
        passwordChangedBody = "Matagumpay na na-update ang iyong password.",
        currentPasswordLabel = "Kasalukuyang password",
        newPasswordLabel = "Bagong password",
        confirmNewPasswordLabel = "Kumpirmahin ang bagong password",
        passwordsDontMatch = "Hindi tugma ang mga password",
        doneButton = "Tapos",
        saveButton = "I-save",
        savingDots = "Ise-save…",

        deleteAccountWarning = "Aalisin nito ang iyong account at i-unlink ang bawat senior na binabantayan mo. Hindi na sila " +
            "makakapadala ng alerto sa iyo, at kailangan mo ulit mag-sign up upang gamitin ang app. " +
            "Hindi na ito maibabalik.",
        tellUsWhyRequired = "Sabihin sa amin kung bakit (kinakailangan)",
        deleteReasonSeniorNoLongerNeeds = "Hindi na kailangan ng senior na binabantayan ko ito",
        deleteReasonNotCaregiver = "Hindi na ako caregiver ng senior na ito",
        deleteReasonDuplicate = "Nagawa ko ito nang mali o duplicate lang",
        deleteReasonPrivacy = "Alalahanin sa privacy",
        deleteReasonNotUseful = "Hindi ito gumana sa inaasahan ko",
        deleteReasonOther = "Ibang dahilan",
        tellUsMoreLabel = "Sabihin pa sa amin",
        tellUsMoreError = "Pakisabi ang iyong dahilan",
        deletingEllipsis = "BINUBURA…",
        deleteMyAccountButton = "BURAHIN ANG AKING ACCOUNT",
        deleteConfirmTitle = "Burahin ang iyong account?",
        deleteConfirmBody = "Aalisin ang iyong account at ma-unlink ang bawat senior. Hindi na ito maibabalik.",
        deleteButton = "Burahin",

        linkToYourSeniorHeading = "I-link sa Iyong Senior",
        enterInviteCodeBody = "Ilagay ang invite code mula sa SEENior app ng senior upang kumonekta.",
        inviteCodeLabel = "Invite code:",
        lookingUpCode = "Hinahanap ang code…",
        verifyCodeButton = "→  I-verify ang code",
        seniorMustGenerateHint = "Kailangan munang gumawa ng code ang senior mula sa kanilang SEENior app.",
        manageLinkedSeniors = "Pamahalaan ang mga naka-link na senior",

        connectedTitle = "Nakakonekta",
        youAreSeniorsLabel = "Ikaw ang kanyang:",
        relationshipDaughter = "Anak na babae",
        relationshipSon = "Anak na lalaki",
        relationshipGrandchild = "Apo",
        relationshipCaregiver = "Caregiver",
        relationshipHusband = "Asawang lalaki",
        relationshipWife = "Asawang babae",
        otherRelationshipPlaceholder = "Iba pa (hal. pamangkin, kapitbahay)",
        connectingEllipsis = "KUMOKONEKTA…",
        goToHomeButton = "Pumunta sa Home",
        addAnotherSenior = "Magdagdag ng isa pang senior",
        linkedSuccessTitle = "Matagumpay na na-link",
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
