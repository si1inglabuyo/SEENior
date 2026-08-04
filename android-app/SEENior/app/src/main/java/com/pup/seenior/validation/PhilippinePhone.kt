package com.pup.seenior.validation

/**
 * Philippine mobile number validation. Accepts "09XXXXXXXXX" (11 digits) or
 * "+639XXXXXXXXX" / "639XXXXXXXXX"; everything is stored normalized as "09XXXXXXXXX".
 */
object PhilippinePhone {

    /** Returns the number normalized to "09XXXXXXXXX", or null if it isn't a valid PH mobile number. */
    fun normalize(raw: String): String? {
        val digits = raw.filter(Char::isDigit)
        return when {
            digits.length == 11 && digits.startsWith("09") -> digits
            digits.length == 12 && digits.startsWith("639") -> "0" + digits.substring(2)
            else -> null
        }
    }

    fun isValid(raw: String): Boolean = normalize(raw) != null
}
