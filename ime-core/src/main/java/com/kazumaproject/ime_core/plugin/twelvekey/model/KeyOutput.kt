package com.kazumaproject.ime_core.plugin.twelvekey.model

import com.kazumaproject.ime_core.mvi.KeyActionRaw
import com.kazumaproject.ime_core.mvi.KeyboardAction

sealed class KeyOutput {
    data class Text(val text: String) : KeyOutput()
    data class Action(val action: KeyboardAction) : KeyOutput()
    data class Raw(val raw: KeyActionRaw) : KeyOutput()
    data object Noop : KeyOutput()
}
