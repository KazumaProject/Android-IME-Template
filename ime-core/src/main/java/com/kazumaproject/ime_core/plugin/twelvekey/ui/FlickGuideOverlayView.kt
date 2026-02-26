package com.kazumaproject.ime_core.plugin.twelvekey.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.kazumaproject.ime_core.plugin.twelvekey.model.KeyGesture
import kotlin.math.roundToInt

/**
 * Single-view flick preview overlay.
 *
 * - Shows only ONE text (what will be output if released now).
 * - Hides on finger up/cancel (controller handles).
 */
class FlickGuideOverlayView(context: Context) : FrameLayout(context) {

    /**
     * Kept for compatibility with existing call sites.
     * Only [center] is used in this simplified overlay.
     */
    data class GuideTexts(
        val center: String,
        val up: String? = null,
        val right: String? = null,
        val down: String? = null,
        val left: String? = null,
    )

    private val tv = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.BLACK)
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(Color.WHITE)
            setStroke(dp(2), Color.parseColor("#33000000"))
        }
    }

    init {
        addView(
            tv,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
        isClickable = false
        isFocusable = false
        visibility = View.GONE
    }

    fun bind(texts: GuideTexts) {
        updatePreview(texts.center)
    }

    fun updatePreview(text: String) {
        tv.text = text
    }

    /**
     * No-op in simplified mode (only one view).
     * Keep it so controller/router code doesn't need changes.
     */
    fun highlight(@Suppress("UNUSED_PARAMETER") dir: KeyGesture?) = Unit

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).roundToInt()
}
