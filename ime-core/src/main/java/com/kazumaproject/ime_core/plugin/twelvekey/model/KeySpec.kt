package com.kazumaproject.ime_core.plugin.twelvekey.model

data class KeySpec(
    val label: String,
    val row: Int,
    val col: Int,
    val rowSpan: Int = 1,
    val colSpan: Int = 1,
    val outputs: Map<KeyGesture, KeyOutput> = emptyMap(),
)
