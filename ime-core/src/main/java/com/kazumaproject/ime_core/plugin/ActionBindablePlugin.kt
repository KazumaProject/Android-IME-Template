package com.kazumaproject.ime_core.plugin

import com.kazumaproject.ime_core.mvi.KeyboardAction

/**
 * Plugin が host（IME側）へ Action を送るための bind 口。
 * BaseImeService 側で plugin がこれを実装していたら bind してやる。
 */
interface ActionBindablePlugin {
    fun bind(dispatch: (KeyboardAction) -> Unit)
}
