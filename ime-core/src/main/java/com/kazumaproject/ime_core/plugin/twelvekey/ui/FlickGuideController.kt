package com.kazumaproject.ime_core.plugin.twelvekey.ui

import android.graphics.Rect
import android.os.Build
import android.view.View
import android.widget.FrameLayout
import com.kazumaproject.ime_core.plugin.twelvekey.model.KeyGesture

/**
 * Controller for a single overlay.
 * - Avoids flicker by positioning + updating text BEFORE making it visible.
 * - Cancels pending positioning callbacks.
 */
class FlickGuideController(
    private val overlayHost: FrameLayout
) {
    companion object {
        private const val TAG_OVERLAY = "FlickGuideOverlayView:single"
        private const val TOP_Z = 100_000f
    }

    private val overlay: FlickGuideOverlayView =
        FlickGuideOverlayView(overlayHost.context).also { v ->
            // Remove old overlay if any (rotation/recreate safety)
            overlayHost.findViewWithTag<View>(TAG_OVERLAY)?.let { old ->
                overlayHost.removeView(old)
            }
            v.tag = TAG_OVERLAY

            overlayHost.addView(
                v,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )

            if (Build.VERSION.SDK_INT >= 21) {
                v.elevation = TOP_Z
                v.translationZ = TOP_Z
            }

            v.visibility = View.GONE
            v.isClickable = false
            v.isFocusable = false
        }

    private val positionRunnable = Runnable { /* overwritten in show() */ }

    fun show(anchorInHost: Rect, texts: FlickGuideOverlayView.GuideTexts) {
        // ✅ cancel any pending positioning from previous show()
        overlay.removeCallbacks(positionRunnable)

        // ✅ keep it hidden while we update text & position (prevents one-frame stale draw)
        overlay.visibility = View.INVISIBLE

        // update content first (kills stale text)
        overlay.bind(texts)
        overlay.updatePreview(texts.center)

        // measure now to get correct width/height before making visible
        overlay.measure(
            View.MeasureSpec.makeMeasureSpec(overlayHost.width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(overlayHost.height, View.MeasureSpec.AT_MOST)
        )

        // compute and apply position immediately
        val x = anchorInHost.centerX() - (overlay.measuredWidth / 2f)
        val y = anchorInHost.top - overlay.measuredHeight * 0.75f
        overlay.translationX = x
        overlay.translationY = y

        forceTopMost()

        // ✅ now show (no stale frame)
        overlay.visibility = View.VISIBLE
    }

    fun highlight(dir: KeyGesture?) {
        overlay.highlight(dir)
    }

    fun updatePreview(text: String) {
        // if called while INVISIBLE (during show), this still updates correctly
        overlay.updatePreview(text)
    }

    fun hide() {
        overlay.removeCallbacks(positionRunnable)
        overlay.visibility = View.GONE
        // ✅ clear to avoid “previous text flashes”
        overlay.updatePreview("")
    }

    private fun forceTopMost() {
        overlay.bringToFront()
        overlay.translationZ = TOP_Z
        overlay.elevation = TOP_Z
        overlayHost.invalidate()
        overlayHost.requestLayout()
    }
}
