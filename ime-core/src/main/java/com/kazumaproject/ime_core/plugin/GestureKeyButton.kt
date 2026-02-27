package com.kazumaproject.ime_core.plugin

import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import com.kazumaproject.ime_core.plugin.twelvekey.model.KeyGesture
import com.kazumaproject.ime_core.plugin.twelvekey.ui.FlickGuideController
import com.kazumaproject.ime_core.plugin.twelvekey.ui.FlickGuideOverlayView
import com.kazumaproject.ime_core.plugin.twelvekey.ui.FlickGuideSpec
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Dynamic-resize friendly key view:
 * - Center label (auto-size)
 * - 4-direction flick hints (auto-size, optional)
 * - Handles tap / flick / long-press gestures (same behavior as old GestureKeyButton)
 *
 * This is future-proof for:
 * - Dynamic key size changes
 * - Dynamic font scaling per key / per user setting
 */
class GestureKeyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    var onGestureResolved: ((KeyGesture) -> Unit)? = null

    var guideSpec: FlickGuideSpec? = null
        set(value) {
            field = value
            applyGuideTexts()
        }

    var guideController: FlickGuideController? = null

    /** Overlay host where FlickGuideOverlayView is attached (ImeResizableView.root recommended). */
    var guideOverlayHost: FrameLayout? = null

    /** Show small flick hints inside the key. */
    var showFlickHints: Boolean = false
        set(value) {
            field = value
            applyGuideTexts()
        }

    /** Optional: show long label as-is; otherwise center label may shrink/ellipsize. */
    var allowMultiCharCenterLabel: Boolean = true

    /** Key label (center). */
    var labelText: String = ""
        set(value) {
            field = value
            applyCenterLabel()
        }

    /** Background drawable for the key */
    var keyBackgroundDrawable: Drawable? = null
        set(value) {
            field = value
            background = value
        }

    /** Center text appearance control (future settings) */
    var centerTextMaxSp: Int = 26
        set(value) {
            field = value; applyAutoSize()
        }
    var centerTextMinSp: Int = 10
        set(value) {
            field = value; applyAutoSize()
        }

    /** Hint text appearance control (future settings) */
    var hintTextMaxSp: Int = 12
        set(value) {
            field = value; applyAutoSize()
        }
    var hintTextMinSp: Int = 7
        set(value) {
            field = value; applyAutoSize()
        }

    var flickThresholdPx: Float? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong()

    private var downX = 0f
    private var downY = 0f
    private var isDown = false
    private var longPressed = false
    private var flickDir: KeyGesture? = null

    private val tmpRect = Rect()

    // --- child views ---

    private val centerLabel = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(0xFF000000.toInt())
        typeface = Typeface.DEFAULT_BOLD
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        // remove font padding for stable vertical centering
        includeFontPadding = false
    }

    private val hintUp = hintTextView(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
    private val hintDown = hintTextView(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
    private val hintLeft = hintTextView(Gravity.CENTER_VERTICAL or Gravity.START)
    private val hintRight = hintTextView(Gravity.CENTER_VERTICAL or Gravity.END)

    init {
        isClickable = true
        isFocusable = false

        addView(
            centerLabel,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.CENTER
            }
        )
        addView(hintUp)
        addView(hintDown)
        addView(hintLeft)
        addView(hintRight)

        applyAutoSize()
        applyCenterLabel()
        applyGuideTexts()
    }

    private fun hintTextView(gravity: Int): TextView {
        return TextView(context).apply {
            this.gravity = Gravity.CENTER
            setTextColor(0x66000000) // translucent black
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            visibility = View.GONE

            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                this.gravity = gravity
            }
        }
    }

    private fun applyAutoSize() {
        // Center
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            centerLabel,
            centerTextMinSp,
            centerTextMaxSp,
            1,
            TypedValue.COMPLEX_UNIT_SP
        )

        // Hints
        val hints = listOf(hintUp, hintRight, hintDown, hintLeft)
        hints.forEach { tv ->
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                tv,
                hintTextMinSp,
                hintTextMaxSp,
                1,
                TypedValue.COMPLEX_UNIT_SP
            )
        }
    }

    private fun applyCenterLabel() {
        centerLabel.text = if (allowMultiCharCenterLabel) labelText else labelText.take(1)
    }

    private fun applyGuideTexts() {
        val layer = guideSpec?.normal

        if (!showFlickHints || layer == null) {
            hintUp.visibility = View.GONE
            hintRight.visibility = View.GONE
            hintDown.visibility = View.GONE
            hintLeft.visibility = View.GONE
            return
        }

        // “長い場合は先頭1文字のみ”
        fun oneCharOrNull(s: String?): String? = s?.takeIf { it.isNotBlank() }?.take(1)

        val u = oneCharOrNull(layer.up)
        val r = oneCharOrNull(layer.right)
        val d = oneCharOrNull(layer.down)
        val l = oneCharOrNull(layer.left)

        hintUp.text = u
        hintRight.text = r
        hintDown.text = d
        hintLeft.text = l

        hintUp.visibility = if (u != null) View.VISIBLE else View.GONE
        hintRight.visibility = if (r != null) View.VISIBLE else View.GONE
        hintDown.visibility = if (d != null) View.VISIBLE else View.GONE
        hintLeft.visibility = if (l != null) View.VISIBLE else View.GONE

        requestLayout()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // Key size changes => padding should scale to avoid collisions (future-proof)
        val shortSide = minOf(w, h).toFloat()
        val pad = (shortSide * 0.12f).roundToInt().coerceIn(dp(6), dp(14))
        val hintPad = (shortSide * 0.08f).roundToInt().coerceIn(dp(4), dp(12))

        // Center has larger safe area; hints sit near edges.
        centerLabel.setPadding(pad, pad, pad, pad)

        (hintUp.layoutParams as LayoutParams).apply {
            topMargin = hintPad
        }
        (hintDown.layoutParams as LayoutParams).apply {
            bottomMargin = hintPad
        }
        (hintLeft.layoutParams as LayoutParams).apply {
            marginStart = hintPad
        }
        (hintRight.layoutParams as LayoutParams).apply {
            marginEnd = hintPad
        }
    }

    // ---- gesture logic (ported from GestureKeyButton) ----

    private val longPressRunnable = Runnable {
        if (!isDown) return@Runnable
        longPressed = true

        guideSpec?.longPress?.let { layer ->
            guideController?.show(anchorRectInOverlayHost(), layer.toGuideTexts())
            guideController?.updatePreview(layer.center)
        }
        guideController?.highlight(KeyGesture.LONG_PRESS)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                isDown = true
                longPressed = false
                flickDir = null
                downX = event.x
                downY = event.y
                isPressed = true

                guideSpec?.normal?.let { layer ->
                    guideController?.show(anchorRectInOverlayHost(), layer.toGuideTexts())
                    guideController?.highlight(KeyGesture.TAP)
                    guideController?.updatePreview(layer.center)
                }

                mainHandler.removeCallbacks(longPressRunnable)
                mainHandler.postDelayed(longPressRunnable, longPressTimeoutMs)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDown) return false
                val dx = event.x - downX
                val dy = event.y - downY

                val threshold = flickThresholdPx ?: defaultFlickThresholdPx()
                val movedEnough = (abs(dx) >= threshold) || (abs(dy) >= threshold)

                val gesture = if (movedEnough) {
                    val dir = resolveDirection(dx, dy)
                    flickDir = dir
                    if (!longPressed) mainHandler.removeCallbacks(longPressRunnable)
                    dir
                } else {
                    flickDir = null
                    if (longPressed) KeyGesture.LONG_PRESS else KeyGesture.TAP
                }

                guideController?.highlight(gesture)

                val layer = if (longPressed) (guideSpec?.longPress
                    ?: guideSpec?.normal) else guideSpec?.normal
                layer?.let {
                    val preview = when (gesture) {
                        KeyGesture.FLICK_UP -> it.up
                        KeyGesture.FLICK_RIGHT -> it.right
                        KeyGesture.FLICK_DOWN -> it.down
                        KeyGesture.FLICK_LEFT -> it.left
                        else -> it.center
                    } ?: it.center
                    guideController?.updatePreview(preview)
                }

                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cleanup()
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!isDown) return false
                mainHandler.removeCallbacks(longPressRunnable)
                isPressed = false
                isDown = false

                val resolved = decideGestureOnUp(longPressed, flickDir)
                onGestureResolved?.invoke(resolved)

                guideController?.hide()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun cleanup() {
        mainHandler.removeCallbacks(longPressRunnable)
        isPressed = false
        isDown = false
        longPressed = false
        flickDir = null
        guideController?.hide()
    }

    private fun decideGestureOnUp(longPressed: Boolean, flickDir: KeyGesture?): KeyGesture {
        return if (longPressed) {
            when (flickDir) {
                KeyGesture.FLICK_UP -> KeyGesture.LONG_PRESS_FLICK_UP
                KeyGesture.FLICK_DOWN -> KeyGesture.LONG_PRESS_FLICK_DOWN
                KeyGesture.FLICK_LEFT -> KeyGesture.LONG_PRESS_FLICK_LEFT
                KeyGesture.FLICK_RIGHT -> KeyGesture.LONG_PRESS_FLICK_RIGHT
                else -> KeyGesture.LONG_PRESS
            }
        } else {
            flickDir ?: KeyGesture.TAP
        }
    }

    private fun resolveDirection(dx: Float, dy: Float): KeyGesture {
        return if (abs(dx) >= abs(dy)) {
            if (dx < 0) KeyGesture.FLICK_LEFT else KeyGesture.FLICK_RIGHT
        } else {
            if (dy < 0) KeyGesture.FLICK_UP else KeyGesture.FLICK_DOWN
        }
    }

    private fun defaultFlickThresholdPx(): Float {
        val dp24 = 24f * context.resources.displayMetrics.density
        return maxOf(touchSlop * 2f, dp24)
    }

    private fun anchorRectInOverlayHost(): Rect {
        val host = guideOverlayHost ?: return Rect()

        val locThis = IntArray(2)
        val locHost = IntArray(2)
        getLocationOnScreen(locThis)
        host.getLocationOnScreen(locHost)

        tmpRect.set(
            locThis[0] - locHost[0],
            locThis[1] - locHost[1],
            locThis[0] - locHost[0] + width,
            locThis[1] - locHost[1] + height
        )
        return tmpRect
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()
}

private fun FlickGuideSpec.Layer.toGuideTexts(): FlickGuideOverlayView.GuideTexts {
    return FlickGuideOverlayView.GuideTexts(
        center = center,
        up = up,
        right = right,
        down = down,
        left = left,
    )
}
