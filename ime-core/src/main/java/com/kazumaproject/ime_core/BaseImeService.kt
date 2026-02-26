package com.kazumaproject.ime_core

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.ViewGroup
import com.kazumaproject.ime_core.candidates.DefaultCandidateProvider
import com.kazumaproject.ime_core.mvi.CompositionMode
import com.kazumaproject.ime_core.mvi.ImeAction
import com.kazumaproject.ime_core.mvi.ImeEffectHandler
import com.kazumaproject.ime_core.mvi.ImeHost
import com.kazumaproject.ime_core.mvi.ImeStore
import com.kazumaproject.ime_core.mvi.KeyboardAction
import com.kazumaproject.ime_core.plugin.ActionBindablePlugin
import com.kazumaproject.ime_core.plugin.ImeViewPlugin
import com.kazumaproject.ime_core.plugin.TwelveKeyKeyboardPlugin
import com.kazumaproject.ime_core.resize.ImeResizeOverlay
import com.kazumaproject.ime_core.state.CandidateUiMode
import com.kazumaproject.ime_core.state.ImeState
import com.kazumaproject.ime_core.ui.CandidateBarView
import kotlin.math.roundToInt

open class BaseImeService : InputMethodService(), ImeHost {

    private var ui: ImeResizableView? = null
    private var overlay: ImeResizeOverlay? = null

    private var plugin: ImeViewPlugin? = null
    private var pluginView: View? = null

    private lateinit var store: ImeStore
    private lateinit var effectHandler: ImeEffectHandler

    private var currentW = 0
    private var currentH = 0
    private var offsetX = 0
    private var offsetY = 0

    protected open fun createPlugin(): ImeViewPlugin = TwelveKeyKeyboardPlugin()
    protected open fun moveHandleIconResId(): Int? = null
    protected open fun candidatePlacement(): CandidateBarView.Placement =
        CandidateBarView.Placement.TOP

    override fun inputConnection() = currentInputConnection
    override fun editorInfo() = currentInputEditorInfo
    override fun sendDownUpKeyEvents(keyCode: Int) = super.sendDownUpKeyEvents(keyCode)

    override fun onCreate() {
        super.onCreate()

        lateinit var tmpStore: ImeStore
        effectHandler = ImeEffectHandler(
            host = this,
            candidateProvider = DefaultCandidateProvider(),
            dispatchInternal = { act -> tmpStore.dispatch(act) }
        )
        tmpStore = ImeStore(initialState = ImeState.Precomposition(), effectHandler = effectHandler)
        store = tmpStore
    }

    override fun onCreateInputView(): View {
        val view = ImeResizableView(this)
        ui = view

        view.setCandidatePlacement(candidatePlacement())
        view.setTopRightButtonsVisible(false)

        store.addListener { s -> renderFromState(s) }

        view.candidateBar.onClickResize = { toggleResizeMode() }
        view.candidateBar.onClickToggleMode = { toggleImeMode() }
        view.candidateBar.onClickDefaultSize = {
            ImeSizePrefs.resetToDefaultForCurrentOrientation(this)
            applyFromPrefs()
            overlay?.updateLayout()
        }
        view.candidateBar.onClickSettings = { /* hook later */ }

        // ★タップは reducer へ（commit/carry を一元化）
        view.candidateBar.onCandidateClick = { cand ->
            store.dispatch(ImeAction.CandidateChosen(cand))
        }

        plugin = createPlugin()
        installPluginView()
        (plugin as? ActionBindablePlugin)?.bind { action -> store.dispatchUi(action) }

        applyFromPrefs()

        overlay = ImeResizeOverlay(
            host = this,
            root = view.root,
            content = view.content,
            onResize = { w, h ->
                currentW = w; currentH = h
                clampOffsets()
                view.applyLayoutPx(currentW, currentH, offsetX, offsetY)
            },
            onMoveDelta = { dx, dy ->
                offsetX += dx; offsetY += dy
                clampOffsets()
                view.applyLayoutPx(currentW, currentH, offsetX, offsetY)
            },
            onCommit = {
                persistSizeForCurrentOrientation()
                persistPositionForCurrentOrientation()
            }
        )
        overlay?.setMoveHandleIconRes(moveHandleIconResId())
        overlay?.setEnabled(false)

        renderFromState(store.state)
        return view.root
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        applyFromPrefs()
        overlay?.updateLayout()
        store.clearPreeditIfAny()
        plugin?.onStartInputView(info, restarting)
    }

