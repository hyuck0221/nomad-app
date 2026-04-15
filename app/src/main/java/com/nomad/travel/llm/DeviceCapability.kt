package com.nomad.travel.llm

import android.app.ActivityManager
import android.content.Context

/**
 * Device hardware facts the app uses to gate model downloads.
 * Total RAM is read once at construction — it never changes at runtime.
 */
class DeviceCapability(context: Context) {

    val totalRamBytes: Long

    init {
        val am = context.applicationContext
            .getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        totalRamBytes = info.totalMem
    }

    /** Hard check: is this device allowed to download & load [entry]? */
    fun isEligible(entry: ModelEntry): Boolean =
        entry.minRamBytes <= 0L || totalRamBytes >= entry.minRamBytes

    /** Soft check: should we show a performance warning for [entry]? */
    fun shouldWarn(entry: ModelEntry): Boolean =
        entry.warnRamBytes > 0L && totalRamBytes < entry.warnRamBytes && isEligible(entry)

    fun totalRamGb(): Double = totalRamBytes / (1024.0 * 1024.0 * 1024.0)
}
