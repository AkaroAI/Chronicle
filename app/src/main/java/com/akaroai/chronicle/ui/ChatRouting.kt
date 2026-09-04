package com.akaroai.chronicle.ui

data class PairedInteraction(val first: String, val second: String, val action: String)

enum class MessagePresentation {
    NARRATION,
    PLAYER_ACTION,
    DIALOGUE,
    DM
}

object ChatRouting {
    private const val DM_PREFIX = "[DM Conversation]"

    fun isDmConversation(content: String): Boolean = content.startsWith(DM_PREFIX)

    fun visibleContent(content: String): String =
        if (content.lineSequence().firstOrNull()?.let { it.startsWith("[") && it.endsWith("]") } == true) {
            content.substringAfter('\n', "")
        } else content

    fun presentation(content: String, role: String): MessagePresentation {
        if (isDmConversation(content)) return MessagePresentation.DM
        val route = content.lineSequence().firstOrNull().orEmpty()
        if (role == "user") {
            return if (route.contains("Intent: Talking to", ignoreCase = true)) {
                MessagePresentation.DIALOGUE
            } else {
                MessagePresentation.PLAYER_ACTION
            }
        }
        return if (route.contains("DM", ignoreCase = true)) MessagePresentation.DM
        else MessagePresentation.NARRATION
    }

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

    fun explicitSoloDepartureSubject(clause: String): String? = Regex(
        """(?i)^(.+?)\s+leaves?\s+.+?\s+to\s+(?:go|travel|head|move)\b"""
    ).find(clause)?.groupValues?.get(1)?.trim()
}
