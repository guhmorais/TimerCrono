package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.domain.TimeFormatted
import com.example.domain.TimerCategory
import com.example.domain.TimerConfig
import com.example.domain.TimerStatus
import com.example.settings.AppSettings
import com.example.ui.components.ColorSwatchesSummaryRow
import com.example.ui.components.FloatingWindowPreviewBadge
import com.example.ui.components.ResetConfirmationDialog
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    timers: List<TimerConfig>,
    settings: AppSettings,
    selectedTab: TimerCategory,
    uiTickMillis: Long,
    formatTimer: (TimerConfig, Long) -> TimeFormatted,
    onSelectTab: (TimerCategory) -> Unit,
    onToggleEnabled: (TimerConfig, Boolean) -> Unit,
    onToggleStartPause: (Int) -> Unit,
    onResetTimer: (Int) -> Unit,
    onUpdatePosition: (Int, Int, Int) -> Unit,
    onEditTimer: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTutorial: () -> Unit,
    onPauseAll: () -> Unit,
    onResetAll: () -> Unit,
    onDisableAll: () -> Unit,
    onRestoreDefaults: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var canDrawOverlays by remember { mutableStateOf(checkOverlayPermission(context)) }
    var hasNotificationPerm by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPerm = granted
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canDrawOverlays = checkOverlayPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showOptionsMenu by remember { mutableStateOf(false) }
    var timerPendingResetConfirmation by remember { mutableStateOf<TimerConfig?>(null) }

    val displayedTimers = remember(timers, selectedTab) {
        timers.filter { it.category == selectedTab }
    }
    val activeFloatingTimers = remember(timers) {
        timers.filter { it.category == TimerCategory.FLOATING && it.isEnabled }
    }
    val activeNotificationCount = remember(timers) {
        timers.count { it.category == TimerCategory.NOTIFICATION && it.isEnabled }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge
                            )
                            val activeTotal = activeFloatingTimers.size + activeNotificationCount
                            Text(
                                text = if (activeTotal > 0) {
                                    "$activeTotal ativo(s) • Precisão Monotônica"
                                } else {
                                    "5 Flutuantes • 5 por Notificação"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (settings.lockPosition) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Posição Fixada",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Fixo",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.action_settings)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showOptionsMenu = true },
                            modifier = Modifier.testTag("more_options_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.action_more_options)
                            )
                        }

                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_show_tutorial)) },
                                onClick = {
                                    showOptionsMenu = false
                                    onOpenTutorial()
                                },
                                modifier = Modifier.testTag("menu_tutorial")
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_pause_all)) },
                                onClick = {
                                    showOptionsMenu = false
                                    onPauseAll()
                                },
                                modifier = Modifier.testTag("menu_pause_all")
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_reset_all)) },
                                onClick = {
                                    showOptionsMenu = false
                                    onResetAll()
                                },
                                modifier = Modifier.testTag("menu_reset_all")
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_disable_all)) },
                                onClick = {
                                    showOptionsMenu = false
                                    onDisableAll()
                                },
                                modifier = Modifier.testTag("menu_disable_all")
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_restore_defaults)) },
                                onClick = {
                                    showOptionsMenu = false
                                    onRestoreDefaults()
                                },
                                modifier = Modifier.testTag("menu_restore_defaults")
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                windowInsets = WindowInsets.navigationBars
            ) {
                NavigationBarItem(
                    selected = selectedTab == TimerCategory.FLOATING,
                    onClick = { onSelectTab(TimerCategory.FLOATING) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (activeFloatingTimers.isNotEmpty()) {
                                    Badge { Text(activeFloatingTimers.size.toString()) }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (selectedTab == TimerCategory.FLOATING) {
                                    Icons.Default.PictureInPictureAlt
                                } else {
                                    Icons.Outlined.Layers
                                },
                                contentDescription = stringResource(R.string.tab_floating)
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.tab_floating)) },
                    modifier = Modifier.testTag("tab_floating")
                )

                NavigationBarItem(
                    selected = selectedTab == TimerCategory.NOTIFICATION,
                    onClick = { onSelectTab(TimerCategory.NOTIFICATION) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (activeNotificationCount > 0) {
                                    Badge { Text(activeNotificationCount.toString()) }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (selectedTab == TimerCategory.NOTIFICATION) {
                                    Icons.Default.Notifications
                                } else {
                                    Icons.Outlined.Notifications
                                },
                                contentDescription = stringResource(R.string.tab_notifications)
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.tab_notifications)) },
                    modifier = Modifier.testTag("tab_notifications")
                )
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val containerWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
            val containerHeightPx = with(LocalDensity.current) { maxHeight.toPx() }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .align(Alignment.TopCenter)
            ) {
                // Banner orientativo de permissão SYSTEM_ALERT_WINDOW na aba Flutuando
                AnimatedVisibility(visible = selectedTab == TimerCategory.FLOATING && !canDrawOverlays) {
                    PermissionBannerCard(
                        title = stringResource(R.string.overlay_permission_title),
                        description = stringResource(R.string.overlay_permission_desc),
                        buttonLabel = stringResource(R.string.overlay_permission_grant),
                        testTag = "grant_overlay_permission_button",
                        onGrantClick = {
                            requestOverlayPermissionSettings(context)
                        }
                    )
                }

                // Banner orientativo de permissão POST_NOTIFICATIONS na aba Notificações
                AnimatedVisibility(
                    visible = selectedTab == TimerCategory.NOTIFICATION &&
                        !hasNotificationPerm &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ) {
                    PermissionBannerCard(
                        title = stringResource(R.string.notification_permission_title),
                        description = stringResource(R.string.notification_permission_desc),
                        buttonLabel = stringResource(R.string.notification_permission_grant),
                        testTag = "grant_notification_permission_button",
                        onGrantClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("timers_lazy_column"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(
                        items = displayedTimers,
                        key = { it.id }
                    ) { timer ->
                        val formatted = formatTimer(timer, uiTickMillis)
                        TimerCardItem(
                            timer = timer,
                            formatted = formatted,
                            onToggleEnabled = { enabled ->
                                if (timer.category == TimerCategory.NOTIFICATION &&
                                    enabled &&
                                    !hasNotificationPerm &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                onToggleEnabled(timer, enabled)
                            },
                            onToggleStartPause = { onToggleStartPause(timer.id) },
                            onRequestReset = {
                                if (settings.confirmBeforeReset) {
                                    timerPendingResetConfirmation = timer
                                } else {
                                    onResetTimer(timer.id)
                                }
                            },
                            onEdit = { onEditTimer(timer.id) }
                        )
                    }
                }
            }

            // Se a permissão SYSTEM_ALERT_WINDOW ainda não foi habilitada no Android mas o usuário
            // ativou uma janela flutuante, exibimos a janela flutuante interativa dentro do palco
            // do app para que ele possa testar todos os gestos imediatamente (1 toque, 2 toques, arrastar).
            // Quando canDrawOverlays == true, o FloatingTimerService exibe a janela real via WindowManager!
            if (!canDrawOverlays && activeFloatingTimers.isNotEmpty()) {
                activeFloatingTimers.forEach { floatingTimer ->
                    val formatted = formatTimer(floatingTimer, uiTickMillis)
                    InAppFallbackFloatingWindow(
                        timer = floatingTimer,
                        formatted = formatted,
                        isLocked = settings.lockPosition || floatingTimer.isPositionLocked,
                        containerWidthPx = containerWidthPx,
                        containerHeightPx = containerHeightPx,
                        onSingleTap = { onToggleStartPause(floatingTimer.id) },
                        onDoubleTap = {
                            if (settings.confirmBeforeReset) {
                                timerPendingResetConfirmation = floatingTimer
                            } else {
                                onResetTimer(floatingTimer.id)
                            }
                        },
                        onDragFinished = { newX, newY ->
                            onUpdatePosition(floatingTimer.id, newX, newY)
                        }
                    )
                }
            }
        }
    }

    timerPendingResetConfirmation?.let { pending ->
        ResetConfirmationDialog(
            timerName = pending.name,
            onConfirm = { onResetTimer(pending.id) },
            onDismiss = { timerPendingResetConfirmation = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimerCardItem(
    timer: TimerConfig,
    formatted: TimeFormatted,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleStartPause: () -> Unit,
    onRequestReset: () -> Unit,
    onEdit: () -> Unit
) {
    val statusText = when (timer.status) {
        TimerStatus.IDLE -> stringResource(R.string.timer_status_idle)
        TimerStatus.RUNNING -> stringResource(R.string.timer_status_running)
        TimerStatus.PAUSED -> stringResource(R.string.timer_status_paused)
        TimerStatus.COMPLETED -> stringResource(R.string.timer_status_completed)
    }

    val statusDotColor = when (timer.status) {
        TimerStatus.RUNNING -> MaterialTheme.colorScheme.secondary
        TimerStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
        TimerStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        TimerStatus.IDLE -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("timer_card_${timer.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = if (timer.isEnabled) 1.5.dp else 1.dp,
                    color = if (timer.isEnabled) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    },
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Linha superior: Ícone + Nome + Modo + Interruptor de Ativação
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (timer.isEnabled) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (timer.isCountdown) {
                                    Icons.Default.HourglassBottom
                                } else {
                                    Icons.Default.Timer
                                },
                                contentDescription = timer.name,
                                tint = if (timer.isEnabled) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = timer.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusDotColor)
                            )
                            Text(
                                text = buildString {
                                    append(statusText)
                                    append(" • ")
                                    if (timer.isCountdown) {
                                        val mins = timer.countdownDurationMs / 60_000L
                                        val secs = (timer.countdownDurationMs % 60_000L) / 1000L
                                        append(
                                            String.format(
                                                Locale.US,
                                                "Regressivo (%02d:%02d)",
                                                mins,
                                                secs
                                            )
                                        )
                                        if (timer.autoRepeat) append(" ↻")
                                    } else {
                                        append(stringResource(R.string.timer_mode_stopwatch))
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Switch(
                    checked = timer.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.testTag("timer_switch_${timer.id}")
                )
            }

            // Área central: Prévia visual interativa da janela flutuante (suporta 1 toque e 2 toques)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FloatingWindowPreviewBadge(
                        config = timer,
                        formatted = formatted,
                        modifier = Modifier
                            .combinedClickable(
                                onClick = onToggleStartPause,
                                onDoubleClick = onRequestReset
                            )
                            .testTag("timer_preview_${timer.id}")
                    )

                    ColorSwatchesSummaryRow(
                        runningBgColor = timer.runningBgColor,
                        pausedBgColor = timer.pausedBgColor,
                        textColor = timer.textColor
                    )
                }
            }

            // Linha inferior de controles rápidos e acesso à edição completa
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isRunning = timer.status == TimerStatus.RUNNING
                Button(
                    onClick = onToggleStartPause,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("timer_toggle_${timer.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (timer.status) {
                            TimerStatus.RUNNING -> stringResource(R.string.btn_pause)
                            TimerStatus.PAUSED -> stringResource(R.string.btn_resume)
                            else -> stringResource(R.string.btn_start)
                        }
                    )
                }

                FilledTonalButton(
                    onClick = onRequestReset,
                    modifier = Modifier.testTag("timer_reset_${timer.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.btn_reset),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.btn_reset))
                }

                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("timer_edit_${timer.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.btn_edit),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.btn_edit))
                }
            }
        }
    }
}

