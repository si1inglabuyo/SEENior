package com.pup.seenior.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.pup.seenior.ui.wellness.WellnessMessages

/**
 * The onboarding copy in the language chosen so far, for the whole setup flow.
 *
 * A CompositionLocal rather than a parameter threaded through every screen: the language is
 * answered in the middle of the flow and every screen after it has to follow, so passing it by
 * hand would mean adding the same argument to eight screens and their private composables.
 * Provided once, in `SeniorNavGraph`, from the shared `OnboardingViewModel`.
 *
 * Defaults to English, which is what the screens before the language question render in.
 */
val LocalOnboardingCopy = staticCompositionLocalOf {
    OnboardingStrings.forLanguage(WellnessMessages.ENGLISH)
}

/**
 * Every string on the screens a senior sees once, while setting the app up — Welcome through
 * All Set — in both supported languages.
 *
 * Split out of [SeniorStrings] rather than nested in it only for size: this is the same container,
 * reached through `SeniorStrings.Copy.onboarding`, and that file's reasoning about why none of
 * this lives in `strings.xml` applies here unchanged.
 *
 * **One thing to know before reading further.** The senior chooses their language *inside* this
 * flow — it is the first question on the questionnaire — so the screens before that question
 * (Welcome, Choose your role, Tell Us About Yourself, Terms) render in English on the way in. The
 * Filipino for them is written here and takes effect the moment the answer exists, which covers a
 * senior who goes back, and covers the day the question moves earlier in the flow. Moving it is a
 * UX decision nobody has made yet; nothing here depends on which way it goes.
 *
 * **Option labels are translated for display only.** `OnboardingOptions` values are persisted
 * (`Seniors.gender`, `Senior_Onboarding.activity_level`) and compared in code (`hasNap == "Yes"`,
 * `SeedBaselineGenerator.ACTIVITY_PROFILES[activityLevel]`), so the English string stays the state
 * and stays the stored value; only what is drawn on screen changes. Every lookup below falls back
 * to the value it was handed, so an option added later shows up untranslated rather than blank.
 *
 * **These translations are a draft pending a native speaker's review** — the same standing caveat
 * as [SeniorStrings] and [WellnessMessages]. The legal prose on the Terms screen deserves a second
 * look in particular: a translated consent line is still the line the senior is agreeing to.
 */
object OnboardingStrings {

    data class Section(val heading: String, val body: String? = null, val bullets: List<String>? = null)

