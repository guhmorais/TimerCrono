package com.example.data

import com.example.domain.DefaultTimerFactory
import com.example.domain.TimerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TimerPersistenceDataSource {
    val timersFlow: Flow<List<TimerConfig>>
    suspend fun getAllTimersOnce(): List<TimerConfig>
    suspend fun getTimerById(id: Int): TimerConfig?
    suspend fun saveTimer(timer: TimerConfig)
    suspend fun saveAllTimers(timers: List<TimerConfig>)
    suspend fun ensureSeeded(): List<TimerConfig>
    suspend fun resetToDefaults(): List<TimerConfig>
}

class TimerRepository(private val dao: TimerDao) : TimerPersistenceDataSource {

    override val timersFlow: Flow<List<TimerConfig>> = dao.observeAllTimers().map { entities ->
        entities.map { it.toDomain() }
    }

    override suspend fun getAllTimersOnce(): List<TimerConfig> {
        return dao.getAllTimersOnce().map { it.toDomain() }
    }

    override suspend fun getTimerById(id: Int): TimerConfig? {
        return dao.getTimerById(id)?.toDomain()
    }

    override suspend fun saveTimer(timer: TimerConfig) {
        dao.upsert(TimerEntity.fromDomain(timer))
    }

    override suspend fun saveAllTimers(timers: List<TimerConfig>) {
        dao.insertAll(timers.map { TimerEntity.fromDomain(it) })
    }

    override suspend fun ensureSeeded(): List<TimerConfig> {
        val existing = dao.getAllTimersOnce()
        if (existing.size < 10) {
            val defaults = DefaultTimerFactory.createDefaultTimers()
            val existingById = existing.associateBy { it.id }
            val merged = defaults.map { def ->
                existingById[def.id]?.toDomain() ?: def
            }
            dao.insertAll(merged.map { TimerEntity.fromDomain(it) })
            return merged
        }
        return existing.map { it.toDomain() }
    }

    override suspend fun resetToDefaults(): List<TimerConfig> {
        val defaults = DefaultTimerFactory.createDefaultTimers()
        dao.deleteAll()
        dao.insertAll(defaults.map { TimerEntity.fromDomain(it) })
        return defaults
    }
}
