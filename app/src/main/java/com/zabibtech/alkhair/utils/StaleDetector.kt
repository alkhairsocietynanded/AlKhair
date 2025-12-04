package com.zabibtech.alkhair.utils

object StaleDetector {
    private const val STALE_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes

    // ============================================================
    // STALE DETECTION
    // ============================================================
     fun isStale(updatedAt: Long): Boolean {
        return System.currentTimeMillis() - updatedAt > STALE_THRESHOLD_MS
    }
}