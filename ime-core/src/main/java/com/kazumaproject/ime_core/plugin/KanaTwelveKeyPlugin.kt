package com.kazumaproject.ime_core.plugin

import android.content.Context
import com.kazumaproject.ime_core.mvi.KeyboardAction
import com.kazumaproject.ime_core.plugin.twelvekey.model.KeyGesture
import com.kazumaproject.ime_core.plugin.twelvekey.model.KeyOutput
import com.kazumaproject.ime_core.plugin.twelvekey.model.KeySpec

/**
 * かな配列（12-key）
 * - タップ: 行頭（例: あ）
 * - フリック: 上=い / 右=う / 下=え / 左=お（慣例寄せ）
 * - 長押し: 小書き（例: ぁ）
 *
 * Enter は action column 側で heightDp を2倍にして「縦2つ分」を実現。
 */
class KanaTwelveKeyPlugin : TwelveKeyKeyboardPlugin() {

    override fun actionButtonsFor(side: ActionSide, mode: LayoutMode): List<ActionButtonSpec> {
        // デフォルトは RIGHT / LEFT_RIGHT なので、その想定で Enter を縦2つ分にする
        return when (placementFor(mode)) {
            ActionPlacement.RIGHT -> {
                if (side == ActionSide.RIGHT) {
                    listOf(
                        specCursorLeft(),
                        specCursorRight(),
                        specSpace(),
                        specBackspace(),
                        // ✅ 縦2つ分（44dp * 2 + 間の見た目分の余白）
                        specEnter(heightDp = 44 * 2 + 10)
                    )
                } else emptyList()
            }

            ActionPlacement.LEFT -> {
                if (side == ActionSide.LEFT) {
                    listOf(
                        specCursorLeft(),
                        specCursorRight(),
                        specSpace(),
                        specBackspace(),
                        specEnter(heightDp = 44 * 2 + 10)
                    )
                } else emptyList()
            }

            ActionPlacement.LEFT_RIGHT -> {
                when (side) {
                    ActionSide.LEFT -> listOf(specCursorLeft(), specCursorRight())
                    ActionSide.RIGHT -> listOf(
                        specSpace(),
                        specBackspace(),
                        specEnter(heightDp = 44 * 2 + 10)
                    )

                    else -> emptyList()
                }
            }

            // TOP/BOTTOM で縦2つ分は意味が変わるので、ここは通常サイズのまま
            ActionPlacement.TOP -> super.actionButtonsFor(side, mode)
            ActionPlacement.BOTTOM -> super.actionButtonsFor(side, mode)
        }
    }

    // 既存の specEnter() は heightDp を受け取れないので、ここで同名のヘルパを追加
    private fun specEnter(widthWeight: Float = 1f, heightDp: Int): ActionButtonSpec {
        return ActionButtonSpec(
            "return",
            KeyboardAction.Enter,
            widthWeight = widthWeight,
            heightDp = heightDp
        )
    }

    override fun keySpecs(context: Context): List<KeySpec> {
        // 4x3 を前提（row=0..3, col=0..2）
        // 配列:
        // [ あ  か  さ ]
        // [ た  な  は ]
        // [ ま  や  ら ]
        // [ わ  、  。 ]
        return listOf(
            kanaKey(
                "あ",
                row = 0,
                col = 0,
                up = "う",
                right = "え",
                down = "お",
                left = "い",
                small = "あ"
            ),
            kanaKey(
                "か",
                row = 0,
                col = 1,
                up = "く",
                right = "け",
                down = "こ",
                left = "き",
                small = "か"
            ),
            kanaKey(
                "さ",
                row = 0,
                col = 2,
                up = "す",
                right = "せ",
                down = "そ",
                left = "し",
                small = "さ"
            ),

            kanaKey(
                "た",
                row = 1,
                col = 0,
                up = "ち",
                right = "つ",
                down = "て",
                left = "と",
                small = "っ"
            ),
            kanaKey(
                "な",
                row = 1,
                col = 1,
                up = "に",
                right = "ぬ",
                down = "ね",
                left = "の",
                small = null
            ),
            kanaKey(
                "は",
                row = 1,
                col = 2,
                up = "ひ",
                right = "ふ",
                down = "へ",
                left = "ほ",
                small = null
            ),

            kanaKey(
                "ま",
                row = 2,
                col = 0,
                up = "み",
                right = "む",
                down = "め",
                left = "も",
                small = null
            ),
            kanaKey(
                "や",
                row = 2,
                col = 1,
                up = "ゃ",
                right = "ゆ",
                down = "ゅ",
                left = "よ",
                small = null
            ),
            kanaKey(
                "ら",
                row = 2,
                col = 2,
                up = "り",
                right = "る",
                down = "れ",
                left = "ろ",
                small = null
            ),

            kanaKey(
                "わ",
                row = 3,
                col = 0,
                up = "を",
                right = "ん",
                down = "ー",
                left = "ゎ",
                small = null
            ),

            punctKey("、", row = 3, col = 1),
            punctKey("。", row = 3, col = 2),
        )
    }

    private fun kanaKey(
        label: String,
        row: Int,
        col: Int,
        up: String,
        right: String,
        down: String,
        left: String,
        small: String?,
    ): KeySpec {
        val outputs = buildMap<KeyGesture, KeyOutput> {
            put(KeyGesture.TAP, KeyOutput.Action(KeyboardAction.InputText(label)))
            put(KeyGesture.FLICK_UP, KeyOutput.Action(KeyboardAction.InputText(up)))
            put(KeyGesture.FLICK_RIGHT, KeyOutput.Action(KeyboardAction.InputText(right)))
            put(KeyGesture.FLICK_DOWN, KeyOutput.Action(KeyboardAction.InputText(down)))
            put(KeyGesture.FLICK_LEFT, KeyOutput.Action(KeyboardAction.InputText(left)))

            if (small != null) {
                put(KeyGesture.LONG_PRESS, KeyOutput.Action(KeyboardAction.InputText(small)))
            }
        }

        return KeySpec(
            label = label,
            row = row,
            col = col,
            outputs = outputs
        )
    }

    private fun punctKey(label: String, row: Int, col: Int): KeySpec {
        // 句読点は単純に（タップ=そのまま、長押しで切り替え程度）
        val outputs = buildMap<KeyGesture, KeyOutput> {
            put(KeyGesture.TAP, KeyOutput.Action(KeyboardAction.InputText(label)))
            put(
                KeyGesture.LONG_PRESS,
                KeyOutput.Action(KeyboardAction.InputText(if (label == "、") "？" else "！"))
            )
            put(KeyGesture.FLICK_UP, KeyOutput.Action(KeyboardAction.InputText("「")))
            put(KeyGesture.FLICK_DOWN, KeyOutput.Action(KeyboardAction.InputText("」")))
        }

        return KeySpec(
            label = label,
            row = row,
            col = col,
            outputs = outputs
        )
    }
}