    data class Copy(
        // Welcome + role selection
        val getStarted: String,
        val chooseRole: String,
        val roleSeniorTitle: String,
        val roleSeniorBullets: List<String>,
        val roleFamilyTitle: String,
        val roleFamilyBullets: List<String>,

        // Sign up
        val signUpTitle: String,
        val signUpSubtitle: String,
        val firstNameLabel: String,
        val firstNamePlaceholder: String,
        val lastNameLabel: String,
        val lastNamePlaceholder: String,
        val ageLabel: String,
        val genderLabel: String,
        val selectPlaceholder: String,
        val mobileLabel: String,
        val mobileError: String,
        val livingArrangementLabel: String,
        val regionLabel: String,
        val provinceLabel: String,
        val cityLabel: String,
        val barangayLabel: String,
        val streetLabel: String,
        val streetPlaceholder: String,
        val findOnMapTitle: String,
        val findOnMapBody: String,

        // Address map picker
        val mapTitle: String,
        val mapSubtitle: String,
        val mapFindMe: String,
        val mapUseAddress: String,
        val mapLookingUp: String,
        val mapNoName: String,
        val mapNoBarangay: String,
        val mapCheckThis: String,

        // Terms
        val termsTitle: String,
        val termsSubtitle: String,
        val termsSections: List<Section>,
        val termsConsentPrefix: String,
        val termsConsentLink: String,
        val termsConsentSuffix: String,
        val privacyConsentPrefix: String,
        val privacyConsentLink: String,
        val privacyConsentSuffix: String,

        // Questionnaire
        val questionnaireTitle: String,
        val questionnaireSubtitle: String,
        val qLanguage: String,
        val qWakeTime: String,
        val qSleepTime: String,
        val qHasNap: String,
        val qNapTime: String,
        val qNapDuration: String,
        val qActivityLevel: String,
        val qGoesOutside: String,
        val qOutsideTime: String,
        val qChargesOvernight: String,
        val optionPlaceholder: String,

        // Permissions
        val permissionsTitle: String,
        val permissionsSubtitle: String,
        val permissionRows: List<Pair<String, String>>,
        val permissionsPrivacyNote: String,
        val permissionsCta: String,
        val permissionsDialogBody: String,
        val allow: String,
        val deny: String,
        val close: String,
        val deniedTitle: String,
        val deniedBody: String,

        // All set
        val allSetBody: String,
        val allSetMonitoringStarted: String,
        val allSetLearningRoutine: String,
        val continueLabel: String,

        val next: String,
        val cancel: String,
        val search: String,
        val mapPinDescription: String,
        val welcomeLead: String,
        val welcomeMid: String,

        // Option labels — display only, see the file KDoc
        private val genderLabels: Map<String, String>,
        private val yesNoLabels: Map<String, String>,
        private val napDurationLabels: Map<String, String>,
        private val outsideTimeLabels: Map<String, String>,
        private val livingArrangementLabels: Map<String, String>,
        private val activityLevelLabels: Map<String, String>,
        private val honorifics: Map<String, String>,
    ) {
        fun gender(value: String): String = genderLabels[value] ?: value
        fun yesNo(value: String): String = yesNoLabels[value] ?: value
        fun napDuration(value: String): String = napDurationLabels[value] ?: value
        fun outsideTime(value: String): String = outsideTimeLabels[value] ?: value
        fun livingArrangement(label: String): String = livingArrangementLabels[label] ?: label
        fun activityLevel(label: String): String = activityLevelLabels[label] ?: label

        /**
         * "You're All Set, Lolo Reviman!"
         *
         * The honorific used to be a hardcoded "Lola", which called every male senior a grandmother
         * on the last screen of his own setup. It is read from the answer two screens earlier
         * instead, and a senior who chose neither is greeted by name alone — better no honorific
         * than the wrong one.
         */
        fun allSetTitle(gender: String?, firstName: String, lastName: String): String {
            val name = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
            val honorific = gender?.let { honorifics[it] }
            val who = if (honorific.isNullOrBlank()) name else honorific + " " + name
            return if (this === FILIPINO_COPY) "Handa na po kayo, " + who + "!" else "You're All Set, " + who + "!"
        }
    }

