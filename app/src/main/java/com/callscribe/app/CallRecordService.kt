package com.callscribe.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallRecordService : Service() {

    companion object {
        const val TAG = "CallRecordService"
        const val ACTION_START_RECORDING = "com.callscribe.app.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.callscribe.app.STOP_RECORDING"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_IS_INCOMING = "extra_is_incoming"
        private const val CHANNEL_ID = "call_recording_channel"
        private const val NOTIFICATION_ID = 1001
        private const val COMPLETED_NOTIFICATION_ID = 1002
    }

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentRecordingFile: File? = null
    private var currentPhoneNumber: String = "Unknown"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                currentPhoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: "Unknown"
                startForeground(NOTIFICATION_ID, buildRecordingNotification("Recording call with $currentPhoneNumber..."))
                startAudioRecording()
            }
            ACTION_STOP_RECORDING -> {
                stopAudioRecording()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startAudioRecording() {
        if (isRecording) return

        try {
            val outputDir = getRecordingsDirectory()
            val timeStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val safePhone = currentPhoneNumber.replace(Regex("[^0-9+]"), "")
            val fileName = "Call_${timeStamp}_${safePhone}.m4a"
            currentRecordingFile = File(outputDir, fileName)

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                // Try VOICE_COMMUNICATION first for best call clarity, fallback to MIC
                try {
                    setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                } catch (e: Exception) {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                }

                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(44100)
                setOutputFile(currentRecordingFile?.absolutePath)

                prepare()
                start()
                isRecording = true
                Log.d(TAG, "Audio recording started successfully: ${currentRecordingFile?.absolutePath}")
            }

        } catch (e: IOException) {
            Log.e(TAG, "Failed to start MediaRecorder", e)
            isRecording = false
            mediaRecorder?.release()
            mediaRecorder = null
        }
    }

    private fun stopAudioRecording() {
        if (!isRecording) return

        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
            Log.d(TAG, "Call recording saved at: ${currentRecordingFile?.absolutePath}")
            notifyRecordingCompleted(currentRecordingFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recorder", e)
        } finally {
            mediaRecorder = null
            isRecording = false
        }
    }

    private fun getRecordingsDirectory(): File {
        val baseDir = getExternalFilesDir(Environment.DIRECTORY_RECORDINGS)
            ?: File(filesDir, "Recordings")
        val callDir = File(baseDir, "CallScribe")
        if (!callDir.exists()) callDir.mkdirs()
        return callDir
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Recording & AI Transcriptions",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status while phone call is being recorded"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildRecordingNotification(content: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CallScribe: Call Recording in Progress")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun notifyRecordingCompleted(file: File?) {
        if (file == null) return
        val openIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("selected_audio_path", file.absolutePath)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Call Recorded: Ready for Organized Text")
            .setContentText("Tap to transcribe and extract summary & action items")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(COMPLETED_NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopAudioRecording()
        super.onDestroy()
    }
}
