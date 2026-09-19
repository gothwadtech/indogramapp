package com.gothwad.grixchat.ui

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import java.util.UUID

class GrixJavascriptInterface(
    private val context: Context,
    private val viewModel: GrixViewModel
) {
    private val tag = "GrixJavascriptInterface"
    private val appToken = "grix_app_tok_" + UUID.randomUUID().toString().substring(0, 8)

    /**
     * Trigger a native Android push/local notification from JavaScript.
     * JavaScript call: window.GrixApp.postNotification("Group Chat", "Alice sent a photo");
     */
    @JavascriptInterface
    fun postNotification(title: String, message: String) {
        Log.d(tag, "postNotification: Title=$title, Message=$message")
        viewModel.triggerLocalNotification(title, message)
    }

    /**
     * Allows website to request a push registration token.
     * JavaScript call: var token = window.GrixApp.getPushToken();
     */
    @JavascriptInterface
    fun getPushToken(): String {
        val sharedPrefs = context.getSharedPreferences("grix_prefs", Context.MODE_PRIVATE)
        val cachedToken = sharedPrefs.getString("fcm_token", null)
        Log.d(tag, "getPushToken requested. Cached token available: ${cachedToken != null}")
        return cachedToken ?: appToken
    }

    /**
     * Check if device is connected to internet.
     * JavaScript call: var online = window.GrixApp.isDeviceOnline();
     */
    @JavascriptInterface
    fun isDeviceOnline(): Boolean {
        return viewModel.isOnline.value
    }

    /**
     * Save an offline draft from the web app client.
     * JavaScript call: window.GrixApp.saveOfflineDraft("Draft text goes here");
     */
    @JavascriptInterface
    fun saveOfflineDraft(content: String) {
        Log.d(tag, "saveOfflineDraft: $content")
        viewModel.saveDraft(content)
    }

    /**
     * Show a simple toast message.
     * JavaScript call: window.GrixApp.showToast("Logged in successfully!");
     */
    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Notify native Android container of a theme change with dark parameter (boolean).
     * JavaScript call: window.GrixApp.setTheme(true);
     */
    @JavascriptInterface
    fun setTheme(isDark: Boolean) {
        Log.d(tag, "setTheme(Boolean) received: isDark=$isDark")
        viewModel.setDarkThemeOverride(isDark)
    }

    /**
     * Notify native Android container of a theme change with theme name (string).
     * JavaScript call: window.GrixApp.setTheme("dark"); or window.GrixApp.setTheme("light");
     */
    @JavascriptInterface
    fun setTheme(theme: String) {
        val isDark = when (theme.lowercase().trim()) {
            "dark" -> true
            "light" -> false
            else -> null
        }
        Log.d(tag, "setTheme(String) received: theme=$theme -> mapped isDark=$isDark")
        viewModel.setDarkThemeOverride(isDark)
    }
}