    private val ENGLISH_COPY = Copy(
        getStarted = "GET STARTED",
        chooseRole = "Choose your role",
        roleSeniorTitle = "I am a Senior",
        roleSeniorBullets = listOf(
            "Requires safety and wellness support.",
            "Needs daily health monitoring.",
            "Stays connected with family members.",
            "Emergency alerts."
        ),
        roleFamilyTitle = "Family Member",
        roleFamilyBullets = listOf(
            "Monitors senior status and activity.",
            "Receives safety and emergency alerts.",
            "Has alert-based location access.",
            "Maintains connection and care remotely."
        ),

        signUpTitle = "Tell Us About Yourself",
        signUpSubtitle = "Proceed with your setup.",
        firstNameLabel = "FIRST NAME",
        firstNamePlaceholder = "First Name",
        lastNameLabel = "LAST NAME",
        lastNamePlaceholder = "Last Name",
        ageLabel = "AGE",
        genderLabel = "GENDER",
        selectPlaceholder = "Select",
        mobileLabel = "MOBILE NUMBER",
        mobileError = "Enter a valid PH mobile number (09XXXXXXXXX or +639XXXXXXXXX)",
        livingArrangementLabel = "LIVING ARRANGEMENT",
        regionLabel = "REGION",
        provinceLabel = "PROVINCE",
        cityLabel = "CITY / MUNICIPALITY",
        barangayLabel = "BARANGAY",
        streetLabel = "STREET ADDRESS",
        streetPlaceholder = "House No., Street, Subdivision",
        findOnMapTitle = "Find my address on a map",
        findOnMapBody = "Drag a pin to your house instead of filling in the boxes below.",

        mapTitle = "Point to Your Home",
        mapSubtitle = "Drag the map until the pin sits on your house.",
        mapFindMe = "Find me",
        mapUseAddress = "USE THIS ADDRESS",
        mapLookingUp = "Looking up this spot...",
        mapNoName = "We could not name this spot. Try moving the pin, or go back and type your " +
            "address instead.",
        mapNoBarangay = "We could not tell which barangay this is — please choose it on the " +
            "next screen.",
        mapCheckThis = "Check this is right. You can still change it on the next screen.",

        termsTitle = "Terms & Conditions",
        termsSubtitle = "Review our Terms and Conditions to continue using the app.",
        termsSections = listOf(
            Section(
                "1. Acceptance of Terms",
                body = "By creating an account and using SEENior, you agree to be bound by these Terms of Use. If you do not agree to these terms, please do not use our services."
            ),
            Section(
                "2. Description of Service",
                body = "SEENior is a passive behavioral monitoring application that uses your smartphone's built-in sensors to establish a daily routine baseline and detect significant deviations that may indicate an emergency."
            ),
            Section(
                "3. User Eligibility",
                body = "The SEENior senior app is intended for use by senior citizens (60 years and older) residing in the Philippines, consistent with RA 9994 (Expanded Senior Citizens Act of 2010). Use by persons below this age threshold is permitted only for testing purposes by the development team."
            ),
            Section(
                "4. User Responsibilities",
                bullets = listOf(
                    "Keep your phone charged and with you for the monitoring system to function reliably.",
                    "Provide accurate personal information during account setup."
                )
            )
        ),
        termsConsentPrefix = "I acknowledge that I have carefully read and accepted the ",
        termsConsentLink = "Terms and Conditions",
        termsConsentSuffix = " of SEENior.",
        privacyConsentPrefix = "I have read and agree to the ",
        privacyConsentLink = "Privacy Policy",
        privacyConsentSuffix = " and consent to the collection of my data as described, under RA 10173.",

        questionnaireTitle = "Onboarding",
        questionnaireSubtitle = "Let's get started",
        qLanguage = "What language do you prefer? / Anong wika ang gusto ninyo?",
        qWakeTime = "What time do you usually wake up?",
        qSleepTime = "What time do you usually go to sleep?",
        qHasNap = "Do you take naps during the day?",
        qNapTime = "What time do you usually nap?",
        qNapDuration = "How long is your usual nap?",
        qActivityLevel = "How active are you during the day?",
        qGoesOutside = "Do you go outside regularly?",
        qOutsideTime = "What time do you usually go outside?",
        qChargesOvernight = "Do you charge your phone overnight?",
        optionPlaceholder = "-Select Option-",

        permissionsTitle = "Allow Permissions",
        permissionsSubtitle = "SEENior needs these to monitor quietly in the background. All data stays on your phone.",
        permissionRows = listOf(
            "Motion & Activity" to "Detects movement to track routine",
            // Was "only as an approximate area", which contradicted the design: one precise fix is
            // captured, and only at the moment an alert fires (CLAUDE.md §11). The protection is
            // that it is never continuous — say that, rather than claim a vagueness we do not add.
            "Location (Alerts)" to "Only when an alert triggers — one exact location, never continuous tracking",
            "Notifications" to "Check-in prompts & SOS alerts",
            "Battery & screen" to "Tracks charging & screen use",
            "Run in background" to "So alerts still go out while the phone rests",
            "Wake your screen" to "So a check-in appears even while the phone is locked",
            "Show over other apps" to "So a check-in is not hidden behind whatever you are using"
        ),
        permissionsPrivacyNote = "Protected under RA 10173 · Data Privacy Act",
        permissionsCta = "ALLOW PERMISSIONS NOW",
        permissionsDialogBody = "Allow SEENior to access your device's activity, notifications, battery usage, and location during emergency alerts to support routine monitoring and emergency assistance?",
        allow = "ALLOW",
        deny = "DENY",
        close = "CLOSE",
        deniedTitle = "App was denied access",
        deniedBody = "It is possible the app won't work properly without this restricted permission.",

        allSetBody = "SEENior is now watching over you quietly. Your family will be notified if anything seems unusual.",
        allSetMonitoringStarted = "Passive monitoring started",
        allSetLearningRoutine = "Learning your routine - Day 1 of 14",
        continueLabel = "CONTINUE",

        next = "NEXT",
        cancel = "Cancel",
        search = "Search…",
        mapPinDescription = "Map pin",
        welcomeLead = "Welcome ",
        welcomeMid = "To ",

        genderLabels = emptyMap(),
        yesNoLabels = emptyMap(),
        napDurationLabels = emptyMap(),
        outsideTimeLabels = emptyMap(),
        livingArrangementLabels = emptyMap(),
        activityLevelLabels = emptyMap(),
        honorifics = mapOf("Male" to "Lolo", "Female" to "Lola"),
    )

