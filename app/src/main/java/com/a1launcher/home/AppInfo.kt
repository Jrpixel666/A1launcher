package com.a1launcher.home

import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable,
) {
    val componentKey: String get() = "$packageName/$activityName"
}
