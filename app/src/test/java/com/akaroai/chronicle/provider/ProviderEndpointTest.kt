package com.akaroai.chronicle.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderEndpointTest {
    @Test
    fun `proposal scans add the OpenAI compatible v1 route to an engine root`() {
        assertEquals(
            "http://10.0.0.81:8765/v1/chat/completions",
            providerEndpoint("http://10.0.0.81:8765", nativeEngineRequest = false)
        )
    }

    @Test
    fun `proposal scans do not duplicate an existing v1 route`() {
        assertEquals(
            "http://10.0.0.81:8765/v1/chat/completions",
            providerEndpoint("http://10.0.0.81:8765/v1", nativeEngineRequest = false)
        )
    }

    @Test
    fun `native narration continues to use the structured engine route`() {
        assertEquals(
            "http://10.0.0.81:8765/api/v1/generate",
            providerEndpoint("http://10.0.0.81:8765/v1", nativeEngineRequest = true)
        )
    }
}