    private val FILIPINO_COPY = Copy(
        getStarted = "MAGSIMULA",
        chooseRole = "Pumili po ng inyong papel",
        roleSeniorTitle = "Ako po ay Senior",
        roleSeniorBullets = listOf(
            "Nangangailangan ng tulong sa kaligtasan at kalusugan.",
            "Kailangan ng araw-araw na pagbantay sa kalusugan.",
            "Nananatiling konektado sa pamilya.",
            "Mga alertong pang-emergency."
        ),
        roleFamilyTitle = "Kapamilya",
        roleFamilyBullets = listOf(
            "Binabantayan ang kalagayan at galaw ng senior.",
            "Tumatanggap ng mga alerto sa kaligtasan at emergency.",
            "May access sa lokasyon kapag may alerto.",
            "Nananatiling konektado at nakakaalaga kahit malayo."
        ),

        signUpTitle = "Ikuwento po ang Inyong Sarili",
        signUpSubtitle = "Ipagpatuloy po ang inyong setup.",
        firstNameLabel = "PANGALAN",
        firstNamePlaceholder = "Pangalan",
        lastNameLabel = "APELYIDO",
        lastNamePlaceholder = "Apelyido",
        ageLabel = "EDAD",
        genderLabel = "KASARIAN",
        selectPlaceholder = "Pumili",
        mobileLabel = "NUMERO NG CELLPHONE",
        mobileError = "Maglagay po ng tamang numero sa Pilipinas (09XXXXXXXXX o +639XXXXXXXXX)",
        livingArrangementLabel = "KASAMA SA BAHAY",
        regionLabel = "REHIYON",
        provinceLabel = "PROBINSYA",
        cityLabel = "LUNGSOD / BAYAN",
        barangayLabel = "BARANGAY",
        streetLabel = "TIRAHAN",
        streetPlaceholder = "Bilang ng Bahay, Kalye, Subdivision",
        findOnMapTitle = "Hanapin ang aking tirahan sa mapa",
        findOnMapBody = "I-drag po ang pin papunta sa inyong bahay sa halip na punan ang mga kahon sa ibaba.",

        mapTitle = "Ituro po ang Inyong Bahay",
        mapSubtitle = "I-drag po ang mapa hanggang tumapat ang pin sa inyong bahay.",
        mapFindMe = "Hanapin ako",
        mapUseAddress = "GAMITIN ANG TIRAHANG ITO",
        mapLookingUp = "Hinahanap po ang lugar na ito...",
        mapNoName = "Hindi po namin makilala ang lugar na ito. Subukan pong igalaw ang pin, o " +
            "bumalik at i-type na lang ang inyong tirahan.",
        mapNoBarangay = "Hindi po namin matukoy kung aling barangay ito — piliin po ninyo ito sa " +
            "susunod na screen.",
        mapCheckThis = "Tingnan po kung tama ito. Mababago pa po ninyo ito sa susunod na screen.",

        termsTitle = "Mga Tuntunin at Kondisyon",
        termsSubtitle = "Basahin po ang aming Mga Tuntunin at Kondisyon upang magpatuloy sa paggamit ng app.",
        termsSections = listOf(
            Section(
                "1. Pagtanggap sa mga Tuntunin",
                body = "Sa paggawa po ng account at paggamit ng SEENior, sumasang-ayon kayo sa mga Tuntunin ng Paggamit na ito. Kung hindi po kayo sang-ayon, huwag pong gamitin ang aming serbisyo."
            ),
            Section(
                "2. Paglalarawan ng Serbisyo",
                body = "Ang SEENior po ay isang tahimik na aplikasyon sa pagbantay ng gawi. Ginagamit nito ang mga sensor na nasa loob na ng inyong telepono upang matutunan ang inyong pang-araw-araw na rutina at matukoy ang malalaking pagbabago na maaaring senyales ng emergency."
            ),
            Section(
                "3. Sino ang Maaaring Gumamit",
                body = "Ang senior app po ng SEENior ay para sa mga senior citizen (60 taong gulang pataas) na naninirahan sa Pilipinas, ayon sa RA 9994 (Expanded Senior Citizens Act of 2010). Ang paggamit ng mas bata rito ay pinapayagan lamang para sa pagsubok ng development team."
            ),
            Section(
                "4. Mga Tungkulin ng Gumagamit",
                bullets = listOf(
                    "Panatilihin pong may baterya ang inyong telepono at dala ito, upang maayos na gumana ang pagbantay.",
                    "Magbigay po ng tama at totoong impormasyon sa pag-setup ng account."
                )
            )
        ),
        termsConsentPrefix = "Kinikilala ko pong maingat kong nabasa at tinanggap ang ",
        termsConsentLink = "Mga Tuntunin at Kondisyon",
        termsConsentSuffix = " ng SEENior.",
        privacyConsentPrefix = "Nabasa ko na po at sumasang-ayon ako sa ",
        privacyConsentLink = "Patakaran sa Pagkapribado",
        privacyConsentSuffix = " at pumapayag po ako sa pangongolekta ng aking datos ayon sa nakasaad, sa ilalim ng RA 10173.",

        questionnaireTitle = "Pagsisimula",
        questionnaireSubtitle = "Simulan na po natin",
        qLanguage = "What language do you prefer? / Anong wika ang gusto ninyo?",
        qWakeTime = "Anong oras po kayo karaniwang gumigising?",
        qSleepTime = "Anong oras po kayo karaniwang natutulog?",
        qHasNap = "Umiidlip po ba kayo sa maghapon?",
        qNapTime = "Anong oras po kayo karaniwang umiidlip?",
        qNapDuration = "Gaano po katagal ang inyong idlip?",
        qActivityLevel = "Gaano po kayo kaaktibo sa maghapon?",
        qGoesOutside = "Madalas po ba kayong lumabas ng bahay?",
        qOutsideTime = "Anong oras po kayo karaniwang lumalabas?",
        qChargesOvernight = "Nagcha-charge po ba kayo ng telepono kapag gabi?",
        optionPlaceholder = "-Pumili po-",

        permissionsTitle = "Payagan po ang mga Pahintulot",
        permissionsSubtitle = "Kailangan po ito ng SEENior upang tahimik na makapagbantay sa background. Ang lahat ng datos ay nananatili sa inyong telepono.",
        permissionRows = listOf(
            "Galaw at Aktibidad" to "Tinutukoy ang galaw upang masundan ang rutina",
            "Lokasyon (Alerto)" to "Kapag lang po may alerto — isang tumpak na lokasyon, hindi tuloy-tuloy na pagsubaybay",
            "Mga Abiso" to "Mga check-in at alertong SOS",
            "Baterya at screen" to "Sinusubaybayan ang pag-charge at paggamit ng screen",
            "Tumakbo sa background" to "Upang makapagpadala pa rin ng alerto habang nagpapahinga ang telepono",
            "Buksan ang inyong screen" to "Upang lumitaw ang check-in kahit naka-lock ang telepono",
            "Ipakita sa ibabaw ng ibang app" to "Upang hindi matabunan ang check-in ng app na ginagamit ninyo"
        ),
        permissionsPrivacyNote = "Protektado sa ilalim ng RA 10173 · Data Privacy Act",
        permissionsCta = "PAYAGAN NA PO ANG MGA PAHINTULOT",
        permissionsDialogBody = "Payagan po ba ang SEENior na gamitin ang aktibidad, mga abiso, baterya at lokasyon ng inyong telepono kapag may alertong emergency, upang masubaybayan ang rutina at makatulong sa oras ng emergency?",
        allow = "PAYAGAN",
        deny = "TANGGIHAN",
        close = "ISARA",
        deniedTitle = "Hindi pinayagan ang app",
        deniedBody = "Maaari pong hindi gumana nang maayos ang app kung wala ang pahintulot na ito.",

        allSetBody = "Tahimik na pong nagbabantay ang SEENior. Aabisuhan po ang inyong pamilya kung may mapansing hindi pangkaraniwan.",
        allSetMonitoringStarted = "Nagsimula na ang tahimik na pagbantay",
        allSetLearningRoutine = "Inaaral ang inyong rutina - Araw 1 ng 14",
        continueLabel = "MAGPATULOY",

        next = "SUSUNOD",
        cancel = "Kanselahin",
        search = "Maghanap…",
        mapPinDescription = "Pin sa mapa",
        welcomeLead = "Maligayang ",
        welcomeMid = "Pagdating sa ",

        genderLabels = mapOf("Male" to "Lalaki", "Female" to "Babae", "Other" to "Iba"),
        yesNoLabels = mapOf("Yes" to "Oo", "No" to "Hindi"),
        napDurationLabels = mapOf(
            "15 minutes" to "15 minuto",
            "30 minutes" to "30 minuto",
            "45 minutes" to "45 minuto",
            "1 hour" to "1 oras",
            "1.5 hours" to "1.5 oras",
            "2 hours or more" to "2 oras o higit pa"
        ),
        outsideTimeLabels = mapOf(
            "Morning (6 AM - 10 AM)" to "Umaga (6 AM - 10 AM)",
            "Midday (10 AM - 2 PM)" to "Tanghali (10 AM - 2 PM)",
            "Afternoon (2 PM - 6 PM)" to "Hapon (2 PM - 6 PM)",
            "Evening (6 PM - 9 PM)" to "Gabi (6 PM - 9 PM)",
            "Varies (no fixed time)" to "Nagbabago (walang takdang oras)"
        ),
        livingArrangementLabels = mapOf(
            "With family" to "May kasamang pamilya",
            "Lives alone" to "Nag-iisa sa bahay"
        ),
        activityLevelLabels = mapOf(
            "Mostly resting (stays in bed or chair most of the day)" to
                "Halos nagpapahinga (nasa kama o upuan halos buong araw)",
            "Light activity (short walks around the house)" to
                "Magaan na gawain (maikling lakad sa loob ng bahay)",
            "Moderate activity (walks outside, light chores)" to
                "Katamtamang gawain (lumalabas, magaan na gawaing bahay)",
            "Active (regular walking or exercise)" to
                "Aktibo (regular na paglalakad o ehersisyo)"
        ),
        honorifics = mapOf("Male" to "Lolo", "Female" to "Lola"),
    )

    fun forLanguage(language: String): Copy =
        if (language == WellnessMessages.FILIPINO) FILIPINO_COPY else ENGLISH_COPY
}
