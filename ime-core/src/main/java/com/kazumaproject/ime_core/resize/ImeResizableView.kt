package com.kazumaproject.ime_core

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.kazumaproject.ime_core.ui.CandidateBarView
import kotlin.math.roundToInt

class ImeResizableView(private val host: android.content.Context) {

    val root: FrameLayout = FrameLayout(host).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setBackgroundColor(Color.parseColor("#FF121212"))
    }

    val content: FrameLayout = FrameLayout(host).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        setBackgroundColor(Color.parseColor("#FF1E1E1E"))
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    // content の中で「候補バー + plugin」を積む
    private val stack: LinearLayout = LinearLayout(host).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(Color.TRANSPARENT)
    }

    /**
     * ★候補バーは “固定高さスロット” に入れる
     * これで CandidateBar の Mode による高さ変化が pluginContainer に影響しない
     */
    private val candidateSlotHeightPx = dp(56)

    private val candidateSlot: FrameLayout = FrameLayout(host).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            candidateSlotHeightPx
        )
        setBackgroundColor(Color.TRANSPARENT)
    }

    val candidateBar: CandidateBarView = CandidateBarView(host).apply {
        // スロット内にフィットさせる
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    }

    val pluginContainer: FrameLayout = FrameLayout(host).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0
        ).apply {
            weight = 1f
        }
        setBackgroundColor(Color.TRANSPARENT)
    }

    // 右上ボタン群（今は CandidateBar の Controls に寄せてるなら非表示運用でOK）
    val btnResize: TextView = topButton("Resize")
    val btnMode: TextView = topButton("Mode")
    val btnDefault: TextView = topButton("Default")

    private val topRightBar: LinearLayout = LinearLayout(host).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = dp(8)
            marginEnd = dp(8)
        }
        addView(btnResize)
        addView(
            btnMode,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(6) }
        )
        addView(
            btnDefault,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(6) }
        )
    }

    init {
        // CandidateBar を固定スロットに格納
        candidateSlot.addView(candidateBar)

        // デフォルトは上
        stack.addView(candidateSlot)
        stack.addView(pluginContainer)

        content.addView(stack)
        root.addView(content)
        root.addView(topRightBar)
    }

    fun setCandidatePlacement(placement: CandidateBarView.Placement) {
        stack.removeAllViews()
        if (placement == CandidateBarView.Placement.TOP) {
            stack.addView(candidateSlot)
            stack.addView(pluginContainer)
        } else {
            stack.addView(pluginContainer)
            stack.addView(candidateSlot)
        }
    }

    fun setTopRightButtonsVisible(visible: Boolean) {
        topRightBar.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun applyLayoutPx(contentWidthPx: Int, contentHeightPx: Int, offsetXPx: Int, offsetYPx: Int) {
        val lp = (content.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(contentWidthPx, contentHeightPx)

        lp.width = contentWidthPx
        lp.height = contentHeightPx
        lp.leftMargin = offsetXPx
        lp.topMargin = offsetYPx
        lp.gravity = Gravity.TOP or Gravity.START
        content.layoutParams = lp

        val extraCanvas = dp(48)
        val targetRootH = contentHeightPx + extraCanvas

        root.layoutParams = (root.layoutParams ?: FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )).apply {
            height = targetRootH
        }
        root.minimumHeight = targetRootH
        root.requestLayout()
    }

    private fun topButton(text: String): TextView {
        return TextView(host).apply {
            this.text = text
            textSize = 12f
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setBackgroundColor(Color.parseColor("#FF3A3A3A"))
            setTextColor(Color.WHITE)
        }
    }

    private fun dp(value: Int): Int {
        return (value * host.resources.displayMetrics.density).roundToInt()
    }
}
