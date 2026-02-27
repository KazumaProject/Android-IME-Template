package com.kazumaproject.ime_core.plugin.twelvekey.ui

import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.kazumaproject.ime_core.plugin.twelvekey.model.KeyGesture

/**
 * Controller for a single overlay.
 * Attaches overlay to the given [overlayHost] (should be ImeResizableView.root) so it is ALWAYS on top.
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
            // ✅ Remove older overlay if recreated (rotation / plugin recreate etc.)
            (overlayHost.findViewWithTag<View>(TAG_OVERLAY) as? ViewGroup)?.let {
                overlayHost.removeView(it)
            }
            (overlayHost.findViewWithTag<View>(TAG_OVERLAY))?.let { old ->
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
            } else {
                // pre-L: draw order depends on child order -> bringToFront() at show() is key
                v.elevation = 0f
            }

            v.visibility = View.GONE
            v.isClickable = false
            v.isFocusable = false
        }

    fun show(anchorInHost: Rect, texts: FlickGuideOverlayView.GuideTexts) {
        overlay.bind(texts)
        overlay.visibility = View.VISIBLE

        forceTopMost()
        position(anchorInHost)
    }

    fun highlight(dir: KeyGesture?) {
        overlay.highlight(dir)
        if (overlay.visibility == View.VISIBLE) forceTopMost()
    }

    fun updatePreview(text: String) {
        overlay.updatePreview(text)
        if (overlay.visibility == View.VISIBLE) forceTopMost()
    }

    fun hide() {
        overlay.visibility = View.GONE
    }

    private fun forceTopMost() {
        // ✅ Always last child (drawn last) within overlayHost
        overlay.bringToFront()

        // ✅ Strong Z on 21+
        if (Build.VERSION.SDK_INT >= 21) {
            overlay.translationZ = TOP_Z
            overlay.elevation = TOP_Z
        }

        overlayHost.invalidate()
        overlayHost.requestLayout()
    }

    private fun position(anchor: Rect) {
        overlay.post {
            val x = anchor.centerX() - (overlay.measuredWidth / 2f)
            val y = anchor.top - overlay.measuredHeight * 0.75f
            overlay.translationX = x
            overlay.translationY = y
        }
    }
}
