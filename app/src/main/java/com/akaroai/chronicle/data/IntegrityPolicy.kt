package com.akaroai.chronicle.data

import com.akaroai.chronicle.model.CharacterEntity

object IntegrityPolicy {
    val allCharacterFields = listOf(
        "name","aliases","species","age","pronouns","appearance","personality",
        "backstory","abilities","equipment","relationship","affiliations","goals",
        "fears","secrets","injuries","notes","status"
    )

    private val strictDefaults = setOf(
        "name","aliases","species","age","pronouns","personality",
        "backstory","abilities","secrets"
    )

    private val balancedDefaults = setOf(
        "name","species","pronouns","backstory"
    )

    fun protectedFields(character: CharacterEntity): Set<String> {
        val base = when (character.integrityMode) {
            "Strict" -> strictDefaults
            "Flexible" -> emptySet()
            else -> balancedDefaults
        }
        val manual = character.protectedFields
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        return base + manual
    }

    fun protectedOverlap(character: CharacterEntity, changed: Set<String>): Set<String> =
        protectedFields(character).intersect(changed)

    fun warning(character: CharacterEntity, changed: Set<String>, changeMode: String): String {
        val locked = protectedOverlap(character, changed)
        if (locked.isNotEmpty()) {
            return "Protected core field${if (locked.size > 1) "s" else ""}: ${locked.joinToString(", ")}. Explicit override required."
        }
        val identityLike = changed.intersect(
            setOf("personality","backstory","abilities","relationship","goals","fears","secrets")
        )
        if (changeMode == "Replace" && identityLike.isNotEmpty()) {
            return "Replacement may overwrite established development: ${identityLike.joinToString(", ")}. Consider Append."
        }
        return ""
    }

    fun applyMode(current: String, incoming: String, mode: String): String = when (mode) {
        "Clear" -> ""
        "Append" -> {
            val next = incoming.trim()
            when {
                next.isBlank() -> current
                current.isBlank() -> next
                current.contains(next, ignoreCase = true) -> current
                else -> "$current\n• $next"
            }
        }
        else -> incoming
    }
}
