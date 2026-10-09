package me.weishu.kernelsu.wallpaper

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList

/** Android 12 locale fallback, using the protocol's language tags. */
object ThemeLanguage {
    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return context
        val tags = context.getSharedPreferences("settings", 0).getString("fpt_app_language", "").orEmpty()
        if (tags.isEmpty()) return context
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(tags))
        })
    }
}
