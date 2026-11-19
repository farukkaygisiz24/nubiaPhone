package com.nubiaphone.dialer.telecom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeSilencePolicyTest {
    @Test
    fun ringDecrease_silences() {
        assertTrue(VolumeSilencePolicy.shouldSilence(stream = STREAM_RING, current = 10, previous = 15))
    }

    @Test
    fun ringSameIndex_ignores() {
        assertFalse(VolumeSilencePolicy.shouldSilence(stream = STREAM_RING, current = 15, previous = 15))
    }

    @Test
    fun ringIncrease_ignores() {
        assertFalse(VolumeSilencePolicy.shouldSilence(stream = STREAM_RING, current = 15, previous = 10))
    }

    @Test
    fun musicStream_ignores() {
        assertFalse(VolumeSilencePolicy.shouldSilence(stream = STREAM_MUSIC, current = 0, previous = 10))
    }

    @Test
    fun graceNotElapsed_blocks() {
        assertFalse(VolumeSilencePolicy.pastGrace(nowElapsed = 500, armedAtElapsed = 0, graceMs = 1000))
    }

    @Test
    fun graceElapsed_allows() {
        assertTrue(VolumeSilencePolicy.pastGrace(nowElapsed = 1000, armedAtElapsed = 0, graceMs = 1000))
        assertTrue(VolumeSilencePolicy.pastGrace(nowElapsed = 1500, armedAtElapsed = 0, graceMs = 1000))
    }

    @Test
    fun prevMissing_currZero_silences() {
        assertTrue(VolumeSilencePolicy.shouldSilence(stream = STREAM_RING, current = 0, previous = null))
    }

    @Test
    fun prevMissing_currNonZero_withoutKnown_ignores() {
        assertFalse(VolumeSilencePolicy.shouldSilence(stream = STREAM_RING, current = 12, previous = null))
    }

    @Test
    fun prevMissing_knownPriorDecrease_silences() {
        assertTrue(
            VolumeSilencePolicy.shouldSilence(
                stream = STREAM_RING,
                current = 8,
                previous = null,
                knownRingVolume = 15,
            ),
        )
    }

    @Test
    fun nullStream_ignores() {
        assertFalse(VolumeSilencePolicy.shouldSilence(stream = null, current = 0, previous = 15))
    }

    companion object {
        private const val STREAM_RING = 2
        private const val STREAM_MUSIC = 3
    }
}
