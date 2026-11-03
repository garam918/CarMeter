package com.garam.hackathon.collector

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The feature flag for starting collection at boot. The system stores it as the enabled
 * state of [BootReceiver], so nothing else needs saving. Off by default; builds without
 * the receiver (release, until Pleos approves auto-start) report it as unavailable.
 */
object BootStartSetting {
    private fun component(context: Context) = ComponentName(context, BootReceiver::class.java)

    fun isAvailable(context: Context): Boolean = try {
        context.packageManager.getReceiverInfo(component(context), PackageManager.MATCH_DISABLED_COMPONENTS)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun isEnabled(context: Context): Boolean {
        if (!isAvailable(context)) return false
        return when (context.packageManager.getComponentEnabledSetting(component(context))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ->
                context.packageManager.getReceiverInfo(component(context), PackageManager.MATCH_DISABLED_COMPONENTS).enabled
            else -> false
        }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        if (!isAvailable(context)) return
        context.packageManager.setComponentEnabledSetting(
            component(context),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
