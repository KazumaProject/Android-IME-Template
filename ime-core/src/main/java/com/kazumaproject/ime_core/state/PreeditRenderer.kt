package com.kazumaproject.ime_core.state

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.UnderlineSpan
import kotlin.math.min

object PreeditRenderer {

    data class ComputedRanges(
        val bg: Range,
        val ul: Range
    )

    fun computeRanges(textLen: Int, cursor: Int, decor: PreeditDecor): ComputedRanges {
        val len = textLen.coerceAtLeast(0)
        val c = cursor.coerceIn(0, len)

        return when (decor) {
            PreeditDecor.SplitAtCursor -> ComputedRanges(
                bg = Range(0, c),
                ul = Range(c, len)
            )

            PreeditDecor.CursorChar -> {
                val bgStart = c
                val bgEnd = min(c + 1, len)
                ComputedRanges(
                    bg = Range(bgStart, bgEnd),
                    ul = Range(0, len)
                )
            }

            is PreeditDecor.Custom -> ComputedRanges(
                bg = decor.bg.clamp(len),
                ul = decor.ul.clamp(len)
            )
        }
    }

    fun buildSpannable(
        text: String,
        ranges: ComputedRanges,
        backgroundColor: Int = Color.argb(120, 59, 130, 246)
    ): SpannableString {
        val s = SpannableString(text)
        val len = text.length
        if (len == 0) return s

        val ul = ranges.ul.clamp(len)
        val bg = ranges.bg.clamp(len)

        if (!ul.isEmpty()) {
            s.setSpan(
                UnderlineSpan(),
                ul.start,
                ul.endExclusive,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        if (!bg.isEmpty()) {
            s.setSpan(
                BackgroundColorSpan(backgroundColor),
                bg.start,
                bg.endExclusive,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return s
    }
}
