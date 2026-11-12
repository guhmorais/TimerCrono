package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.example.domain.TimerCategory
import com.example.domain.TimerConfig
import com.example.domain.TimerStatus
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "timers")
data class TimerEntity(
    @PrimaryKey val id: Int,
    val category: String,
    val name: String,
    val isEnabled: Boolean,
    val status: String,
    val isCountdown: Boolean,
    val countdownDurationMs: Long,
    val vibrateOnFinish: Boolean,
    val autoRepeat: Boolean,
    val notifyOnFinish: Boolean,
    val accumulatedMs: Long,
    val lastStartElapsedRealtime: Long,
    val lastStartWallClockMs: Long,
    val lastCompletedCycleCount: Int,
    val scaleFactor: Float,
    val runningBgColor: Long,
    val pausedBgColor: Long,
    val textColor: Long,
    val showHundredths: Boolean,
    val showHours: Boolean,
    val useSevenSegmentFont: Boolean,
    val windowX: Int,
    val windowY: Int,
    val isPositionLocked: Boolean
) {
    fun toDomain(): TimerConfig = TimerConfig(
        id = id,
        category = runCatching { TimerCategory.valueOf(category) }.getOrDefault(TimerCategory.FLOATING),
        name = name,
        isEnabled = isEnabled,
        status = runCatching { TimerStatus.valueOf(status) }.getOrDefault(TimerStatus.IDLE),
        isCountdown = isCountdown,
        countdownDurationMs = countdownDurationMs,
        vibrateOnFinish = vibrateOnFinish,
        autoRepeat = autoRepeat,
        notifyOnFinish = notifyOnFinish,
        accumulatedMs = accumulatedMs,
        lastStartElapsedRealtime = lastStartElapsedRealtime,
        lastStartWallClockMs = lastStartWallClockMs,
        lastCompletedCycleCount = lastCompletedCycleCount,
        scaleFactor = scaleFactor,
        runningBgColor = runningBgColor,
        pausedBgColor = pausedBgColor,
        textColor = textColor,
        showHundredths = showHundredths,
        showHours = showHours,
        useSevenSegmentFont = useSevenSegmentFont,
        windowX = windowX,
        windowY = windowY,
        isPositionLocked = isPositionLocked
    )

    companion object {
        fun fromDomain(config: TimerConfig): TimerEntity = TimerEntity(
            id = config.id,
            category = config.category.name,
            name = config.name,
            isEnabled = config.isEnabled,
            status = config.status.name,
            isCountdown = config.isCountdown,
            countdownDurationMs = config.countdownDurationMs,
            vibrateOnFinish = config.vibrateOnFinish,
            autoRepeat = config.autoRepeat,
            notifyOnFinish = config.notifyOnFinish,
            accumulatedMs = config.accumulatedMs,
            lastStartElapsedRealtime = config.lastStartElapsedRealtime,
            lastStartWallClockMs = config.lastStartWallClockMs,
            lastCompletedCycleCount = config.lastCompletedCycleCount,
            scaleFactor = config.scaleFactor,
            runningBgColor = config.runningBgColor,
            pausedBgColor = config.pausedBgColor,
            textColor = config.textColor,
            showHundredths = config.showHundredths,
            showHours = config.showHours,
            useSevenSegmentFont = config.useSevenSegmentFont,
            windowX = config.windowX,
            windowY = config.windowY,
            isPositionLocked = config.isPositionLocked
        )
    }
}

@Dao
interface TimerDao {
    @Query("SELECT * FROM timers ORDER BY id ASC")
    fun observeAllTimers(): Flow<List<TimerEntity>>

    @Query("SELECT * FROM timers ORDER BY id ASC")
    suspend fun getAllTimersOnce(): List<TimerEntity>

    @Query("SELECT * FROM timers WHERE id = :id LIMIT 1")
    suspend fun getTimerById(id: Int): TimerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(timers: List<TimerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(timer: TimerEntity)

    @Update
    suspend fun update(timer: TimerEntity)

    @Query("DELETE FROM timers")
    suspend fun deleteAll()
}

@Database(entities = [TimerEntity::class], version = 1, exportSchema = false)
abstract class TimerDatabase : RoomDatabase() {
    abstract fun timerDao(): TimerDao

    companion object {
        @Volatile
        private var INSTANCE: TimerDatabase? = null

        fun getInstance(context: Context): TimerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TimerDatabase::class.java,
                    "floating_timers.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
