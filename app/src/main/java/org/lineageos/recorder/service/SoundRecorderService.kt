/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.service

import android.Manifest.permission
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.IBinder.DeathRecipient
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.text.format.DateUtils
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.lineageos.recorder.BuildConfig
import org.lineageos.recorder.ListActivity
import org.lineageos.recorder.R
import org.lineageos.recorder.RecorderActivity
import org.lineageos.recorder.status.UiStatus
import org.lineageos.recorder.task.AddRecordingToContentProviderTask
import org.lineageos.recorder.task.DeleteRecordingTask
import org.lineageos.recorder.task.TaskExecutor
import org.lineageos.recorder.utils.PreferencesManager
import org.lineageos.recorder.utils.RecordIntentHelper
import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoUnit
import java.util.LinkedList
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

class SoundRecorderService : Service() {
    // System services
    private val notificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    private val preferencesManager by lazy {
        PreferencesManager(this)
    }

    private val taskExecutor = TaskExecutor()

    private val lock = Any()

    @GuardedBy("lock")
    private val clients = HashMap<IBinder, RecorderClient>()

    private val handler: Handler = object : Handler(Looper.myLooper()!!) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_REGISTER_CLIENT -> registerClient(
                    RecorderClient.of(
                        msg.replyTo,
                        msg.replyTo.binder
                    )
                )

                MSG_UNREGISTER_CLIENT -> synchronized(lock) {
                    unregisterClientLocked(msg.replyTo.binder)
                }