    override fun onDestroy() {
        super.onDestroy()
        effectHandler.dispose()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyFromPrefs()
        overlay?.updateLayout()
        renderFromState(store.state)
    }

    private fun renderFromState(state: ImeState) {
        val view = ui ?: return

        if (state is ImeState.Precomposition) {
            val bgText = state.candidateUi.bgText
            val showSuggestion =
                (state.candidateUi.mode == CandidateUiMode.SUGGESTION) && bgText.isNotBlank()

            if (showSuggestion) {
                view.candidateBar.setMode(CandidateBarView.Mode.SUGGESTION)
                view.candidateBar.setLoading(state.candidateUi.isLoading)
                view.candidateBar.submitCandidates(state.candidateUi.candidates)
                view.candidateBar.setSelectedIndex(if (state.candidateUi.selectMode) state.candidateUi.selectedIndex else -1)
            } else {
                view.candidateBar.setMode(CandidateBarView.Mode.CONTROLS)
                view.candidateBar.setLoading(false)
                view.candidateBar.setSelectedIndex(-1)
            }
        } else {
            view.candidateBar.setMode(CandidateBarView.Mode.CONTROLS)
            view.candidateBar.setLoading(false)
            view.candidateBar.setSelectedIndex(-1)
        }
    }

    private fun toggleResizeMode() {
        val enabled = !(overlay?.isEnabled() ?: false)
        overlay?.setEnabled(enabled)
        if (!enabled) {
            persistSizeForCurrentOrientation()
            persistPositionForCurrentOrientation()
        }
    }

    private fun toggleImeMode() {
        val cur = store.state
        val next = when (cur) {
            ImeState.Direct -> CompositionMode.PRECOMPOSITION
            is ImeState.Precomposition -> CompositionMode.DIRECT
        }
        store.dispatchUi(KeyboardAction.SetCompositionMode(next))
    }

    private fun installPluginView() {
        val view = ui ?: return
        val p = plugin ?: return
        pluginView?.let { old -> view.pluginContainer.removeView(old) }
        val v = p.createView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        pluginView = v
        view.pluginContainer.addView(v)
    }

    // ---- size/position ----

    private fun applyFromPrefs() {
        val view = ui ?: return
        val sizePref = ImeSizePrefs.resolveUiSize(this)

        val screenW = resources.displayMetrics.widthPixels
        val targetH = dp(sizePref.heightDp)
        val targetW = (screenW * sizePref.contentWidthRatio).roundToInt()

        currentW = targetW
        currentH = targetH

        val posPref = ImeSizePrefs.getPositionForCurrentOrientation(this)
        val restoredX = dp(posPref.offsetXdp)
        val restoredY = dp(posPref.offsetYdp)

        val defaultCentered = (posPref.offsetXdp == 0 && posPref.offsetYdp == 0)
        if (defaultCentered) {
            offsetX = ((screenW - currentW) / 2).coerceAtLeast(0)
            offsetY = ((rootHeightPx(currentH) - currentH) / 2).coerceAtLeast(0)
        } else {
            offsetX = restoredX
            offsetY = restoredY
        }

        clampOffsets()
        view.applyLayoutPx(currentW, currentH, offsetX, offsetY)
    }

    private fun rootHeightPx(contentHeightPx: Int): Int = contentHeightPx + dp(48)

    private fun clampOffsets() {
        val screenW = resources.displayMetrics.widthPixels
        val rootH = rootHeightPx(currentH)
        val maxX = (screenW - currentW).coerceAtLeast(0)
        val maxY = (rootH - currentH).coerceAtLeast(0)
        offsetX = offsetX.coerceIn(0, maxX)
        offsetY = offsetY.coerceIn(0, maxY)
    }

    private fun persistSizeForCurrentOrientation() {
        val screenW = resources.displayMetrics.widthPixels
        val widthRatio = (currentW.toFloat() / screenW.toFloat()).coerceIn(0.55f, 1.0f)
        val heightDp = pxToDp(currentH)
        ImeSizePrefs.setCustomForCurrentOrientation(
            this,
            heightDp = heightDp,
            widthRatio = widthRatio
        )
    }

    private fun persistPositionForCurrentOrientation() {
        ImeSizePrefs.setPositionForCurrentOrientation(
            this,
            offsetXdp = pxToDp(offsetX),
            offsetYdp = pxToDp(offsetY)
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    private fun pxToDp(px: Int): Int = (px / resources.displayMetrics.density).roundToInt()
}
