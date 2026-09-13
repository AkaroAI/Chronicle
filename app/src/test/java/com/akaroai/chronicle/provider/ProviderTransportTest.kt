package com.akaroai.chronicle.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class ProviderTransportTest {
    @Test
    fun `private IPv4 ranges are recognized`() {
        assertTrue(isPrivateLanHost("10.0.0.81"))
        assertTrue(isPrivateLanHost("172.16.0.1"))
        assertTrue(isPrivateLanHost("172.31.255.254"))
        assertTrue(isPrivateLanHost("192.168.1.20"))
        assertTrue(isPrivateLanHost("127.0.0.1"))
        assertTrue(isPrivateLanHost("localhost"))
        assertFalse(isPrivateLanHost("8.8.8.8"))
        assertFalse(isPrivateLanHost("172.32.0.1"))
        assertFalse(isPrivateLanHost("provider.example"))
    }

    @Test
    fun `private HTTP without an API key is allowed`() {
        val settings = ProviderSettings(baseUrl = "http://10.0.0.81:8765/v1")
        assertEquals("http://10.0.0.81:8765/v1", validateProviderTransport(settings))
    }

    @Test
    fun `public HTTP is rejected`() {
        val settings = ProviderSettings(baseUrl = "http://provider.example/v1")
        assertThrows(IllegalArgumentException::class.java) {
            validateProviderTransport(settings)
        }
    }

    @Test
    fun `API keys over private HTTP are rejected`() {
        val settings = ProviderSettings(
            baseUrl = "http://10.0.0.81:8765/v1",
            apiKey = "secret"
        )
        assertThrows(IllegalArgumentException::class.java) {
            validateProviderTransport(settings)
        }
    }

    @Test
    fun `HTTPS providers remain supported`() {
        val settings = ProviderSettings(
            baseUrl = "https://provider.example/v1/",
            apiKey = "secret"
        )
        assertEquals("https://provider.example/v1", validateProviderTransport(settings))
    }

    @Test
    fun `native engine payload retains DM system and user messages`() {
        val payload = buildProviderPayload(
            ProviderRequest(
                systemPrompt = "Non-canonical DM conversation",
                memoryContext = "",
                messages = listOf(ProviderMessage("user", "[DM Conversation]\nHello")),
                nativeEnginePayload = JSONObject().put("campaign", JSONObject().put("campaign_id", "1"))
            ),
            "qwen3:14b"
        )
        assertEquals("Non-canonical DM conversation", payload.getString("system_prompt"))
        assertEquals("[DM Conversation]\nHello", payload.getJSONArray("messages").getJSONObject(1).getString("content"))
        assertEquals("1", payload.getJSONObject("campaign").getString("campaign_id"))
    }
}
