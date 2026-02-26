package com.kazumaproject.ime_core.mvi

import com.kazumaproject.ime_core.state.CandidateUiMode
import com.kazumaproject.ime_core.state.ImeState
import com.kazumaproject.ime_core.state.PreeditRenderer
import com.kazumaproject.ime_core.state.Range
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
        // 1) Suggestion Select mode
        // -------------------------
        if (s0.candidateUi.selectMode) {
            return when (action) {

                // Space: 次候補（wrap）して bg を差し替えてプレビュー
                KeyboardAction.Space -> {
                    val list = s0.candidateUi.candidates
                    if (list.isEmpty()) return syncCandidateUi(s0, emptyList())

                    val nextIdx =
                        if (s0.candidateUi.selectedIndex !in list.indices) 0
                        else (s0.candidateUi.selectedIndex + 1) % list.size

                    val s1 = buildPreviewFromBase(
                        s = s0,
                        candidateSurface = list[nextIdx].surface,
                        selectedIndex = nextIdx
                    )

                    // Select中は候補要求しない（チラつき防止）
                    Next(s1, listOf(ImeEffect.RenderPreedit(s1.composing, s1.cursor, s1.decor)))
                }

                // ★ Enter: 「確定」= bg部分をcommitし、ulがあれば次preeditとして残してbgに移す
                KeyboardAction.Enter -> {
                    val list = s0.candidateUi.candidates
                    if (list.isEmpty()) return syncCandidateUi(s0, emptyList())

                    val idx = s0.candidateUi.selectedIndex.coerceIn(0, list.lastIndex)
                    val out = commitBgAndKeepUlAsNextPreedit(
                        s = s0,
                        candidateSurface = list[idx].surface
                    )
                    out
                }

                else -> {
                    // Select解除して通常処理へ
                    val s1 = s0.copy(
                        candidateUi = s0.candidateUi.copy(
                            selectMode = false,
                            selectedIndex = -1,
                            baseComposing = null,
                            baseCursor = 0
                        )
                    )
                    reducePrecomp(s1, action)
                }
            }
        }

        // -------------------------
        // 2) Normal preedit
        // -------------------------
        val (s1, effects1) = when (action) {

            is KeyboardAction.InputText -> {
                val r = insert(s0.composing, s0.cursor, action.text)
                val s = s0.copy(composing = r.text, cursor = r.newCursor)
                s to listOf(ImeEffect.RenderPreedit(s.composing, s.cursor, s.decor))
            }

            // Space: 候補があるなら Select mode / なければスペース挿入
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
                            baseCursor = s0.cursor
                        )
                    )
                    val sPreview = buildPreviewFromBase(
                        s = sEnter,
                        candidateSurface = sEnter.candidateUi.candidates[idx0].surface,
                        selectedIndex = idx0
                    )
                    sPreview to listOf(
                        ImeEffect.RenderPreedit(
                            sPreview.composing,
                            sPreview.cursor,
                            sPreview.decor
                        )
                    )
                } else {
                    val r = insert(s0.composing, s0.cursor, " ")
                    val s = s0.copy(composing = r.text, cursor = r.newCursor)
                    s to listOf(ImeEffect.RenderPreedit(s.composing, s.cursor, s.decor))
                }
            }

            KeyboardAction.Backspace -> {
                if (s0.composing.isEmpty() || s0.cursor <= 0) {
                    s0 to listOf(ImeEffect.BackspaceInEditor)
                } else {
                    val r = deleteBeforeCursor(s0.composing, s0.cursor)
                    val s = s0.copy(composing = r.text, cursor = r.newCursor)
                    val eff = if (s.composing.isEmpty()) listOf(ImeEffect.ClearComposing)
                    else listOf(ImeEffect.RenderPreedit(s.composing, s.cursor, s.decor))
                    s to eff
                }
            }

            KeyboardAction.Enter -> {
                if (s0.composing.isNotEmpty()) {
                    val s = s0.copy(composing = "", cursor = 0)
                    s to listOf(ImeEffect.CommitText(s0.composing), ImeEffect.ClearComposing)
                } else {
                    s0 to listOf(ImeEffect.PerformEditorEnter)
                }
            }

            is KeyboardAction.MoveCursor -> {
                val len = s0.composing.length
                val s = s0.copy(cursor = (s0.cursor + action.dx).coerceIn(0, len))
                s to listOf(ImeEffect.RenderPreedit(s.composing, s.cursor, s.decor))
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

    // ★ 候補タップも Select Enter と同じ「確定」挙動（bgをcommit / ulを次preeditに残す）
    private fun reduceCandidateChosen(
        state: ImeState,
        candidate: com.kazumaproject.ime_core.candidates.Candidate
    ): Next {
        val s0 = state as? ImeState.Precomposition ?: return Next(state, emptyList())
        if (s0.candidateUi.bgText.isBlank()) return Next(state, emptyList())

        return commitBgAndKeepUlAsNextPreedit(s0, candidate.surface)
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

    /**
     * Candidate UI同期（select中は呼び出し側で回避しているが保険でreturn）
     */
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

        val key = makeRequestKey(bgText, s0.composing.length, s0.cursor)
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
        val ranges = PreeditRenderer.computeRanges(s.composing.length, s.cursor, s.decor)
        val bg = ranges.bg.clamp(s.composing.length)
        if (bg.isEmpty()) return null
        return runCatching { s.composing.substring(bg.start, bg.endExclusive) }.getOrNull()
    }

    private fun bgRange(s: ImeState.Precomposition, text: String, cursor: Int): Range {
        val ranges = PreeditRenderer.computeRanges(text.length, cursor, s.decor)
        return ranges.bg.clamp(text.length)
    }

    /**
     * baseComposing/baseCursor を基準に bg を candidateSurface に差し替えてプレビュー。
     */
    private fun buildPreviewFromBase(
        s: ImeState.Precomposition,
        candidateSurface: String,
        selectedIndex: Int
    ): ImeState.Precomposition {
        val baseText = s.candidateUi.baseComposing ?: s.composing
        val baseCursor = s.candidateUi.baseCursor.coerceIn(0, baseText.length)

        val bg = bgRange(s, baseText, baseCursor)
        if (bg.isEmpty()) return s

        val replaced = StringBuilder(baseText).apply {
            replace(bg.start, bg.endExclusive, candidateSurface)
        }.toString()

        val newCursor = (bg.start + candidateSurface.length).coerceIn(0, replaced.length)

        return s.copy(
            composing = replaced,
            cursor = newCursor,
            candidateUi = s.candidateUi.copy(
                selectedIndex = selectedIndex,
                baseComposing = baseText,
                baseCursor = baseCursor
            )
        )
    }

    /**
     * ★今回の本質:
     * - bg部分（候補）を Editor に commit して「確定」
     * - ul部分が残っているなら、それを “次の preedit” として残す
     *   その際 cursor を末尾にして (SplitAtCursorなら) ul→bg に移った状態にする
     *
     * 重要: ここでは「元の preedit 全文を bg にする」ことはしない。
     */
    private fun commitBgAndKeepUlAsNextPreedit(
        s: ImeState.Precomposition,
        candidateSurface: String
    ): Next {
        val baseText = s.candidateUi.baseComposing ?: s.composing
        val baseCursor =
            (if (s.candidateUi.baseComposing != null) s.candidateUi.baseCursor else s.cursor)
                .coerceIn(0, baseText.length)

        val bg = bgRange(s, baseText, baseCursor)
        if (bg.isEmpty()) {
            // bgが空なら何もしない
            return Next(s, emptyList())
        }

        // ul = bgの後ろ（SplitAtCursor 前提の自然な挙動）
        val ulText = if (bg.endExclusive < baseText.length) {
            baseText.substring(bg.endExclusive)
        } else {
            ""
        }

        val nextComposing = ulText
        val nextCursor = nextComposing.length // ul→bg（次preeditでは全部bgになる）

        val s1 = s.copy(
            composing = nextComposing,
            cursor = nextCursor,
            candidateUi = s.candidateUi.copy(
                selectMode = false,
                selectedIndex = -1,
                baseComposing = null,
                baseCursor = 0
            )
        )

        // 1) まず候補をcommitして確定
        // 2) その後、残りがあれば preedit を再描画（なければ ClearComposing）
        val effects = buildList {
            add(ImeEffect.CommitText(candidateSurface))

            if (nextComposing.isEmpty()) {
                add(ImeEffect.ClearComposing)
            } else {
                // composing表示を新しい残りに差し替える
                add(ImeEffect.RenderPreedit(s1.composing, s1.cursor, s1.decor))
            }
        }

        return syncCandidateUi(s1, effects)
    }

    private fun makeRequestKey(bgText: String, len: Int, cursor: Int): String =
        "$bgText#$len#$cursor"

    private data class TextEditResult(val text: String, val newCursor: Int)

    private fun insert(text: String, cursor: Int, ins: String): TextEditResult {
        val c = cursor.coerceIn(0, text.length)
        val out = StringBuilder(text.length + ins.length)
            .append(text.substring(0, c))
            .append(ins)
            .append(text.substring(c))
            .toString()
        return TextEditResult(out, c + ins.length)
    }

    private fun deleteBeforeCursor(text: String, cursor: Int): TextEditResult {
        val c = cursor.coerceIn(0, text.length)
        if (c == 0) return TextEditResult(text, 0)
        val out = StringBuilder(text.length - 1)
            .append(text.substring(0, c - 1))
            .append(text.substring(c))
            .toString()
        return TextEditResult(out, c - 1)
    }
}
