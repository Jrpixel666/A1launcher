package com.a1launcher.home

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

/**
 * Thin wrapper around the platform AppWidgetHost APIs so the launcher's home surface
 * can embed *any* third-party widget, not just A1's own Glance widgets.
 *
 * A launcher is one of the few app categories allowed to host arbitrary widgets this
 * way (BIND_APPWIDGET is granted implicitly to the default HOME app for widgets the
 * user explicitly picks via AppWidgetManager.ACTION_APPWIDGET_PICK / bindAppWidgetIdIfAllowed).
 */
class WidgetHostManager(context: Context) {

    companion object {
        private const val HOST_ID = 0xA1
    }

    private val appContext = context.applicationContext
    private val appWidgetManager = AppWidgetManager.getInstance(appContext)
    private val appWidgetHost = AppWidgetHost(appContext, HOST_ID)

    fun startListening() = appWidgetHost.startListening()
    fun stopListening() = appWidgetHost.stopListening()

    fun allocateWidgetId(): Int = appWidgetHost.allocateAppWidgetId()
    fun deleteWidgetId(widgetId: Int) = appWidgetHost.deleteAppWidgetId(widgetId)

    /** True if the widget can be bound without the system picker UI (rare outside device owners). */
    fun bindWidgetIdIfAllowed(widgetId: Int, provider: AppWidgetProviderInfo): Boolean =
        appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, provider.provider)

    fun createHostView(widgetId: Int, info: AppWidgetProviderInfo): AppWidgetHostView =
        appWidgetHost.createView(appContext, widgetId, info)

    fun getProviderInfo(widgetId: Int): AppWidgetProviderInfo? =
        appWidgetManager.getAppWidgetInfo(widgetId)

    fun installedProviders(): List<AppWidgetProviderInfo> = appWidgetManager.installedProviders
}
