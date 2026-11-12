package com.example.timer

import android.os.SystemClock

/**
 * Abstração de relógio para garantir precisão monotônica em produção
 * (via SystemClock.elapsedRealtime()) e determinismo total nos testes unitários.
 */
interface ClockProvider {
    fun elapsedRealtime(): Long
    fun currentTimeMillis(): Long
}

class SystemClockProvider : ClockProvider {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}

class FakeClockProvider(
    private var currentElapsedMs: Long = 10_000L,
    private var currentWallMs: Long = 1_700_000_000_000L
) : ClockProvider {
    override fun elapsedRealtime(): Long = currentElapsedMs
    override fun currentTimeMillis(): Long = currentWallMs

    fun advanceBy(deltaMs: Long) {
        require(deltaMs >= 0L) { "O avanço de tempo não pode ser negativo." }
        currentElapsedMs += deltaMs
        currentWallMs += deltaMs
    }

    fun simulateDeviceReboot(newElapsedAfterBootMs: Long = 5_000L, rebootDurationWallMs: Long = 30_000L) {
        currentElapsedMs = newElapsedAfterBootMs
        currentWallMs += rebootDurationWallMs
    }

    fun setElapsedRealtime(value: Long) {
        currentElapsedMs = value
    }
}
