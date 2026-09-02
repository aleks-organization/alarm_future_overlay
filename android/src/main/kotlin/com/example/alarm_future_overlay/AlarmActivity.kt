package com.example.alarm_future_overlay

import android.app.Activity
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmActivity : Activity() {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var originalAlarmVolume: Int = -1
    private var alarmId: Int = 0
    private var alarmTime: Long = 0L
    private var alarmLabel: String = "Alarm"
    private var alarmSound: String? = null

    companion object {
        @Volatile
        private var activeInstance: AlarmActivity? = null

        /// Dismisses the currently visible alarm activity (if any).
        fun dismissActive() {
            val instance = activeInstance ?: return
            instance.runOnUiThread { instance.dismissActivity() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeInstance = this

        alarmId = intent.getIntExtra("alarm_id", 0)
        alarmTime = intent.getLongExtra("alarm_time", 0L)
        alarmLabel = intent.getStringExtra("alarm_label") ?: "Alarm"
        alarmSound = intent.getStringExtra("alarm_sound")

        setupLockScreenDisplay()
        buildUI()
        startAlarmSound()
        startVibration()
    }

    private fun dismissActivity() {
        stopAlarmSound()
        stopVibration()
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(alarmId)
        } catch (_: Exception) {
        }
        finish()
    }

    private fun setupLockScreenDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun buildUI() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(Color.parseColor("#10101E"))
        root.setPadding(dp(24), dp(24), dp(24), dp(24))

        val title = TextView(this)
        title.text = "ALARM"
        title.textSize = 18f
        title.setTextColor(0xCCFFFFFF.toInt())
        title.gravity = Gravity.CENTER
        root.addView(title)

        val timeTv = TextView(this)
        timeTv.text = formatTime(alarmTime)
        timeTv.textSize = 72f
        timeTv.setTextColor(Color.WHITE)
        timeTv.gravity = Gravity.CENTER
        val timeLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        timeLp.topMargin = dp(16)
        timeLp.bottomMargin = dp(16)
        timeTv.layoutParams = timeLp
        root.addView(timeTv)

        val labelTv = TextView(this)
        labelTv.text = alarmLabel
        labelTv.textSize = 20f
        labelTv.setTextColor(0xE6FFFFFF.toInt())
        labelTv.gravity = Gravity.CENTER
        val labelLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        labelLp.bottomMargin = dp(48)
        labelTv.layoutParams = labelLp
        root.addView(labelTv)

        val btnRow = LinearLayout(this)
        btnRow.orientation = LinearLayout.HORIZONTAL
        btnRow.gravity = Gravity.CENTER

        val snoozeBtn = makeButton("SNOOZE", 0xFFFF9800.toInt())
        snoozeBtn.setOnClickListener { snoozeAlarm() }
        val snoozeLp = LinearLayout.LayoutParams(0, dp(64), 1f)
        snoozeLp.marginEnd = dp(12)
        btnRow.addView(snoozeBtn, snoozeLp)

        val dismissBtn = makeButton("DISMISS", 0xFF4CAF50.toInt())
        dismissBtn.setOnClickListener { dismissAlarm() }
        val dismissLp = LinearLayout.LayoutParams(0, dp(64), 1f)
        dismissLp.marginStart = dp(12)
        btnRow.addView(dismissBtn, dismissLp)

        root.addView(
            btnRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)
    }

    private fun makeButton(text: String, color: Int): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 16f
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(color)
                cornerRadius = dp(16).toFloat()
            }
            background = drawable
        }
    }

    private fun startAlarmSound() {
        try {
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            originalAlarmVolume = audioManager?.getStreamVolume(AudioManager.STREAM_ALARM) ?: -1
            if (originalAlarmVolume == 0) {
                val maxVolume =
                    audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 0
                // Alarm must be audible even when the phone is in vibrate/silent mode
                audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
            }

            mediaPlayer = MediaPlayer()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaPlayer?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                mediaPlayer?.setAudioStreamType(AudioManager.STREAM_ALARM)
            }

            val soundFile = alarmSound ?: "over_the_horizon.mp3"
            val afd = assets.openFd(soundFile)
            mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            mediaPlayer?.prepare()
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
        } catch (e: Exception) {
            playSystemAlarm()
        }
    }

    private fun playSystemAlarm() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setAudioStreamType(AudioManager.STREAM_ALARM)
                }
                setDataSource(
                    this@AlarmActivity,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e2: Exception) {
            e2.printStackTrace()
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 1000, 1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun dismissAlarm() {
        stopAlarmSound()
        stopVibration()
        AlarmScheduler.dismiss(this, alarmId)
        notifyFlutter("close")
        finish()
    }

    private fun snoozeAlarm() {
        stopAlarmSound()
        stopVibration()
        AlarmScheduler.snooze(this, alarmId)
        notifyFlutter("snooze")
        finish()
    }

    private fun notifyFlutter(action: String) {
        try {
            AlarmFutureOverlayPlugin.dartEventSink?.success(
                mapOf("action" to action, "id" to alarmId, "time" to alarmTime)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlarmSound() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (_: Exception) {
            }
        }
        mediaPlayer = null
        restoreAlarmVolume()
    }

    private fun restoreAlarmVolume() {
        if (originalAlarmVolume >= 0) {
            try {
                audioManager?.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    originalAlarmVolume,
                    0
                )
            } catch (_: Exception) {
            }
            originalAlarmVolume = -1
        }
    }

    private fun stopVibration() {
        vibrator?.cancel()
    }

    private fun formatTime(timeMillis: Long): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMillis))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        if (activeInstance === this) {
            activeInstance = null
        }
        stopAlarmSound()
        stopVibration()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Back button disabled; user must use Snooze or Dismiss
    }
}
