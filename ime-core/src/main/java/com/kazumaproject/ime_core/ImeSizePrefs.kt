package com.kazumaproject.ime_core

import android.content.Context
import android.content.res.Configuration

object ImeSizePrefs {
    private const val PREF_NAME = "ime_framework_prefs"
    private const val KEY_MODE = "size_mode"

    private const val KEY_PORTRAIT_HEIGHT_DP = "portrait_height_dp"
    private const val KEY_PORTRAIT_WIDTH_RATIO = "portrait_width_ratio"
    private const val KEY_LANDSCAPE_HEIGHT_DP = "landscape_height_dp"
    private const val KEY_LANDSCAPE_WIDTH_RATIO = "landscape_width_ratio"

    private const val KEY_PORTRAIT_OFFSET_X_DP = "portrait_offset_x_dp"
    private const val KEY_PORTRAIT_OFFSET_Y_DP = "portrait_offset_y_dp"
    private const val KEY_LANDSCAPE_OFFSET_X_DP = "landscape_offset_x_dp"
    private const val KEY_LANDSCAPE_OFFSET_Y_DP = "landscape_offset_y_dp"

    enum class SizeMode { NORMAL, COMPACT, TALL, CUSTOM }

    data class UiSize(val heightDp: Int, val contentWidthRatio: Float)
    data class UiPos(val offsetXdp: Int, val offsetYdp: Int)

    private enum class DeviceOrientation { PORTRAIT, LANDSCAPE }

    fun isLandscape(context: Context): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun currentOrientation(context: Context): DeviceOrientation {
        return if (isLandscape(context)) DeviceOrientation.LANDSCAPE else DeviceOrientation.PORTRAIT
    }

    fun getMode(context: Context): SizeMode {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY_MODE, SizeMode.NORMAL.name) ?: SizeMode.NORMAL.name
        return runCatching { SizeMode.valueOf(raw) }.getOrDefault(SizeMode.NORMAL)
    }

    fun setMode(context: Context, mode: SizeMode) {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_MODE, mode.name).apply()
    }

    private fun maxRatioFor(@Suppress("UNUSED_PARAMETER") orientation: DeviceOrientation): Float =
        1.0f

    fun setCustomForCurrentOrientation(context: Context, heightDp: Int, widthRatio: Float) {
        setCustomSize(context, currentOrientation(context), heightDp, widthRatio)
    }

    private fun setCustomSize(
        context: Context,
        orientation: DeviceOrientation,
        heightDp: Int,
        widthRatio: Float
    ) {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val clampedHeight = heightDp.coerceIn(140, 520)
        val clampedRatio = widthRatio.coerceIn(0.55f, maxRatioFor(orientation))

        val (hKey, wKey) = when (orientation) {
            DeviceOrientation.PORTRAIT -> KEY_PORTRAIT_HEIGHT_DP to KEY_PORTRAIT_WIDTH_RATIO
            DeviceOrientation.LANDSCAPE -> KEY_LANDSCAPE_HEIGHT_DP to KEY_LANDSCAPE_WIDTH_RATIO
        }

        sp.edit()
            .putInt(hKey, clampedHeight)
            .putFloat(wKey, clampedRatio)
            .putString(KEY_MODE, SizeMode.CUSTOM.name)
            .apply()
    }

    private fun getCustomSize(context: Context, orientation: DeviceOrientation): UiSize {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val (hKey, wKey) = when (orientation) {
            DeviceOrientation.PORTRAIT -> KEY_PORTRAIT_HEIGHT_DP to KEY_PORTRAIT_WIDTH_RATIO
            DeviceOrientation.LANDSCAPE -> KEY_LANDSCAPE_HEIGHT_DP to KEY_LANDSCAPE_WIDTH_RATIO
        }

        val defaultHeight = if (orientation == DeviceOrientation.LANDSCAPE) 200 else 240
        val defaultRatio = 1.0f

        val h = sp.getInt(hKey, defaultHeight).coerceIn(140, 520)
        val r = sp.getFloat(wKey, defaultRatio).coerceIn(0.55f, maxRatioFor(orientation))
        return UiSize(h, r)
    }

    fun resolveUiSize(context: Context): UiSize {
        val orientation = currentOrientation(context)
        val base = when (getMode(context)) {
            SizeMode.NORMAL -> UiSize(240, 1.0f)
            SizeMode.COMPACT -> UiSize(180, 0.85f)
            SizeMode.TALL -> UiSize(320, 1.0f)
            SizeMode.CUSTOM -> getCustomSize(context, orientation)
        }
        return base.copy(
            contentWidthRatio = base.contentWidthRatio.coerceIn(
                0.55f,
                maxRatioFor(orientation)
            )
        )
    }

    fun setPositionForCurrentOrientation(context: Context, offsetXdp: Int, offsetYdp: Int) {
        val orientation = currentOrientation(context)
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val (xKey, yKey) = when (orientation) {
            DeviceOrientation.PORTRAIT -> KEY_PORTRAIT_OFFSET_X_DP to KEY_PORTRAIT_OFFSET_Y_DP
            DeviceOrientation.LANDSCAPE -> KEY_LANDSCAPE_OFFSET_X_DP to KEY_LANDSCAPE_OFFSET_Y_DP
        }

        sp.edit()
            .putInt(xKey, offsetXdp.coerceIn(-2000, 2000))
            .putInt(yKey, offsetYdp.coerceIn(-2000, 2000))
            .apply()
    }

    fun getPositionForCurrentOrientation(context: Context): UiPos {
        val orientation = currentOrientation(context)
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val (xKey, yKey) = when (orientation) {
            DeviceOrientation.PORTRAIT -> KEY_PORTRAIT_OFFSET_X_DP to KEY_PORTRAIT_OFFSET_Y_DP
            DeviceOrientation.LANDSCAPE -> KEY_LANDSCAPE_OFFSET_X_DP to KEY_LANDSCAPE_OFFSET_Y_DP
        }

        val x = sp.getInt(xKey, Int.MIN_VALUE)
        val y = sp.getInt(yKey, Int.MIN_VALUE)
        val ox = if (x == Int.MIN_VALUE) 0 else x
        val oy = if (y == Int.MIN_VALUE) 0 else y
        return UiPos(ox, oy)
    }

    /**
     * ★ Default（初期）サイズに戻す（縦/横別）
     * - サイズ: portrait=240dp, landscape=200dp, ratio=1.0
     * - 位置: (0,0) を保存して「次回はセンターから開始」扱いにする
     * - mode は CUSTOM にして、必ずこの値が反映されるようにする
     */
    fun resetToDefaultForCurrentOrientation(context: Context) {
        val orientation = currentOrientation(context)
        val defaultHeight = if (orientation == DeviceOrientation.LANDSCAPE) 200 else 240
        setCustomSize(context, orientation, heightDp = defaultHeight, widthRatio = 1.0f)
        setPositionForCurrentOrientation(context, offsetXdp = 0, offsetYdp = 0)
    }
}
