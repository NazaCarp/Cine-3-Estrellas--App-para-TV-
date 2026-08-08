package com.cine3estrellas

fun String.cleanTitle(): String {
    return this.replace(Regex("\\s\\(\\d{4}\\)$"), "").trim()
}

/**
 * Converts text-based certifications (TMDB) to numerical format (+18, +16, etc.)
 */
fun formatCertification(cert: String?): String {
    if (cert.isNullOrBlank()) return ""

    // Normalize string
    val c = cert.uppercase().trim()

    // Check if the certification is just a number
    if (c.all { it.isDigit() }) return "+$c"
    return when {
        // Spain / Generic numeric
        c.contains("18") -> "+18"
        c.contains("16") -> "+16"
        c.contains("15") -> "+15"
        c.contains("13") -> "+13"
        c.contains("12") -> "+12"
        c.contains("7") -> "+7"

        // US Ratings (Common in TMDB)
        c == "R" || c == "NC-17" || c == "TV-MA" -> "+18"
        c == "PG-13" || c == "TV-14" -> "+13"
        c == "PG" || c == "TV-PG" -> "+7"
        c == "G" || c == "TV-G" || c == "TV-Y" || c == "TV-Y7" -> "TP" // Todo Público

        // Latin America
        c == "C" -> "+18"
        c == "B15" -> "+15"
        c == "B" -> "+12"
        c == "A" -> "TP"

        else -> cert // Fallback
    }
}
