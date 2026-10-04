package com.pup.seenior.alerts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Sounds and vibrates while an alert is waiting on an answer. Not the notification channel's
 * sound, which plays once. Held in a process-wide object so backgrounding the prompt or the
 * screen timing out can't silence an unanswered alert. Uses USAGE_ALARM so it is heard across
 * a room and passes a silenced ringer and Do Not Disturb.
 *
 * Read the trace with: adb logcat -s AlertAlarm
 */
object AlertAlarm {

    private const val TAG = "AlertAlarm"

    /**
     * Hard ceiling, independent of callers. A missed [stop] would leave an elderly person's
     * phone sounding indefinitely. By ten minutes the chain has reached the barangay.
     */
    private const val MAX_DURATION_MS = 10 * 60 * 1000L

    /** Wait, buzz, wait — repeating from index 0 until cancelled. */
    private val PATTERN = longArrayOf(0L, 800L, 400L)

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    /** Open alerts currently claiming the alarm. Silencing waits until this is empty, so answering one alert doesn't silence another. */
    private val activeAlertIds = mutableSetOf<Int>()

    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable {
        Log.w(TAG, "stopped on the safety ceiling — a caller failed to stop it")
        forceStopAll()
    }

    /** Idempotent: a second alert arriving mid-alarm joins the one already sounding. */
    @Synchronized
    fun start(context: Context, alertId: Int) {
        activeAlertIds += alertId
        if (player != null || vibrator != null) return
        val app = context.applicationContext

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        // Some phones ship no default alarm tone. The ringtone is a better fallback than silence.
        val tone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        if (tone != null) {
            player = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(attributes)
                    setDataSource(app, tone)
                    isLooping = true
                    prepare()
                    start()
                }
            }.onFailure { Log.w(TAG, "alarm tone failed to start", it) }.getOrNull()
        }

        // Started even if the tone failed: a senior with reduced hearing may get nothing else.
        vibratorOf(app)?.let { v ->
            val started = runCatching {
                @Suppress("DEPRECATION")
                v.vibrate(VibrationEffect.createWaveform(PATTERN, 0), attributes)
            }.onFailure { Log.w(TAG, "vibration failed to start", it) }.isSuccess
            if (started) vibrator = v
        }

        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, MAX_DURATION_MS)
    }

    /**
     * Safe to call when nothing is sounding, and twice. Only silences once every alert
     * claiming the alarm has stopped.
     */
    @Synchronized
    fun stop(alertId: Int) {
        activeAlertIds -= alertId
        if (activeAlertIds.isEmpty()) stopSound()
    }

    /** The safety ceiling's hard stop (see [MAX_DURATION_MS]). Whatever is left in [activeAlertIds] is presumed stale. */
    @Synchronized
    private fun forceStopAll() {
        activeAlertIds.clear()
        stopSound()
    }

    private fun stopSound() {
        handler.removeCallbacks(autoStop)
        player?.let { p -> runCatching { p.stop(); p.release() } }
        player = null
        vibrator?.let { v -> runCatching { v.cancel() } }
        vibrator = null
    }

    private fun vibratorOf(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}
