package org.astianx.midori.one

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

enum class AccentTone(val color: Long, val label: Int) {
    JADE(0xFF236B56, R.string.style_jade), OCEAN(0xFF225E99, R.string.style_ocean),
    PLUM(0xFF765488, R.string.style_plum), CORAL(0xFF9C4938, R.string.style_coral),
    AMBER(0xFF805B16, R.string.style_amber), GRAPHITE(0xFF45565B, R.string.style_graphite)
}
enum class AppTypeface(val label: Int) { MANROPE(R.string.style_manrope), SYSTEM(R.string.theme_system), SERIF(R.string.style_serif) }
enum class LauncherStyle(val component: String, val label: Int, val icon: Int) {
    ORIGINAL("LauncherDefault", R.string.style_original, R.mipmap.ic_launcher),
    JADE("LauncherJade", R.string.style_jade, R.mipmap.ic_launcher_jade),
    NIGHT("LauncherNight", R.string.theme_dark, R.mipmap.ic_launcher_night),
    OCEAN("LauncherOcean", R.string.style_ocean, R.mipmap.ic_launcher_ocean)
}

data class AppStyle(val accent: AccentTone = AccentTone.JADE, val typeface: AppTypeface = AppTypeface.MANROPE,
    val textScale: Int = 100, val pureBlack: Boolean = false) {
    fun toJson() = JSONObject().put("accent", accent.name).put("typeface", typeface.name)
        .put("textScale", textScale).put("pureBlack", pureBlack)
    companion object {
        fun fromJson(json: JSONObject) = AppStyle(AccentTone.valueOf(json.getString("accent")),
            AppTypeface.valueOf(json.getString("typeface")), json.getInt("textScale").also { require(it in 90..130) },
            json.getBoolean("pureBlack"))
    }
}
internal val LocalAppStyle = staticCompositionLocalOf { AppStyle() }

/** Only appearance values, never account metadata or credentials. */
internal class PersonalizationStore(context: Context) {
    private val prefs = context.getSharedPreferences("midori_personalization", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(runCatching { AppStyle.fromJson(JSONObject(prefs.getString("style", "")!!)) }.getOrDefault(AppStyle()))
    val state: StateFlow<AppStyle> = mutable
    fun save(value: AppStyle) {
        require(value.textScale in 90..130)
        prefs.edit().putString("style", value.toJson().toString()).apply()
        mutable.value = value
    }
}

/** API 33+ batches changes so a launcher always has exactly one enabled entry. */
internal object LauncherIcons {
    fun current(context: Context): LauncherStyle = LauncherStyle.entries.firstOrNull {
        context.packageManager.getComponentEnabledSetting(android.content.ComponentName(context, "${context.packageName}.${it.component}")) ==
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    } ?: LauncherStyle.ORIGINAL

    fun select(context: Context, style: LauncherStyle) {
        val pm = context.packageManager
        pm.setComponentEnabledSettings(LauncherStyle.entries.map {
            android.content.pm.PackageManager.ComponentEnabledSetting(
                android.content.ComponentName(context, "${context.packageName}.${it.component}"),
                if (it == style) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP)
        })
    }
}
