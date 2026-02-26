package com.kazumaproject.ime_core.plugin.twelvekey.gesture

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import com.kazumaproject.ime_core.plugin.twelvekey.model.KeyGesture
import com.kazumaproject.ime_core.plugin.twelvekey.model.KeyOutput
import com.kazumaproject.ime_core.plugin.twelvekey.model.KeySpec
import com.kazumaproject.ime_core.plugin.twelvekey.ui.FlickGuideController
import com.kazumaproject.ime_core.plugin.twelvekey.ui.FlickGuideOverlayView
import kotlin.math.abs

class TwelveKeyTouchRouter(
    private val grid: ViewGroup,
    private val overlayHost: ViewGroup,
    private val overlay: FlickGuideController,
    private val viewToSpec: Map<View, KeySpec>,
    private val onResolved: (KeyOutput) -> Unit,
) : View.OnTouchListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(grid.context).scaledTouchSlop.toFloat()
    private val longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong()

    private var activePointerId: Int = MotionEvent.INVALID_POINTER_ID
    private var activeView: View? = null
    private var activeSpec: KeySpec? = null

    private var downRawX = 0f
    private var downRawY = 0f
    private var longPressed = false
    private var currentGesture: KeyGesture? = null

    private val tmpRect = Rect()

    private val longPressRunnable = Runnable {
        if (activePointerId == MotionEvent.INVALID_POINTER_ID) return@Runnable
        longPressed = true
        // treat as center long-press until moved
        currentGesture = KeyGesture.LONG_PRESS
        updateOverlayForCurrent()
    }

    override fun onTouch(v: View?, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                begin(event, pointerIndex = 0)
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // ✅ 2本目が来た瞬間に 1本目を「離れた」扱いで確定
                finalizeActiveAsIfUp()

                val idx = event.actionIndex
                begin(event, pointerIndex = idx)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!hasActive()) return false
                val idx = event.findPointerIndex(activePointerId)
                if (idx < 0) return true

                val rawX = event.getRawX(idx)
                val rawY = event.getRawY(idx)

                val dx = rawX - downRawX
                val dy = rawY - downRawY
                val threshold = defaultFlickThresholdPx()

                val movedEnough = (abs(dx) >= threshold) || (abs(dy) >= threshold)
                currentGesture = if (movedEnough) {
                    if (!longPressed) {
                        mainHandler.removeCallbacks(longPressRunnable)
                    }
                    resolveDirection(dx, dy)
                } else {
                    if (longPressed) KeyGesture.LONG_PRESS else KeyGesture.TAP
                }

                updateOverlayForCurrent()
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!hasActive()) return false
                finalizeActiveAsIfUp()
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // active pointer が上がった場合のみ finalize
                val idx = event.actionIndex
                val pid = event.getPointerId(idx)
                if (pid == activePointerId) {
                    finalizeActiveAsIfUp()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelActive()
                return true
            }
        }
        return false
    }

    private fun begin(event: MotionEvent, pointerIndex: Int) {
        cancelActive() // safety

        activePointerId = event.getPointerId(pointerIndex)
        downRawX = event.getRawX(pointerIndex)
        downRawY = event.getRawY(pointerIndex)
        longPressed = false
        currentGesture = KeyGesture.TAP

        val target = findChildUnder(grid, downRawX, downRawY)
        if (target == null) {
            cancelActive()
            return
        }

        val spec = viewToSpec[target] ?: run {
            cancelActive()
            return
        }

        activeView = target
        activeSpec = spec

        // show overlay
        overlay.show(anchorRectInOverlayHost(target), makeGuideTexts(spec))
        updateOverlayForCurrent()

        mainHandler.removeCallbacks(longPressRunnable)
        mainHandler.postDelayed(longPressRunnable, longPressTimeoutMs)
    }

    private fun updateOverlayForCurrent() {
        val spec = activeSpec ?: return
        val g = currentGesture ?: KeyGesture.TAP
        val resolvedGesture = if (longPressed) toLongPressVariant(g) else g

        // highlight + preview = "when you release, what will output"
        overlay.highlight(resolvedGesture)
        val out = spec.outputs[resolvedGesture] ?: KeyOutput.Noop
        overlay.updatePreview(labelOf(out) ?: spec.label)
    }

    private fun finalizeActiveAsIfUp() {
        if (!hasActive()) return

        mainHandler.removeCallbacks(longPressRunnable)

        val spec = activeSpec
        val g0 = currentGesture ?: KeyGesture.TAP
        val g = if (longPressed) toLongPressVariant(g0) else g0

        val out = spec?.outputs?.get(g) ?: KeyOutput.Noop
        onResolved(out)

        cancelActive()
    }

    private fun cancelActive() {
        mainHandler.removeCallbacks(longPressRunnable)
        overlay.hide()

        activePointerId = MotionEvent.INVALID_POINTER_ID
        activeView = null
        activeSpec = null
        longPressed = false
        currentGesture = null
    }

    private fun hasActive(): Boolean = activePointerId != MotionEvent.INVALID_POINTER_ID

    private fun defaultFlickThresholdPx(): Float {
        val dp24 = 24f * grid.context.resources.displayMetrics.density
        return maxOf(touchSlop * 2f, dp24)
    }

    private fun resolveDirection(dx: Float, dy: Float): KeyGesture {
        return if (abs(dx) >= abs(dy)) {
            if (dx < 0) KeyGesture.FLICK_LEFT else KeyGesture.FLICK_RIGHT
        } else {
            if (dy < 0) KeyGesture.FLICK_UP else KeyGesture.FLICK_DOWN
        }
    }

    private fun toLongPressVariant(g: KeyGesture): KeyGesture {
        return when (g) {
            KeyGesture.FLICK_UP -> KeyGesture.LONG_PRESS_FLICK_UP
            KeyGesture.FLICK_DOWN -> KeyGesture.LONG_PRESS_FLICK_DOWN
            KeyGesture.FLICK_LEFT -> KeyGesture.LONG_PRESS_FLICK_LEFT
            KeyGesture.FLICK_RIGHT -> KeyGesture.LONG_PRESS_FLICK_RIGHT
            KeyGesture.TAP, KeyGesture.LONG_PRESS -> KeyGesture.LONG_PRESS
            else -> g
        }
    }

    private fun makeGuideTexts(spec: KeySpec): FlickGuideOverlayView.GuideTexts {
        return FlickGuideOverlayView.GuideTexts(
            center = labelOf(spec.outputs[KeyGesture.TAP]) ?: spec.label,
            up = labelOf(spec.outputs[KeyGesture.FLICK_UP]),
            right = labelOf(spec.outputs[KeyGesture.FLICK_RIGHT]),
            down = labelOf(spec.outputs[KeyGesture.FLICK_DOWN]),
            left = labelOf(spec.outputs[KeyGesture.FLICK_LEFT]),
        )
    }

    private fun labelOf(out: KeyOutput?): String? {
        return when (out) {
            is KeyOutput.Text -> out.text
            is KeyOutput.Action -> {
                val a = out.action
                if (a is com.kazumaproject.ime_core.mvi.KeyboardAction.InputText) a.text else null
            }

            is KeyOutput.Raw -> {
                val r = out.raw
                if (r is com.kazumaproject.ime_core.mvi.KeyActionRaw.CommitText) r.text else null
            }

            else -> null
        }
    }

    private fun findChildUnder(root: ViewGroup, rawX: Float, rawY: Float): View? {
        // brute-force: find topmost child whose screen rect contains raw point
        for (i in root.childCount - 1 downTo 0) {
            val c = root.getChildAt(i)
            if (c.visibility != View.VISIBLE) continue
            if (isPointInViewOnScreen(c, rawX, rawY)) return c
        }
        return null
    }

    private fun isPointInViewOnScreen(v: View, rawX: Float, rawY: Float): Boolean {
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        val x = loc[0]
        val y = loc[1]
        return rawX >= x && rawX < x + v.width && rawY >= y && rawY < y + v.height
    }

    private fun anchorRectInOverlayHost(keyView: View): Rect {
        val locKey = IntArray(2)
        val locHost = IntArray(2)
        keyView.getLocationOnScreen(locKey)
        overlayHost.getLocationOnScreen(locHost)

        tmpRect.set(
            locKey[0] - locHost[0],
            locKey[1] - locHost[1],
            locKey[0] - locHost[0] + keyView.width,
            locKey[1] - locHost[1] + keyView.height
        )
        return tmpRect
    }
}

/** MotionEvent.getRawX/Y(index) helpers (API差分吸収) */
private fun MotionEvent.getRawX(pointerIndex: Int): Float {
    val dx = rawX - getX(0)
    return getX(pointerIndex) + dx
}

private fun MotionEvent.getRawY(pointerIndex: Int): Float {
    val dy = rawY - getY(0)
    return getY(pointerIndex) + dy
}
