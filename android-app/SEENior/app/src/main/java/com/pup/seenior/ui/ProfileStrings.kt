package com.pup.seenior.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.pup.seenior.ui.wellness.WellnessMessages

/**
 * The everyday screens a senior reaches from the tabs but does not live on — Profile, Contacts,
 * Invite — in both supported languages.
 *
 * Third and last of the senior-side copy containers, and the split between them is by *when* the
 * language is known, not by taste:
 *
 * - [SeniorStrings] — the tabs and Home, resolved from the database on every composition.
 * - [OnboardingStrings] — the setup flow, where the answer is still being given. Its form
 *   vocabulary (FIRST NAME, GENDER, Select…) is reused by Edit profile rather than translated
 *   twice, which is why `SeniorDashboard` provides that local as well as this one.
 * - this file — everything else behind the tabs.
 *
 * **A draft pending a native speaker's review**, same as its two siblings.
 */
object ProfileStrings {

    data class Copy(
        // Profile tab
        val profileHeader: String,
        val sectionMyInfo: String,
        val editProfile: String,
        val editProfileSubtitle: String,
        val languageRow: String,
        val languageRowSubtitle: String,
        /* The Profile -> Language screen's own scaffold title reuses languageRow directly
         * (same bilingual "Language / Wika" label) rather than duplicating it here. This is
         * that screen's card heading only. */
        val languageChooseHeading: String,
        val sectionMyContacts: String,
        val familyContacts: String,
        val familyContactsSubtitle: String,
        val sectionHelp: String,
        val aboutApp: String,
        val howToUse: String,
        val faqs: String,
        val contactSupport: String,
        val sectionAboutApp: String,
        val termsRow: String,
        val privacyRow: String,
        val getInviteCode: String,

        // Edit profile — the labels it shares with sign-up come from OnboardingStrings
        val addressLabel: String,
        val addressPlaceholder: String,
        val saving: String,
        val saveChanges: String,

        // Delete account
        val sectionAccount: String,
        val deleteAccountRow: String,
        val deleteAccountRowSubtitle: String,
        val deleteHeader: String,
        val deleteIntro: String,
        val deleteReasonHeading: String,
        val deleteReasonNoLongerNeeded: String,
        val deleteReasonSwitchingPhone: String,
        val deleteReasonBattery: String,
        val deleteReasonTooManyAlerts: String,
        val deleteReasonPrivacy: String,
        val deleteReasonOther: String,
        val deleteNoteLabel: String,
        val deleteNotePlaceholder: String,
        val deleteNoteRequiredForOther: String,
        val deleteButton: String,
        val deleting: String,
        val deleteConfirmTitle: String,
        val deleteConfirmBody: String,
        val deleteConfirmYes: String,

        // Contacts tab
        val contactsHeader: String,
        val contactsLoadFailedInline: String,
        val contactsLoadFailedTitle: String,
        val contactsStillConnected: String,
        val tryAgain: String,
        val familyMemberFallback: String,
        val familyFallbackLabel: String,
        val phone: String,
        val status: String,
        val removeContact: String,
        val thisFamilyMember: String,
        val removeBody: String,
        val remove: String,
        val cancel: String,
        val noFamilyTitle: String,
        val noFamilyBody: String,
        val noFamilyBarangayNote: String,
        val inviteTabLabel: String,

        // Invite tab
        val inviteHeader: String,
        val shareWithFamily: String,
        val shareWithFamilyBody: String,
        val yourInviteCode: String,
        val copyCode: String,
        val generating: String,
        val generateNew: String,
        val generateCode: String,
        val codesExpireNote: String,
        val pairSuccessTitle: String,
        val pairSuccessDismiss: String,
    ) {
        /** Shown to the senior when a family member links with their live code. */
        fun pairSuccessBody(name: String): String =
            if (this === FILIPINO_COPY)
                "Naka-konekta na si " + name + ". Aabisuhan sila kung may mapapansing hindi " +
                    "karaniwan sa inyong routine."
            else
                name + " is now connected. They will be alerted if anything unusual is " +
                    "detected in your routine."

        /** "Senior · 65 years old" — the age is the senior's own and reads the same either way. */
        fun seniorAge(age: Int): String =
            if (this === FILIPINO_COPY) "Senior · " + age + " taong gulang"
            else "Senior · " + age + " years old"

        fun expiresIn(remaining: String): String =
            if (this === FILIPINO_COPY) "Mag-e-expire sa " + remaining else "Expires in " + remaining

        fun removeTitle(name: String): String =
            if (this === FILIPINO_COPY) "Alisin si " + name + "?" else "Remove " + name + "?"

        /**
         * How recently this family member last opened their app, in words. [minutesAgo] is null
         * when they never have. Replaces the old always-green "Online Now" — this is a
         * "recently active" signal, not live presence, and is worded so it never overclaims.
         */
        fun contactActive(minutesAgo: Long?): String = when {
            minutesAgo == null ->
                if (this === FILIPINO_COPY) "Hindi pa nabubuksan kamakailan" else "Not opened recently"
            minutesAgo < 15L ->
                if (this === FILIPINO_COPY) "Aktibo ngayon" else "Active now"
            minutesAgo < 60L ->
                if (this === FILIPINO_COPY) "Aktibo $minutesAgo min ang nakalipas"
                else "Active $minutesAgo min ago"
            minutesAgo < 60L * 24 ->
                (minutesAgo / 60).let { h ->
                    if (this === FILIPINO_COPY) "Aktibo $h oras ang nakalipas" else "Active $h hr ago"
                }
            else ->
                (minutesAgo / (60L * 24)).let { d ->
                    if (this === FILIPINO_COPY) "Aktibo $d araw ang nakalipas" else "Active $d d ago"
                }
        }

        /** code → label, in the senior's language. The code is what goes to the server. */
        fun deleteReasons(): List<Pair<String, String>> = listOf(
            "no_longer_needed" to deleteReasonNoLongerNeeded,
            "switching_phone" to deleteReasonSwitchingPhone,
            "battery" to deleteReasonBattery,
            "too_many_alerts" to deleteReasonTooManyAlerts,
            "privacy" to deleteReasonPrivacy,
            "other" to deleteReasonOther,
        )
    }

