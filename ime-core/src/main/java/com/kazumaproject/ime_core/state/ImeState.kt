package com.kazumaproject.ime_core.state

import com.kazumaproject.ime_core.candidates.Candidate

sealed interface ImeState {
    data object Direct : ImeState

    /**
     * splitCursor = bg/ul の境界（見せたいカーソル）
     * editor selection は動かさない
     */
    data class Precomposition(
        val composing: String = "",
        val splitCursor: Int = 0,
        val decor: PreeditDecor = PreeditDecor.SplitAtCursor,
        val candidateUi: CandidateUiState = CandidateUiState()
    ) : ImeState
}

data class CandidateUiState(
    val mode: CandidateUiMode = CandidateUiMode.CONTROLS,
    val bgText: String = "",
    val requestKey: String = "",
    val candidates: List<Candidate> = emptyList(),
    val isLoading: Boolean = false,

    // Suggestion Select mode
    val selectMode: Boolean = false,
    val selectedIndex: Int = -1,

    // Preview base (avoid cumulative replacement)
    val baseComposing: String? = null,
    val baseSplitCursor: Int = 0
)

enum class CandidateUiMode { SUGGESTION, CONTROLS }

sealed interface PreeditDecor {
    data object SplitAtCursor : PreeditDecor
    data object CursorChar : PreeditDecor
    data class Custom(
        val bg: Range = Range(0, 0),
        val ul: Range = Range(0, 0),
    ) : PreeditDecor
}

data class Range(val start: Int, val endExclusive: Int) {
    fun isEmpty(): Boolean = endExclusive <= start
    fun clamp(len: Int): Range {
        val s = start.coerceIn(0, len)
        val e = endExclusive.coerceIn(0, len)
        return if (e <= s) Range(0, 0) else Range(s, e)
    }
}
