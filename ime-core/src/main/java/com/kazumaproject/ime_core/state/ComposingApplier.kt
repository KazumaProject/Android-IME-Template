package com.kazumaproject.ime_core.state

import android.view.inputmethod.InputConnection

object ComposingApplier {

    fun applyPrecomposition(
        ic: InputConnection,
        state: ImeState.Precomposition,
        backgroundColor: Int? = null
    ): Boolean {
        val text = state.composing
        val len = text.length
        if (len == 0) {
            ic.setComposingText("", 1)
            ic.finishComposingText()
            return true
        }

        val split = state.splitCursor.coerceIn(0, len)

        val ranges = PreeditRenderer.computeRanges(len, split, state.decor)
        val spannable = PreeditRenderer.buildSpannable(
            text = text,
            ranges = ranges,
            backgroundColor = backgroundColor ?: android.graphics.Color.argb(120, 59, 130, 246)
        )

        // ★ editor selection は動かさない
        ic.setComposingText(spannable, 1)
        return true
    }
}
