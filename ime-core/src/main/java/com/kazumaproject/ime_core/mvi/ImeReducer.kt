package com.kazumaproject.ime_core.mvi

import com.kazumaproject.ime_core.state.CandidateUiMode
import com.kazumaproject.ime_core.state.ImeState
import com.kazumaproject.ime_core.state.PreeditRenderer
import kotlin.math.abs

object ImeReducer {

    data class Next(val state: ImeState, val effects: List<ImeEffect>)

    fun reduce(state: ImeState, action: ImeAction): Next {
        return when (action) {
            is ImeAction.Ui -> reduceUi(state, action.action)
            is ImeAction.CandidatesLoaded -> reduceCandidatesLoaded(state, action)
            is ImeAction.CandidateChosen -> reduceCandidateChosen(state, action.candidate)
            else -> Next(state, emptyList())
        }
    }

    private fun reduceUi(state: ImeState, action: KeyboardAction): Next {
        return when (state) {
            ImeState.Direct -> reduceDirect(action)
            is ImeState.Precomposition -> reducePrecomp(state, action)
        }
    }

    private fun reduceDirect(action: KeyboardAction): Next {
        return when (action) {
            is KeyboardAction.InputText -> Next(
                ImeState.Direct,
                listOf(ImeEffect.CommitText(action.text))
            )

            KeyboardAction.Space -> Next(ImeState.Direct, listOf(ImeEffect.CommitText(" ")))
            KeyboardAction.Backspace -> Next(ImeState.Direct, listOf(ImeEffect.BackspaceInEditor))
            KeyboardAction.Enter -> Next(ImeState.Direct, listOf(ImeEffect.PerformEditorEnter))

            is KeyboardAction.MoveCursor -> {
                val effects = buildList {
                    if (action.dx != 0) add(
                        ImeEffect.SendDpad(
                            if (action.dx < 0) DpadDirection.LEFT else DpadDirection.RIGHT,
                            abs(action.dx)
                        )
                    )
                    if (action.dy != 0) add(
                        ImeEffect.SendDpad(
                            if (action.dy < 0) DpadDirection.UP else DpadDirection.DOWN,
                            abs(action.dy)
                        )
                    )
                }
                Next(ImeState.Direct, effects)
            }

            is KeyboardAction.SetCompositionMode -> {
                when (action.mode) {
                    CompositionMode.DIRECT -> Next(ImeState.Direct, emptyList())
                    CompositionMode.PRECOMPOSITION -> Next(
                        ImeState.Precomposition(),
                        listOf(ImeEffect.ClearComposing)
                    )
                }
            }

            else -> Next(ImeState.Direct, emptyList())
        }
    }

