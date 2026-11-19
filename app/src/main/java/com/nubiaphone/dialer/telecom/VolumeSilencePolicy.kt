package com.nubiaphone.dialer.telecom

/**
 * Pure rules for "volume key → silence ringer". Keeps Telecom startup volume writes from
 * looking like a user press.
 */
object VolumeSilencePolicy {
    /** Wait out Telecom/OEM ring-start volume events before arming. */
    const val GRACE_MS: Long = 1000L

    /** [android.media.AudioManager.STREAM_RING] — avoid Android dependency in JVM tests. */
    const val STREAM_RING: Int = 2

    fun pastGrace(nowElapsed: Long, armedAtElapsed: Long, graceMs: Long = GRACE_MS): Boolean =
        nowElapsed >= armedAtElapsed + graceMs

    /**
     * @param previous volume before this event, or null when OEM omits EXTRA_PREV
     * @param knownRingVolume last observed STREAM_RING level (Settings path / prev fallback)
     */
    fun shouldSilence(
        stream: Int?,
        current: Int?,
        previous: Int?,
        knownRingVolume: Int? = null,
    ): Boolean {
        if (stream != STREAM_RING) return false
        val curr = current ?: return false
        if (previous != null) return curr < previous
        if (knownRingVolume != null) return curr < knownRingVolume
        return curr == 0
    }
}
