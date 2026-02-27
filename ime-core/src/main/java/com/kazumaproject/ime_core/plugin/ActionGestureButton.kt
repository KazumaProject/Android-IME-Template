package com.kazumaproject.ime_core.plugin

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.kazumaproject.ime_core.mvi.KeyboardAction
import com.kazumaproject.ime_core.plugin.twelvekey.model.KeyGesture
import kotlin.math.abs

/**
 * Action button with:
 * - Tap / Flick (up/right/down/left)
 * - Long-press action (optional) via outputs[LONG_PRESS]
 * - Repeat while pressed (e.g., backspace, cursor move)
 */
class ActionGestureButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : androidx.appcompat.widget.AppCompatButton(context, attrs) {

    /** Gesture -> action mapping (TAP/FLICK_*/
    var outputs: Map<KeyGesture, KeyboardAction> = emptyMap()

    /** Optional repeat action while pressed (e.g., Backspace, MoveCursor). */
    var repeatAction: KeyboardAction? = null

    /** Repeat starts after this delay (ms). Default: long-press timeout. */
    var repeatStartDelayMs: Long = ViewConfiguration.getLongPressTimeout().toLong()

    /** Repeat interval (ms) once started. */
    var repeatIntervalMs: Long = 45L

    /** Flick threshold (px). If null uses max(touchSlop*2, 24dp). */
    var flickThresholdPx: Float? = null

    /** Dispatcher injected by plugin. */
    var dispatchAction: ((KeyboardAction) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong()

    private var isDown = false
    private var longPressed = false
    private var flickDir: KeyGesture? = null
    private var downX = 0f
    private var downY = 0f

    private var repeatCount = 0

    private val longPressRunnable = Runnable {
        if (!isDown) return@Runnable
        longPressed = true
        outputs[KeyGesture.LONG_PRESS]?.let { dispatchAction?.invoke(it) }
    }

    private val repeatTick = object : Runnable {
        override fun run() {
            if (!isDown) return
            repeatAction?.let {
                repeatCount += 1
                dispatchAction?.invoke(it)
            }
            mainHandler.postDelayed(this, repeatIntervalMs)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                isDown = true
                longPressed = false
                flickDir = null
                repeatCount = 0
                downX = event.x
                downY = event.y
                isPressed = true

                mainHandler.removeCallbacks(longPressRunnable)
                if (outputs.containsKey(KeyGesture.LONG_PRESS)) {
                    mainHandler.postDelayed(longPressRunnable, longPressTimeoutMs)
                }

                mainHandler.removeCallbacks(repeatTick)
                if (repeatAction != null) {
                    mainHandler.postDelayed(repeatTick, repeatStartDelayMs)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDown) return false

                val dx = event.x - downX
                val dy = event.y - downY

                val threshold = flickThresholdPx ?: defaultFlickThresholdPx()
                val movedEnough = (abs(dx) >= threshold) || (abs(dy) >= threshold)

                if (movedEnough) {
                    flickDir = resolveDirection(dx, dy)
                    // flick中はrepeat停止（誤爆防止）
                    mainHandler.removeCallbacks(repeatTick)
                } else {
                    flickDir = null
                }

                // 大きく動いたら長押しキャンセルしたい場合はここで removeCallbacks
                val movedSlop = abs(dx) > touchSlop || abs(dy) > touchSlop
                if (movedSlop) {
                    // mainHandler.removeCallbacks(longPressRunnable)
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
                mainHandler.removeCallbacks(repeatTick)

                isPressed = false
                isDown = false

                val gesture = decideGestureOnUp(longPressed, flickDir)
                val act = outputs[gesture] ?: outputs[KeyGesture.TAP]

                // ✅ repeatが発火してたら release-tap を抑制
                val suppressTap = (repeatCount > 0)
                if (act != null) {
                    if (!(suppressTap && gesture == KeyGesture.TAP)) {
                        dispatchAction?.invoke(act)
                    }
                }

                cleanupAfterUp()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun cleanup() {
        mainHandler.removeCallbacks(longPressRunnable)
        mainHandler.removeCallbacks(repeatTick)
        isPressed = false
        isDown = false
        longPressed = false
        flickDir = null
        repeatCount = 0
    }

    private fun cleanupAfterUp() {
        longPressed = false
        flickDir = null
        repeatCount = 0
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
}
