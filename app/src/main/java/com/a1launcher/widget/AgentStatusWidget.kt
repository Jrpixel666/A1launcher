package com.a1launcher.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import com.a1launcher.A1LauncherApp
import kotlinx.coroutines.flow.first

/** A1's own home-screen widget: at-a-glance AI agent + Linux desktop status. */
class AgentStatusWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as A1LauncherApp
        val linuxReady = app.settingsRepository.isLinuxEnvironmentReady.first()
        val installedAgents = app.settingsRepository.installedAgentIds.first()

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(android.graphics.Color.parseColor("#1A1A22"))
                    .padding(12.dp),
            ) {
                Text(text = "A1 Agent")
                Text(text = "CLIs installed: ${installedAgents.size}")
                Text(text = if (linuxReady) "Linux desktop: ready" else "Linux desktop: not set up")
            }
        }
    }
}

class AgentStatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgentStatusWidget()
}
