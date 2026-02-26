package com.kazumaproject.ime_core.mvi

import com.kazumaproject.ime_core.state.PreeditDecor

sealed class KeyboardAction {
    data class InputText(val text: String) : KeyboardAction()
    data object Space : KeyboardAction()
    data object Backspace : KeyboardAction()
    data object Enter : KeyboardAction()

    /** dx: -1/+1, dy: -1/+1 */
    data class MoveCursor(val dx: Int, val dy: Int = 0) : KeyboardAction()

    data class SetCompositionMode(val mode: CompositionMode) : KeyboardAction()
    data class SetPreeditDecor(val decor: PreeditDecor) : KeyboardAction()

    data object Noop : KeyboardAction()
}

enum class CompositionMode { DIRECT, PRECOMPOSITION }
