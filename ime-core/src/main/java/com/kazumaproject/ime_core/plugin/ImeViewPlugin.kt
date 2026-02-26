package com.kazumaproject.ime_core.plugin

import android.content.Context
import android.view.View
import android.view.inputmethod.EditorInfo

/**
 * IMEの「中身View」を差し替えるためのPlugin。
 *
 * BaseImeService 側は:
 * - root/content の枠
 * - resize/move overlay
 * - 永続化
 * を担当し、contentの中身（キーボードUI）は plugin が提供する。
 */
interface ImeViewPlugin {

    /**
     * content 内に配置する View を生成して返す。
     * - 返した View は content(FrameLayout) の子として addView される。
     */
    fun createView(context: Context): View

    /**
     * 入力開始時。必要なら EditorInfo を見て UI を切り替える等。
     */
    fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {}

    /**
     * 入力終了時。リソース解放など。
     */
    fun onFinishInputView(finishingInput: Boolean) {}

    /**
     * 必要ならPlugin側で追加の overlay（例: 候補バー）を root に載せたい場合に使う。
     * デフォルトは何もしない。
     */
    fun onAttachedToImeRoot(rootView: View) {}

    /**
     * Resizeモード切替通知（Plugin側でUI変える場合に）
     */
    fun onResizeModeChanged(enabled: Boolean) {}
}
