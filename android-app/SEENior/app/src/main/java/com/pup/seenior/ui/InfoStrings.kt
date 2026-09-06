package com.pup.seenior.ui

import com.pup.seenior.ui.wellness.WellnessMessages

/**
 * The reading screens behind Profile — About, How to use, FAQs, Contact support, Terms and
 * Privacy — in both supported languages.
 *
 * Served through [LocalProfileCopy]'s neighbour [LocalInfoCopy], provided in `SeniorDashboard`
 * beside the others. Split from [ProfileStrings] because this is prose and that is chrome: these
 * strings are paragraphs a senior reads once, and keeping them apart stops a 60-line legal section
 * from burying the label of a button.
 *
 * Legal sections reuse [OnboardingStrings.Section] rather than declaring a third identical
 * heading/body/bullets triple.
 *
 * **Two warnings before this text is shown to a panel or a real senior.**
 *
 * 1. **The Filipino is an agent-written draft.** It has not been reviewed by a native speaker, and
 *    the panel has already rejected one phrasing in this app as too informal. The legal sections
 *    matter most: a translated privacy notice is the notice the senior is relying on.
 * 2. **One English claim is already false, and is translated here faithfully rather than quietly
 *    reworded.** The offline FAQ promises "SEENior will try to send an SMS instead". SMS fallback
 *    is not built (CLAUDE.md §9 lists Semaphore PH; nothing calls it). A senior with no signal is
 *    being told help will still get through, and it will not. Fix the claim or build the feature —
 *    do not leave it as it stands.
 */
object InfoStrings {

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

