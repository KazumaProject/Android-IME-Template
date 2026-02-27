package com.kazumaproject.ime_core.plugin

import android.content.Context
import com.kazumaproject.ime_core.mvi.KeyboardAction
import com.kazumaproject.ime_core.plugin.twelvekey.model.KeyGesture
import com.kazumaproject.ime_core.plugin.twelvekey.model.KeyOutput
import com.kazumaproject.ime_core.plugin.twelvekey.model.KeySpec

class KanaTwelveKeyPlugin : TwelveKeyKeyboardPlugin() {

    override fun actionColumnItems(
        side: ActionSide,
        mode: LayoutMode
    ): List<ActionColumnItem> {
        val placement = placementFor(mode)
        if (placement == ActionPlacement.TOP || placement == ActionPlacement.BOTTOM) {
            return super.actionColumnItems(side, mode)
        }

        return when (placement) {
            ActionPlacement.RIGHT -> {
                if (side != ActionSide.RIGHT) return emptyList()
                listOf(
                    ActionColumnItem.Single(specBackspaceRepeat(), weight = 1f),
                    ActionColumnItem.Row(listOf(specCursorLeft(), specCursorRight()), weight = 1f),
                    ActionColumnItem.Single(specSpace(), weight = 1f),
                    ActionColumnItem.Single(specEnter(), weight = 2f),
                )
            }

            ActionPlacement.LEFT -> {
                if (side != ActionSide.LEFT) return emptyList()
                listOf(
                    ActionColumnItem.Single(specBackspaceRepeat(), weight = 1f),
                    ActionColumnItem.Row(listOf(specCursorLeft(), specCursorRight()), weight = 1f),
                    ActionColumnItem.Single(specSpace(), weight = 1f),
                    ActionColumnItem.Single(specEnter(), weight = 2f),
                )
            }

            ActionPlacement.LEFT_RIGHT -> {
                when (side) {
                    ActionSide.LEFT -> listOf(
                        ActionColumnItem.Single(specBackspaceRepeat(), weight = 1f),
                        ActionColumnItem.Row(
                            listOf(specCursorLeft(), specCursorRight()),
                            weight = 1f
                        ),
                        ActionColumnItem.Empty(weight = 1f),
                        ActionColumnItem.Empty(weight = 2f),
                    )

                    ActionSide.RIGHT -> listOf(
                        ActionColumnItem.Single(specSpace(), weight = 1f),
                        ActionColumnItem.Empty(weight = 1f),
                        ActionColumnItem.Empty(weight = 1f),
                        ActionColumnItem.Single(specEnter(), weight = 2f),
                    )

                    else -> emptyList()
                }
            }

            else -> super.actionColumnItems(side, mode)
        }
    }

    override fun keySpecs(context: Context): List<KeySpec> {
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
            if (small != null) put(
                KeyGesture.LONG_PRESS,
                KeyOutput.Action(KeyboardAction.InputText(small))
            )
        }
        return KeySpec(label = label, row = row, col = col, outputs = outputs)
    }

    private fun punctKey(label: String, row: Int, col: Int): KeySpec {
        val outputs = buildMap<KeyGesture, KeyOutput> {
            put(KeyGesture.TAP, KeyOutput.Action(KeyboardAction.InputText(label)))
            put(
                KeyGesture.LONG_PRESS,
                KeyOutput.Action(KeyboardAction.InputText(if (label == "、") "？" else "！"))
            )
            put(KeyGesture.FLICK_UP, KeyOutput.Action(KeyboardAction.InputText("「")))
            put(KeyGesture.FLICK_DOWN, KeyOutput.Action(KeyboardAction.InputText("」")))
        }
        return KeySpec(label = label, row = row, col = col, outputs = outputs)
    }
}
