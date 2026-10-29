package com.a1launcher.linux

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.a1launcher.A1LauncherApp
import com.a1launcher.R
import com.a1launcher.home.MainActivity
import com.a1launcher.model.LOCAL_MODELS
import com.a1launcher.model.LocalAiProgress
import com.a1launcher.model.LocalAiStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Builds llama.cpp (if needed) and downloads one model, reporting progress on [progress]. */
class LlamaCppInstallService : Service() {

    companion object {
        const val EXTRA_MODEL_ID = "model_id"
        private const val CHANNEL_ID = "local_ai_setup"
        private const val NOTIFICATION_ID = 43

        private val _progress = MutableStateFlow(LocalAiProgress())
        val progress: StateFlow<LocalAiProgress> = _progress.asStateFlow()

        fun start(context: Context, modelId: String) {
            val intent = Intent(context, LlamaCppInstallService::class.java).apply {
                putExtra(EXTRA_MODEL_ID, modelId)
            }
            context.startForegroundService(intent)
        }
    }

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private lateinit var proot: Proot

    override fun onCreate() {
        super.onCreate()
        proot = (application as A1LauncherApp).linuxRuntime.proot
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Setting up offline AI…"))
        val model = LOCAL_MODELS.firstOrNull { it.id == intent?.getStringExtra(EXTRA_MODEL_ID) } ?: LOCAL_MODELS.first()
        scope.launch { runInstall(model) }
        return START_NOT_STICKY
    }

    private suspend fun runInstall(model: com.a1launcher.model.LocalModel) {
        val settings = (application as A1LauncherApp).settingsRepository
        try {
            proot.prepare()
            if (!LlamaCpp.isBuilt(proot)) {
                report(LocalAiStep.InstallingBuildTools)
                report(LocalAiStep.CloningLlamaCpp)
                report(LocalAiStep.Building)
                val code = proot.run(LlamaCpp.buildScript()) { line -> report(LocalAiStep.Building, line = line) }
                check(code == 0) { "Build failed (exit $code) — see log" }
            }
            if (!LlamaCpp.isModelDownloaded(proot, model)) {
                report(LocalAiStep.DownloadingModel(model.label))
                val code = proot.run(LlamaCpp.downloadModelScript(model)) { line ->
                    report(LocalAiStep.DownloadingModel(model.label), line = line)
                }
                check(code == 0) { "Model download failed (exit $code)" }
            }
            settings.markLocalAiInstalled(model.id)
            _progress.value = LocalAiProgress(step = LocalAiStep.Done, fraction = 1f)
            updateNotification("Offline AI ready")
        } catch (t: Throwable) {
            _progress.value = _progress.value.copy(step = LocalAiStep.Failed(t.message ?: "Unknown error"))
            updateNotification("Offline AI setup failed: ${t.message}")
        } finally {
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private val log = mutableListOf<String>()

    private fun report(step: LocalAiStep, line: String? = null) {
        line?.let { log.add(it); if (log.size > 200) log.removeAt(0) }
        _progress.value = LocalAiProgress(step = step, logTail = log.toList())
        updateNotification(step.toUserLabel())
    }

    private fun LocalAiStep.toUserLabel(): String = when (this) {
        is LocalAiStep.NotStarted -> "Preparing…"
        is LocalAiStep.InstallingBuildTools -> "Installing build tools…"
        is LocalAiStep.CloningLlamaCpp -> "Fetching llama.cpp source…"
        is LocalAiStep.Building -> "Building llama.cpp…"
        is LocalAiStep.DownloadingModel -> "Downloading $modelName…"
        is LocalAiStep.Done -> "Offline AI ready"
        is LocalAiStep.Failed -> "Setup failed: $message"
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Offline AI setup", NotificationManager.IMPORTANCE_LOW))
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
