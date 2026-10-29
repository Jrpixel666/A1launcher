package com.a1launcher.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.appwidget.AppWidgetHostView

@Composable
fun HomeScreen(
    appWidgetViews: List<Pair<Int, AppWidgetHostView>>,
    isLinuxReady: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenLinuxDesktop: () -> Unit,
    onAddWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenOfflineAi: () -> Unit,
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenAssistant) {
                Icon(Icons.Filled.SmartToy, contentDescription = "AI agent")
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                WidgetArea(
                    appWidgetViews = appWidgetViews,
                    onAddWidget = onAddWidget,
                    onRemoveWidget = onRemoveWidget,
                    modifier = Modifier.weight(1f),
                )
                Dock(
                    isLinuxReady = isLinuxReady,
                    onOpenDrawer = onOpenDrawer,
                    onOpenLinuxDesktop = onOpenLinuxDesktop,
                    onOpenSettings = onOpenSettings,
                    onOpenOfflineAi = onOpenOfflineAi,
                )
            }
        }
    }
}

@Composable
private fun WidgetArea(
    appWidgetViews: List<Pair<Int, AppWidgetHostView>>,
    onAddWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(appWidgetViews, key = { it.first }) { (widgetId, view) ->
            Box {
                AndroidView(
                    factory = { view },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                )
                Text(
                    text = "✕",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clickable { onRemoveWidget(widgetId) },
                )
            }
        }
        item {
            Button(onClick = onAddWidget, modifier = Modifier.fillMaxWidth()) {
                Text("Add widget")
            }
        }
    }
}

@Composable
private fun Dock(
    isLinuxReady: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenLinuxDesktop: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenOfflineAi: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockButton(icon = Icons.Filled.Apps, label = "Apps", onClick = onOpenDrawer)
        AnimatedVisibility(visible = isLinuxReady) {
            DockButton(icon = Icons.Filled.Dns, label = "Linux", onClick = onOpenLinuxDesktop)
        }
        AnimatedVisibility(visible = isLinuxReady) {
            DockButton(icon = Icons.Filled.Bolt, label = "Offline AI", onClick = onOpenOfflineAi)
        }
        DockButton(icon = Icons.Filled.Settings, label = "Settings", onClick = onOpenSettings)
    }
}

@Composable
private fun DockButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp).clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(32.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}
