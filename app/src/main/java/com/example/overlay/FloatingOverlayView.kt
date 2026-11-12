package com.example.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.domain.TimeFormatted
import com.example.domain.TimerConfig
import com.example.domain.TimerStatus
import kotlin.math.roundToInt

/**
 * View nativa exibida em sobreposição (TYPE_APPLICATION_OVERLAY) pelo WindowManager.
 * Suporta renderização em 7 segmentos ou fonte digital monoespaçada, ajuste de tamanho
 * em tempo real, confirmação de zerar embutida e barra de controles rápida opcional.
 */
@SuppressLint("ViewConstructor")
class FloatingOverlayView(
    context: Context,
    initialConfig: TimerConfig,
    private val onSingleTapAction: () -> Unit,
    private val onConfirmResetAction: () -> Unit,
    private val onToggleLockAction: () -> Unit,
    private val onCloseWindowAction: () -> Unit
) : LinearLayout(context) {

    private var currentConfig: TimerConfig = initialConfig
    private var currentFormatted: TimeFormatted = TimeFormatted(
        displayMillis = 0L,
        mainDigits = "00:00",
        hundredthsDigits = "00",
        isCompleted = false,
        completedCycles = 0
    )

    private var showExpandedControls: Boolean = false
    private var showResetConfirmation: Boolean = false

    private val backgroundDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
    }

    private val headerView: TextView
    private val sevenSegmentView: SevenSegmentOverlayCanvasView
    private val standardTextView: TextView
    private val controlsContainer: LinearLayout
    private val resetConfirmContainer: LinearLayout
    private val lockToggleButton: TextView
    private val playPauseButton: TextView

    private val digitalTypeface: Typeface by lazy {
        runCatching {
            ResourcesCompat.getFont(context, R.font.share_tech_mono)
                ?: Typeface.MONOSPACE
        }.getOrDefault(Typeface.MONOSPACE)
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        background = backgroundDrawable
        elevation = dpToPx(8f).toFloat()

        // Cabeçalho compacto com o nome do cronômetro e indicador de modo/bloqueio
        headerView = TextView(context).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            alpha = 0.85f
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        }
        addView(
            headerView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )

        // Canvas de 7 segmentos para o modo display digital clássico
        sevenSegmentView = SevenSegmentOverlayCanvasView(context)
        addView(
            sevenSegmentView,
            LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(44f))
        )

        // TextView para o modo de fonte tipográfica digital
        standardTextView = TextView(context).apply {
            gravity = Gravity.CENTER
            typeface = digitalTypeface
            includeFontPadding = false
            maxLines = 1
        }
        addView(
            standardTextView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )

        // Barra de confirmação de duplo toque ("Zerar?") embutida na janela flutuante
        resetConfirmContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(dpToPx(4f), dpToPx(4f), dpToPx(4f), dpToPx(2f))

            val label = TextView(context).apply {
                text = "Zerar? "
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(Color.WHITE)
            }
            val yesBtn = createMiniChipButton("Sim", 0xFFEF4444.toInt()) {
                showResetConfirmation = false
                updateVisibilityStates()
                onConfirmResetAction()
            }
            val noBtn = createMiniChipButton("Não", 0xFF475569.toInt()) {
                showResetConfirmation = false
                updateVisibilityStates()
            }
            addView(label)
            addView(yesBtn)
            addView(noBtn)
        }
        addView(
            resetConfirmContainer,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )

        // Barra de controles adicionais ao pressionar e segurar
        controlsContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(dpToPx(2f), dpToPx(4f), dpToPx(2f), dpToPx(2f))
        }

        playPauseButton = createMiniChipButton("⏯", 0xFF0284C7.toInt()) {
            onSingleTapAction()
        }
        val resetBtn = createMiniChipButton("↺", 0xFFD97706.toInt()) {
            onConfirmResetAction()
        }
        lockToggleButton = createMiniChipButton("🔓", 0xFF475569.toInt()) {
            onToggleLockAction()
        }
        val closeBtn = createMiniChipButton("✕", 0xFFE11D48.toInt()) {
            onCloseWindowAction()
        }

        controlsContainer.addView(playPauseButton)
        controlsContainer.addView(resetBtn)
        controlsContainer.addView(lockToggleButton)
        controlsContainer.addView(closeBtn)

        addView(
            controlsContainer,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )

        applyConfiguration(initialConfig, currentFormatted, globalLocked = false)
    }

    fun requestResetWithConfirmation(confirmRequired: Boolean) {
        if (confirmRequired) {
            showResetConfirmation = true
            showExpandedControls = false
            updateVisibilityStates()
        } else {
            showResetConfirmation = false
            updateVisibilityStates()
            onConfirmResetAction()
        }
    }

    fun toggleExpandedControls() {
        showExpandedControls = !showExpandedControls
        if (showExpandedControls) {
            showResetConfirmation = false
        }
        updateVisibilityStates()
    }

    fun applyConfiguration(
        config: TimerConfig,
        formatted: TimeFormatted,
        globalLocked: Boolean
    ) {
        currentConfig = config
        currentFormatted = formatted

        val scale = config.scaleFactor.coerceIn(0.6f, 2.0f)
        val padH = dpToPx(12f * scale)
        val padV = dpToPx(7f * scale)
        setPadding(padH, padV, padH, padV)

        // Cor de fundo dinâmica conforme estado (Ao iniciar vs Ao pausar/Concluído)
        val bgArgb = config.activeBackgroundColor.toInt()
        backgroundDrawable.setColor(bgArgb)
        backgroundDrawable.cornerRadius = dpToPx(14f * scale).toFloat()

        // Borda sutil luminosa quando concluído na contagem regressiva
        if (config.status == TimerStatus.COMPLETED) {
            backgroundDrawable.setStroke(dpToPx(2f), 0xFFFACC15.toInt())
        } else {
            backgroundDrawable.setStroke(dpToPx(1f), 0x33FFFFFF)
        }

        val txtArgb = config.textColor.toInt()
        headerView.setTextColor(txtArgb)
        val lockBadge = if (globalLocked || config.isPositionLocked) " 🔒" else ""
        val modeBadge = if (config.isCountdown) "⏳ " else ""
        headerView.text = "$modeBadge${config.name}$lockBadge"
        headerView.setTextSize(TypedValue.COMPLEX_UNIT_SP, (10f * scale).coerceIn(8f, 16f))

        lockToggleButton.text = if (config.isPositionLocked) "🔒" else "🔓"
        playPauseButton.text = if (config.status == TimerStatus.RUNNING) "⏸" else "▶"

        val baseCharCount = formatted.mainDigits.length + (formatted.hundredthsDigits?.length ?: 0)
        val canvasWidthPx = dpToPx((baseCharCount * 19f + 24f) * scale)
        val canvasHeightPx = dpToPx(40f * scale)

        if (config.useSevenSegmentFont) {
            sevenSegmentView.visibility = View.VISIBLE
            standardTextView.visibility = View.GONE
            val lp = sevenSegmentView.layoutParams
            if (lp.width != canvasWidthPx || lp.height != canvasHeightPx) {
                lp.width = canvasWidthPx
                lp.height = canvasHeightPx
                sevenSegmentView.layoutParams = lp
            }
            sevenSegmentView.updateDisplay(
                mainDigits = formatted.mainDigits,
                hundredthsDigits = formatted.hundredthsDigits,
                textColorInt = txtArgb
            )
        } else {
            sevenSegmentView.visibility = View.GONE
            standardTextView.visibility = View.VISIBLE
            standardTextView.setTextColor(txtArgb)
            standardTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f * scale)
            standardTextView.text = buildSpannableTimeText(
                mainDigits = formatted.mainDigits,
                hundredthsDigits = formatted.hundredthsDigits
            )
        }

        updateVisibilityStates()
    }

    fun updateTimeOnly(formatted: TimeFormatted, statusChanged: Boolean, config: TimerConfig, globalLocked: Boolean) {
        if (statusChanged || config.status != currentConfig.status) {
            applyConfiguration(config, formatted, globalLocked)
            return
        }
        currentFormatted = formatted
        val txtArgb = config.textColor.toInt()
        if (config.useSevenSegmentFont) {
            sevenSegmentView.updateDisplay(
                mainDigits = formatted.mainDigits,
                hundredthsDigits = formatted.hundredthsDigits,
                textColorInt = txtArgb
            )
        } else {
            standardTextView.text = buildSpannableTimeText(
                mainDigits = formatted.mainDigits,
                hundredthsDigits = formatted.hundredthsDigits
            )
        }
    }

    private fun updateVisibilityStates() {
        resetConfirmContainer.visibility = if (showResetConfirmation) View.VISIBLE else View.GONE
        controlsContainer.visibility = if (showExpandedControls && !showResetConfirmation) View.VISIBLE else View.GONE
    }

    private fun buildSpannableTimeText(mainDigits: String, hundredthsDigits: String?): CharSequence {
        if (hundredthsDigits.isNullOrEmpty()) {
            return mainDigits
        }
        val builder = SpannableStringBuilder(mainDigits)
        val startIdx = builder.length
        builder.append(".").append(hundredthsDigits)
        // O campo dos centésimos é proporcionalmente menor (65%) que os minutos e segundos
        builder.setSpan(
            RelativeSizeSpan(0.62f),
            startIdx,
            builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return builder
    }

    private fun createMiniChipButton(label: String, bgColor: Int, onClick: () -> Unit): TextView {
        val chipBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(8f).toFloat()
            setColor(bgColor)
        }
        return TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            background = chipBg
            setPadding(dpToPx(8f), dpToPx(4f), dpToPx(8f), dpToPx(4f))
            val marginLp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = dpToPx(3f)
                marginEnd = dpToPx(3f)
            }
            layoutParams = marginLp
            setOnClickListener { onClick() }
        }
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).roundToInt()
    }

    private class SevenSegmentOverlayCanvasView(context: Context) : View(context) {
        private var mainDigits: String = "00:00"
        private var hundredthsDigits: String? = "00"
        private var textColorInt: Int = Color.WHITE

        fun updateDisplay(mainDigits: String, hundredthsDigits: String?, textColorInt: Int) {
            if (this.mainDigits != mainDigits ||
                this.hundredthsDigits != hundredthsDigits ||
                this.textColorInt != textColorInt
            ) {
                this.mainDigits = mainDigits
                this.hundredthsDigits = hundredthsDigits
                this.textColorInt = textColorInt
                invalidate()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            SevenSegmentDigitDrawer.drawTimeOnCanvas(
                canvas = canvas,
                width = width.toFloat(),
                height = height.toFloat(),
                mainDigits = mainDigits,
                hundredthsDigits = hundredthsDigits,
                textColorInt = textColorInt
            )
        }
    }
}
