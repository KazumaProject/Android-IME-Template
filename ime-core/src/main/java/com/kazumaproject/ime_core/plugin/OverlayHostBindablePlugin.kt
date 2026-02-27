package com.kazumaproject.ime_core.plugin

import android.widget.FrameLayout

/**
 * Plugin that wants to place an overlay outside of pluginContainer
 * (e.g., on ImeResizableView.root), so it can stay above CandidateBar etc.
 */
interface OverlayHostBindablePlugin {
    fun bindOverlayHost(overlayHost: FrameLayout)
}
