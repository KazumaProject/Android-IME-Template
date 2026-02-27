package com.kazumaproject.ime_core.plugin

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.appcompat.widget.AppCompatButton
import com.kazumaproject.ime_core.plugin.twelvekey.model.KeyGesture
import com.kazumaproject.ime_core.plugin.twelvekey.ui.FlickGuideController
import com.kazumaproject.ime_core.plugin.twelvekey.ui.FlickGuideOverlayView
import com.kazumaproject.ime_core.plugin.twelvekey.ui.FlickGuideSpec
import kotlin.math.abs

class GestureKeyButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.buttonStyle,
) : AppCompatButton(context, attrs, defStyleAttr) {

    var onGestureResolved: ((KeyGesture) -> Unit)? = null

    var guideSpec: FlickGuideSpec? = null
    var guideController: FlickGuideController? = null

    /**
     * ✅ IMPORTANT
     * Rect calculation must use the SAME host where overlay is attached (ImeResizableView.root).
     */
    var guideOverlayHost: android.widget.FrameLayout? = null

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
                    // 中心に戻ったらフリック確定解除
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