    private fun reducePrecomp(s0: ImeState.Precomposition, action: KeyboardAction): Next {
        // -------------------------
        // Select mode
        // -------------------------
        if (s0.candidateUi.selectMode) {
            return when (action) {
                KeyboardAction.Space -> {
                    val list = s0.candidateUi.candidates
                    if (list.isEmpty()) return syncCandidateUi(s0, emptyList())

                    val nextIdx = if (s0.candidateUi.selectedIndex !in list.indices) 0
                    else (s0.candidateUi.selectedIndex + 1) % list.size

                    val s1 = previewCandidateOnBase(
                        s = s0,
                        candidateSurface = list[nextIdx].surface,
                        selectedIndex = nextIdx
                    )
                    Next(
                        s1,
                        listOf(ImeEffect.RenderPreedit(s1.composing, s1.splitCursor, s1.decor))
                    )
                }

                // ★ Delete/Backspace: bg を元に戻して Select 解除
                KeyboardAction.Backspace -> {
                    val restored = restoreFromBaseAndExitSelect(s0)
                    syncCandidateUi(
                        restored,
                        listOf(
                            ImeEffect.RenderPreedit(
                                restored.composing,
                                restored.splitCursor,
                                restored.decor
                            )
                        )
                    )
                }

                // ★ MoveCursor: まず元に戻して Select解除 → その上で通常の MoveCursor を実行
                is KeyboardAction.MoveCursor -> {
                    val restored = restoreFromBaseAndExitSelect(s0)
                    // restored は selectMode=false なので、通常分岐に入る
                    reducePrecomp(restored, action)
                }

                // ★確定（Enter）: commit(bg) + ulを次preeditとして残す（全文をbgにしない）
                KeyboardAction.Enter -> {
                    val list = s0.candidateUi.candidates
                    if (list.isEmpty()) return syncCandidateUi(s0, emptyList())

                    val idx = s0.candidateUi.selectedIndex.coerceIn(0, list.lastIndex)
                    commitBgAndCarryUl(s = s0, candidateSurface = list[idx].surface)
                }

                else -> {
                    val restored = restoreFromBaseAndExitSelect(s0)
                    reducePrecomp(restored, action)
                }
            }
        }

        // -------------------------
        // Normal preedit
        // -------------------------
        val (s1, effects1) = when (action) {
            is KeyboardAction.InputText -> {
                val newText = s0.composing + action.text
                val s = s0.copy(composing = newText, splitCursor = newText.length)
                s to listOf(ImeEffect.RenderPreedit(s.composing, s.splitCursor, s.decor))
            }

            KeyboardAction.Space -> {
                val canEnterSelect =
                    s0.candidateUi.bgText.isNotBlank() && s0.candidateUi.candidates.isNotEmpty()

                if (canEnterSelect) {
                    val idx0 = 0
                    val sEnter = s0.copy(
                        candidateUi = s0.candidateUi.copy(
                            selectMode = true,
                            selectedIndex = idx0,
                            baseComposing = s0.composing,
                            baseSplitCursor = s0.splitCursor
                        )
                    )
                    val sPreview = previewCandidateOnBase(
                        s = sEnter,
                        candidateSurface = sEnter.candidateUi.candidates[idx0].surface,
                        selectedIndex = idx0
                    )
                    sPreview to listOf(
                        ImeEffect.RenderPreedit(
                            sPreview.composing,
                            sPreview.splitCursor,
                            sPreview.decor
                        )
                    )
                } else {
                    val newText = s0.composing + " "
                    val s = s0.copy(composing = newText, splitCursor = newText.length)
                    s to listOf(ImeEffect.RenderPreedit(s.composing, s.splitCursor, s.decor))
                }
            }

            KeyboardAction.Backspace -> {
                if (s0.composing.isEmpty()) {
                    s0 to listOf(ImeEffect.BackspaceInEditor)
                } else {
                    val newText = s0.composing.dropLast(1)
                    val newSplit = s0.splitCursor.coerceAtMost(newText.length)
                    val s = s0.copy(composing = newText, splitCursor = newSplit)
                    val eff =
                        if (newText.isEmpty()) listOf(ImeEffect.ClearComposing)
                        else listOf(ImeEffect.RenderPreedit(s.composing, s.splitCursor, s.decor))
                    s to eff
                }
            }

            KeyboardAction.Enter -> {
                if (s0.composing.isNotEmpty()) {
                    val s = s0.copy(composing = "", splitCursor = 0)
                    s to listOf(ImeEffect.CommitText(s0.composing), ImeEffect.ClearComposing)
                } else {
                    s0 to listOf(ImeEffect.PerformEditorEnter)
                }
            }

            is KeyboardAction.MoveCursor -> {
                // ★ Preeditに文字が無いなら editor 側を動かす（Direct同等）
                if (s0.composing.isEmpty()) {
                    val effects = buildList {
                        if (action.dx != 0) add(
                            ImeEffect.SendDpad(
                                if (action.dx < 0) DpadDirection.LEFT else DpadDirection.RIGHT,
                                abs(action.dx)
                            )
                        )
                        if (action.dy != 0) add(
                            ImeEffect.SendDpad(
                                if (action.dy < 0) DpadDirection.UP else DpadDirection.DOWN,
                                abs(action.dy)
                            )
                        )
                    }
                    s0 to effects
                } else {
                    val len = s0.composing.length
                    val s = s0.copy(splitCursor = (s0.splitCursor + action.dx).coerceIn(0, len))
                    s to listOf(ImeEffect.RenderPreedit(s.composing, s.splitCursor, s.decor))
                }
            }

            is KeyboardAction.SetCompositionMode -> {
                when (action.mode) {
                    CompositionMode.PRECOMPOSITION -> s0 to emptyList()
                    CompositionMode.DIRECT -> {
                        val eff = buildList {
                            if (s0.composing.isNotEmpty()) add(ImeEffect.CommitText(s0.composing))
                            add(ImeEffect.ClearComposing)
                        }
                        return Next(ImeState.Direct, eff)
                    }
                }
            }

            else -> s0 to emptyList()
        }

        return syncCandidateUi(s1, effects1)
    }

    private fun reduceCandidateChosen(
        state: ImeState,
        candidate: com.kazumaproject.ime_core.candidates.Candidate
    ): Next {
        val s0 = state as? ImeState.Precomposition ?: return Next(state, emptyList())
        if (s0.candidateUi.bgText.isBlank()) return Next(state, emptyList())
        return commitBgAndCarryUl(s0, candidate.surface)
    }

    private fun reduceCandidatesLoaded(state: ImeState, action: ImeAction.CandidatesLoaded): Next {
        val s0 = state as? ImeState.Precomposition ?: return Next(state, emptyList())
        if (s0.candidateUi.requestKey != action.requestKey) return Next(state, emptyList())
        if (s0.candidateUi.bgText != action.bgText) return Next(state, emptyList())

        val s1 = s0.copy(
            candidateUi = s0.candidateUi.copy(
                candidates = action.candidates,
                isLoading = false
            )
        )
        return Next(s1, emptyList())
    }

