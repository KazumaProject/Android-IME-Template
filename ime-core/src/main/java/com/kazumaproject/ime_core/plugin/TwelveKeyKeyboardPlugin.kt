package com.kazumaproject.ime_core.plugin

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import com.kazumaproject.ime_core.mvi.KeyboardAction
import kotlin.math.roundToInt

/**
 * iOS風 12 Key Keyboard Plugin（拡張可能版）
 *
 * 要件:
 * - actionRow（操作ボタン領域）を上下左右に配置できる
 * - actionButton をユーザーが任意に配置できる
 * - デフォルト: 右側のみ
 * - Horizontal（横画面想定）では左右に出せる（LEFT_RIGHT）
 *
 * 使い方（例）:
 * class MyPlugin : TwelveKeyKeyboardPlugin() {
 *   override fun placementFor(mode: LayoutMode): ActionPlacement =
 *      if (mode == LayoutMode.HORIZONTAL) ActionPlacement.LEFT_RIGHT else ActionPlacement.BOTTOM
 *
 *   override fun actionButtonsFor(side: ActionSide, mode: LayoutMode): List<ActionButtonSpec> =
 *      when (side) {
 *        ActionSide.LEFT -> listOf(specCursorLeft(), specCursorRight())
 *        ActionSide.RIGHT -> listOf(specBackspace(), specEnter())
 *        else -> emptyList()
 *      }
 * }
 */
open class TwelveKeyKeyboardPlugin : ImeViewPlugin, ActionBindablePlugin {

    private var dispatch: ((KeyboardAction) -> Unit)? = null

    override fun bind(dispatch: (KeyboardAction) -> Unit) {
        this.dispatch = dispatch
    }

    // -------------------------
    // Extensibility points
    // -------------------------

    enum class LayoutMode { VERTICAL, HORIZONTAL } // VERTICAL=portrait想定, HORIZONTAL=landscape想定
    enum class ActionPlacement { LEFT, RIGHT, TOP, BOTTOM, LEFT_RIGHT }
    enum class ActionSide { LEFT, RIGHT, TOP, BOTTOM }

    data class ActionButtonSpec(
        val label: String,
        val action: KeyboardAction,
        val widthWeight: Float = 1f,  // 横並び時の幅
        val heightDp: Int = 44        // 縦並び時も含めたボタンの高さ
    )

    /**
     * action領域の配置を決める
     * デフォルト:
     * - 縦画面: RIGHT
     * - 横画面: LEFT_RIGHT
     */
    protected open fun placementFor(mode: LayoutMode): ActionPlacement {
        return if (mode == LayoutMode.HORIZONTAL) ActionPlacement.LEFT_RIGHT else ActionPlacement.RIGHT
    }

    /**
     * サイドごとの action ボタン定義（ユーザーが任意で入れ替え可能）
     * デフォルト:
     * - RIGHT: ← → space ⌫ return を縦に積む（使いやすい最小）
     * - HORIZONTAL(LEFT_RIGHT): LEFTに←→、RIGHTにspace/⌫/return
     *
     * ※ TOP/BOTTOM を使う場合もここを override して返す
     */
    protected open fun actionButtonsFor(
        side: ActionSide,
        mode: LayoutMode
    ): List<ActionButtonSpec> {
        return when (placementFor(mode)) {
            ActionPlacement.RIGHT -> {
                if (side == ActionSide.RIGHT) {
                    listOf(
                        specCursorLeft(),
                        specCursorRight(),
                        specSpace(),
                        specBackspace(),
                        specEnter()
                    )
                } else emptyList()
            }

            ActionPlacement.LEFT -> {
                if (side == ActionSide.LEFT) {
                    listOf(
                        specCursorLeft(),
                        specCursorRight(),
                        specSpace(),
                        specBackspace(),
                        specEnter()
                    )
                } else emptyList()
            }

            ActionPlacement.TOP -> {
                if (side == ActionSide.TOP) {
                    listOf(
                        specCursorLeft(widthWeight = 0.9f),
                        specCursorRight(widthWeight = 0.9f),
                        specSpace(widthWeight = 2.0f),
                        specBackspace(widthWeight = 1.0f),
                        specEnter(widthWeight = 1.1f),
                    )
                } else emptyList()
            }

            ActionPlacement.BOTTOM -> {
                if (side == ActionSide.BOTTOM) {
                    listOf(
                        specCursorLeft(widthWeight = 0.9f),
                        specCursorRight(widthWeight = 0.9f),
                        specSpace(widthWeight = 2.0f),
                        specBackspace(widthWeight = 1.0f),
                        specEnter(widthWeight = 1.1f),
                    )
                } else emptyList()
            }

            ActionPlacement.LEFT_RIGHT -> {
                // 横画面想定: 左にカーソル、右に space/⌫/return（例）
                when (side) {
                    ActionSide.LEFT -> listOf(specCursorLeft(), specCursorRight())
                    ActionSide.RIGHT -> listOf(specSpace(), specBackspace(), specEnter())
                    else -> emptyList()
                }
            }
        }
    }

