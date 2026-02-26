package com.kazumaproject.ime_core.extensions

/**
 * ひらがなをカタカナに変換する（ひらがな以外はそのまま）
 * 例: "こんにちはABC123" -> "コンニチハABC123"
 */
fun String.toKatakana(): String = buildString(length) {
    for (ch in this@toKatakana) {
        append(
            if (ch in '\u3041'..'\u3096') { // ぁ..ゖ (ひらがな)
                (ch.code + 0x60).toChar()   // カタカナへ
            } else {
                ch
            }
        )
    }
}
