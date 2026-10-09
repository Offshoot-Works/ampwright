package io.github.offshootworks.ampwright

import android.app.Application
import io.github.offshootworks.ampwright.diagnostics.CrashLog

class AmpWrightApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
