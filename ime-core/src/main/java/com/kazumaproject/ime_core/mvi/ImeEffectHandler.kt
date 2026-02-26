package com.kazumaproject.ime_core.mvi

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.kazumaproject.ime_core.candidates.CandidateProvider
import com.kazumaproject.ime_core.state.ComposingApplier
import com.kazumaproject.ime_core.state.ImeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface ImeHost {
    fun inputConnection(): InputConnection?
    fun editorInfo(): EditorInfo?
    fun sendDownUpKeyEvents(keyCode: Int)
}

class ImeEffectHandler(
    private val host: ImeHost,
    private val candidateProvider: CandidateProvider,
    private val dispatchInternal: (ImeAction) -> Unit
) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)

    fun dispose() {
        job.cancel()
    }

    fun handleAll(effects: List<ImeEffect>) {
        for (e in effects) handle(e)
    }

    fun handle(effect: ImeEffect) {
        when (effect) {
            is ImeEffect.CommitText -> host.inputConnection()?.commitText(effect.text, 1)

            ImeEffect.ClearComposing -> {
                val ic = host.inputConnection() ?: return
                ic.setComposingText("", 1)
                ic.finishComposingText()
            }

            is ImeEffect.RenderPreedit -> {
                val ic = host.inputConnection() ?: return
                ComposingApplier.applyPrecomposition(
                    ic = ic,
                    state = ImeState.Precomposition(
                        composing = effect.composing,
                        splitCursor = effect.splitCursor,
                        decor = effect.decor
                    )
                )
            }

            is ImeEffect.SendDpad -> {
                val code = when (effect.direction) {
                    DpadDirection.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
                    DpadDirection.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
                    DpadDirection.UP -> KeyEvent.KEYCODE_DPAD_UP
                    DpadDirection.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
                }
                repeat(effect.times.coerceAtLeast(1)) { host.sendDownUpKeyEvents(code) }
            }

            ImeEffect.BackspaceInEditor -> {
                val ic = host.inputConnection() ?: return
                val selected = ic.getSelectedText(0)
                if (!selected.isNullOrEmpty()) ic.commitText("", 1) else ic.deleteSurroundingText(
                    1,
                    0
                )
            }

            ImeEffect.PerformEditorEnter -> {
                val ic = host.inputConnection() ?: return
                val info = host.editorInfo()
                val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
                if (action != null && action != EditorInfo.IME_ACTION_NONE) ic.performEditorAction(
                    action
                )
                else ic.commitText("\n", 1)
            }

            is ImeEffect.RequestCandidates -> {
                if (effect.bgText.isBlank()) {
                    dispatchInternal(
                        ImeAction.CandidatesLoaded(
                            effect.requestKey,
                            effect.bgText,
                            emptyList()
                        )
                    )
                    return
                }
                scope.launch {
                    val list = withContext(Dispatchers.Default) {
                        runCatching {
                            candidateProvider.suggest(
                                effect.bgText,
                                effect.limit
                            )
                        }.getOrDefault(emptyList())
                    }
                    dispatchInternal(
                        ImeAction.CandidatesLoaded(
                            effect.requestKey,
                            effect.bgText,
                            list
                        )
                    )
                }
            }

            is ImeEffect.PerformRaw -> {
                when (val r = effect.raw) {
                    is KeyActionRaw.SendKeyCode -> host.sendDownUpKeyEvents(r.keyCode)
                    is KeyActionRaw.PerformEditorAction -> host.inputConnection()
                        ?.performEditorAction(r.actionId)

                    is KeyActionRaw.CommitText -> host.inputConnection()
                        ?.commitText(r.text, 1)
                }
            }
        }
    }
}
