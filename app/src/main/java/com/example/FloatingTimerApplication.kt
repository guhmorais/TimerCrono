package com.example

import android.app.Application
import com.example.data.TimerDatabase
import com.example.data.TimerRepository
import com.example.notification.TimerNotificationHelper
import com.example.settings.SettingsRepository
import com.example.timer.SystemClockProvider
import com.example.timer.TimerEngine

class FloatingTimerApplication : Application() {

    lateinit var database: TimerDatabase
        private set

    lateinit var timerRepository: TimerRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var timerEngine: TimerEngine
        private set

    lateinit var notificationHelper: TimerNotificationHelper
        private set

    override fun onCreate() {
        super.onCreate()
        database = TimerDatabase.getInstance(this)
        timerRepository = TimerRepository(database.timerDao())
        settingsRepository = SettingsRepository(this)
        timerEngine = TimerEngine(
            repository = timerRepository,
            clockProvider = SystemClockProvider()
        )
        notificationHelper = TimerNotificationHelper(this)
        notificationHelper.createNotificationChannels()
    }
}
