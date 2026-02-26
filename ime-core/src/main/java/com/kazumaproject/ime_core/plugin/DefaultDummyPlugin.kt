package com.kazumaproject.ime_core.plugin

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.TextView

class DefaultDummyPlugin : ImeViewPlugin {
    override fun createView(context: Context): View {
        return TextView(context).apply {
            text = "Dummy IME Plugin View"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
    }
}