    private fun syncCandidateUi(s0: ImeState.Precomposition, baseEffects: List<ImeEffect>): Next {
        if (s0.candidateUi.selectMode) return Next(s0, baseEffects)

        val bgText = extractBgTextOrNull(s0).orEmpty()
        if (bgText.isBlank()) {
            val s1 = s0.copy(
                candidateUi = s0.candidateUi.copy(
                    mode = CandidateUiMode.CONTROLS,
                    bgText = "",
                    requestKey = "",
                    candidates = emptyList(),
                    isLoading = false,
                    selectedIndex = -1
                )
            )
            return Next(s1, baseEffects)
        }

        val key = makeRequestKey(bgText, s0.composing.length, s0.splitCursor)
        val already = (s0.candidateUi.requestKey == key && s0.candidateUi.bgText == bgText)

        val s1 = s0.copy(
            candidateUi = s0.candidateUi.copy(
                mode = CandidateUiMode.SUGGESTION,
                bgText = bgText,
                requestKey = key,
                isLoading = !already,
                candidates = if (already) s0.candidateUi.candidates else emptyList()
            )
        )

        val extra =
            if (already) emptyList() else listOf(ImeEffect.RequestCandidates(key, bgText, 8))
        return Next(s1, baseEffects + extra)
    }

    private fun extractBgTextOrNull(s: ImeState.Precomposition): String? {
        if (s.composing.isEmpty()) return null
        val ranges = PreeditRenderer.computeRanges(s.composing.length, s.splitCursor, s.decor)
        val bg = ranges.bg.clamp(s.composing.length)
        if (bg.isEmpty()) return null
        return runCatching { s.composing.substring(bg.start, bg.endExclusive) }.getOrNull()
    }

    private fun rangesFor(
        text: String,
        split: Int,
        decor: com.kazumaproject.ime_core.state.PreeditDecor
    ): PreeditRenderer.ComputedRanges {
        return PreeditRenderer.computeRanges(text.length, split.coerceIn(0, text.length), decor)
    }

    private fun previewCandidateOnBase(
        s: ImeState.Precomposition,
        candidateSurface: String,
        selectedIndex: Int
    ): ImeState.Precomposition {
        val baseText = s.candidateUi.baseComposing ?: s.composing
        val baseSplit =
            (if (s.candidateUi.baseComposing != null) s.candidateUi.baseSplitCursor else s.splitCursor)
                .coerceIn(0, baseText.length)

        val ranges = rangesFor(baseText, baseSplit, s.decor)
        val bg = ranges.bg.clamp(baseText.length)
        if (bg.isEmpty()) return s

        val replaced = StringBuilder(baseText).apply {
            replace(bg.start, bg.endExclusive, candidateSurface)
        }.toString()

        val newSplit = (bg.start + candidateSurface.length).coerceIn(0, replaced.length)

        return s.copy(
            composing = replaced,
            splitCursor = newSplit,
            candidateUi = s.candidateUi.copy(
                selectedIndex = selectedIndex,
                baseComposing = baseText,
                baseSplitCursor = baseSplit
            )
        )
    }

    private fun commitBgAndCarryUl(
        s: ImeState.Precomposition,
        candidateSurface: String
    ): Next {
        val baseText = s.candidateUi.baseComposing ?: s.composing
        val baseSplit =
            (if (s.candidateUi.baseComposing != null) s.candidateUi.baseSplitCursor else s.splitCursor)
                .coerceIn(0, baseText.length)

        val ranges = rangesFor(baseText, baseSplit, s.decor)
        val bg = ranges.bg.clamp(baseText.length)
        val ul = ranges.ul.clamp(baseText.length)

        if (bg.isEmpty()) {
            val s1 = restoreFromBaseAndExitSelect(s)
            return Next(s1, emptyList())
        }

        val ulText = if (!ul.isEmpty()) baseText.substring(ul.start, ul.endExclusive) else ""
        val nextComposing = ulText
        val nextSplit = nextComposing.length

        val s1 = s.copy(
            composing = nextComposing,
            splitCursor = nextSplit,
            candidateUi = s.candidateUi.copy(
                selectMode = false,
                selectedIndex = -1,
                baseComposing = null,
                baseSplitCursor = 0
            )
        )

        val effects = buildList {
            add(ImeEffect.CommitText(candidateSurface))
            if (nextComposing.isEmpty()) add(ImeEffect.ClearComposing)
            else add(ImeEffect.RenderPreedit(s1.composing, s1.splitCursor, s1.decor))
        }

        return syncCandidateUi(s1, effects)
    }

    private fun restoreFromBaseAndExitSelect(s0: ImeState.Precomposition): ImeState.Precomposition {
        val baseText = s0.candidateUi.baseComposing
        val baseSplit = s0.candidateUi.baseSplitCursor

        return if (baseText != null) {
            s0.copy(
                composing = baseText,
                splitCursor = baseSplit.coerceIn(0, baseText.length),
                candidateUi = s0.candidateUi.copy(
                    selectMode = false,
                    selectedIndex = -1,
                    baseComposing = null,
                    baseSplitCursor = 0
                )
            )
        } else {
            s0.copy(
                candidateUi = s0.candidateUi.copy(
                    selectMode = false,
                    selectedIndex = -1,
                    baseComposing = null,
                    baseSplitCursor = 0
                )
            )
        }
    }

    private fun makeRequestKey(bgText: String, len: Int, split: Int): String = "$bgText#$len#$split"
}
