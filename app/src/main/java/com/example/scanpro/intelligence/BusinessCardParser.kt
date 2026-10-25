package com.example.scanpro.intelligence

import java.util.Locale

/** The fields of a business card, each taken word for word from the recognised text. */
data class BusinessCard(
    val name: String? = null,
    val jobTitle: String? = null,
    val company: String? = null,
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val website: String? = null,
    val address: String? = null
) {
    val isUseful: Boolean get() = name != null || phones.isNotEmpty() || emails.isNotEmpty()
}

/**
 * Splits business-card text into contact fields with simple, predictable rules: contact details by pattern,
 * then the remaining lines by what they look like (a job title, a company, a person's name, an address).
 */
object BusinessCardParser {
    private val emailRegex = Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)
    private val phoneRegex = Regex("(?<![\\d])\\+?\\(?\\d[\\d ()./-]{6,}\\d(?![\\d])")
    private val webRegex = Regex("(?i)\\b(?:https?://)?(?:www\\.)?[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.(?:com|in|net|org|io|co|biz|info|ai|app|dev|uk|us|de|ae|sg|au|ca)(?:/[^\\s]*)?\\b")
    private val labelRegex = Regex("(?i)^(?:tel|ph|phone|mob|mobile|cell|fax|off|office|m|t|e|email|e-mail|w|web|website)\\s*[.:]?\\s*")

    private val titleWords = listOf(
        "director", "manager", "ceo", "cto", "cfo", "coo", "founder", "co-founder", "engineer", "consultant", "president",
        "officer", "head", "lead", "designer", "developer", "sales", "marketing", "partner", "executive", "analyst",
        "advocate", "architect", "specialist", "associate", "proprietor", "owner", "chairman", "principal", "vp",
        "vice president", "accountant", "doctor", "md", "gm", "representative", "coordinator", "administrator"
    )
    private val companyWords = listOf(
        "ltd", "limited", "pvt", "private", "inc", "llc", "llp", "gmbh", "corp", "corporation", "company", "co.",
        "technologies", "technology", "solutions", "studio", "group", "labs", "services", "industries", "enterprises",
        "consulting", "systems", "associates", "traders", "agency", "bank", "hospital", "clinic", "university", "pharma"
    )
    private val addressWords = listOf(
        "street", "st.", "road", "rd", "avenue", "ave", "floor", "suite", "nagar", "sector", "block", "lane", "plot",
        "building", "bldg", "tower", "colony", "marg", "highway", "city", "district", "pin", "zip", "po box", "layout", "main"
    )

    fun parse(text: String): BusinessCard {
        val lines = text.lineSequence().map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.length >= 2 }.toList()
        val emails = emailRegex.findAll(text).map { it.value }.distinct().toList()
        val phones = lines.flatMap { line ->
            phoneRegex.findAll(line).map { it.value.trim() }.filter { it.count(Char::isDigit) in 8..15 }.toList()
        }.distinct()
        val website = lines.asSequence()
            .filter { '@' !in it }
            .mapNotNull { webRegex.find(it)?.value }
            .firstOrNull()

        // Drop contact-detail lines; a phone line may carry a short label ("Mob:") but no other words.
        val rest = lines.filterNot { line ->
            val labelLength = labelRegex.find(line)?.value?.length ?: 0
            '@' in line || (website != null && website in line) ||
                (phones.any { it in line } && line.count(Char::isLetter) <= labelLength + 6)
        }.map { it.replace(labelRegex, "").trim() }.filter { it.count(Char::isLetter) >= 2 }

        fun lower(s: String) = s.lowercase(Locale.ROOT)
        fun hasWord(line: String, words: List<String>) = words.any { w ->
            Regex("(?<![a-z])" + Regex.escape(w) + "(?![a-z])").containsMatchIn(lower(line))
        }

        val jobTitle = rest.firstOrNull { hasWord(it, titleWords) && !hasWord(it, companyWords) }
        val company = rest.firstOrNull { it != jobTitle && hasWord(it, companyWords) }
            ?: (emails.firstOrNull()?.substringAfter('@') ?: website?.removePrefix("www."))
                ?.substringBefore('.')
                ?.takeIf { it.length >= 3 && it !in GENERIC_MAIL_DOMAINS }
                ?.replaceFirstChar { it.titlecase(Locale.ROOT) }
        val addressLines = rest.filter { line ->
            line != jobTitle && line != company &&
                (hasWord(line, addressWords) || Regex("\\b\\d{5,6}\\b").containsMatchIn(line)) && line.any(Char::isDigit)
        }.take(2)
        val name = rest.firstOrNull { line ->
            val words = line.split(' ')
            line != jobTitle && line != company && line !in addressLines &&
                words.size in 2..4 && line.none(Char::isDigit) &&
                words.all { w -> w.firstOrNull()?.isUpperCase() == true && w.all { it.isLetter() || it == '.' || it == '-' || it == '\'' } } &&
                !hasWord(line, companyWords) && !hasWord(line, titleWords)
        }

        return BusinessCard(
            name = name,
            jobTitle = jobTitle,
            company = company,
            phones = phones,
            emails = emails,
            website = website,
            address = addressLines.takeIf { it.isNotEmpty() }?.joinToString(", ")
        )
    }

    private val GENERIC_MAIL_DOMAINS = setOf("gmail", "yahoo", "outlook", "hotmail", "icloud", "proton", "protonmail", "rediffmail", "live", "aol")
}