        // Contact support
        val supportTitle: String,
        val callUs: String,
        val sendMessage: String,
        val messageLabel: String,
        val messagePlaceholder: String,
        val send: String,

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
            "Set up your profile" to
                "Enter your basic details and living arrangement during onboarding. This is done only " +
                "once, and your app will proceed immediately after setup.",
            "Grant permissions and enable alerts (required)" to
                "During onboarding, you must enable notifications and required permissions for SEENior " +
                "to function properly. The app cannot proceed without this setup.",
            "Answer onboarding questions (14-day baseline period)" to
                "After setup, you will answer a short set of onboarding questions. SEENior uses this " +
                "information to learn your normal behavior patterns over a 14-day monitoring " +
                "period, which serves as your personal baseline.",
            "Invite a trusted contact" to
                "Go to the Invite tab to generate a unique code. The code expires every 5 minutes for " +
                "security, and you can generate a new one once it does. Share this code to connect " +
                "with a trusted contact.",
            "Manage your contacts" to
                "In the Contacts tab, you can view connected contacts, check their status, and remove " +
                "them if needed.",
            "Answer the wellness check when it appears" to
                "If SEENior notices something unusual, it will ask \"Are you safe and well?\" and tell " +
                "you why it is asking. Tapping \"I'm safe\" stops the alert right there. If you do " +
                "not answer, your family contact is notified, and then your barangay responder."
        ),

        faqsTitle = "FAQs",
        faqs = listOf(
            Faq(
                "Is this app free?",
                "Yes. SEENior is completely free to download and use."
            ),
            Faq(
                "Will my location always be shared?",
                "No. Your location is only captured at the moment an alert is triggered, never " +
                    "continuously. Only that last known location is shared with your trusted contacts."
            ),
            Faq(
                "Can I use SEENior without internet?",
                "Yes. SEENior keeps watching over you and can still show your saved contacts with no " +
                    "internet at all. Sending alerts and real-time updates to your family needs an " +
                    "internet or network connection.",
                note = "If you have no signal at all, SEENior will try to send an SMS instead. Keep your " +
                    "phone charged and with you so it can reach someone for you."
            ),
            Faq(
                "How do I remove a paired contact?",
                "Go to the Contacts tab, select the contact, scroll down, and tap Remove Contact. A " +
                    "confirmation prompt will appear before removal."
            ),
            Faq(
                "Do I need to open the app every day?",
                "No. SEENior runs quietly in the background. You only need to answer if it asks you " +
                    "\"Are you safe and well?\""
            )
        ),

        importantNote = "Important Note:",

        supportTitle = "Contact support",
        callUs = "Call us",
        sendMessage = "Send us a message",
        messageLabel = "MESSAGE",
        messagePlaceholder = "Suggest a new feature…",
        send = "SEND",

        termsScaffoldTitle = "Terms & conditions",
        termsBodyTitle = "Terms & Conditions",
        termsSections = listOf(
            OnboardingStrings.Section(
                "1. Acceptance of Terms",
                "By creating an account and using SEENior, you agree to be bound by these Terms of Use. " +
                    "If you do not agree to these terms, please do not use our services."
            ),
            OnboardingStrings.Section(
                "2. Description of Service",
                "SEENior is a passive behavioral monitoring application that uses your smartphone's " +
                    "built-in sensors to establish a daily routine baseline and detect significant " +
                    "deviations that may indicate an emergency."
            ),
            OnboardingStrings.Section(
                "3. User Eligibility",
                "The SEENior senior app is intended for use by senior citizens (60 years and older) " +
                    "residing in the Philippines, consistent with RA 9994 (Expanded Senior Citizens Act " +
                    "of 2010). Use by persons below this age threshold is permitted only for testing " +
                    "purposes by the development team."
            ),
            OnboardingStrings.Section(
                "4. User Responsibilities",
                bullets = listOf(
                    "Keep your phone charged and with you for the monitoring system to function reliably.",
                    "Provide accurate personal information during account setup.",
                    "Grant the required app permissions (motion, notifications, background activity) for the system to work.",
                    "Add at least one emergency contact so alerts can be delivered if an anomaly is detected.",
                    "Inform your emergency contacts that they will receive alerts from SEENior on your behalf."
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
                "2. What data we collect",
                body = "We collect the following data from senior users:",
                bullets = listOf(
                    "Personal profile — full name, age, gender, living arrangement, address, phone number, and relationship to emergency contacts.",
                    "Behavioral sensor data — accelerometer readings, screen on/off timestamps, and battery/charging status. Used only to build your activity baseline.",
                    "Location (GPS) — captured only when an alert is triggered. Not continuously tracked. Stops immediately when the alert is resolved."
                )
            ),
            OnboardingStrings.Section(
                "3. How data is stored",
                "Behavioral sensor data is stored locally on your device. It is not uploaded to any " +
                    "external server during normal monitoring. Alert records are synchronized to a " +
                    "secure cloud backend only when an alert is triggered, to enable notification " +
                    "delivery to your emergency contacts."
            ),
            OnboardingStrings.Section(
                "4. Who can see your data",
                bullets = listOf(
                    "Your emergency contacts — receive alert notifications and your location only during active alert events.",
                    "Barangay responders — receive escalated alerts only when your contacts do not respond within the set timeframe.",
                    "Development team — may access anonymized, aggregated data for academic research purposes only, with no individual identification."
                )
            )
        ),
    )

    private val FILIPINO_COPY = Copy(
        aboutTitle = "Tungkol sa App",
        versionPrefix = "Bersyon",
        missionHeading = "Ang aming misyon",
        missionBody = "Ang magbigay po ng ligtas, madaling gamitin, at tahimik na sistema ng " +
            "pagbantay na sumusuporta sa kapakanan ng mga senior sa pamamagitan ng matalinong " +
            "teknolohiyang hindi nakakaabala, para sa maagang pagtuklas at agarang tulong.",
        whoWeAreHeading = "Sino po kami",
        whoWeAreBody1 = "Kami po ay isang maliit na pangkat ng mga developer at mananaliksik sa " +
            "larangan ng kalusugan na bumuo ng SEENior upang tugunan ang lumalaking " +
            "pangangailangan ng suporta para sa mga nakatatandang nag-iisa o pansamantalang " +
            "naiiwang mag-isa sa bahay dito sa Pilipinas.",
        whoWeAreBody2 = "Bumubuo po kami ng mga sistemang nakabatay sa cellphone, hindi " +
            "nakakaabala, na gumagamit ng tahimik na pagbantay at simpleng disenyo upang " +
            "matukoy ang hindi pangkaraniwang gawi at makapagbigay ng agarang tulong kung " +
            "kinakailangan.",
        valuesHeading = "Ang aming mga pinahahalagahan",
        valuesList = "Pagkapribado. Pagiging simple. Dangal. Maaasahan.",
        valuesBody = "Ginawa po ang SEENior upang igalang ang datos ng gumagamit, gawing simple " +
            "ang lahat para sa mga senior, at magbigay ng maaasahang tulong sa oras ng " +
            "pangangailangan.",

        howToTitle = "Paano gamitin",
        howToSteps = listOf(
            "Ihanda ang inyong profile" to
                "Ilagay po ang inyong mga pangunahing detalye at kung sino ang inyong kasama sa bahay " +
                "habang nagse-setup. Isang beses lang po ito gagawin, at magpapatuloy agad ang app " +
                "pagkatapos.",
            "Payagan ang mga pahintulot at buksan ang mga alerto (kailangan)" to
                "Habang nagse-setup, kailangan pong buksan ang mga abiso at ang mga hinihinging " +
                "pahintulot upang gumana nang maayos ang SEENior. Hindi po makakatuloy ang app kung " +
                "wala ito.",
            "Sagutin ang mga tanong sa pagsisimula (14 na araw na baseline)" to
                "Pagkatapos ng setup, sasagot po kayo ng ilang maikling tanong. Ginagamit ng SEENior " +
                "ang mga sagot na ito upang matutunan ang inyong karaniwang gawi sa loob ng 14 na " +
                "araw, at ito po ang magiging batayan ninyong sariling rutina.",
            "Mag-imbita ng mapagkakatiwalaang contact" to
                "Pumunta po sa tab na Imbitasyon upang gumawa ng natatanging code. Nag-e-expire po " +
                "ang code tuwing 5 minuto para sa seguridad, at makakagawa po kayo ng bago kapag " +
                "nag-expire ito. Ibahagi ang code na ito upang makakonekta sa taong pinagkakatiwalaan " +
                "ninyo.",
            "Pamahalaan ang inyong mga contact" to
                "Sa tab na Mga Contact, makikita po ninyo ang mga nakakonektang contact, ang kanilang " +
                "kalagayan, at maaari po ninyo silang alisin kung kinakailangan.",
            "Sagutin ang wellness check kapag lumitaw ito" to
                "Kung may mapansing hindi pangkaraniwan ang SEENior, itatanong po nito ang \"Ligtas po " +
                "ba kayo? Maayos po ba kayo?\" at sasabihin kung bakit ito nagtatanong. Ang pagpindot " +
                "sa \"Maayos po ako\" ay agad na hihinto sa alerto. Kung hindi po kayo sasagot, " +
                "aabisuhan ang inyong contact sa pamilya, at pagkatapos ay ang inyong barangay " +
                "responder."
        ),

        faqsTitle = "Mga Madalas Itanong",
        faqs = listOf(
            Faq(
                "Libre po ba ang app na ito?",
                "Opo. Libre pong i-download at gamitin ang SEENior."
            ),
            Faq(
                "Palagi po bang ibinabahagi ang aking lokasyon?",
                "Hindi po. Kinukuha lang po ang inyong lokasyon sa mismong sandaling may alerto, " +
                    "hindi tuloy-tuloy. Ang huling lokasyong iyon lamang ang ibinabahagi sa mga taong " +
                    "pinagkakatiwalaan ninyo."
            ),
            Faq(
                "Magagamit po ba ang SEENior kahit walang internet?",
                "Opo. Patuloy pong nagbabantay ang SEENior at maipapakita pa rin ang inyong mga " +
                    "naka-save na contact kahit walang internet. Ang pagpapadala po ng alerto at " +
                    "update sa inyong pamilya ay nangangailangan ng internet o signal.",
                note = "Kung wala pong signal, susubukan ng SEENior na magpadala ng SMS. Panatilihin " +
                    "pong may baterya ang inyong telepono at dala ito upang may maabot para sa inyo."
            ),
            Faq(
                "Paano ko po aalisin ang isang nakakonektang contact?",
                "Pumunta po sa tab na Mga Contact, piliin ang contact, mag-scroll pababa, at pindutin " +
                    "ang Alisin ang Contact. May lilitaw pong tanong upang kumpirmahin bago ito alisin."
            ),
            Faq(
                "Kailangan ko po bang buksan ang app araw-araw?",
                "Hindi po. Tahimik pong tumatakbo ang SEENior sa background. Kailangan lang po ninyong " +
                    "sumagot kapag tinanong kayo ng \"Ligtas po ba kayo? Maayos po ba kayo?\""
            )
        ),

        importantNote = "Mahalagang Paalala:",

        supportTitle = "Makipag-ugnayan sa suporta",
        callUs = "Tawagan po kami",
        sendMessage = "Magpadala po ng mensahe",
        messageLabel = "MENSAHE",
        messagePlaceholder = "Magmungkahi ng bagong feature…",
        send = "IPADALA",

        termsScaffoldTitle = "Mga tuntunin at kondisyon",
        termsBodyTitle = "Mga Tuntunin at Kondisyon",
        termsSections = listOf(
            OnboardingStrings.Section(
                "1. Pagtanggap sa mga Tuntunin",
                "Sa paggawa po ng account at paggamit ng SEENior, sumasang-ayon kayo sa mga Tuntunin " +
                    "ng Paggamit na ito. Kung hindi po kayo sang-ayon, huwag pong gamitin ang aming " +
                    "serbisyo."
            ),
            OnboardingStrings.Section(
                "2. Paglalarawan ng Serbisyo",
                "Ang SEENior po ay isang tahimik na aplikasyon sa pagbantay ng gawi. Ginagamit nito " +
                    "ang mga sensor na nasa loob na ng inyong telepono upang matutunan ang inyong " +
                    "pang-araw-araw na rutina at matukoy ang malalaking pagbabago na maaaring " +
                    "senyales ng emergency."
            ),
            OnboardingStrings.Section(
                "3. Sino ang Maaaring Gumamit",
                "Ang senior app po ng SEENior ay para sa mga senior citizen (60 taong gulang pataas) " +
                    "na naninirahan sa Pilipinas, ayon sa RA 9994 (Expanded Senior Citizens Act of " +
                    "2010). Ang paggamit ng mas bata rito ay pinapayagan lamang para sa pagsubok ng " +
                    "development team."
            ),
            OnboardingStrings.Section(
                "4. Mga Tungkulin ng Gumagamit",
                bullets = listOf(
                    "Panatilihin pong may baterya ang inyong telepono at dala ito, upang maayos na gumana ang pagbantay.",
                    "Magbigay po ng tama at totoong impormasyon sa pag-setup ng account.",
                    "Payagan po ang mga kinakailangang pahintulot (galaw, mga abiso, pagtakbo sa background) upang gumana ang sistema.",
                    "Magdagdag po ng kahit isang emergency contact upang may makatanggap ng alerto kapag may napansing hindi pangkaraniwan.",
                    "Ipaalam po sa inyong mga emergency contact na sila ay makakatanggap ng mga alerto mula sa SEENior para sa inyo."
                )
            ),
            OnboardingStrings.Section(
                "5. Hangganan ng Pananagutan",
                "Ang SEENior po ay ibinibigay \"as is\" at isa lamang pantulong na kasangkapan. Hindi " +
                    "po ito kapalit ng propesyonal na pangangalagang medikal, ng mga serbisyong " +
                    "pang-emergency, o ng tunay na pagbabantay ng tao. Hindi po mananagot ang mga " +
                    "developer sa mga alertong hindi naipadala o naantala dahil sa problema sa " +
                    "telepono, sa network, o sa serbisyo ng ibang kompanya."
            )
        ),
        privacyScaffoldTitle = "Patakaran sa pagkapribado",
        privacyBodyTitle = "Patakaran sa Pagkapribado",
        privacySections = listOf(
            OnboardingStrings.Section(
                "1. Sino ang kumukuha ng inyong datos",
                "Ang SEENior po ay binuo ng mga mag-aaral ng Polytechnic University of the " +
                    "Philippines. Kami po ang personal information controller ng datos na nakukuha sa " +
                    "aplikasyong ito, alinsunod sa RA 10173 at sa mga panuntunan ng NPC."
            ),
            OnboardingStrings.Section(
                "2. Anong datos ang aming kinukuha",
                body = "Ito po ang mga datos na kinukuha namin sa mga senior na gumagamit:",
                bullets = listOf(
                    "Personal na profile — buong pangalan, edad, kasarian, kasama sa bahay, tirahan, numero ng telepono, at kaugnayan sa mga emergency contact.",
                    "Datos ng gawi mula sa sensor — mga pagbasa ng accelerometer, oras ng pagbukas at pagsara ng screen, at kalagayan ng baterya o pag-charge. Ginagamit lamang upang mabuo ang batayan ng inyong aktibidad.",
                    "Lokasyon (GPS) — kinukuha lamang kapag may alerto. Hindi tuloy-tuloy na sinusubaybayan. Humihinto agad kapag natapos na ang alerto."
                )
            ),
            OnboardingStrings.Section(
                "3. Paano iniimbak ang datos",
                "Ang datos ng gawi mula sa sensor ay iniimbak po sa loob mismo ng inyong telepono. " +
                    "Hindi po ito ipinapadala sa anumang panlabas na server sa karaniwang pagbantay. " +
                    "Ang mga tala ng alerto lamang ang ipinapadala sa ligtas na cloud, at kapag may " +
                    "alerto lamang, upang maabisuhan ang inyong mga emergency contact."
            ),
            OnboardingStrings.Section(
                "4. Sino ang nakakakita ng inyong datos",
                bullets = listOf(
                    "Ang inyong mga emergency contact — tumatanggap ng abiso at ng inyong lokasyon tuwing may aktibong alerto lamang.",
                    "Ang mga barangay responder — tumatanggap ng alerto kapag hindi sumagot ang inyong mga contact sa loob ng takdang panahon.",
                    "Ang development team — maaaring makakita ng datos na walang pangalan at pinagsama-sama, para lamang sa pananaliksik na pang-akademiko, na hindi matutukoy ang sinuman."
                )
            )
        ),
    )

    fun forLanguage(language: String): Copy =
        if (language == WellnessMessages.FILIPINO) FILIPINO_COPY else ENGLISH_COPY
}

/** Info-screen prose in the senior's stored language. Provided in `SeniorDashboard`. */
val LocalInfoCopy = androidx.compose.runtime.staticCompositionLocalOf {
    InfoStrings.forLanguage(WellnessMessages.ENGLISH)
}
