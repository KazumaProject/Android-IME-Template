package com.kazumaproject.ime_core.mvi

/**
 * Low-level actions executed directly against InputConnection/Editor.
 *
 * This is intentionally minimal and stable. Add new variants only when
 * they map cleanly to InputConnection / EditorInfo operations.
 */
sealed class KeyActionRaw {
    /**
     * Sends a keycode via ImeHost.sendDownUpKeyEvents(keyCode).
     * Example: KeyEvent.KEYCODE_DEL, KEYCODE_DPAD_LEFT, etc.
     */
    data class SendKeyCode(val keyCode: Int) : KeyActionRaw()

    /**
     * Calls InputConnection.performEditorAction(actionId).
     * Example: EditorInfo.IME_ACTION_SEARCH, IME_ACTION_GO, etc.
     */
    data class PerformEditorAction(val actionId: Int) : KeyActionRaw()

    /**
     * Convenience: commits text directly (bypassing reducer composition logic).
     * Prefer KeyboardAction.InputText when you want to respect composition mode.
     */
    data class CommitText(val text: String) : KeyActionRaw()
}
