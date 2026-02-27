package com.kazumaproject.ime_core.plugin

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import com.kazumaproject.ime_core.mvi.KeyActionRaw
import com.kazumaproject.ime_core.mvi.KeyboardAction
import com.kazumaproject.ime_core.plugin.twelvekey.model.KeyGesture
import com.kazumaproject.ime_core.plugin.twelvekey.model.KeyOutput
import com.kazumaproject.ime_core.plugin.twelvekey.model.KeySpec
import com.kazumaproject.ime_core.plugin.twelvekey.ui.FlickGuideController
import com.kazumaproject.ime_core.plugin.twelvekey.ui.FlickGuideSpec
import kotlin.math.roundToInt

open class TwelveKeyKeyboardPlugin : ImeViewPlugin, ActionBindablePlugin,
    OverlayHostBindablePlugin {

    private var dispatch: ((KeyboardAction) -> Unit)? = null

    override fun bind(dispatch: (KeyboardAction) -> Unit) {
        this.dispatch = dispatch
    }

    // ✅ Provided from BaseImeService (ImeResizableView.root). If null, fallback to plugin host.
    private var externalOverlayHost: FrameLayout? = null

    // ✅ Keep one controller per plugin instance.
    private var overlayController: FlickGuideController? = null

    override fun bindOverlayHost(overlayHost: FrameLayout) {
        externalOverlayHost = overlayHost
        // controller will be created lazily in createView() when context is ready
    }

    enum class LayoutMode { VERTICAL, HORIZONTAL }
    enum class ActionPlacement { LEFT, RIGHT, TOP, BOTTOM, LEFT_RIGHT }
    enum class ActionSide { LEFT, RIGHT, TOP, BOTTOM }

    data class ActionButtonSpec(
        val label: String,
        val action: KeyboardAction,
        val widthWeight: Float = 1f,
        val heightDp: Int = 44
    )

    protected open fun placementFor(mode: LayoutMode): ActionPlacement {
        return if (mode == LayoutMode.HORIZONTAL) ActionPlacement.LEFT_RIGHT else ActionPlacement.RIGHT
    }

    protected open fun actionButtonsFor(
        side: ActionSide,
        mode: LayoutMode
    ): List<ActionButtonSpec> {
        return when (placementFor(mode)) {
            ActionPlacement.RIGHT -> if (side == ActionSide.RIGHT) listOf(
                specCursorLeft(), specCursorRight(), specSpace(), specBackspace(), specEnter()
            ) else emptyList()

            ActionPlacement.LEFT -> if (side == ActionSide.LEFT) listOf(
                specCursorLeft(), specCursorRight(), specSpace(), specBackspace(), specEnter()
            ) else emptyList()

            ActionPlacement.TOP -> if (side == ActionSide.TOP) listOf(
                specCursorLeft(widthWeight = 0.9f),
                specCursorRight(widthWeight = 0.9f),
                specSpace(widthWeight = 2.0f),
                specBackspace(widthWeight = 1.0f),
                specEnter(widthWeight = 1.1f),
            ) else emptyList()

            ActionPlacement.BOTTOM -> if (side == ActionSide.BOTTOM) listOf(
                specCursorLeft(widthWeight = 0.9f),
                specCursorRight(widthWeight = 0.9f),
                specSpace(widthWeight = 2.0f),
                specBackspace(widthWeight = 1.0f),
                specEnter(widthWeight = 1.1f),
            ) else emptyList()

            ActionPlacement.LEFT_RIGHT -> when (side) {
                ActionSide.LEFT -> listOf(specCursorLeft(), specCursorRight())
                ActionSide.RIGHT -> listOf(specSpace(), specBackspace(), specEnter())
                else -> emptyList()
            }
        }
    }

    override fun createView(context: Context): View {
        val mode = currentLayoutMode(context)

        // plugin's own host (returned view)
        val pluginHost = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ✅ Use external root host if provided; otherwise fallback to pluginHost
        val overlayHost = externalOverlayHost ?: pluginHost

        // ✅ Create controller once
        val controller = overlayController ?: FlickGuideController(overlayHost).also {
            overlayController = it
        }

        val contentRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10))
            setBackgroundColor(Color.TRANSPARENT)
            background = keyboardBackground(context)
        }

        val grid = buildMainGrid(context, controller, overlayHost)

        when (val placement = placementFor(mode)) {
            ActionPlacement.TOP -> {
                val topRow =
                    buildActionRowHorizontal(context, actionButtonsFor(ActionSide.TOP, mode))
                contentRoot.addView(topRow)
                contentRoot.addView(
                    grid,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
                        .apply { weight = 1f })
            }

            ActionPlacement.BOTTOM -> {
                contentRoot.addView(
                    grid,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
                        .apply { weight = 1f })
                val bottomRow =
                    buildActionRowHorizontal(context, actionButtonsFor(ActionSide.BOTTOM, mode))
                contentRoot.addView(bottomRow)
            }

            ActionPlacement.LEFT, ActionPlacement.RIGHT, ActionPlacement.LEFT_RIGHT -> {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                if (placement == ActionPlacement.LEFT || placement == ActionPlacement.LEFT_RIGHT) {
                    val leftCol =
                        buildActionColumnVertical(context, actionButtonsFor(ActionSide.LEFT, mode))
                    if (leftCol != null) row.addView(leftCol)
                }

                row.addView(
                    grid,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT)
                        .apply { weight = 1f })

                if (placement == ActionPlacement.RIGHT || placement == ActionPlacement.LEFT_RIGHT) {
                    val rightCol =
                        buildActionColumnVertical(context, actionButtonsFor(ActionSide.RIGHT, mode))
                    if (rightCol != null) row.addView(rightCol)
                }

                contentRoot.addView(row)
            }
        }

        pluginHost.addView(contentRoot)
        return pluginHost
    }

    private fun currentLayoutMode(context: Context): LayoutMode {
        val o = context.resources.configuration.orientation
        return if (o == Configuration.ORIENTATION_LANDSCAPE) LayoutMode.HORIZONTAL else LayoutMode.VERTICAL
    }

    private fun buildMainGrid(
        context: Context,
        overlay: FlickGuideController,
        overlayHost: FrameLayout
    ): View {
        val (rows, cols) = gridSize(context)
        val grid = GridLayout(context).apply {
            rowCount = rows
            columnCount = cols
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
            isMotionEventSplittingEnabled = true
        }

        val specs = keySpecs(context)
        specs.forEach { spec ->
            val b = keyButton(context, spec.label)

            // ✅ overlay wiring
            b.guideController = overlay
            b.guideOverlayHost = overlayHost
            b.guideSpec = spec.toGuideSpec()

            b.onGestureResolved = { gesture ->
                val out = spec.outputs[gesture] ?: KeyOutput.Noop
                dispatchKeyOutput(out)
            }

            val lp = GridLayout.LayoutParams().apply {
                rowSpec = GridLayout.spec(spec.row, spec.rowSpan, 1f)
                columnSpec = GridLayout.spec(spec.col, spec.colSpan, 1f)
                width = 0
                height = 0
                setMargins(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4))
            }
            grid.addView(b, lp)
        }

        return grid
    }

    protected open fun gridSize(context: Context): Pair<Int, Int> = 4 to 3
    protected open fun keySpecs(context: Context): List<KeySpec> = emptyList()

    private fun dispatchKeyOutput(out: KeyOutput) {
        when (out) {
            is KeyOutput.Text -> dispatch?.invoke(KeyboardAction.InputText(out.text))
            is KeyOutput.Action -> dispatch?.invoke(out.action)
            is KeyOutput.Raw -> dispatch?.invoke(KeyboardAction.Raw(out.raw))
            KeyOutput.Noop -> dispatch?.invoke(KeyboardAction.Noop)
        }
    }

    private fun buildActionRowHorizontal(context: Context, specs: List<ActionButtonSpec>): View? {
        if (specs.isEmpty()) return null

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
            }
            gravity = Gravity.CENTER_VERTICAL
        }

        specs.forEachIndexed { idx, spec ->
            val btn = actionButton(context, spec.label) { dispatch?.invoke(spec.action) }
            row.addView(
                btn,
                LinearLayout.LayoutParams(0, dp(context, spec.heightDp)).apply {
                    weight = spec.widthWeight
                    if (idx != 0) marginStart = dp(context, 8)
                }
            )
        }
        return row
    }

    private fun buildActionColumnVertical(context: Context, specs: List<ActionButtonSpec>): View? {
        if (specs.isEmpty()) return null

        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
            }
            gravity = Gravity.CENTER_HORIZONTAL
        }

        specs.forEachIndexed { idx, spec ->
            val btn = actionButton(context, spec.label) { dispatch?.invoke(spec.action) }
            col.addView(
                btn,
                LinearLayout.LayoutParams(dp(context, 72), dp(context, spec.heightDp)).apply {
                    if (idx != 0) topMargin = dp(context, 10)
                }
            )
        }
        return col
    }

    protected fun specCursorLeft(widthWeight: Float = 1f) =
        ActionButtonSpec("←", KeyboardAction.MoveCursor(dx = -1), widthWeight = widthWeight)

    protected fun specCursorRight(widthWeight: Float = 1f) =
        ActionButtonSpec("→", KeyboardAction.MoveCursor(dx = +1), widthWeight = widthWeight)

    protected fun specSpace(widthWeight: Float = 1f) =
        ActionButtonSpec("space", KeyboardAction.Space, widthWeight = widthWeight)

    protected fun specBackspace(widthWeight: Float = 1f) =
        ActionButtonSpec("⌫", KeyboardAction.Backspace, widthWeight = widthWeight)

    protected fun specEnter(widthWeight: Float = 1f) =
        ActionButtonSpec("return", KeyboardAction.Enter, widthWeight = widthWeight)

    private fun keyboardBackground(context: Context): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dp(context, 14).toFloat()
            setColor(Color.parseColor("#FF1C1C1E"))
        }
    }

    private fun keyButton(context: Context, text: String): GestureKeyView {
        return GestureKeyView(context).apply {
            // label
            labelText = text
            allowMultiCharCenterLabel = true // ✅ space/return 等もOK

            // styling (旧ボタン相当)
            // ここは元の roundedBg をそのまま使える前提
            keyBackgroundDrawable = roundedBg(
                context,
                Color.parseColor("#FFF2F2F7"),
                Color.parseColor("#1A000000"),
                12
            )

            // center label color
            // （GestureKeyView 側で黒をデフォルトにしてるので、変えるなら API 化も可能）
            // show hints
            showFlickHints = true // ✅ 設定で切り替えたいならここを Pref に

            // 未来の調整ポイント（ユーザー設定に紐づけやすい）
            centerTextMaxSp = 26
            centerTextMinSp = 10
            hintTextMaxSp = 12
            hintTextMinSp = 7
        }
    }

    private fun actionButton(
        context: Context,
        text: String,
        onClick: () -> Unit
    ): android.widget.Button {
        return android.widget.Button(context).apply {
            this.text = text
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background =
                roundedBg(context, Color.parseColor("#FF2C2C2E"), Color.parseColor("#26000000"), 12)
            setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
            setOnClickListener { onClick() }
        }
    }

    private fun roundedBg(
        context: Context,
        fill: Int,
        stroke: Int,
        radiusDp: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dp(context, radiusDp).toFloat()
            setColor(fill)
            setStroke(dp(context, 1), stroke)
        }
    }

    private fun dp(context: Context, v: Int): Int =
        (v * context.resources.displayMetrics.density).roundToInt()
}

