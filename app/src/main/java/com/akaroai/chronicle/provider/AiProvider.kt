package com.akaroai.chronicle.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ProviderMessage(
    val role: String,
    val content: String
)

data class ProviderRequest(
    val systemPrompt: String,
    val memoryContext: String,
    val messages: List<ProviderMessage>
)

interface AiProvider {
    val id: String
    val displayName: String
    suspend fun generate(request: ProviderRequest): String
}

class ChronicleDemoProvider : AiProvider {
    override val id = "demo"
    override val displayName = "Chronicle Demo"

    override suspend fun generate(request: ProviderRequest): String {
        val last = request.messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        return if (last.isBlank()) {
            "Chronicle is ready."
        } else {
            "Demo mode received: \"$last\"\n\nOpen AI Settings to connect a provider."
        }
    }
}

class OpenAiCompatibleProvider(
    private val settingsProvider: () -> ProviderSettings
) : AiProvider {
    override val id = "openai-compatible"
    override val displayName = "OpenAI-compatible"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun generate(request: ProviderRequest): String = withContext(Dispatchers.IO) {
        val settings = settingsProvider()
        require(settings.enabled) { "AI provider is disabled." }
        require(settings.baseUrl.isNotBlank()) { "Provider base URL is missing." }
        require(settings.model.isNotBlank()) { "Model name is missing." }

        val endpoint = settings.baseUrl.trimEnd('/') + "/chat/completions"

        val messages = JSONArray()
        val system = buildString {
            append(request.systemPrompt.trim())
            if (request.memoryContext.isNotBlank()) {
                append("\n\nCAMPAIGN MEMORY — ONLY THIS CAMPAIGN:\n")
                append(request.memoryContext.trim())
            }
        }

        messages.put(JSONObject().put("role", "system").put("content", system))
        request.messages.forEach {
            messages.put(JSONObject().put("role", it.role).put("content", it.content))
        }

        val payload = JSONObject()
            .put("model", settings.model)
            .put("messages", messages)
            .put("temperature", 0.9)
            .put("stream", false)

        val httpRequest = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Content-Type", "application/json")
            .apply {
                if (settings.apiKey.isNotBlank()) {
                    header("Authorization", "Bearer ${settings.apiKey}")
                }
            }
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Provider error ${response.code}: $body")
            }

            val json = JSONObject(body)
            val choices = json.optJSONArray("choices")
                ?: throw IllegalStateException("Provider returned no choices.")

            if (choices.length() == 0) {
                throw IllegalStateException("Provider returned an empty response.")
            }

            choices.getJSONObject(0)
                .getJSONObject("message")
                .optString("content")
                .ifBlank { "The provider returned an empty message." }
        }
    }
}
