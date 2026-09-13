package com.akaroai.chronicle.provider

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProposalSanitizerTest {
    private fun proposal(type: String, changes: JSONObject, label: String = "") = ParsedProposal(
        summary = "test", targetType = type, targetId = null, proposedChanges = changes.toString(),
        reason = "test", priority = "Normal", groupType = "World", groupLabel = label,
        changeMode = "Replace", evidenceType = "Player Confirmed"
    )

    @Test fun `unsupported campaign year becomes canon memory`() {
        val input = proposal("campaign_update", JSONObject().put("fields", JSONObject().put("year", "789")))
        val result = ProposalSanitizer.sanitize(listOf(input), "campaign is set in year 789", emptySet()).single()
        assertEquals("memory_new", result.targetType)
        assertEquals("Campaign Year", JSONObject(result.proposedChanges).getString("title"))
    }

    @Test fun `narrator invented location description is removed`() {
        val input = proposal("location_upsert", JSONObject().put("name", "Moonfall Village").put("description", "Silver towers"))
        val result = ProposalSanitizer.sanitize(listOf(input), "location: Moonfall Village", emptySet()).single()
        assertEquals("", JSONObject(result.proposedChanges).getString("description"))
    }

    @Test fun `missing character id becomes minimal character sheet`() {
        val input = proposal(
            "character_update",
            JSONObject().put("fields", JSONObject().put("relationship", "Asira (significant ally)")),
            "Yuki"
        )
        val result = ProposalSanitizer.sanitize(listOf(input), "Yuki and Asira hold hands", emptySet()).single()
        assertEquals("character_new", result.targetType)
        val changes = JSONObject(result.proposedChanges)
        assertEquals("Yuki", changes.getString("name"))
        assertEquals("Held hands with Asira.", changes.getString("relationship"))
        assertFalse(changes.has("personality"))
    }
}