    private val ENGLISH_COPY = Copy(
        profileHeader = "Profile",
        sectionMyInfo = "MY INFO",
        editProfile = "Edit profile",
        editProfileSubtitle = "First Name, Surname, Age, Gender, Mobile Number…",
        languageRow = "Language / Wika",
        languageRowSubtitle = "English, Filipino / Tagalog",
        languageChooseHeading = "Choose your language",
        sectionMyContacts = "MY CONTACTS",
        familyContacts = "Family contacts",
        familyContactsSubtitle = "Add someone to be notified before your barangay",
        sectionHelp = "HELP & INFORMATION",
        aboutApp = "About this app",
        howToUse = "How to use",
        faqs = "FAQs",
        contactSupport = "Contact support",
        sectionAboutApp = "ABOUT SEENIOR",
        termsRow = "Terms & conditions",
        privacyRow = "Privacy policy",
        getInviteCode = "Get an invite code",

        addressLabel = "ADDRESS",
        addressPlaceholder = "House No., Street, Barangay, City",
        saving = "SAVING…",
        saveChanges = "SAVE CHANGES",

        sectionAccount = "ACCOUNT",
        deleteAccountRow = "Delete my account",
        deleteAccountRowSubtitle = "Stop monitoring and remove your profile from this phone",
        deleteHeader = "Delete account",
        deleteIntro = "This stops all monitoring and erases your profile, routine, and alert " +
            "history from this phone. Your family contacts will be unlinked. This cannot be undone.",
        deleteReasonHeading = "Please tell us why (required)",
        deleteReasonNoLongerNeeded = "I don't need monitoring anymore",
        deleteReasonSwitchingPhone = "I'm switching to a new phone",
        deleteReasonBattery = "It uses too much battery",
        deleteReasonTooManyAlerts = "Too many check-ins or false alerts",
        deleteReasonPrivacy = "Privacy concerns",
        deleteReasonOther = "Another reason",
        deleteNoteLabel = "Tell us more",
        deleteNotePlaceholder = "Optional",
        deleteNoteRequiredForOther = "Please tell us your reason",
        deleteButton = "Delete my account",
        deleting = "Deleting…",
        deleteConfirmTitle = "Delete your account?",
        deleteConfirmBody = "Monitoring stops now and everything on this phone is erased. " +
            "This cannot be undone.",
        deleteConfirmYes = "Delete",

        contactsHeader = "Contacts",
        contactsLoadFailedInline = "Could not load your contacts.",
        contactsLoadFailedTitle = "Could not load your contacts",
        contactsStillConnected = "Your family members are still connected.",
        tryAgain = "Try again",
        familyMemberFallback = "Family member",
        familyFallbackLabel = "Family",
        phone = "Phone",
        status = "Status",
        removeContact = "Remove Contact",
        thisFamilyMember = "this family member",
        removeBody = "They will stop receiving your alerts, and you will disappear from their " +
            "app too. You can connect again later with a new invite code.",
        remove = "Remove",
        cancel = "Cancel",
        noFamilyTitle = "No family connected yet",
        noFamilyBody = "Share your code with a trusted family member so they can receive alerts and updates.",
        noFamilyBarangayNote = "Until then, if something seems wrong we will alert your barangay directly.",
        inviteTabLabel = "Invite tab",

        inviteHeader = "Invite",
        shareWithFamily = "Share With Family",
        shareWithFamilyBody = "Give this code to family members so they can enter it in their app and monitor you.",
        yourInviteCode = "Your invite code:",
        copyCode = "Copy code",
        generating = "Generating...",
        generateNew = "Generate new",
        generateCode = "Generate code",
        codesExpireNote = "Codes expire after 5 minutes. A new code can only be generated once the current code expires.",
        pairSuccessTitle = "Family member linked",
        pairSuccessDismiss = "OK",
    )

