package com.akaroai.chronicle.provider

import android.content.Context

data class ProviderSettings(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val enabled: Boolean = false,
    val autoReviewEnabled: Boolean = true
)

class ProviderSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("chronicle_provider", Context.MODE_PRIVATE)

    fun load(): ProviderSettings = ProviderSettings(
        baseUrl = prefs.getString("baseUrl", "") ?: "",
        apiKey = prefs.getString("apiKey", "") ?: "",
        model = prefs.getString("model", "") ?: "",
        enabled = prefs.getBoolean("enabled", false),
        autoReviewEnabled = prefs.getBoolean("autoReviewEnabled", true)
    )

    fun save(settings: ProviderSettings) {
        prefs.edit()
            .putString("baseUrl", settings.baseUrl.trim())
            .putString("apiKey", settings.apiKey.trim())
            .putString("model", settings.model.trim())
            .putBoolean("enabled", settings.enabled)
            .putBoolean("autoReviewEnabled", settings.autoReviewEnabled)
            .apply()
    }
}
