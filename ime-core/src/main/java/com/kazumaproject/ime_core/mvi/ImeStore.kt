package com.kazumaproject.ime_core.mvi

import com.kazumaproject.ime_core.state.ImeState

class ImeStore(
    initialState: ImeState,
    private val effectHandler: ImeEffectHandler
) {
    var state: ImeState = initialState
        private set

    private val listeners = LinkedHashSet<(ImeState) -> Unit>()

    fun addListener(listener: (ImeState) -> Unit) {
        listeners.add(listener)
        listener(state)
    }

    fun removeListener(listener: (ImeState) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyStateChanged() {
        val s = state
        for (l in listeners) l(s)
    }

    fun dispatchUi(action: KeyboardAction) {
        dispatch(ImeAction.Ui(action))
    }

    fun dispatch(action: ImeAction) {
        val next = ImeReducer.reduce(state, action)
        state = next.state
        notifyStateChanged()
        effectHandler.handleAll(next.effects)
    }

    fun clearPreeditIfAny() {
        val s = state
        if (s is ImeState.Precomposition && s.composing.isNotEmpty()) {
            state = s.copy(composing = "", splitCursor = 0)
            notifyStateChanged()
            effectHandler.handle(ImeEffect.ClearComposing)
        }
    }
}
