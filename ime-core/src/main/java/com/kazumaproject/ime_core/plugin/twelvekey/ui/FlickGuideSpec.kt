package com.kazumaproject.ime_core.plugin.twelvekey.ui

/**
 * What to show while user is interacting with a key.
 *
 * - normal: shown on ACTION_DOWN immediately
 * - longPress: replace layer when long-press is triggered
 */
data class FlickGuideSpec(
    val normal: Layer,
    val longPress: Layer? = null,
) {
    data class Layer(
        val center: String,
        val up: String?,
        val right: String?,
        val down: String?,
        val left: String?,
    )
}
