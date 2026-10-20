package com.pup.seenior.alerts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
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

    /** The alarm slider is raised to at least this share of its range while an alert sounds. */
    private const val MIN_VOLUME_FRACTION = 0.8f

    /** How long the one-shot family notification sound keeps the raised volume. */
    private const val BOOST_HOLD_MS = 20_000L

    private var player: MediaPlayer? = null
    private var appContext: Context? = null

    /** The senior's own alarm level, set only while we have raised it, so it can be put back. */
    private var volumeToRestore: Int? = null
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
        raiseVolume(app)

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

    /**
     * Raises the alarm slider for a sound that plays once and then ends, such as the family
     * notification. Call it before posting the notification, since the channel sound is
     * played at whatever level the slider holds at that moment. Puts the level back after
     * [BOOST_HOLD_MS] unless a looping alarm is still using it.
     */
    @Synchronized
    fun boostForNotification(context: Context) {
        raiseVolume(context.applicationContext)
        handler.postDelayed({ restoreIfIdle() }, BOOST_HOLD_MS)
    }

    @Synchronized
    private fun restoreIfIdle() {
        if (activeAlertIds.isEmpty() && player == null) restoreVolume()
    }

    /**
     * Android gives an app no way to lock the slider, but it can lift it for the length of an
     * alert. A senior who has turned the alarm down or muted it would otherwise sleep through
     * the one sound meant to reach them. Never lowers a level that is already high enough.
     */
    private fun raiseVolume(app: Context) {
        appContext = app
        val audio = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val target = (max * MIN_VOLUME_FRACTION).toInt().coerceAtLeast(1)
            val current = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            if (current >= target) return
            // Keep the first original level if raised twice in a row, so restore never "restores" a raised one.
            if (volumeToRestore == null) volumeToRestore = current
            audio.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
        }.onFailure { Log.w(TAG, "could not raise the alarm volume", it) }
    }

    private fun restoreVolume() {
        val original = volumeToRestore ?: return
        volumeToRestore = null
        val audio = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, original, 0) }
            .onFailure { Log.w(TAG, "could not restore the alarm volume", it) }
    }

    private fun stopSound() {
        restoreVolume()
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
