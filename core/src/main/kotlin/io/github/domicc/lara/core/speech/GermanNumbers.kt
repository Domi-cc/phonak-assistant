package io.github.domicc.lara.core.speech

/**
 * German number words for the spoken count. The speech engine gets words, not digits, so that
 * it cannot read a number as a date, an ordinal or a time.
 */
object GermanNumbers {

    const val MAX = 99

    private val upToNineteen = listOf(
        "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn",
        "elf", "zwölf", "dreizehn", "vierzehn", "fünfzehn", "sechzehn", "siebzehn", "achtzehn", "neunzehn",
    )
    private val tens = listOf("zwanzig", "dreißig", "vierzig", "fünfzig", "sechzig", "siebzig", "achtzig", "neunzig")

    /** The word for [number]; null outside 1 to [MAX]. */
    fun word(number: Int): String? = when {
        number !in 1..MAX -> null
        number < 20 -> upToNineteen[number - 1]
        number % 10 == 0 -> tens[number / 10 - 2]
        // "einundzwanzig", not "einsundzwanzig"
        else -> (if (number % 10 == 1) "ein" else upToNineteen[number % 10 - 1]) + "und" + tens[number / 10 - 2]
    }
}
