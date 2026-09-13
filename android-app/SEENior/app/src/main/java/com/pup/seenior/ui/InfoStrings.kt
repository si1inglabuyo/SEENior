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
 * 1. **The Filipino got a first native-speaker pass on 2026-09-14** — "po"/"pong" removed
 *    throughout (except inside the two places this file quotes the wellness prompt itself
 *    verbatim, which CLAUDE.md §14 pins and this file does not touch), "telepono" changed to
 *    "cellphone". It has not been read end-to-end by a native speaker since, and the panel has
 *    already rejected one phrasing in this app as too informal. The legal sections matter most: a
 *    translated privacy notice is the notice the senior is relying on.
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
            "Ihanda ang iyong profile" to
                "Ilagay ang iyong mga pangunahing detalye at kung sino ang iyong kasama sa bahay " +
                "habang nagse-setup. Isang beses lang ito gagawin, at magpapatuloy agad ang app " +
                "pagkatapos.",
            "Payagan ang mga pahintulot at buksan ang mga alerto (kailangan)" to
                "Habang nagse-setup, kailangan buksan ang mga abiso at ang mga hinihinging " +
                "pahintulot upang gumana nang maayos ang SEENior. Hindi makakatuloy ang app kung " +
                "wala ito.",
            "Sagutin ang mga tanong sa pagsisimula (14 na araw na baseline)" to
                "Pagkatapos ng setup, sasagot kayo ng ilang maikling tanong. Ginagamit ng SEENior " +
                "ang mga sagot na ito upang matutunan ang iyong karaniwang gawi sa loob ng 14 na " +
                "araw, at ito ang magiging batayan ninyong sariling rutina.",
            "Mag-imbita ng mapagkakatiwalaang contact" to
                "Pumunta sa Imbitasyon upang gumawa ng natatanging code. Nag-e-expire " +
                "ang code tuwing 5 minuto para sa seguridad, at makakagawa kayo ng bago kapag " +
                "nag-expire ito. Ibahagi ang code na ito upang makakonekta sa taong pinagkakatiwalaan " +
                "ninyo.",
            "Pamahalaan ang iyong mga contact" to
                "Sa Contacts, makikita ninyo ang mga nakakonektang contact, ang kanilang " +
                "kalagayan, at maaari ninyo silang alisin kung kinakailangan.",
            "Sagutin ang wellness check kapag lumitaw ito" to
                "Kung may mapansing hindi pangkaraniwan ang SEENior, itatanong nito ang \"Ligtas po " +
                "ba kayo? Maayos po ba kayo?\" at sasabihin kung bakit ito nagtatanong. Ang pagpindot " +
                "sa \"Maayos po ako\" ay agad na hihinto sa alerto. Kung hindi kayo sasagot, " +
                "aabisuhan ang iyong contact sa pamilya, at pagkatapos ay ang iyong barangay " +
                "responder."
        ),

        faqsTitle = "Mga Madalas Itanong",
        faqs = listOf(
            Faq(
                "Libre ba ang app na ito?",
                "Oo. Libre i-download at gamitin ang SEENior."
            ),
            Faq(
                "Palagi bang ibinabahagi ang aking lokasyon?",
                "Hindi. Kinukuha lang ang iyong lokasyon sa mismong sandaling may alerto, " +
                    "hindi tuloy-tuloy. Ang huling lokasyong iyon lamang ang ibinabahagi sa mga taong " +
                    "pinagkakatiwalaan ninyo."
            ),
            Faq(
                "Magagamit ba ang SEENior kahit walang internet?",
                "Oo. Patuloy nagbabantay ang SEENior at maipapakita pa rin ang iyong mga " +
                    "naka-save na contact kahit walang internet. Ang pagpapadala ng alerto at " +
                    "update sa iyong pamilya ay nangangailangan ng internet o signal.",
                note = "Kung wala pang signal, susubukan ng SEENior na magpadala ng SMS. Panatilihing " +
                    "may baterya ang iyong cellphone at dala ito upang may maabot para sa inyo."
            ),
            Faq(
                "Paano ko aalisin ang isang nakakonektang contact?",
                "Pumunta sa Contacts, piliin ang contact, mag-scroll pababa, at pindutin " +
                    "ang Alisin ang Contact. May lilitaw na tanong upang kumpirmahin bago ito alisin."
            ),
            Faq(
                "Kailangan ko bang buksan ang app araw-araw?",
                "Hindi. Tahimik tumatakbo ang SEENior sa background. Kailangan lang ninyong " +
                    "sumagot kapag tinanong kayo ng \"Ligtas po ba kayo? Maayos po ba kayo?\""
            )
        ),

        importantNote = "Mahalagang Paalala:",

        supportTitle = "Makipag-ugnayan sa support",
        callUs = "Tawagan kami",
        sendMessage = "Magpadala ng mensahe",
        messageLabel = "MENSAHE",
        messagePlaceholder = "Magmungkahi ng bagong feature…",
        send = "IPADALA",

        termsScaffoldTitle = "Mga tuntunin at kondisyon",
        termsBodyTitle = "Mga Tuntunin at Kondisyon",
        termsSections = listOf(
            OnboardingStrings.Section(
                "1. Pagtanggap sa mga Tuntunin",
                "Sa paggawa ng account at paggamit ng SEENior, sumasang-ayon kayo sa mga Tuntunin " +
                    "ng Paggamit na ito. Kung hindi kayo sang-ayon, huwag gamitin ang aming " +
                    "serbisyo."
            ),
            OnboardingStrings.Section(
                "2. Paglalarawan ng Serbisyo",
                "Ang SEENior ay isang tahimik na aplikasyon sa pagbantay ng gawi. Ginagamit nito " +
                    "ang mga sensor na nasa loob na ng iyong cellphone upang matutunan ang iyong " +
                    "pang-araw-araw na rutina at matukoy ang malalaking pagbabago na maaaring " +
                    "senyales ng emergency."
            ),
            OnboardingStrings.Section(
                "3. Sino ang Maaaring Gumamit",
                "Ang senior app ng SEENior ay para sa mga senior citizen (60 taong gulang pataas) " +
                    "na naninirahan sa Pilipinas, ayon sa RA 9994 (Expanded Senior Citizens Act of " +
                    "2010). Ang paggamit ng mas bata rito ay pinapayagan lamang para sa pagsubok ng " +
                    "development team."
            ),
            OnboardingStrings.Section(
                "4. Mga Tungkulin ng Gumagamit",
                bullets = listOf(
                    "Panatilihing may baterya ang iyong cellphone at dala ito, upang maayos na gumana ang pagbantay.",
                    "Magbigay ng tama at totoong impormasyon sa pag-setup ng account.",
                    "Payagan ang mga kinakailangang pahintulot (galaw, mga abiso, pagtakbo sa background) upang gumana ang sistema.",
                    "Magdagdag ng kahit isang emergency contact upang may makatanggap ng alerto kapag may napansing hindi pangkaraniwan.",
                    "Ipaalam sa iyong mga emergency contact na sila ay makakatanggap ng mga alerto mula sa SEENior para sa inyo."
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
                "2. Anong datos ang aming kinukuha",
                body = "Ito ang mga datos na kinukuha namin sa mga senior na gumagamit:",
                bullets = listOf(
                    "Personal na profile — buong pangalan, edad, kasarian, kasama sa bahay, tirahan, numero ng cellphone, at kaugnayan sa mga emergency contact.",
                    "Datos ng gawi mula sa sensor — mga pagbasa ng accelerometer, oras ng pagbukas at pagsara ng screen, at kalagayan ng baterya o pag-charge. Ginagamit lamang upang mabuo ang batayan ng iyong aktibidad.",
                    "Lokasyon (GPS) — kinukuha lamang kapag may alerto. Hindi tuloy-tuloy na sinusubaybayan. Humihinto agad kapag natapos na ang alerto."
                )
            ),
            OnboardingStrings.Section(
                "3. Paano iniimbak ang datos",
                "Ang datos ng gawi mula sa sensor ay iniimbak sa loob mismo ng iyong cellphone. " +
                    "Hindi ito ipinapadala sa anumang panlabas na server sa karaniwang pagbantay. " +
                    "Ang mga tala ng alerto lamang ang ipinapadala sa ligtas na cloud, at kapag may " +
                    "alerto lamang, upang maabisuhan ang iyong mga emergency contact."
            ),
            OnboardingStrings.Section(
                "4. Sino ang nakakakita ng iyong datos",
                bullets = listOf(
                    "Ang iyong mga emergency contact — tumatanggap ng abiso at ng iyong lokasyon tuwing may aktibong alerto lamang.",
                    "Ang mga barangay responder — tumatanggap ng alerto kapag hindi sumagot ang iyong mga contact sa loob ng takdang panahon.",
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
