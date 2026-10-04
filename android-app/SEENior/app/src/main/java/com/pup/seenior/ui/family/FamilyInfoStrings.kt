package com.pup.seenior.ui.family

import androidx.compose.runtime.staticCompositionLocalOf
import com.pup.seenior.ui.OnboardingStrings
import com.pup.seenior.ui.wellness.WellnessMessages

/**
 * The reading screens behind the family app's Profile (About, How to use, FAQs, Feedback &
 * requests, Contact support, Terms, Privacy) in both languages. Follows `ui/InfoStrings.kt`'s
 * pattern and is split from [FamilyStrings] so long prose doesn't bury button labels.
 *
 * Built from `designs/family_contact/infos`, with two corrections to the mockups:
 * 1. "How to use" was the senior app's onboarding steps, including one that pointed to an
 *    "Invite tab" that doesn't exist here (the family pairing tab is Link). Rewritten to
 *    describe what a family member does: sign up, link with the senior's code, what the
 *    14-day baseline means, and how to read Home and Alerts.
 * 2. Terms and Privacy were worded as the senior's own. Reworded to cover what is collected
 *    from a family account (name, phone, email, password, linked seniors); the sensor data
 *    policy lives in the senior app's Privacy screen.
 *
 * This is a draft that a native speaker and whoever signs off on the legal sections haven't reviewed.
 */
object FamilyInfoStrings {

    data class Copy(
        // About
        val aboutTitle: String,
        val versionPrefix: String,
        val missionHeading: String,
        val missionBody: String,
        val whoWeAreHeading: String,
        val whoWeAreBody1: String,
        val whoWeAreBody2: String,
        val valuesHeading: String,
        val valuesList: String,
        val valuesBody: String,

        // How to use
        val howToTitle: String,
        val howToSteps: List<Pair<String, String>>,

        // FAQs
        val faqsTitle: String,
        val faqs: List<Faq>,
        val importantNote: String,

        // Feedback & requests
        val feedbackTitle: String,
        val howAreWeDoing: String,
        val tapAStarToRate: String,
        val whatCanWeImprove: String,
        val improvePlaceholder: String,
        val featureRequestLabel: String,
        val featureRequestPlaceholder: String,
        val sendFeedback: String,
        val feedbackNotConnected: String,
        /** Intent.EXTRA_SUBJECT for the "Send us a message" mailto intent below. */
        val feedbackEmailSubject: String,

        // Contact support
        val supportTitle: String,
        val callUs: String,
        val sendMessage: String,
        val messageLabel: String,
        val messagePlaceholder: String,
        val send: String,
        /** Intent.EXTRA_SUBJECT for this screen's mailto intent. */
        val supportEmailSubject: String,

        // Legal
        val termsScaffoldTitle: String,
        val termsBodyTitle: String,
        val termsSections: List<OnboardingStrings.Section>,
        val privacyScaffoldTitle: String,
        val privacyBodyTitle: String,
        val privacySections: List<OnboardingStrings.Section>,
    ) {
        fun version(name: String): String = versionPrefix + " " + name
    }

    data class Faq(val question: String, val answer: String, val note: String? = null)

