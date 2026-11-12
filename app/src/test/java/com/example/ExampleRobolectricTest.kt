package com.example

import android.app.NotificationManager
import android.content.Context
import android.os.Looper
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.example.domain.DefaultTimerFactory
import com.example.domain.TimerCategory
import com.example.domain.TimerStatus
import com.example.notification.TimerNotificationHelper
import com.example.overlay.OverlayGestureController
import com.example.overlay.OverlayWindowManager
import com.example.settings.AppSettings
import com.example.timer.FakeClockProvider
import com.example.timer.TimerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun verifyAppNameAndResources() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Floating Timer", context.getString(R.string.app_name))
    }

    // Cenário 9: Movimentar uma janela sem disparar toque simples ou duplo
    @Test
    fun scenario09_dragOverlayWindow_updatesPositionWithoutTriggeringTap() {
        var singleTapCount = 0
        var doubleTapCount = 0
        var lastX = 50
        var lastY = 100
        var dragCompleted = false

        val controller = OverlayGestureController(
            touchSlopPx = 10,
            doubleTapTimeoutMs = 250L,
            isPositionLockedProvider = { false },
            isLongPressEnabledProvider = { true },
            screenBoundsProvider = { 1080 to 2400 },
            viewSizeProvider = { 200 to 80 },
            initialPositionProvider = { lastX to lastY },
            onSingleTap = { singleTapCount++ },
            onDoubleTap = { doubleTapCount++ },
            onLongPress = {},
            onPositionChanged = { x, y, finished ->
                lastX = x
                lastY = y
                dragCompleted = finished
            }
        )

        controller.handleTouchEvent(MotionEvent.ACTION_DOWN, 100f, 200f)
        controller.handleTouchEvent(MotionEvent.ACTION_MOVE, 250f, 380f)
        controller.handleTouchEvent(MotionEvent.ACTION_UP, 250f, 380f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))

        assertEquals(200, lastX) // 50 + (250 - 100)
        assertEquals(280, lastY) // 100 + (380 - 200)
        assertTrue(dragCompleted)
        assertEquals("Um arrasto nunca deve disparar toque simples", 0, singleTapCount)
        assertEquals("Um arrasto nunca deve disparar toque duplo", 0, doubleTapCount)
    }

    // Cenário 10: Bloquear a movimentação impede arrasto mas mantém toques funcionando
    @Test
    fun scenario10_lockedPosition_preventsWindowMovementAndSeparatesSingleFromDoubleTap() {
        var singleTapCount = 0
        var doubleTapCount = 0
        var posX = 60
        var posY = 120

        val controller = OverlayGestureController(
            touchSlopPx = 10,
            doubleTapTimeoutMs = 250L,
            isPositionLockedProvider = { true }, // Posição bloqueada!
            isLongPressEnabledProvider = { false },
            screenBoundsProvider = { 1080 to 2400 },
            viewSizeProvider = { 200 to 80 },
            initialPositionProvider = { posX to posY },
            onSingleTap = { singleTapCount++ },
            onDoubleTap = { doubleTapCount++ },
            onLongPress = {},
            onPositionChanged = { x, y, _ ->
                posX = x
                posY = y
            }
        )

        // Tentativa de arrasto com posição bloqueada
        controller.handleTouchEvent(MotionEvent.ACTION_DOWN, 100f, 100f)
        controller.handleTouchEvent(MotionEvent.ACTION_MOVE, 300f, 400f)
        controller.handleTouchEvent(MotionEvent.ACTION_UP, 300f, 400f)
        assertEquals(60, posX)
        assertEquals(120, posY)

        // Teste de duplo toque: não deve disparar toque simples!
        controller.handleTouchEvent(MotionEvent.ACTION_DOWN, 100f, 100f)
        controller.handleTouchEvent(MotionEvent.ACTION_UP, 100f, 100f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
        controller.handleTouchEvent(MotionEvent.ACTION_DOWN, 100f, 100f)
        controller.handleTouchEvent(MotionEvent.ACTION_UP, 100f, 100f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350))

        assertEquals("Duplo toque não deve executar toque simples", 0, singleTapCount)
        assertEquals("Duplo toque deve ser contabilizado exatamente 1 vez", 1, doubleTapCount)
    }

    // Cenário 14: Sem permissão de sobreposição, OverlayWindowManager não falha e remove janelas com segurança
    @Test
    fun scenario14_revokedOverlayPermission_handlesGracefullyWithoutCrash() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FakeTimerPersistenceDataSource()
        val clock = FakeClockProvider()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val engine = TimerEngine(repo, clock, scope)
        val overlayManager = OverlayWindowManager(context, engine, scope)

        val enabledTimers = DefaultTimerFactory.createDefaultTimers().map {
            if (it.id == 1) it.copy(isEnabled = true, status = TimerStatus.RUNNING) else it
        }

        // Quando Settings.canDrawOverlays é false por padrão no ambiente JVM sem permissão concedida
        overlayManager.synchronizeOverlays(enabledTimers, AppSettings())
        assertFalse(overlayManager.canDrawOverlays())
    }

    // Cenário 18: Verificar canais e comportamento dos 5 temporizadores por notificação
    @Test
    fun scenario18_notificationTimers_createsChannelsAndPublishesNotifications() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val helper = TimerNotificationHelper(context)
        helper.createNotificationChannels()

        val serviceChannel =
            notificationManager.getNotificationChannel(TimerNotificationHelper.CHANNEL_SERVICE_ID)
        val timersChannel =
            notificationManager.getNotificationChannel(TimerNotificationHelper.CHANNEL_TIMERS_ID)
        val alertsChannel =
            notificationManager.getNotificationChannel(TimerNotificationHelper.CHANNEL_ALERTS_ID)

        assertNotNull(serviceChannel)
        assertNotNull(timersChannel)
        assertNotNull(alertsChannel)

        val repo = FakeTimerPersistenceDataSource()
        val clock = FakeClockProvider(currentElapsedMs = 20_000L)
        val engine = TimerEngine(repo, clock, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        engine.initializeFromStorage()

        // Habilita e inicia o Temporizador de Notificação #1 (ID 101)
        engine.setTimerEnabled(101, enabled = true, autoStart = true)
        clock.advanceBy(5_000L)

        val notifTimers = engine.timersState.value.filter { it.category == TimerCategory.NOTIFICATION }
        assertEquals(5, notifTimers.size)
        val activeNotif = notifTimers.first { it.id == 101 }
        assertTrue(activeNotif.isEnabled)
        assertEquals("00:00:05", engine.getFormattedTime(activeNotif).mainDigits)
    }
}
