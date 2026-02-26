package com.kazumaproject.ime_core.plugin.twelvekey.ui

import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import com.kazumaproject.ime_core.plugin.twelvekey.model.KeyGesture

/**
 * Controller for a single overlay.
 * Always brings the overlay to front to avoid being covered by keyboard content.
 */
class FlickGuideController(
    private val overlayHost: FrameLayout
) {
    private val overlay = FlickGuideOverlayView(overlayHost.context).also {
        overlayHost.addView(
            it,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        // A small elevation helps on some devices/themes.
        it.elevation = 1000f
    }

    fun show(anchorInHost: Rect, texts: FlickGuideOverlayView.GuideTexts) {
        overlay.bind(texts)
        overlay.visibility = View.VISIBLE

        // ✅ Ensure it is not covered by contentRoot
        overlay.bringToFront()
        overlayHost.invalidate()

        position(anchorInHost)
    }

    fun highlight(dir: KeyGesture?) {
        overlay.highlight(dir)
    }

    fun updatePreview(text: String) {
        overlay.updatePreview(text)
    }

    fun hide() {
        overlay.visibility = View.GONE
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
