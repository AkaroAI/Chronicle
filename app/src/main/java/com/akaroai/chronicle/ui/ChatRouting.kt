package com.akaroai.chronicle.ui

data class PairedInteraction(val first: String, val second: String, val action: String)

object ChatRouting {
    private const val DM_PREFIX = "[DM Conversation]"

    fun isDmConversation(content: String): Boolean = content.startsWith(DM_PREFIX)

    fun visibleContent(content: String): String =
        if (content.lineSequence().firstOrNull()?.let { it.startsWith("[") && it.endsWith("]") } == true) {
            content.substringAfter('\n', "")
        } else content

    fun parsePairedInteraction(raw: String): PairedInteraction? {
        val text = raw.substringAfter('\n', raw).trim()
        val match = Regex(
            """^([\p{L}][\p{L}'-]*)\s+and\s+([\p{L}][\p{L}'-]*)\s+(hold hands|are holding hands|hug|kiss)\b""",
            RegexOption.IGNORE_CASE
        ).find(text) ?: return null
        return PairedInteraction(
            match.groupValues[1].replaceFirstChar { it.titlecase() },
            match.groupValues[2].replaceFirstChar { it.titlecase() },
            match.groupValues[3].lowercase()
        )
    }
}
