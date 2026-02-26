package com.kazumaproject.ime_core.resize

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.kazumaproject.ime_core.R
import kotlin.math.roundToInt

/**
 * Resizeモード用 overlay:
 * - 8ハンドルでリサイズ
 * - 中央の moveHandle（固定サイズ）で移動
 *
 * moveHandle は「ただのハンドル」にするため:
 * - 背景は透明
 * - サイズ固定（48dp）
 * - アイコンは外から設定可能
 */
class ImeResizeOverlay(
    private val host: Context,
    private val root: FrameLayout,
    private val content: FrameLayout,
    private val onResize: (widthPx: Int, heightPx: Int) -> Unit,
    private val onMoveDelta: (dxPx: Int, dyPx: Int) -> Unit,
    private val onCommit: () -> Unit
) {
    private var enabled = false
    private val handles = mutableListOf<View>()

    // ==== Move handle (small, fixed size, icon configurable) ====
    private val moveHandleSizePx = dp(48)

    private val moveHandle: ImageView = ImageView(host).apply {
        // 背景を塗らない（＝埋め尽くさない）
        setBackgroundColor(Color.TRANSPARENT)

        // デフォルトはアイコンなし（ユーザーが設定する）
        setImageDrawable(null)

        // 触りやすい固定サイズのハンドル
        layoutParams = FrameLayout.LayoutParams(moveHandleSizePx, moveHandleSizePx)

        // 中に収める表示
        scaleType = ImageView.ScaleType.CENTER_INSIDE

        // 少しだけ内側余白（アイコンが端に当たらないように）
        setPadding(dp(10), dp(10), dp(10), dp(10))

        elevation = dp(4).toFloat()
    }

    /** 外部から移動ハンドルのアイコンを設定したい場合に使う */
    fun setMoveHandleIcon(drawable: Drawable?) {
        moveHandle.setImageDrawable(drawable)
    }

    /** 外部から移動ハンドルのアイコンを resId で設定したい場合に使う */
    @SuppressLint("UseCompatLoadingForDrawables")
    fun setMoveHandleIconRes(resId: Int?) {
        if (resId == null) {
            moveHandle.setImageDrawable(
                content.resources.getDrawable(
                    R.drawable.arrows_output_24px,
                    null
                )
            )
        } else {
            moveHandle.setImageResource(resId)
        }
    }

    private enum class HandleType {
        TOP_LEFT, TOP, TOP_RIGHT,
        LEFT, RIGHT,
        BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT
    }

    init {
        createHandles()

        root.addView(moveHandle)
        moveHandle.setOnTouchListener(MoveTouchListener())

        setVisible(false)
        content.post { updateLayout() }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        setVisible(value)
        if (value) updateLayout()
    }

    fun isEnabled(): Boolean = enabled

    /**
     * 8ハンドル + moveHandle を配置し直す。
     * moveHandle は必ず content のど真ん中（固定サイズでセンタリング）
     */
    fun updateLayout() {
        if (!enabled) return

        // content の root 内座標を計算
        val loc = IntArray(2)
        content.getLocationInWindow(loc)
        val contentX = loc[0]
        val contentY = loc[1]

        val rootLoc = IntArray(2)
        root.getLocationInWindow(rootLoc)
        val rootX = rootLoc[0]
        val rootY = rootLoc[1]

        val left = contentX - rootX
        val top = contentY - rootY
        val right = left + content.width
        val bottom = top + content.height

        // --- 8 resize handles ---
        val handleSize = dp(20)
        val half = handleSize / 2
        val inset = half + dp(6) // 画面外に出ないよう内側に寄せる

        val xL = left + inset
        val xC = (left + right) / 2
        val xR = right - inset

        val yT = top + inset
        val yC = (top + bottom) / 2
        val yB = bottom - inset

        fun placeSquare(handle: View, cx: Int, cy: Int) {
            val lp = (handle.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(handleSize, handleSize)
            lp.width = handleSize
            lp.height = handleSize
            lp.leftMargin = cx - half
            lp.topMargin = cy - half
            handle.layoutParams = lp
        }

        handles.forEach { h ->
            when (h.tag as HandleType) {
                HandleType.TOP_LEFT -> placeSquare(h, xL, yT)
                HandleType.TOP -> placeSquare(h, xC, yT)
                HandleType.TOP_RIGHT -> placeSquare(h, xR, yT)
                HandleType.LEFT -> placeSquare(h, xL, yC)
                HandleType.RIGHT -> placeSquare(h, xR, yC)
                HandleType.BOTTOM_LEFT -> placeSquare(h, xL, yB)
                HandleType.BOTTOM -> placeSquare(h, xC, yB)
                HandleType.BOTTOM_RIGHT -> placeSquare(h, xR, yB)
            }
        }

        // --- move handle: fixed-size, centered ---
        val mw = moveHandleSizePx
        val mh = moveHandleSizePx

        val centeredX = left + ((content.width - mw) / 2)
        val centeredY = top + ((content.height - mh) / 2)

        val moveLp = (moveHandle.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(mw, mh)

        moveLp.width = mw
        moveLp.height = mh
        moveLp.leftMargin = centeredX
        moveLp.topMargin = centeredY
        moveHandle.layoutParams = moveLp
    }

    // -------------------
    // handles creation
    // -------------------

    private fun createHandles() {
        handles.clear()

        fun newHandle(type: HandleType): View {
            val v = View(host).apply {
                setBackgroundColor(Color.parseColor("#FFFFD54F")) // amber
                elevation = dp(3).toFloat()
                tag = type
            }
            root.addView(v)
            handles.add(v)
            v.setOnTouchListener(ResizeTouchListener(type))
            return v
        }

        newHandle(HandleType.TOP_LEFT)
        newHandle(HandleType.TOP)
        newHandle(HandleType.TOP_RIGHT)
        newHandle(HandleType.LEFT)
        newHandle(HandleType.RIGHT)
        newHandle(HandleType.BOTTOM_LEFT)
        newHandle(HandleType.BOTTOM)
        newHandle(HandleType.BOTTOM_RIGHT)
    }

    private fun setVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        handles.forEach { it.visibility = v }
        moveHandle.visibility = v
    }

    // -------------------
    // resize drag
    // -------------------

    private inner class ResizeTouchListener(
        private val type: HandleType
    ) : View.OnTouchListener {

        private var startRawX = 0f
        private var startRawY = 0f
        private var startW = 0
        private var startH = 0

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (!enabled) return false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    startW = content.width
                    startH = content.height
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startRawX).roundToInt()
                    val dy = (event.rawY - startRawY).roundToInt()
                    val (newW, newH) = computeNewSize(type, startW, startH, dx, dy)
                    onResize(newW, newH)
                    updateLayout()
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    onCommit()
                    updateLayout()
                    return true
                }
            }
            return false
        }

        private fun computeNewSize(
            type: HandleType,
            w0: Int,
            h0: Int,
            dx: Int,
            dy: Int
        ): Pair<Int, Int> {
            val screenW = host.resources.displayMetrics.widthPixels
            val minW = (screenW * 0.55f).roundToInt()
            val maxW = screenW

            val minH = dp(140)
            val maxH = dp(520)

            var w = w0
            var h = h0

            val affectLeft =
                type in setOf(HandleType.TOP_LEFT, HandleType.LEFT, HandleType.BOTTOM_LEFT)
            val affectRight =
                type in setOf(HandleType.TOP_RIGHT, HandleType.RIGHT, HandleType.BOTTOM_RIGHT)
            val affectTop = type in setOf(HandleType.TOP_LEFT, HandleType.TOP, HandleType.TOP_RIGHT)
            val affectBottom =
                type in setOf(HandleType.BOTTOM_LEFT, HandleType.BOTTOM, HandleType.BOTTOM_RIGHT)

            if (affectRight) w = w0 + dx
            if (affectLeft) w = w0 - dx
            if (affectBottom) h = h0 + dy
            if (affectTop) h = h0 - dy

            w = w.coerceIn(minW, maxW)
            h = h.coerceIn(minH, maxH)
            return w to h
        }
    }

    // -------------------
    // move drag
    // -------------------

    private inner class MoveTouchListener : View.OnTouchListener {
        private var lastRawX = 0f
        private var lastRawY = 0f

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (!enabled) return false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - lastRawX).roundToInt()
                    val dy = (event.rawY - lastRawY).roundToInt()
                    lastRawX = event.rawX
                    lastRawY = event.rawY

                    onMoveDelta(dx, dy)
                    updateLayout()
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    onCommit()
                    updateLayout()
                    return true
                }
            }
            return false
        }
    }

    private fun dp(value: Int): Int {
        return (value * host.resources.displayMetrics.density).roundToInt()
    }
}