    private val ENGLISH_COPY = Copy(
        aboutTitle = "About",
        versionPrefix = "Version",
        missionHeading = "Our mission",
        missionBody = "To provide a safe, accessible, and passive monitoring system that supports the " +
            "well-being of seniors through intelligent, low-burden technology designed " +
            "for early detection and timely assistance.",
        whoWeAreHeading = "Who we are",
        whoWeAreBody1 = "We are a small team of developers and healthcare-focused researchers who " +
            "designed SEENior to address the growing need for proactive support for " +
            "elderly individuals living alone or temporarily left alone at home in the " +
            "Philippines.",
        whoWeAreBody2 = "We build mobile-based, low-burden systems that use passive monitoring and " +
            "simple design to help detect unusual behavior and enable timely assistance " +
            "when needed.",
        valuesHeading = "Our values",
        valuesList = "Privacy. Simplicity. Dignity. Reliability.",
        valuesBody = "SEENior is built to respect user data, reduce complexity for seniors, and " +
            "provide dependable support in times of need.",

        howToTitle = "How to use",
        howToSteps = listOf(
            "Create your account" to
                "Sign up with your email or Google account, or log in if you already have one. " +
                "This is separate from, and happens before, linking to any senior.",
            "Link to your senior" to
                "Your senior generates a one-time invite code from their own app and shares it with " +
                "you. Enter it in the Link tab to start monitoring them — up to 3 seniors per " +
                "account.",
            "The first 14 days" to
                "SEENior watches from day one, but your senior's first two weeks build their " +
                "personal routine baseline. Don't be surprised if things are quiet at first — the " +
                "system gets more precise about what's \"unusual\" for them as this period " +
                "continues.",
            "Check on your senior" to
                "The Home tab shows each linked senior's status at a glance — risk level, alerts " +
                "today, battery. The Alerts tab shows anything that needs your attention right now.",
            "Respond to an alert" to
                "From the Alerts tab you can acknowledge an alert, call your senior directly, view " +
                "their last known location, or request an official barangay welfare check if they " +
                "don't respond."
        ),

        faqsTitle = "FAQs",
        faqs = listOf(
            Faq(
                "Is this app free?",
                "Yes. SEENior is completely free to download and use."
            ),
            Faq(
                "Will my senior's location always be shared?",
                "No. Location sharing is only active when an alert triggers.",
                note = "If location is off, SEENior may not be able to provide a real-time location " +
                    "during an emergency. Only the last known location is shared with trusted " +
                    "contacts."
            ),
            Faq(
                "Can I use SEENior without internet?",
                "You can view your saved contacts offline, but sending alerts and receiving " +
                    "real-time updates requires an internet or network connection."
            ),
            Faq(
                "How do I remove a paired contact?",
                "Go to the Contacts tab, select the contact, scroll down, and tap Remove Contact. " +
                    "A confirmation prompt will appear before removal."
            )
        ),

        importantNote = "Important Note:",

        feedbackTitle = "Feedback & requests",
        howAreWeDoing = "How are we doing?",
        tapAStarToRate = "Tap a star to rate your experience",
        whatCanWeImprove = "WHAT CAN WE IMPROVE?",
        improvePlaceholder = "Tell us anything…",
        featureRequestLabel = "FEATURE REQUEST",
        featureRequestPlaceholder = "Suggest a new feature…",
        sendFeedback = "SEND FEEDBACK",
        feedbackNotConnected = "Feedback isn't connected to an inbox yet — nothing is sent until it is.",
        feedbackEmailSubject = "SEENior family app feedback",

        supportTitle = "Contact support",
        callUs = "Call us",
        sendMessage = "Send us a message",
        messageLabel = "MESSAGE",
        messagePlaceholder = "Tell us what's going on…",
        send = "SEND FEEDBACK",
        supportEmailSubject = "SEENior support request",

        termsScaffoldTitle = "Terms & conditions",
        termsBodyTitle = "Terms & Conditions",
        termsSections = listOf(
            OnboardingStrings.Section(
                "1. Acceptance of Terms",
                "By creating a family account and using SEENior, you agree to be bound by these " +
                    "Terms of Use. If you do not agree to these terms, please do not use our services."
            ),
            OnboardingStrings.Section(
                "2. Description of Service",
                "The SEENior family app lets you monitor a linked senior's passive monitoring status " +
                    "and receive alerts if something unusual is detected. It shows alert-level status " +
                    "and metadata only — it does not display your senior's raw sensor readings, which " +
                    "stay on their own device."
            ),
            OnboardingStrings.Section(
                "3. Account Eligibility",
                "The family app is intended for adults acting as a trusted contact for a senior " +
                    "enrolled in SEENior. You must provide accurate contact information (name, " +
                    "mobile number, email) so alerts and pairing invitations can reach you."
            ),
            OnboardingStrings.Section(
                "4. User Responsibilities",
                bullets = listOf(
                    "Keep your contact information accurate and up to date so alerts can reach you.",
                    "Respond to alerts promptly — an unacknowledged alert escalates to the barangay tier.",
                    "Only link to seniors you have their consent to monitor.",
                    "Keep your login credentials confidential.",
                    "Do not use a senior's pairing code without their knowledge."
                )
            ),
            OnboardingStrings.Section(
                "5. Limitation of Liability",
                "SEENior is provided \"as is\" and is a support tool only. It is not a substitute for " +
                    "professional medical care, emergency services, or human supervision. The developers " +
                    "are not liable for missed or delayed alerts caused by device, network, or " +
                    "third-party service failures."
            )
        ),
        privacyScaffoldTitle = "Privacy policy",
        privacyBodyTitle = "Privacy Policy",
        privacySections = listOf(
            OnboardingStrings.Section(
                "1. Who collects your data",
                "SEENior is developed by Polytechnic University of the Philippines students. We act as " +
                    "the personal information controller for data collected through this application, in " +
                    "compliance with RA 10173 and NPC guidelines."
            ),
            OnboardingStrings.Section(
                "2. What data we collect from your family account",
                body = "We collect the following data from family accounts:",
                bullets = listOf(
                    "Account details — full name, mobile number, email, and a password (or your " +
                        "Google/email sign-in identity, if you use that instead).",
                    "Pairing data — which senior(s) you are linked to, and the relationship you enter " +
                        "(e.g. \"daughter\", \"son\") — visible to that senior on their own Contacts " +
                        "screen.",
                    "Alert metadata for your linked seniors — risk level, trigger type, timestamps, and " +
                        "status. Their registered address is shown only during an active alert.",
                    "A push notification token identifying this installation, so alerts can reach this " +
                        "device."
                )
            ),
            OnboardingStrings.Section(
                "3. What we do not collect",
                "The family app never collects or requests your own location. Location is captured on " +
                    "the senior's phone only, only at the moment an alert triggers, and is shown to you " +
                    "as part of that alert — never tracked continuously, and never yours."
            ),
            OnboardingStrings.Section(
                "4. Who can see your data",
                bullets = listOf(
                    "The senior(s) you are linked to — see your name and relationship label on their " +
                        "Contacts screen.",
                    "Barangay responders — do not see your personal account details.",
                    "Development team — may access anonymized, aggregated data for academic research " +
                        "purposes only, with no individual identification."
                )
            )
        ),
    )

