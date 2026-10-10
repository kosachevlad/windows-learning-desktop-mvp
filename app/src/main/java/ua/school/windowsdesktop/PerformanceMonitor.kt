package ua.school.windowsdesktop

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.FrameMetrics
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * Debug-only, low-overhead telemetry for profiling on a physical tablet.
 * It aggregates frame data instead of logging every frame, so collecting it
 * does not itself create visible work on the UI thread.
 */
internal object PerformanceMonitor {
    private const val TAG = "DesktopPerf"
    private const val REPORT_EVERY_FRAMES = 120
    private const val JANK_FRAME_NANOS = 16_666_667L

    private var launchStartedAtNanos = 0L
    private var appContext: Context? = null
    private var frames = 0
    private var jankyFrames = 0
    private var totalFrameNanos = 0L
    private var longestFrameNanos = 0L
    private var listener: WindowFrameListener? = null

    fun start(activity: Activity) {
        if (!BuildConfig.DEBUG) return
        appContext = activity.applicationContext
        launchStartedAtNanos = SystemClock.elapsedRealtimeNanos()
        val frameListener = WindowFrameListener(activity)
        listener = frameListener
        activity.window.addOnFrameMetricsAvailableListener(frameListener, Handler(Looper.getMainLooper()))
    }

    fun stop(activity: Activity) {
        if (!BuildConfig.DEBUG) return
        listener?.let { activity.window.removeOnFrameMetricsAvailableListener(it) }
        listener = null
    }

    fun markContentReady(label: String) {
        if (!BuildConfig.DEBUG || launchStartedAtNanos == 0L) return
        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - launchStartedAtNanos) / 1_000_000
        Log.i(TAG, "$label displayed in ${elapsedMs}ms since Activity.onCreate")
        logMemory(label)
    }

    fun markScreenRequested(label: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, "Open requested: $label at ${SystemClock.elapsedRealtime()}ms")
    }

    fun markScreenDisplayed(label: String, requestedAtMs: Long) {
        if (!BuildConfig.DEBUG) return
        Log.i(TAG, "$label first frame in ${SystemClock.elapsedRealtime() - requestedAtMs}ms")
        logMemory(label)
    }

    private fun logMemory(label: String) {
        val runtime = Runtime.getRuntime()
        val usedMiB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val memoryInfo = appContext?.getSystemService(ActivityManager::class.java)?.let { manager ->
            ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        }
        val availableMiB = memoryInfo?.availMem?.div(1024 * 1024)
        Log.i(TAG, "$label memory: Java heap ${usedMiB}MiB / ${runtime.maxMemory() / (1024 * 1024)}MiB, system available=${availableMiB ?: "?"}MiB")
    }

    private class WindowFrameListener(private val activity: Activity) :
        android.view.Window.OnFrameMetricsAvailableListener {
        override fun onFrameMetricsAvailable(window: android.view.Window, frameMetrics: FrameMetrics, dropCountSinceLastInvocation: Int) {
            val duration = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
            if (duration <= 0L) return
            frames++
            totalFrameNanos += duration
            longestFrameNanos = maxOf(longestFrameNanos, duration)
            if (duration > JANK_FRAME_NANOS) jankyFrames++
            if (frames % REPORT_EVERY_FRAMES == 0) {
                val averageMs = totalFrameNanos / frames / 1_000_000.0
                val worstMs = longestFrameNanos / 1_000_000.0
                Log.i(TAG, "frames=$frames janky=$jankyFrames avg=${"%.1f".format(averageMs)}ms worst=${"%.1f".format(worstMs)}ms dropped=$dropCountSinceLastInvocation")
                frames = 0
                jankyFrames = 0
                totalFrameNanos = 0L
                longestFrameNanos = 0L
            }
        }
    }
}
