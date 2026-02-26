package com.kazumaproject.ime_core.state

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

object ComposingApplier {

    /**
     * Precomposition を editor へ描画する。
     *
     * - setComposingText(spannable, 1) で描画
     * - setSelection(absolute) でカーソル位置を確実に合わせる
     *
     * 返り値: 適用したかどうか
     */
    fun applyPrecomposition(
        ic: InputConnection,
        state: ImeState.Precomposition,
        backgroundColor: Int? = null
    ): Boolean {
        val text = state.composing
        val len = text.length
        if (len == 0) {
            // composing を消す
            ic.setComposingText("", 1)
            ic.finishComposingText()
            return true
        }

        val ranges = PreeditRenderer.computeRanges(len, state.cursor, state.decor)
        val spannable = PreeditRenderer.buildSpannable(
            text = text,
            ranges = ranges,
            backgroundColor = backgroundColor ?: android.graphics.Color.argb(120, 59, 130, 246)
        )

        // まず描画
        ic.setComposingText(spannable, 1)

        // 次にカーソルを正確に合わせる（絶対座標が取れればそれが最強）
        val req = ExtractedTextRequest().apply {
            token = 0
            flags = 0
            hintMaxChars = 0
            hintMaxLines = 0
        }
        val extracted = ic.getExtractedText(req, 0)

        val cursorInPreedit = state.cursor.coerceIn(0, len)
        if (extracted != null) {
            val composingStart = extracted.selectionEnd - len
            val desiredAbs = composingStart + cursorInPreedit
            ic.setSelection(desiredAbs, desiredAbs)
        } else {
            // フォールバック：相対指定でなんとかする
            val newCursorPosition = cursorInPreedit - len
            ic.setComposingText(spannable, newCursorPosition)
        }
        return true
    }
}
