package com.akaroai.chronicle

import android.app.Application
import com.akaroai.chronicle.data.ChronicleDatabase
import com.akaroai.chronicle.provider.ProviderSettingsStore

class ChronicleApp : Application() {
    val database by lazy { ChronicleDatabase.get(this) }
    val providerSettings by lazy { ProviderSettingsStore(this) }
}