    // -------------------------
    // UI build
    // -------------------------

    override fun createView(context: Context): View {
        val mode = currentLayoutMode(context)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10))
            setBackgroundColor(Color.TRANSPARENT)
            background = keyboardBackground(context)
        }

        val grid = buildMainGrid(context)

        when (val placement = placementFor(mode)) {
            ActionPlacement.TOP -> {
                val topRow =
                    buildActionRowHorizontal(context, actionButtonsFor(ActionSide.TOP, mode))
                root.addView(topRow)
                root.addView(
                    grid,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
                        .apply { weight = 1f })
            }

            ActionPlacement.BOTTOM -> {
                root.addView(
                    grid,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
                        .apply { weight = 1f })
                val bottomRow =
                    buildActionRowHorizontal(context, actionButtonsFor(ActionSide.BOTTOM, mode))
                root.addView(bottomRow)
            }

            ActionPlacement.LEFT, ActionPlacement.RIGHT, ActionPlacement.LEFT_RIGHT -> {
                // 横並び: [LEFT action] [grid] [RIGHT action]
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
                        .apply { weight = 1f }
                )

                if (placement == ActionPlacement.RIGHT || placement == ActionPlacement.LEFT_RIGHT) {
                    val rightCol =
                        buildActionColumnVertical(context, actionButtonsFor(ActionSide.RIGHT, mode))
                    if (rightCol != null) row.addView(rightCol)
                }

                root.addView(row)
            }
        }

        return root
    }

    private fun currentLayoutMode(context: Context): LayoutMode {
        val o = context.resources.configuration.orientation
        return if (o == Configuration.ORIENTATION_LANDSCAPE) LayoutMode.HORIZONTAL else LayoutMode.VERTICAL
    }

    // -------------------------
    // Main grid (12 keys)
    // -------------------------

    private fun buildMainGrid(context: Context): View {
        val grid = GridLayout(context).apply {
            rowCount = 4
            columnCount = 3
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
        }

        val keys: List<Pair<String, String>> = listOf(
            "あ" to "あ", "か" to "か", "さ" to "さ",
            "た" to "た", "な" to "な", "は" to "は",
            "ま" to "ま", "や" to "や", "ら" to "ら",
            "わ" to "わ", "、" to "、", "。" to "。"
        )

        keys.forEachIndexed { idx, (label, commit) ->
            val r = idx / 3
            val c = idx % 3

            val b = keyButton(context, label) {
                dispatch?.invoke(KeyboardAction.InputText(commit))
            }

            val lp = GridLayout.LayoutParams().apply {
                rowSpec = GridLayout.spec(r, 1f)
                columnSpec = GridLayout.spec(c, 1f)
                width = 0
                height = 0
                setMargins(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
            }
            grid.addView(b, lp)
        }

        return grid
    }

    // -------------------------
    // Action area builders
    // -------------------------

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

    // -------------------------
    // Default action specs (overrideしなくても組める)
    // -------------------------

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

    // -------------------------
    // Styling (iOS-ish)
    // -------------------------

    private fun keyboardBackground(context: Context): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dp(context, 14).toFloat()
            setColor(Color.parseColor("#FF1C1C1E")) // iOS dark-ish
        }
    }

    private fun keyButton(context: Context, text: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            this.text = text
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            background = roundedBg(
                context = context,
                fill = Color.parseColor("#FFF2F2F7"),
                stroke = Color.parseColor("#1A000000"),
                radiusDp = 12
            )
            setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6))
            setOnClickListener { onClick() }
        }
    }

    private fun actionButton(context: Context, text: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            this.text = text
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = roundedBg(
                context = context,
                fill = Color.parseColor("#FF2C2C2E"),
                stroke = Color.parseColor("#26000000"),
                radiusDp = 12
            )
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

    private fun dp(context: Context, v: Int): Int {
        return (v * context.resources.displayMetrics.density).roundToInt()
    }
}