                else -> super.handleMessage(msg)
            }
        }
    }
    private val messenger = Messenger(handler)
    private var recorder: SoundRecording? = null
    private var recordTag: String? = null
    private var recordPath: Path? = null
    private var amplitudeTimer: Timer? = null
    private var elapsedTimeTimer: Timer? = null
    private var isPaused = false
    private var elapsedTime = 0L

    // Circular recording state
    private var circularRecordingDirectory: String? = null
    private var circularRecordings: LinkedList<Uri>? = null
    private var autoReRecord: Boolean = false

    private val shutdownReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            stopRecording()
        }
    }

    private val startupReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "startupReceiver onReceive called")
            val tag = RecorderActivity.FILE_NAME_FALLBACK // FIXME get tag properly
            val isCircular = preferencesManager.circularRecording
            // FIXME requires SDK 33 if app is in background, see build.gradle.kts for details
            startRecording(tag, isCircular)
        }
    }
    override fun onBind(intent: Intent): IBinder = messenger.binder

    override fun onCreate() {
        Log.i(TAG, "onCreate called")
        super.onCreate()

        ContextCompat.registerReceiver(
            this,
            shutdownReceiver,
            IntentFilter(Intent.ACTION_SHUTDOWN),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        if (notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL) == null) {
            createNotificationChannel()
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy called")

        unregisterReceiver(shutdownReceiver)

        stopTimers()

        unregisterClients()

        taskExecutor.terminate(null)

        super.onDestroy()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = if (intent == null) {
        Log.i(TAG, "onStartCommand called with null Intent, i.e. service auto-restarted, recording " + preferencesManager.isCurrentlyRecording)
        // FIXME: the last unsaved recording from the previous session is lost.
        // recovering it is very difficult because Recorder saves it to a temp file in /sdcard/Android/data or something
        // see https://gitlab.com/LineageOS/issues/android/-/issues/7547

        if (preferencesManager.isCurrentlyRecording) {
            // screen is probably locked, so wait until the user unlocks it via ACTION_USER_PRESENT
            ContextCompat.registerReceiver(
                this,
                startupReceiver,
                IntentFilter(Intent.ACTION_USER_PRESENT),
                ContextCompat.RECEIVER_EXPORTED
            )

            START_STICKY
        } else {
            START_NOT_STICKY
        }
    } else when (intent.action) {
        ACTION_START -> intent.getStringExtra(EXTRA_FILE_NAME)?.let {
            if (startRecording(it, intent.getBooleanExtra(IS_CIRCULAR, false))) {
                START_STICKY
            } else {
                START_NOT_STICKY
            }
        } ?: START_NOT_STICKY

        ACTION_STOP -> if (stopRecording()) START_STICKY else START_NOT_STICKY

        ACTION_PAUSE -> if (pauseRecording()) START_STICKY else START_NOT_STICKY

        ACTION_RESUME -> if (resumeRecording()) START_STICKY else START_NOT_STICKY

        else -> START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        Log.i(TAG, "onTaskRemoved")
    }

    private fun newRecordFileName(tag: String?, circularRecording: Boolean): String {
        val formatter = DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_LOCAL_DATE)
                .appendLiteral(' ')
                .append(DateTimeFormatter.ISO_LOCAL_TIME)
                .toFormatter(Locale.getDefault())
        val now = LocalDateTime.now()
        val fileName = String.format(
            RecorderActivity.FILE_NAME_BASE, tag,
            formatter.format(now.truncatedTo(ChronoUnit.SECONDS)))
        if (circularRecording) {
            if (circularRecordingDirectory == null) {
                circularRecordingDirectory = fileName
                circularRecordings = LinkedList<Uri>()
            }
        }
        return fileName + ".%1\$s"
    }

    private fun startRecording(tag: String?, circularRecording: Boolean): Boolean {
        Log.i(TAG, "startRecording called")
        preferencesManager.isCurrentlyRecording = true

        if (checkSelfPermission(permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Missing permission to record audio")
            return false
        }

        recorder = when (preferencesManager.recordInHighQuality) {
            true -> HighQualityRecorder()
            else -> GoodQualityRecorder(this)
        }
        recordTag = tag
        val fileName = newRecordFileName(tag, circularRecording)

        return recorder?.let { recorder ->
            val optPath = createNewAudioFile(fileName, recorder.fileExtension) ?: run {
                Log.e(TAG, "Failed to prepare output file")
                return@let false
            }

            this.recordPath = optPath
            Log.i(TAG, "recordPath in " + this.recordPath)

            isPaused = false
            elapsedTime = 0
            try {
                recorder.startRecording(optPath)
            } catch (e: IOException) {
                Log.e(TAG, "Error while starting the recorder", e)
                return@let false
            }
            notifyStatus(UiStatus.RECORDING)
            notifyElapsedTime(0)
            startTimers()
            // FIXME requires SDK 33 if app is in background, see build.gradle.kts for details
            startForeground(NOTIFICATION_ID, createRecordingNotification(0), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)

            true
        } ?: false
    }

    private fun stopRecording(): Boolean {
        Log.i(TAG, "stopRecording called")
        preferencesManager.isCurrentlyRecording = false

        val recorder = recorder ?: run {
            Log.e(TAG, "Trying to stop null recorder")
            return false
        }

        if (isPaused) {
            isPaused = false
            recorder.resumeRecording()
        }

        stopTimers()

        val success = recorder.stopRecording()

        return recordPath?.takeIf { success }?.let {
            taskExecutor.runTask(
                AddRecordingToContentProviderTask(
                    contentResolver,
                    it,
                    circularRecordingDirectory,
                    recorder.mimeType
                ), { uri: Uri? -> onRecordCompleted(uri) }
            ) { Log.e(TAG, "Failed to save recording") }

            true
        } ?: run {
            onRecordFailed()
            false
        }
    }

    private fun pauseRecording(): Boolean {
        Log.i(TAG, "pauseRecording called")
        if (isPaused) {
            Log.w(TAG, "Pausing already paused recording")
            return false
        }

        return recorder?.let {
            if (it.pauseRecording()) {
                isPaused = true
                stopTimers()
                notifyCurrentSoundAmplitude(0)
                notifyStatus(UiStatus.PAUSED)
                notificationManager.notify(
                    NOTIFICATION_ID,
                    createRecordingNotification(elapsedTime)
                )

                true
            } else {
                Log.e(TAG, "Failed to pause the recorder")
                false
            }
        } ?: run {
            Log.e(TAG, "Pausing null recorder")
            false
        }
    }

    private fun resumeRecording(): Boolean {
        if (!isPaused) {
            Log.w(TAG, "Resuming non-paused recording")
            return false
        }

        return recorder?.let {
            if (it.resumeRecording()) {
                isPaused = false
                startTimers()
                notifyStatus(UiStatus.RECORDING)
                notificationManager.notify(
                    NOTIFICATION_ID,
                    createRecordingNotification(elapsedTime)
                )

                true
            } else {
                Log.e(TAG, "Failed to resume the recorder")
                false
            }
        } ?: run {
            Log.e(TAG, "Resuming null recorder")
            false
        }
    }

    private fun onRecordCompleted(uri: Uri?) {
        notifyStatus(UiStatus.READY)
        stopForeground(STOP_FOREGROUND_REMOVE)

        uri?.let {
            createShareNotification(it.toString())?.let { shareNotification ->
                notificationManager.notify(NOTIFICATION_ID, shareNotification)
            }

            val tag = recordTag
            recorder = null
            recordTag = null
            if (autoReRecord) {
                autoReRecord = false
                Log.i(TAG, "circular recording save: " + it.toString())
                startRecording(tag, circularRecordingDirectory != null)
                if (circularRecordingDirectory != null) {
                    circularRecordings!!.addLast(it)
                    while (circularRecordings!!.size > preferencesManager.circularRecordingNumber) {
                        val dropUri: Uri = circularRecordings!!.removeFirst()
                        Log.i(TAG, "circular recording drop: " + dropUri.toString())
                        taskExecutor.runTask(
                            DeleteRecordingTask(contentResolver, dropUri)
                        ) {}
                    }
                }
            } else {
                circularRecordingDirectory = null
                circularRecordings = null
            }
        }
    }

    private fun onRecordFailed() {
        notificationManager.cancel(NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        notifyStatus(UiStatus.READY)
    }

    private fun createNewAudioFile(
        fileName: String,
        extension: String
    ): Path? {
        val recordingDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getExternalFilesDir(Environment.DIRECTORY_RECORDINGS)?.toPath()
        } else {
            getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.toPath()
                ?.resolve(LEGACY_MUSIC_DIR)
        } ?: throw Exception("Null external files dir")

        val path = recordingDir.resolve(String.format(fileName, extension))

        if (!Files.exists(recordingDir)) {
            try {
                Files.createDirectories(recordingDir)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to create parent directories for output")
                return null
            }
        }

        return path
    }

    /* Timers */
    private fun startTimers() {
        startElapsedTimeTimer()
        startAmplitudeTimer()
    }

    private fun startElapsedTimeTimer() {
        elapsedTimeTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    val newElapsedTime = ++elapsedTime
                    notifyElapsedTime(newElapsedTime)
                    val notification = createRecordingNotification(newElapsedTime)
                    notificationManager.notify(NOTIFICATION_ID, notification)
                    if (circularRecordingDirectory != null && newElapsedTime >= preferencesManager.circularRecordingPeriod) {
                        stopRecording()
                        autoReRecord = true
                    }
                }
            }, 1000, 1000)
        }
    }

    private fun startAmplitudeTimer() {
        amplitudeTimer = Timer().also {
            it.schedule(object : TimerTask() {
                override fun run() {
                    recorder?.currentAmplitude?.let { currentAmplitude ->
                        notifyCurrentSoundAmplitude(currentAmplitude)
                    }
                }
            }, 0, 350)
        }
    }

    private fun stopTimers() {
        elapsedTimeTimer?.let {
            it.cancel()
            it.purge()
        }
        elapsedTimeTimer = null

        amplitudeTimer?.let {
            it.cancel()
            it.purge()
        }
        amplitudeTimer = null
    }

    /* Clients */
    private fun registerClient(client: RecorderClient) {
        synchronized(lock) {
            if (unregisterClientLocked(client.token)) {
                Log.i(TAG, "Client was already registered, override it.")
            }
            clients.put(client.token, client)
        }
        try {
            client.token.linkToDeath(RecorderClientDeathRecipient(this, client), 0)

            // Notify about the current status
            val currentStatus = if (recorder == null) {
                UiStatus.READY
            } else if (isPaused) {
                UiStatus.PAUSED
            } else {
                UiStatus.RECORDING
            }
            client.send(handler.obtainMessage(MSG_UI_STATUS, currentStatus))

            // Also notify about elapsed time
            if (currentStatus != UiStatus.READY) {
                client.send(handler.obtainMessage(MSG_TIME_ELAPSED, elapsedTime))
            }
        } catch (ignored: RemoteException) {
            // Already gone
        }
    }

    private fun unregisterClients() {
        synchronized(lock) {
            for (client in clients.values) {
                client.deathRecipient?.let {
                    client.token.unlinkToDeath(it, 0)
                }
            }
        }
    }

    @GuardedBy("mLock")
    private fun unregisterClientLocked(token: IBinder): Boolean {
        val client = clients.remove(token) ?: return false
        client.deathRecipient?.let {
            token.unlinkToDeath(it, 0)
        }

        return true
    }

    private fun notifyStatus(newStatus: UiStatus) {
        notifyClients(MSG_UI_STATUS, newStatus)
    }

    private fun notifyCurrentSoundAmplitude(amplitude: Int) {
        notifyClients(MSG_SOUND_AMPLITUDE, amplitude)
    }

    private fun notifyElapsedTime(seconds: Long) {
        notifyClients(MSG_TIME_ELAPSED, seconds)
    }

    private fun notifyClients(what: Int, obj: Any) {
        val clients = synchronized(lock) { this.clients.values }
        for (client in clients) {
            client.send(handler.obtainMessage(what, obj))
        }
    }

    /* Notifications */
    private fun createNotificationChannel() {
        val name = getString(R.string.sound_channel_title)
        val description = getString(R.string.sound_channel_desc)

        val notificationChannel = NotificationChannel(
            NOTIFICATION_CHANNEL, name, NotificationManager.IMPORTANCE_LOW
        ).apply {
            this.description = description
        }

        notificationManager.createNotificationChannel(notificationChannel)
    }

    private fun createRecordingNotification(elapsedTime: Long): Notification {
        val intent = Intent(this, RecorderActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopPIntent = PendingIntent.getService(
            this, 0,
            Intent(this, SoundRecorderService::class.java)
                .setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val duration = DateUtils.formatElapsedTime(elapsedTime)
        val nb = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
            .setOngoing(true)
            .setContentText(getString(R.string.sound_notification_message, duration))
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pi)
        if (isPaused) {
            val resumePIntent = PendingIntent.getService(
                this, 0,
                Intent(this, SoundRecorderService::class.java)
                    .setAction(ACTION_RESUME),
                PendingIntent.FLAG_IMMUTABLE
            )
            nb.setContentTitle(getString(R.string.sound_recording_title_paused))
            nb.addAction(R.drawable.ic_play_arrow, getString(R.string.resume), resumePIntent)
        } else {
            val pausePIntent = PendingIntent.getService(
                this, 0,
                Intent(this, SoundRecorderService::class.java)
                    .setAction(ACTION_PAUSE),
                PendingIntent.FLAG_IMMUTABLE
            )
            var suffix = ""
            if (circularRecordingDirectory != null) {
                val period = preferencesManager.circularRecordingPeriod
                val currentNum = circularRecordings!!.size
                val number = preferencesManager.circularRecordingNumber
                suffix = String.format(" ⏲ %s ↺ %s/%s",
                    DateUtils.formatElapsedTime(period - elapsedTime), currentNum, number)
            }
            nb.setContentTitle(getString(R.string.sound_recording_title_working) + suffix)
            nb.addAction(R.drawable.ic_pause, getString(R.string.pause), pausePIntent)
        }
        nb.addAction(R.drawable.ic_stop, getString(R.string.stop), stopPIntent)
        return nb.build()
    }

    private fun createShareNotification(uri: String): Notification? {
        val fileUri = Uri.parse(uri)

        preferencesManager.lastItemUri = fileUri

        val mimeType = recorder?.mimeType ?: run {
            Log.e(TAG, "No recorder found while creating the share notification")
            return null
        }

        val intent = Intent(this, ListActivity::class.java)

        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val playPIntent = PendingIntent.getActivity(
            this, 0,
            RecordIntentHelper.getOpenIntent(fileUri, mimeType),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sharePIntent = PendingIntent.getActivity(
            this, 0,
            RecordIntentHelper.getShareIntent(fileUri, mimeType),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deletePIntent = PendingIntent.getActivity(
            this, 0,
            RecordIntentHelper.getDeleteIntent(this),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val duration = DateUtils.formatElapsedTime(elapsedTime)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
            .setWhen(System.currentTimeMillis())
            .setContentTitle(getString(R.string.sound_notification_title))
            .setContentText(getString(R.string.sound_notification_message, duration))
            .setSmallIcon(R.drawable.ic_mic)
            .addAction(R.drawable.ic_play_arrow, getString(R.string.play), playPIntent)
            .addAction(R.drawable.ic_share, getString(R.string.share), sharePIntent)
            .addAction(R.drawable.ic_delete, getString(R.string.delete), deletePIntent)
            .setContentIntent(pi)
            .build()
    }

    class RecorderClient private constructor(
        private val messenger: Messenger,
        val token: IBinder
    ) {
        var deathRecipient: DeathRecipient? = null

        fun send(message: Message?) {
            try {
                messenger.send(message)
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to send message", e)
            }
        }

        companion object {
            fun of(messenger: Messenger, token: IBinder) = RecorderClient(messenger, token)
        }
    }

    private class RecorderClientDeathRecipient(
        service: SoundRecorderService,
        private val client: RecorderClient
    ) : DeathRecipient {
        private val serviceRef: WeakReference<SoundRecorderService>

        init {
            serviceRef = WeakReference(service)
            client.deathRecipient = this
        }

        override fun binderDied() {
            val service = serviceRef.get() ?: return
            synchronized(service.lock) { service.unregisterClientLocked(client.token) }
        }
    }

    companion object {
        private const val TAG = "SoundRecorderService"

        const val ACTION_START = "${BuildConfig.APPLICATION_ID}.service.START"
        const val ACTION_STOP = "${BuildConfig.APPLICATION_ID}.service.STOP"
        const val ACTION_PAUSE = "${BuildConfig.APPLICATION_ID}.service.PAUSE"
        const val ACTION_RESUME = "${BuildConfig.APPLICATION_ID}.service.RESUME"

        const val MSG_REGISTER_CLIENT = 0
        const val MSG_UNREGISTER_CLIENT = 1
        const val MSG_UI_STATUS = 2
        const val MSG_SOUND_AMPLITUDE = 3
        const val MSG_TIME_ELAPSED = 4

        const val EXTRA_FILE_NAME = "extra_filename"
        const val IS_CIRCULAR = "is_circular"
        private const val LEGACY_MUSIC_DIR = "Sound records"

        const val NOTIFICATION_ID = 60
        private const val NOTIFICATION_CHANNEL = "soundrecorder_notification_channel"
    }
}
