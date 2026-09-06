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
 * Sounds and vibrates for as long as an alert is waiting on an answer.
 *
 * Deliberately not the notification channel's own sound, which plays once and is finished before
 * a senior in another room has stood up. Held in a process-wide object rather than inside the
 * prompt's Activity, so that backgrounding the prompt — or the screen timing out — cannot silence
 * an alert nobody has answered.
 *
 * USAGE_ALARM throughout. This is the one sound the app makes that has to be heard through a
 * phone left face-down across a room, and alarm usage is what carries it past a silenced ringer
 * and past Do Not Disturb's default allowance for alarms.
 *
 * Read the trace with: adb logcat -s AlertAlarm
 */
object AlertAlarm {

    private const val TAG = "AlertAlarm"

    /**
     * Hard ceiling, independent of every caller.
     *
     * A missed [stop] would otherwise leave an elderly person's phone sounding indefinitely with
     * no obvious way to quiet it, which is a worse harm than the missed alert this exists to
     * prevent. By ten minutes the chain has long since reached the barangay and the noise has
     * nobody left to summon.
     */
    private const val MAX_DURATION_MS = 10 * 60 * 1000L

    /** Wait, buzz, wait — repeating from index 0 until cancelled. */
    private val PATTERN = longArrayOf(0L, 800L, 400L)

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    /** Which open alerts are currently claiming the alarm. Silencing must wait until this is
     *  empty -- answering one alert must never silence a different, still-open one. */
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

        // Some handsets ship no default alarm tone at all. The ringtone is the wrong register for
        // this, and a far better outcome than silence.
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

        // Not a nicety. A senior with reduced hearing, or a phone in a pocket or under a blanket,
        // may get nothing else — so this is started even when the tone above failed.
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
     * Safe to call when nothing is sounding, and safe to call twice.
     *
     * Only silences once every alert claiming the alarm has been stopped -- a second, different
     * alert can arrive while the first is still being answered, and closing that first one must
     * not silence the one still genuinely waiting.
     */
    @Synchronized
    fun stop(alertId: Int) {
        activeAlertIds -= alertId
        if (activeAlertIds.isEmpty()) stopSound()
    }

    /** The safety ceiling's hard stop: independent of every caller and every alert this alarm
     *  was ever asked to track, per [MAX_DURATION_MS]'s KDoc. Whatever is left in
     *  [activeAlertIds] at that point is presumed stale rather than trusted. */
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
