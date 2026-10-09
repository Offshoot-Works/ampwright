package io.github.offshootworks.ampwright.diagnostics

import android.content.Context
import io.github.offshootworks.ampwright.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saves the last crash to a file so the next diagnostics report can include it. */
object CrashLog {
    private const val FILE_NAME = "last-crash.txt"

    fun install(context: Context) {
        val file = file(context)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                file.writeText("$time, app ${BuildConfig.VERSION_NAME}, thread ${thread.name}\n${error.stackTraceToString()}")
            } catch (_: Exception) {
                // Never let recording the crash hide it.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? = try {
        file(context).takeIf { it.exists() }?.readText()
    } catch (_: Exception) {
        null
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)
}
