package com.akaroai.chronicle.data

import org.junit.Assert.*
import org.junit.Test

class LosslessImportPipelineTest {
    @Test fun `primary ranges preserve every source character exactly once`() {
        val source = (1..900).joinToString("\n\n") { "Player: line $it. Asira answers line $it." }
        val segments = LosslessImportPipeline.segment(source, targetChars = 2_500, overlapChars = 240)
        val rebuilt = segments.joinToString("") { source.substring(it.primaryStart, it.primaryEnd) }
        assertEquals(source, rebuilt)
        assertTrue(LosslessImportPipeline.verifyCoverage(source, segments).complete)
        assertTrue(segments.size > 1)
    }

    @Test fun `overlap supplies context without changing primary coverage`() {
        val source = "A".repeat(7_000)
        val segments = LosslessImportPipeline.segment(source, targetChars = 2_000, overlapChars = 200)
        assertEquals(0, segments.first().contextStart)
        assertTrue(segments.drop(1).all { it.contextStart < it.primaryStart })
        assertEquals(source.length, segments.last().contextEnd)
    }

    @Test fun `hash is stable and utf8 aware`() {
        assertEquals(LosslessImportPipeline.sha256("Yuki 🌙"), LosslessImportPipeline.sha256("Yuki 🌙"))
        assertNotEquals(LosslessImportPipeline.sha256("Yuki"), LosslessImportPipeline.sha256("Yūki"))
    }

    @Test fun `segment analyses merge characters and retain latest state`() {
        val first = """{"campaignName":"Moonfall","characters":[{"name":"Asira","personality":"Guarded","currentLocation":"Village","confidence":"High confidence"}],"locations":[],"memories":[],"factions":[],"quests":[],"timelineEvents":[]}"""
        val second = """{"campaignName":"","characters":[{"name":"Asira","goals":"Protect Yuki","currentLocation":"Spire","confidence":"Needs review"},{"name":"Yuki","fears":"Storms"}],"locations":[],"memories":[],"factions":[],"quests":[],"timelineEvents":[]}"""
        val merged = ExternalCampaignImport.mergeAnalyses(listOf(first, second), "Player: complete transcript")
        assertEquals("Moonfall", merged.campaignName)
        assertEquals(2, merged.characters.size)
        val asira = merged.characters.first { it.name == "Asira" }
        assertEquals("Guarded", asira.personality)
        assertEquals("Protect Yuki", asira.goals)
        assertEquals("Spire", asira.currentLocation)
        assertEquals("Needs review", asira.confidence)
        assertEquals("Player: complete transcript", merged.sourceText)
    }
}
