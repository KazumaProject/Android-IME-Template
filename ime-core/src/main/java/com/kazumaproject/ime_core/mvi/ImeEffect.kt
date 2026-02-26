package com.kazumaproject.ime_core.mvi

import com.kazumaproject.ime_core.state.PreeditDecor

sealed interface ImeEffect {
    data class CommitText(val text: String) : ImeEffect
    data object ClearComposing : ImeEffect

    /**
     * splitCursor = bg/ul 境界（見せたいカーソル）
     * editor selection は動かさない前提
     */
    data class RenderPreedit(
        val composing: String,
        val splitCursor: Int,
        val decor: PreeditDecor
    ) : ImeEffect

    data class SendDpad(val direction: DpadDirection, val times: Int = 1) : ImeEffect
    data object BackspaceInEditor : ImeEffect
    data object PerformEditorEnter : ImeEffect

    data class RequestCandidates(
        val requestKey: String,
        val bgText: String,
        val limit: Int = 8
    ) : ImeEffect
}

enum class DpadDirection { LEFT, RIGHT, UP, DOWN }
