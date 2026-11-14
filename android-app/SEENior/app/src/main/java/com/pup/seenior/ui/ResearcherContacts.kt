package com.pup.seenior.ui

/**
 * The research team, shown under Contact support in both the senior and the family app. One list
 * for both apps so the two screens cannot drift apart.
 *
 * These are people's own numbers and addresses, so keep this to what each person agreed to show.
 * Names are deliberately not here: only the contact details were supplied.
 */
object ResearcherContacts {

    data class Researcher(val email: String, val phone: String?)

    val ALL: List<Researcher> = listOf(
        Researcher("seth.oseas.evardo@gmail.com", "0907 592 5255"),
        Researcher("altheagatch@gmail.com", "0991 812 8756"),
        Researcher("andre.dulfo4617@gmail.com", "0947 549 5242"),
        Researcher("liezeljoantamparong@gmail.com", "0975 559 4939"),
        Researcher("ocasionreviman@gmail.com", "0994 219 0424"),
    )

    /** Digits and a leading plus only, which is what the dialler needs. */
    fun dialable(phone: String): String = phone.filter { it.isDigit() || it == '+' }
}
