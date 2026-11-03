package com.garam.hackathon.collector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.util.Log

/**
 * Starts collection after boot. Pleos has not yet approved auto-start, so the release
 * manifest does not declare this receiver; the debug manifest declares it disabled, and
 * it is enabled only for measurement (docs/sdk-notes.md 7.4).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // AAOS also delivers boot to the headless system user, which no driver uses.
        if (isHeadlessSystemUser(context)) {
            Log.i(TAG, "Ignoring boot on the headless system user")
            return
        }
        Log.i(TAG, "Boot completed; starting collection")
        CollectorController.start(context)
    }

    private fun isHeadlessSystemUser(context: Context): Boolean {
        val userManager = context.getSystemService(UserManager::class.java)
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            UserManager.isHeadlessSystemUserMode() &&
            userManager.isSystemUser
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
