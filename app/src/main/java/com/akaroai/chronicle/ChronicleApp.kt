package com.akaroai.chronicle

import android.app.Application
import com.akaroai.chronicle.data.ChronicleDatabase

class ChronicleApp : Application() {
    val database by lazy { ChronicleDatabase.get(this) }
}
