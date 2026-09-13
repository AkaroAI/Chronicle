package com.akaroai.chronicle.provider

import org.json.JSONObject

/** Converts model suggestions into proposals Chronicle can actually and safely apply. */
object ProposalSanitizer {
    private val campaignFields = setOf(
        "name", "description", "setting", "genreTone", "currentLocation", "currentObjective"
    )

    fun sanitize(
        proposals: List<ParsedProposal>,
        userText: String,
        existingCharacterNames: Set<String>
    ): List<ParsedProposal> {
        val known = existingCharacterNames.map { it.trim().lowercase() }.toSet()
        val repaired = proposals.flatMap { proposal ->
            when {
                proposal.targetType == "campaign_update" -> sanitizeCampaign(proposal)
                proposal.targetType == "location_upsert" -> listOf(sanitizeLocation(proposal, userText))
                proposal.targetType == "character_update" && proposal.targetId == null ->
                    repairMissingCharacter(proposal, userText, known)
                else -> listOf(proposal)
            }
        }

        return repaired
            .distinctBy { "${it.targetType}|${it.targetId}|${it.groupLabel.lowercase()}|${it.proposedChanges}" }
            .take(12)
    }

    private fun sanitizeCampaign(proposal: ParsedProposal): List<ParsedProposal> {
        val changes = JSONObject(proposal.proposedChanges)
        val fields = changes.optJSONObject("fields") ?: changes
        val year = fields.optString("year").trim()
        if (year.isNotBlank()) {
            return listOf(
                proposal.copy(
                    summary = "Set campaign year to $year",
                    targetType = "memory_new",
                    targetId = null,
                    proposedChanges = JSONObject()
                        .put("category", "Canon")
                        .put("title", "Campaign Year")
                        .put("content", "The campaign is set in the year $year.")
                        .toString(),
                    groupType = "Lore",
                    groupLabel = "Campaign Calendar",
                    changeMode = "Replace"
                )
            )
        }

        val safe = JSONObject()
        campaignFields.forEach { key ->
            if (fields.has(key) && fields.optString(key).isNotBlank()) safe.put(key, fields.optString(key))
        }
        if (safe.length() == 0) return emptyList()
        return listOf(proposal.copy(proposedChanges = JSONObject().put("fields", safe).toString()))
    }

    private fun sanitizeLocation(proposal: ParsedProposal, userText: String): ParsedProposal {
        val changes = JSONObject(proposal.proposedChanges)
        val description = changes.optString("description").trim()
        val playerDescribedLocation = Regex("(?i)\\b(describe|description|looks like|appears|made of|surrounded by)\\b")
            .containsMatchIn(userText)
        if (description.isNotBlank() && !playerDescribedLocation) changes.put("description", "")
        // Empty optional values represent unknown information, never instructions to clear canon.
        return proposal.copy(proposedChanges = changes.toString())
    }

    private fun repairMissingCharacter(
        proposal: ParsedProposal,
        userText: String,
        known: Set<String>
    ): List<ParsedProposal> {
        val name = proposal.groupLabel.trim()
        if (name.isBlank() || name.lowercase() in known) return emptyList()

        val old = JSONObject(proposal.proposedChanges)
        val fields = old.optJSONObject("fields") ?: old
        var relationship = fields.optString("relationship").trim()
        if (Regex("(?i)\\bhold(?:ing|s)? hands?\\b").containsMatchIn(userText)) {
            val other = Regex("(?i)\\bwith\\s+([A-Za-z][A-Za-z'’-]*)").find(relationship)
                ?.groupValues?.getOrNull(1)
                ?: relationship.substringBefore(' ').takeIf { it.isNotBlank() }
                ?: "the other character"
            relationship = "Held hands with $other."
        }

        val changes = JSONObject().put("name", name)
        setOf(
            "aliases", "species", "age", "pronouns", "appearance", "personality", "backstory",
            "abilities", "equipment", "relationship", "affiliations", "goals", "fears", "secrets",
            "injuries", "notes", "currentLocation"
        ).forEach { key ->
            val value = if (key == "relationship") relationship else fields.optString(key).trim()
            if (value.isNotBlank()) changes.put(key, value)
        }
        changes.put("status", "Active").put("castTier", "Supporting")

        return listOf(
            proposal.copy(
                summary = "Create character sheet: $name",
                targetType = "character_new",
                targetId = null,
                proposedChanges = changes.toString(),
                groupType = "Characters",
                groupLabel = name,
                changeMode = "Replace"
            )
        )
    }
}