    private val FILIPINO_COPY = ENGLISH_COPY.copy(
        aboutTitle = "Tungkol sa App",
        versionPrefix = "Bersyon",
        missionHeading = "Ang aming misyon",
        missionBody = "Ang magbigay ng ligtas, madaling gamitin, at tahimik na sistema ng " +
            "pagbantay na sumusuporta sa kapakanan ng mga senior sa pamamagitan ng matalinong " +
            "teknolohiyang hindi nakakaabala, para sa maagang pagtuklas at agarang tulong.",
        whoWeAreHeading = "Sino kami",
        whoWeAreBody1 = "Kami ay isang maliit na pangkat ng mga developer at mananaliksik sa " +
            "larangan ng kalusugan na bumuo ng SEENior upang tugunan ang lumalaking " +
            "pangangailangan ng suporta para sa mga nakatatandang nag-iisa o pansamantalang " +
            "naiiwang mag-isa sa bahay dito sa Pilipinas.",
        whoWeAreBody2 = "Bumubuo kami ng mga sistemang nakabatay sa cellphone, hindi " +
            "nakakaabala, na gumagamit ng tahimik na pagbantay at simpleng disenyo upang " +
            "matukoy ang hindi pangkaraniwang gawi at makapagbigay ng agarang tulong kung " +
            "kinakailangan.",
        valuesHeading = "Ang aming mga pinahahalagahan",
        valuesList = "Pagkapribado. Pagiging simple. Dangal. Maaasahan.",
        valuesBody = "Ginawa ang SEENior upang igalang ang datos ng gumagamit, gawing simple " +
            "ang lahat para sa mga senior, at magbigay ng maaasahang tulong sa oras ng " +
            "pangangailangan.",

        howToTitle = "Paano gamitin",
        howToSteps = listOf(
            "Gumawa ng account" to
                "Mag-sign up gamit ang iyong email o Google account, o mag-log in kung meron ka " +
                "nang account. Hiwalay ito sa, at nauna sa, pag-link sa kahit anong senior.",
            "I-link sa iyong senior" to
                "Gagawa ang iyong senior ng one-time invite code mula sa kanilang app at ibabahagi " +
                "ito sa iyo. Ilagay ito sa Link tab upang simulan ang pagbantay sa kanila — pinapayagan " +
                "ang hanggang 3 senior kada account.",
            "Ang unang 14 na araw" to
                "Nagbabantay na ang SEENior mula sa unang araw, ngunit ang unang dalawang linggo ng " +
                "iyong senior ay ginagamit upang mabuo ang kanilang personal na baseline. Huwag " +
                "magtaka kung tahimik muna ang lahat — mas magiging tumpak ang sistema sa pagkilala " +
                "ng \"kakaiba\" para sa kanila habang tumatagal ang panahong ito.",
            "Tingnan ang iyong senior" to
                "Ipinapakita ng Home tab ang katayuan ng bawat naka-link na senior — antas ng " +
                "panganib, alerto ngayong araw, baterya. Ipinapakita ng Alerts tab kung may " +
                "kailangan ng iyong agarang atensyon.",
            "Sagutin ang isang alerto" to
                "Mula sa Alerts tab, maaari mong kumpirmahin ang alerto, tawagan ang iyong senior, " +
                "tingnan ang kanilang huling kilalang lokasyon, o humiling ng opisyal na welfare " +
                "check mula sa barangay kung hindi sila sumagot."
        ),

        faqsTitle = "Mga Madalas Itanong",
        faqs = listOf(
            Faq(
                "Libre ba ang app na ito?",
                "Oo. Libre i-download at gamitin ang SEENior."
            ),
            Faq(
                "Palagi bang ibinabahagi ang lokasyon ng aking senior?",
                "Hindi. Aktibo lamang ang pagbabahagi ng lokasyon kapag may alerto.",
                note = "Kung nakapatay ang lokasyon, maaaring hindi makapagbigay ang SEENior ng " +
                    "real-time na lokasyon sa oras ng emergency. Ang huling kilalang lokasyon lamang " +
                    "ang ibinabahagi sa mga pinagkakatiwalaang contact."
            ),
            Faq(
                "Magagamit ba ang SEENior kahit walang internet?",
                "Maaari mong tingnan ang iyong naka-save na contact kahit offline, ngunit " +
                    "nangangailangan ng internet o signal ang pagpapadala ng alerto at real-time na " +
                    "update."
            ),
            Faq(
                "Paano ko aalisin ang isang nakakonektang contact?",
                "Pumunta sa Contacts tab, piliin ang contact, mag-scroll pababa, at pindutin ang " +
                    "Remove Contact. May lilitaw na kumpirmasyon bago ito alisin."
            )
        ),

        importantNote = "Mahalagang Paalala:",

        feedbackTitle = "Puna at Kahilingan",
        howAreWeDoing = "Kumusta kami?",
        tapAStarToRate = "Pindutin ang isang bituin upang i-rate ang iyong karanasan",
        whatCanWeImprove = "ANO ANG MAAARI NAMING PAGBUTIHIN?",
        improvePlaceholder = "Sabihin sa amin ang kahit ano…",
        featureRequestLabel = "KAHILINGANG FEATURE",
        featureRequestPlaceholder = "Magmungkahi ng bagong feature…",
        sendFeedback = "IPADALA ANG PUNA",
        feedbackNotConnected = "Hindi pa konektado ang puna sa isang inbox — walang ipinapadala hangga't hindi ito nakakonekta.",
        feedbackEmailSubject = "Puna sa SEENior family app",

        supportTitle = "Makipag-ugnayan sa support",
        callUs = "Tawagan kami",
        sendMessage = "Magpadala ng mensahe",
        messageLabel = "MENSAHE",
        messagePlaceholder = "Sabihin sa amin ang problema…",
        send = "IPADALA ANG PUNA",
        supportEmailSubject = "Kahilingan sa suporta ng SEENior",

        termsScaffoldTitle = "Mga tuntunin at kondisyon",
        termsBodyTitle = "Mga Tuntunin at Kondisyon",
        termsSections = listOf(
            OnboardingStrings.Section(
                "1. Pagtanggap sa mga Tuntunin",
                "Sa paggawa ng family account at paggamit ng SEENior, sumasang-ayon kayo sa mga " +
                    "Tuntunin ng Paggamit na ito. Kung hindi kayo sang-ayon, huwag gamitin ang aming " +
                    "serbisyo."
            ),
            OnboardingStrings.Section(
                "2. Paglalarawan ng Serbisyo",
                "Ang family app ng SEENior ay nagbibigay-daan sa inyong bantayan ang katayuan ng " +
                    "pagbabantay sa isang naka-link na senior at makatanggap ng alerto kung may " +
                    "natukoy na hindi pangkaraniwan. Ipinapakita lamang ang katayuan at detalye ng " +
                    "alerto — hindi ipinapakita ang mga hilaw na pagbasa ng sensor ng iyong senior, " +
                    "na nananatili sa kanilang sariling cellphone."
            ),
            OnboardingStrings.Section(
                "3. Karapat-dapat na Gumamit",
                "Ang family app ay para sa mga nasa hustong gulang na kumikilos bilang " +
                    "pinagkakatiwalaang contact ng isang senior na naka-enroll sa SEENior. Dapat kayong " +
                    "magbigay ng tumpak na impormasyon (pangalan, numero ng mobile, email) upang " +
                    "maabot kayo ng mga alerto at imbitasyon sa pag-link."
            ),
            OnboardingStrings.Section(
                "4. Mga Tungkulin ng Gumagamit",
                bullets = listOf(
                    "Panatilihing tumpak at napapanahon ang iyong impormasyon upang maabot ka ng mga alerto.",
                    "Sagutin agad ang mga alerto — ang hindi nakumpirmang alerto ay ipapasa sa barangay.",
                    "I-link lamang ang mga senior na may pahintulot kang bantayan.",
                    "Panatilihing kumpidensyal ang iyong login credentials.",
                    "Huwag gamitin ang pairing code ng isang senior nang walang kaalaman nila."
                )
            ),
            OnboardingStrings.Section(
                "5. Hangganan ng Pananagutan",
                "Ang SEENior ay ibinibigay \"as is\" at isa lamang pantulong na kasangkapan. Hindi " +
                    "ito kapalit ng propesyonal na pangangalagang medikal, ng mga serbisyong " +
                    "pang-emergency, o ng tunay na pagbabantay ng tao. Hindi mananagot ang mga " +
                    "developer sa mga alertong hindi naipadala o naantala dahil sa problema sa " +
                    "cellphone, sa network, o sa serbisyo ng ibang kompanya."
            )
        ),
        privacyScaffoldTitle = "Privacy Policy",
        privacyBodyTitle = "Privacy Policy",
        privacySections = listOf(
            OnboardingStrings.Section(
                "1. Sino ang kumukuha ng iyong datos",
                "Ang SEENior ay binuo ng mga mag-aaral ng Polytechnic University of the " +
                    "Philippines. Kami ang personal information controller ng datos na nakukuha sa " +
                    "aplikasyong ito, alinsunod sa RA 10173 at sa mga panuntunan ng NPC."
            ),
            OnboardingStrings.Section(
                "2. Anong datos ang aming kinukuha sa iyong family account",
                body = "Ito ang mga datos na kinukuha namin mula sa mga family account:",
                bullets = listOf(
                    "Detalye ng account — buong pangalan, numero ng mobile, email, at password (o " +
                        "ang iyong Google/email sign-in identity, kung ito ang gamit mo).",
                    "Datos ng pag-link — sino ang mga senior na naka-link sa iyo, at ang relasyon na " +
                        "inilagay mo (hal. \"anak na babae\", \"anak na lalaki\") — nakikita ng senior " +
                        "na iyon sa kanilang Contacts screen.",
                    "Detalye ng alerto ng mga naka-link mong senior — antas ng panganib, uri ng " +
                        "trigger, oras, at katayuan. Ang kanilang tirahan ay ipinapakita lamang kapag " +
                        "may aktibong alerto.",
                    "Isang push notification token na tumutukoy sa install na ito, upang maabot ang " +
                        "device na ito ng mga alerto."
                )
            ),
            OnboardingStrings.Section(
                "3. Ang hindi namin kinukuha",
                "Hindi kailanman kinukuha o hinihingi ng family app ang iyong sariling lokasyon. " +
                    "Ang lokasyon ay kinukuha lamang sa cellphone ng senior, sa mismong sandaling may " +
                    "alerto, at ipinapakita sa iyo bilang bahagi ng alertong iyon — hindi ito tuloy-" +
                    "tuloy na sinusubaybayan, at hindi ito iyong lokasyon."
            ),
            OnboardingStrings.Section(
                "4. Sino ang nakakakita ng iyong datos",
                bullets = listOf(
                    "Ang mga senior na naka-link sa iyo — nakikita ang iyong pangalan at relasyon sa " +
                        "kanilang Contacts screen.",
                    "Ang mga barangay responder — hindi nakikita ang iyong personal na detalye ng " +
                        "account.",
                    "Ang development team — maaaring makakita ng datos na walang pangalan at " +
                        "pinagsama-sama, para lamang sa pananaliksik na pang-akademiko, na hindi " +
                        "matutukoy ang sinuman."
                )
            )
        ),
    )

    fun forLanguage(language: String): Copy =
        if (language == WellnessMessages.FILIPINO) FILIPINO_COPY else ENGLISH_COPY
}

/** Info-screen prose in the family account's stored language. Provided in `FamilyDashboard`. */
val LocalFamilyInfoCopy = staticCompositionLocalOf {
    FamilyInfoStrings.forLanguage(WellnessMessages.ENGLISH)
}
