package com.dove.app.settings

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {
    private const val PREFS_NAME = "dove_settings"
    private const val KEY_TRANSMISSION_FPS = "transmission_fps"
    private const val KEY_CAMERA_RES = "camera_res"
    private const val KEY_IS_MATTE_BLACK = "is_matte_black"
    private const val KEY_GRID_LAYOUT = "grid_layout"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getTransmissionFps(context: Context): Float {
        return getPrefs(context).getFloat(KEY_TRANSMISSION_FPS, 26f)
    }

    fun setTransmissionFps(context: Context, fps: Float) {
        getPrefs(context).edit().putFloat(KEY_TRANSMISSION_FPS, fps.coerceIn(8f, 30f)).apply()
    }

    fun getCameraResolution(context: Context): String {
        return getPrefs(context).getString(KEY_CAMERA_RES, "1080p") ?: "1080p"
    }

    fun setCameraResolution(context: Context, resolution: String) {
        getPrefs(context).edit().putString(KEY_CAMERA_RES, resolution).apply()
    }

    fun isMatteBlack(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_MATTE_BLACK, true)
    }

    fun setMatteBlack(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IS_MATTE_BLACK, enabled).apply()
    }

    fun getGridLayout(context: Context): String {
        return getPrefs(context).getString(KEY_GRID_LAYOUT, "1x1") ?: "1x1"
    }

    fun setGridLayout(context: Context, layout: String) {
        getPrefs(context).edit().putString(KEY_GRID_LAYOUT, layout).apply()
    }
}
