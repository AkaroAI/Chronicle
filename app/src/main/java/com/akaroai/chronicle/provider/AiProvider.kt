package com.akaroai.chronicle.provider

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

/**
 * Offline placeholder provider used while the app foundation is being built.
 * It intentionally does not call any remote service.
 */
class ChronicleDemoProvider : AiProvider {
    override val id = "demo"
    override val displayName = "Chronicle Demo"

    override suspend fun generate(request: ProviderRequest): String {
        val last = request.messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        return if (last.isBlank()) {
            "Chronicle is ready."
        } else {
            "Demo mode received: \"$last\"\n\nConnect an AI provider in a later build to continue."
        }
    }
}
