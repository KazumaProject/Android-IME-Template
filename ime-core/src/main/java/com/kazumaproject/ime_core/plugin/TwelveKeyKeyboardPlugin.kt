package com.kazumaproject.ime_core.plugin

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewConfiguration
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

    protected var dispatch: ((KeyboardAction) -> Unit)? = null
        private set

    override fun bind(dispatch: (KeyboardAction) -> Unit) {
        this.dispatch = dispatch
    }

    private var externalOverlayHost: FrameLayout? = null
    private var overlayController: FlickGuideController? = null
    override fun bindOverlayHost(overlayHost: FrameLayout) {
        externalOverlayHost = overlayHost
    }

    private var keyMarginDpOverride: Int? = null
    fun setKeyMarginDpOverride(dp: Int?) {
        keyMarginDpOverride = dp
    }

    protected open fun keyMarginDp(context: Context): Int = 4
    protected open fun gridPaddingDp(context: Context): Int = 8

    enum class LayoutMode { VERTICAL, HORIZONTAL }
    enum class ActionPlacement { LEFT, RIGHT, TOP, BOTTOM, LEFT_RIGHT }
    enum class ActionSide { LEFT, RIGHT, TOP, BOTTOM }

    data class ActionButtonSpec(
        val label: String,
        val outputs: Map<KeyGesture, KeyboardAction>,
        val repeatAction: KeyboardAction? = null,
        val repeatStartDelayMs: Long? = null,
        val repeatIntervalMs: Long? = null,
        val widthWeight: Float = 1f,
        val heightDp: Int = 44
    )

    sealed class ActionColumnItem(open val weight: Float) {
        data class Row(val specs: List<ActionButtonSpec>, override val weight: Float = 1f) :
            ActionColumnItem(weight)

        data class Single(val spec: ActionButtonSpec, override val weight: Float = 1f) :
            ActionColumnItem(weight)

        data class Empty(override val weight: Float = 1f) : ActionColumnItem(weight)
    }

    protected open fun placementFor(mode: LayoutMode): ActionPlacement {
        return if (mode == LayoutMode.HORIZONTAL) ActionPlacement.LEFT_RIGHT else ActionPlacement.RIGHT
    }

    protected open fun actionButtonsFor(
        side: ActionSide,
        mode: LayoutMode
    ): List<ActionButtonSpec> {
        return when (placementFor(mode)) {
            ActionPlacement.RIGHT -> if (side == ActionSide.RIGHT) listOf(
                specCursorLeft(), specCursorRight(), specSpace(), specBackspaceRepeat(), specEnter()
            ) else emptyList()

            ActionPlacement.LEFT -> if (side == ActionSide.LEFT) listOf(
                specCursorLeft(), specCursorRight(), specSpace(), specBackspaceRepeat(), specEnter()
            ) else emptyList()

            ActionPlacement.TOP -> if (side == ActionSide.TOP) listOf(
                specCursorLeft(widthWeight = 0.9f),
                specCursorRight(widthWeight = 0.9f),
                specSpace(widthWeight = 2.0f),
                specBackspaceRepeat(widthWeight = 1.0f),
                specEnter(widthWeight = 1.1f),
            ) else emptyList()

            ActionPlacement.BOTTOM -> if (side == ActionSide.BOTTOM) listOf(
                specCursorLeft(widthWeight = 0.9f),
                specCursorRight(widthWeight = 0.9f),
                specSpace(widthWeight = 2.0f),
                specBackspaceRepeat(widthWeight = 1.0f),
                specEnter(widthWeight = 1.1f),
            ) else emptyList()

            ActionPlacement.LEFT_RIGHT -> when (side) {
                ActionSide.LEFT -> listOf(specCursorLeft(), specCursorRight())
                ActionSide.RIGHT -> listOf(specSpace(), specBackspaceRepeat(), specEnter())
                else -> emptyList()
            }
        }
    }

    protected open fun actionColumnItems(
        side: ActionSide,
        mode: LayoutMode
    ): List<ActionColumnItem> {
        val list = actionButtonsFor(side, mode)
        return list.map { ActionColumnItem.Single(it, weight = 1f) }
    }

    override fun createView(context: Context): View {
        val mode = currentLayoutMode(context)

        val pluginHost = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val overlayHost = externalOverlayHost ?: pluginHost
        val controller =
            overlayController ?: FlickGuideController(overlayHost).also { overlayController = it }

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
                        buildActionColumnWeighted(context, actionColumnItems(ActionSide.LEFT, mode))
                    if (leftCol != null) row.addView(leftCol)
                }

                row.addView(
                    grid,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT)
                        .apply { weight = 1f })

                if (placement == ActionPlacement.RIGHT || placement == ActionPlacement.LEFT_RIGHT) {
                    val rightCol = buildActionColumnWeighted(
                        context,
                        actionColumnItems(ActionSide.RIGHT, mode)
                    )
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
            val pad = dp(context, gridPaddingDp(context))
            setPadding(pad, pad, pad, pad)
            isMotionEventSplittingEnabled = true
        }

        val mDp = keyMarginDpOverride ?: keyMarginDp(context)
        val mPx = dp(context, mDp)

        keySpecs(context).forEach { spec ->
            val b = keyButton(context, spec.label)

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
                setMargins(mPx, mPx, mPx, mPx)
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
            ).apply { setMargins(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8)) }
            gravity = Gravity.CENTER_VERTICAL
        }

        specs.forEachIndexed { idx, spec ->
            val btn = actionButton(context, spec)
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

    private fun buildActionColumnWeighted(context: Context, items: List<ActionColumnItem>): View? {
        if (items.isEmpty()) return null

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                dp(context, 72),
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { setMargins(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8)) }
        }

        items.forEach { item ->
            val rowContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0
                ).apply { weight = item.weight }
            }

            when (item) {
                is ActionColumnItem.Empty -> Unit

                is ActionColumnItem.Single -> {
                    val btn = actionButton(context, item.spec)
                    rowContainer.addView(
                        btn,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                }

                is ActionColumnItem.Row -> {
                    val specs = item.specs.take(2)
                    if (specs.size == 2) {
                        specs.forEachIndexed { i, spec ->
                            val btn = actionButton(context, spec)
                            rowContainer.addView(
                                btn,
                                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT)
                                    .apply {
                                        weight = 1f
                                        if (i == 0) marginEnd = dp(context, 3) else marginStart =
                                            dp(context, 3)
                                    }
                            )
                        }
                    } else if (specs.size == 1) {
                        val btn = actionButton(context, specs[0])
                        rowContainer.addView(
                            btn,
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                    }
                }
            }

            root.addView(rowContainer)
        }

        return root
    }

    // ---- Specs: repeat for ⌫, ←, → ----

    protected open fun specCursorLeft(widthWeight: Float = 1f): ActionButtonSpec {
        val act = KeyboardAction.MoveCursor(dx = -1)
        return ActionButtonSpec(
            label = "←",
            outputs = mapOf(KeyGesture.TAP to act),
            repeatAction = act,
            repeatStartDelayMs = 250L,
            repeatIntervalMs = 35L,
            widthWeight = widthWeight
        )
    }

    protected open fun specCursorRight(widthWeight: Float = 1f): ActionButtonSpec {
        val act = KeyboardAction.MoveCursor(dx = +1)
        return ActionButtonSpec(
            label = "→",
            outputs = mapOf(KeyGesture.TAP to act),
            repeatAction = act,
            repeatStartDelayMs = 250L,
            repeatIntervalMs = 35L,
            widthWeight = widthWeight
        )
    }

    protected open fun specSpace(widthWeight: Float = 1f): ActionButtonSpec {
        return ActionButtonSpec(
            label = "space",
            outputs = mapOf(KeyGesture.TAP to KeyboardAction.Space),
            widthWeight = widthWeight
        )
    }

    protected open fun specBackspaceRepeat(widthWeight: Float = 1f): ActionButtonSpec {
        return ActionButtonSpec(
            label = "⌫",
            outputs = mapOf(KeyGesture.TAP to KeyboardAction.Backspace),
            repeatAction = KeyboardAction.Backspace,
            repeatStartDelayMs = ViewConfiguration.getLongPressTimeout().toLong(),
            repeatIntervalMs = 45L,
            widthWeight = widthWeight
        )
    }

    protected open fun specEnter(widthWeight: Float = 1f): ActionButtonSpec {
        return ActionButtonSpec(
            label = "return",
            outputs = mapOf(KeyGesture.TAP to KeyboardAction.Enter),
            widthWeight = widthWeight
        )
    }

    private fun keyboardBackground(context: Context): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dp(context, 14).toFloat()
            setColor(Color.parseColor("#FF1C1C1E"))
        }
    }

    private fun keyButton(context: Context, text: String): GestureKeyView {
        return GestureKeyView(context).apply {
            labelText = text
            allowMultiCharCenterLabel = true
            keyBackgroundDrawable =
                roundedBg(context, Color.parseColor("#FFF2F2F7"), Color.parseColor("#1A000000"), 12)
            showFlickHints = true
            centerTextMaxSp = 26
            centerTextMinSp = 10
            hintTextMaxSp = 12
            hintTextMinSp = 7
        }
    }

    private fun actionButton(context: Context, spec: ActionButtonSpec): ActionGestureButton {
        return ActionGestureButton(context).apply {
            text = spec.label
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background =
                roundedBg(context, Color.parseColor("#FF2C2C2E"), Color.parseColor("#26000000"), 12)
            setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))

            outputs = spec.outputs
            repeatAction = spec.repeatAction
            spec.repeatStartDelayMs?.let { repeatStartDelayMs = it }
            spec.repeatIntervalMs?.let { repeatIntervalMs = it }

            // ✅ IMPORTANT: avoid infinite recursion
            dispatchAction = { act -> this@TwelveKeyKeyboardPlugin.dispatch?.invoke(act) }
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
