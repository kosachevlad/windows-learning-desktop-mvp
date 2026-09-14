package ua.school.windowsdesktop

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

object CrashReporter {
    private const val PREFS = "crash_reporter"
    private const val KEY = "last_crash"
    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val application = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
            val trace = StringWriter().also { failure.printStackTrace(PrintWriter(it)) }.toString()
            val report = "Thread: ${thread.name}\nAndroid: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}\n\n$trace"
            application.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, report).commit()
            Log.e("WindowsLearningCrash", report)
            previous?.uncaughtException(thread, failure)
        }
    }

    fun read(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
