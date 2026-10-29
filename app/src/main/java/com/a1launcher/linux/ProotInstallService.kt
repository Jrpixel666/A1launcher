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
import com.a1launcher.model.InstallProgress
import com.a1launcher.model.InstallStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        const val EXTRA_SELECTED_AGENT_BINS = "selected_agent_bins"
        const val EXTRA_OPENROUTER_KEY = "openrouter_key"
        private const val CHANNEL_ID = "linux_setup"
        private const val NOTIFICATION_ID = 42

        private val _progress = MutableStateFlow(InstallProgress())
        val progress: StateFlow<InstallProgress> = _progress.asStateFlow()

        fun start(context: Context, selectedAgentBins: List<String>, openRouterKey: String?) {
            val intent = Intent(context, ProotInstallService::class.java).apply {
                putStringArrayListExtra(EXTRA_SELECTED_AGENT_BINS, ArrayList(selectedAgentBins))
                putExtra(EXTRA_OPENROUTER_KEY, openRouterKey)
            }
            context.startForegroundService(intent)
        }
    }

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private lateinit var proot: Proot
    private lateinit var bootstrap: Bootstrap
    private lateinit var agentStore: AgentStore

    override fun onCreate() {
        super.onCreate()
        proot = Proot(this)
        bootstrap = Bootstrap(this, proot)
        agentStore = AgentStore(proot)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Setting up Linux environment…"))

        val agentBins = intent?.getStringArrayListExtra(EXTRA_SELECTED_AGENT_BINS) ?: arrayListOf()
        val openRouterKey = intent?.getStringExtra(EXTRA_OPENROUTER_KEY)
        val agents = AGENTS.filter { it.bin in agentBins }

        scope.launch { runInstall(agents, openRouterKey) }
        return START_NOT_STICKY
    }

    private suspend fun runInstall(agents: List<Agent>, openRouterKey: String?) {
        val settings = (application as A1LauncherApp).settingsRepository
        try {
            proot.prepare()
            bootstrap.run { step, fraction, log ->
                _progress.value = InstallProgress(step, fraction, log)
                updateNotification(step.toUserLabel())
            }

            agentStore.load()
            for (agent in agents) {
                _progress.value = _progress.value.copy(step = InstallStep.InstallingAgent(agent))
                updateNotification("Installing ${agent.name}…")
                agentStore.install(agent)
                settings.markAgentInstalled(agent.bin)
            }

            if (!openRouterKey.isNullOrBlank()) {
                settings.saveOpenRouterApiKey(openRouterKey)
            }

            settings.markLinuxEnvironmentReady(true)
            _progress.value = InstallProgress(step = InstallStep.Done, fraction = 1f)
            updateNotification("Linux environment ready")
        } catch (t: Throwable) {
            _progress.value = InstallProgress(step = InstallStep.Failed(t.message ?: "Unknown error"))
            updateNotification("Setup failed: ${t.message}")
        } finally {
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private fun InstallStep.toUserLabel(): String = when (this) {
        is InstallStep.Downloading -> "Downloading Arch Linux ARM…"
        is InstallStep.Extracting -> "Extracting rootfs…"
        is InstallStep.Configuring -> "Configuring the guest…"
        is InstallStep.CheckingDevice -> "Checking device compatibility…"
        is InstallStep.InstallingPackages -> "Installing base packages…"
        is InstallStep.CompilingKeymap -> "Compiling the keyboard layout…"
        is InstallStep.InstallingAgent -> "Installing ${agent.name}…"
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