/** ---------- guide spec conversion ---------- */

private fun KeySpec.toGuideSpec(): FlickGuideSpec {
    val normal = FlickGuideSpec.Layer(
        center = label,
        up = outputs[KeyGesture.FLICK_UP].toLabelOrNull(),
        right = outputs[KeyGesture.FLICK_RIGHT].toLabelOrNull(),
        down = outputs[KeyGesture.FLICK_DOWN].toLabelOrNull(),
        left = outputs[KeyGesture.FLICK_LEFT].toLabelOrNull(),
    )
    val long = FlickGuideSpec.Layer(
        center = outputs[KeyGesture.LONG_PRESS].toLabelOrNull() ?: label,
        up = outputs[KeyGesture.LONG_PRESS_FLICK_UP].toLabelOrNull(),
        right = outputs[KeyGesture.LONG_PRESS_FLICK_RIGHT].toLabelOrNull(),
        down = outputs[KeyGesture.LONG_PRESS_FLICK_DOWN].toLabelOrNull(),
        left = outputs[KeyGesture.LONG_PRESS_FLICK_LEFT].toLabelOrNull(),
    )
    val hasLong = outputs.keys.any { it.name.startsWith("LONG_PRESS") }
    return FlickGuideSpec(normal = normal, longPress = if (hasLong) long else null)
}

private fun KeyOutput?.toLabelOrNull(): String? {
    return when (this) {
        is KeyOutput.Text -> this.text
        is KeyOutput.Action -> when (val a = this.action) {
            is KeyboardAction.InputText -> a.text
            KeyboardAction.Space -> "␠"
            KeyboardAction.Backspace -> "⌫"
            KeyboardAction.Enter -> "⏎"
            is KeyboardAction.MoveCursor -> if (a.dx < 0) "←" else "→"
            else -> null
        }

        is KeyOutput.Raw -> when (this.raw) {
            is KeyActionRaw.CommitText -> this.raw.text
            is KeyActionRaw.SendKeyCode -> "Key"
            is KeyActionRaw.PerformEditorAction -> "Act"
        }

        KeyOutput.Noop, null -> null
    }
}
