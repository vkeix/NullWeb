package dev.vkeix.nullweb

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Factory for creating consistently-configured WebViews with Eruda support.
 */
object WebViewFactory {
    
    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context): WebView {
        return WebView(context).apply {
            val settings = this.settings
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.allowFileAccess = true
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = true
            
            // Enable dark mode if configured
            if (context.getString(R.string.mode) == "night") {
                val supportForceDarkStrategy = WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)
                val supportForceDark = WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)
                if (supportForceDarkStrategy && supportForceDark) {
                    WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_ON)
                    WebSettingsCompat.setForceDarkStrategy(
                        settings,
                        WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY
                    )
                }
            }
        }
    }
}
