package com.kazumaproject.ime_core.mvi

import com.kazumaproject.ime_core.candidates.Candidate

sealed interface ImeAction {
    data class Ui(val action: KeyboardAction) : ImeAction

    data class CandidatesLoaded(
        val requestKey: String,
        val bgText: String,
        val candidates: List<Candidate>
    ) : ImeAction

    // ★ 候補タップ（決定）
    data class CandidateChosen(val candidate: Candidate) : ImeAction
}
