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
import com.a1launcher.model.AgentCli
import com.a1launcher.model.InstallProgress
import com.a1launcher.model.InstallStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Runs the one-time (or re-run-to-add-more) Linux environment bootstrap as a
 * foreground service, so it survives the setup wizard activity being
 * backgrounded. Progress is published on [progress] — a process-local
 * StateFlow is the right amount of plumbing for a single-app, same-process
 * observer (the setup wizard UI); it is not meant to be observed cross-process.
 */
class ProotInstallService : Service() {

    companion object {
        const val EXTRA_SELECTED_AGENT_IDS = "selected_agent_ids"
        const val EXTRA_OPENROUTER_KEY = "openrouter_key"
        private const val CHANNEL_ID = "linux_setup"
        private const val NOTIFICATION_ID = 42

        private val _progress = MutableStateFlow(InstallProgress())
        val progress: StateFlow<InstallProgress> = _progress.asStateFlow()

        fun start(context: Context, selectedAgentIds: List<String>, openRouterKey: String?) {
            val intent = Intent(context, ProotInstallService::class.java).apply {
                putStringArrayListExtra(EXTRA_SELECTED_AGENT_IDS, ArrayList(selectedAgentIds))
                putExtra(EXTRA_OPENROUTER_KEY, openRouterKey)
            }
            context.startForegroundService(intent)
        }
    }

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private lateinit var manager: ProotManager

    override fun onCreate() {
        super.onCreate()
        manager = ProotManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Setting up Linux environment…"))

        val agentIds = intent?.getStringArrayListExtra(EXTRA_SELECTED_AGENT_IDS) ?: arrayListOf()
        val openRouterKey = intent?.getStringExtra(EXTRA_OPENROUTER_KEY)
        val agents = AgentCli.entries.filter { it.id in agentIds }

        scope.launch { runInstall(agents, openRouterKey) }
        return START_NOT_STICKY
    }

    private suspend fun runInstall(agents: List<AgentCli>, openRouterKey: String?) {
        val settings = (application as A1LauncherApp).settingsRepository
        try {
            if (!manager.isReady()) {
                runStep(InstallStep.DownloadingRootfs) { manager.bootstrapArch() }
            }
            runStep(InstallStep.InstallingCompositor) { manager.installSway() }

            for (agent in agents) {
                val key = if (agent == AgentCli.OPENROUTER) openRouterKey else null
                runStep(InstallStep.InstallingAgent(agent)) { manager.installAgent(agent, key) }
                settings.markAgentInstalled(agent)
            }

            settings.markLinuxEnvironmentReady(true)
            _progress.value = InstallProgress(step = InstallStep.Done, percent = 100)
            updateNotification("Linux environment ready")
        } catch (t: Throwable) {
            _progress.value = InstallProgress(step = InstallStep.Failed(t.message ?: "Unknown error"))
            updateNotification("Setup failed: ${t.message}")
        } finally {
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private suspend fun runStep(step: InstallStep, scriptFlow: () -> kotlinx.coroutines.flow.Flow<String>) {
        val tail = mutableListOf<String>()
        _progress.value = _progress.value.copy(step = step)
        updateNotification(step.toUserLabel())
        scriptFlow()
            .catch { throw it }
            .collect { line ->
                tail.add(line)
                if (tail.size > 50) tail.removeAt(0)
                _progress.value = _progress.value.copy(logTail = tail.toList())
            }
    }

    private fun InstallStep.toUserLabel(): String = when (this) {
        is InstallStep.DownloadingRootfs -> "Downloading Arch Linux rootfs…"
        is InstallStep.ExtractingRootfs -> "Extracting rootfs…"
        is InstallStep.ConfiguringPacman -> "Configuring package manager…"
        is InstallStep.InstallingCompositor -> "Installing Sway desktop…"
        is InstallStep.InstallingAgent -> "Installing ${agent.displayName}…"
        is InstallStep.Done -> "Linux environment ready"
        is InstallStep.Failed -> "Setup failed: $message"
        is InstallStep.NotStarted -> "Preparing…"
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "Linux environment setup", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
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
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