    private val FILIPINO_COPY = Copy(
        profileHeader = "Profile",
        sectionMyInfo = "AKING IMPORMASYON",
        editProfile = "I-edit ang profile",
        editProfileSubtitle = "Pangalan, Apelyido, Edad, Kasarian, Numero ng Cellphone…",
        languageRow = "Language / Wika",
        languageRowSubtitle = "English, Filipino / Tagalog",
        languageChooseHeading = "Piliin ang Inyong Wika",
        sectionMyContacts = "AKING MGA CONTACT",
        familyContacts = "Mga contact sa pamilya",
        familyContactsSubtitle = "Magdagdag po ng aabisuhan bago ang inyong barangay",
        sectionHelp = "TULONG AT IMPORMASYON",
        aboutApp = "Tungkol sa app na ito",
        howToUse = "Paano gamitin",
        faqs = "Mga Madalas Itanong",
        contactSupport = "Makipag-ugnayan sa suporta",
        sectionAboutApp = "TUNGKOL SA SEENIOR",
        termsRow = "Mga tuntunin at kondisyon",
        privacyRow = "Patakaran sa pagkapribado",
        getInviteCode = "Kumuha ng invite code",

        addressLabel = "TIRAHAN",
        addressPlaceholder = "Bilang ng Bahay, Kalye, Barangay, Lungsod",
        saving = "NAGSE-SAVE…",
        saveChanges = "I-SAVE ANG MGA PAGBABAGO",

        sectionAccount = "ACCOUNT",
        deleteAccountRow = "Burahin ang aking account",
        deleteAccountRowSubtitle = "Ihinto ang pagbantay at alisin ang inyong profile sa teleponong ito",
        deleteHeader = "Burahin ang account",
        deleteIntro = "Ihihinto po nito ang lahat ng pagbantay at buburahin ang inyong profile, " +
            "routine, at kasaysayan ng alerto sa teleponong ito. Maa-unlink po ang inyong mga " +
            "contact sa pamilya. Hindi na po ito maibabalik.",
        deleteReasonHeading = "Pakisabi po kung bakit (kailangan)",
        deleteReasonNoLongerNeeded = "Hindi ko na po kailangan ang pagbantay",
        deleteReasonSwitchingPhone = "Lilipat po ako sa bagong telepono",
        deleteReasonBattery = "Masyadong mabilis maubos ang baterya",
        deleteReasonTooManyAlerts = "Masyadong madalas ang check-in o maling alerto",
        deleteReasonPrivacy = "May alinlangan po ako sa privacy",
        deleteReasonOther = "Iba pang dahilan",
        deleteNoteLabel = "Magdagdag pa po",
        deleteNotePlaceholder = "Opsyonal",
        deleteNoteRequiredForOther = "Pakisabi po ang inyong dahilan",
        deleteButton = "Burahin ang aking account",
        deleting = "Binubura…",
        deleteConfirmTitle = "Burahin ang inyong account?",
        deleteConfirmBody = "Hihinto na po ngayon ang pagbantay at buburahin ang lahat sa " +
            "teleponong ito. Hindi na po ito maibabalik.",
        deleteConfirmYes = "Burahin",

        contactsHeader = "Mga Contact",
        contactsLoadFailedInline = "Hindi po ma-load ang inyong mga contact.",
        contactsLoadFailedTitle = "Hindi po ma-load ang inyong mga contact",
        contactsStillConnected = "Nakakonekta pa rin po ang inyong mga kapamilya.",
        tryAgain = "Subukan muli",
        familyMemberFallback = "Kapamilya",
        familyFallbackLabel = "Pamilya",
        phone = "Telepono",
        status = "Kalagayan",
        removeContact = "Alisin ang Contact",
        thisFamilyMember = "ang kapamilyang ito",
        removeBody = "Hihinto po silang makatanggap ng inyong mga alerto, at mawawala rin po " +
            "kayo sa kanilang app. Maaari po kayong magkonekta muli gamit ang bagong invite code.",
        remove = "Alisin",
        cancel = "Kanselahin",
        noFamilyTitle = "Wala pa pong nakakonektang pamilya",
        noFamilyBody = "Ibahagi po ang inyong code sa isang mapagkakatiwalaang kapamilya upang makatanggap sila ng mga alerto at update.",
        noFamilyBarangayNote = "Hanggang doon po, kung may mapapansin kaming hindi maayos, direktang aabisuhan namin ang inyong barangay.",
        inviteTabLabel = "tab na Imbitasyon",

        inviteHeader = "Imbitasyon",
        shareWithFamily = "Ibahagi sa Pamilya",
        shareWithFamilyBody = "Ibigay po ang code na ito sa inyong pamilya upang mailagay nila ito sa kanilang app at kayo ay mabantayan.",
        yourInviteCode = "Ang inyong invite code:",
        copyCode = "Kopyahin ang code",
        generating = "Ginagawa...",
        generateNew = "Gumawa ng bago",
        generateCode = "Gumawa ng code",
        codesExpireNote = "Nag-e-expire po ang code pagkalipas ng 5 minuto. Makakagawa lang po ng bago kapag nag-expire na ang kasalukuyang code.",
        pairSuccessTitle = "Naka-link na ang kapamilya",
        pairSuccessDismiss = "OK",
    )

    fun forLanguage(language: String): Copy =
        if (language == WellnessMessages.FILIPINO) FILIPINO_COPY else ENGLISH_COPY
}

/**
 * Profile / Contacts / Invite copy in the senior's stored language.
 *
 * Provided in `SeniorDashboard`, which already reads `Senior_Onboarding.language_preference` for
 * the tabs, so this follows a change made in Profile → Language the moment it is written.
 */
val LocalProfileCopy = staticCompositionLocalOf {
    ProfileStrings.forLanguage(WellnessMessages.ENGLISH)
}