@Composable
private fun PermissionBannerCard(
    title: String,
    description: String,
    buttonLabel: String,
    testTag: String,
    onGrantClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
            )
            Button(
                onClick = onGrantClick,
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag(testTag)
            ) {
                Text(buttonLabel)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InAppFallbackFloatingWindow(
    timer: TimerConfig,
    formatted: TimeFormatted,
    isLocked: Boolean,
    containerWidthPx: Float,
    containerHeightPx: Float,
    onSingleTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onDragFinished: (Int, Int) -> Unit
) {
    var offsetX by remember(timer.id, timer.windowX) {
        mutableFloatStateOf(timer.windowX.toFloat().coerceIn(0f, (containerWidthPx - 240f).coerceAtLeast(0f)))
    }
    var offsetY by remember(timer.id, timer.windowY) {
        mutableFloatStateOf(timer.windowY.toFloat().coerceIn(0f, (containerHeightPx - 120f).coerceAtLeast(0f)))
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(isLocked, containerWidthPx, containerHeightPx) {
                if (!isLocked) {
                    detectDragGestures(
                        onDragEnd = {
                            onDragFinished(offsetX.roundToInt(), offsetY.roundToInt())
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(
                            0f,
                            (containerWidthPx - 220f).coerceAtLeast(0f)
                        )
                        offsetY = (offsetY + dragAmount.y).coerceIn(
                            0f,
                            (containerHeightPx - 100f).coerceAtLeast(0f)
                        )
                    }
                }
            }
            .combinedClickable(
                onClick = onSingleTap,
                onDoubleClick = onDoubleTap
            )
            .testTag("in_app_floating_window_${timer.id}")
    ) {
        FloatingWindowPreviewBadge(
            config = timer,
            formatted = formatted,
            respectScaleInPreview = true
        )
    }
}

private fun checkOverlayPermission(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}

private fun requestOverlayPermissionSettings(context: Context) {
    runCatching {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
