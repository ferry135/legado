package io.legado.app.help.config

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.DisplayMetrics
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.constant.Theme
import io.legado.app.help.DefaultData
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.model.BookCover
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.hexString
import io.legado.app.utils.postEvent
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.stackBlur
import splitties.init.appCtx
import java.io.File

@Keep
object ThemeConfig {
    const val configFileName = "themeConfig.json"
    val configFilePath = FileUtils.getPath(appCtx.filesDir, configFileName)

    val configList: ArrayList<Config> by lazy {
        val cList = getConfigs() ?: DefaultData.themeConfigs
        ArrayList(cList)
    }

    fun getTheme() = when {
        AppConfig.isEInkMode -> Theme.EInk
        AppConfig.isNightTheme -> Theme.Dark
        else -> Theme.Light
    }

    fun isDarkTheme(): Boolean = getTheme() == Theme.Dark

    fun applyDayNight(context: Context) {
        applyTheme(context)
        initNightMode()
        BookCover.upDefaultCover()
        postEvent(EventBus.RECREATE, "")
    }

    fun applyDayNightInit(context: Context) {
        applyTheme(context)
        initNightMode()
    }

    private fun initNightMode() {
        val targetMode =
            if (AppConfig.isNightTheme) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
        AppCompatDelegate.setDefaultNightMode(targetMode)
    }

    fun getBgImage(context: Context, metrics: DisplayMetrics): Bitmap? {
        val bgCfg = when (getTheme()) {
            Theme.Light -> Pair(
                context.getPrefString(PreferKey.bgImage),
                context.getPrefInt(PreferKey.bgImageBlurring, 0),
            )

            Theme.Dark -> Pair(
                context.getPrefString(PreferKey.bgImageN),
                context.getPrefInt(PreferKey.bgImageNBlurring, 0),
            )

            else -> null
        } ?: return null
        if (bgCfg.first.isNullOrBlank()) return null
        val bgImage = BitmapUtils
            .decodeBitmap(bgCfg.first!!, metrics.widthPixels, metrics.heightPixels)
        if (bgCfg.second == 0) {
            return bgImage
        }
        return bgImage?.stackBlur(bgCfg.second)
    }

    fun upConfig() {
        getConfigs()?.forEach { config ->
            addConfig(config)
        }
    }

    fun save() {
        val json = GSON.toJson(configList)
        FileUtils.delete(configFilePath)
        FileUtils.createFileIfNotExist(configFilePath).writeText(json)
    }

    fun delConfig(index: Int) {
        configList.removeAt(index)
        save()
    }

    fun addConfig(json: String): Boolean {
        GSON.fromJsonObject<Config>(json.trim { it < ' ' }).getOrNull()
            ?.let {
                if (validateConfig(it)) {
                    addConfig(it)
                    return true
                }
            }
        return false
    }

    fun addConfig(newConfig: Config) {
        if (!validateConfig(newConfig)) {
            return
        }
        configList.forEachIndexed { index, config ->
            if (newConfig.themeName == config.themeName) {
                configList[index] = newConfig
                return
            }
        }
        configList.add(newConfig)
        save()
    }

    private fun validateConfig(config: Config): Boolean {
        try {
            config.primaryColor.toColorInt()
            config.accentColor.toColorInt()
            config.backgroundColor.toColorInt()
            config.bottomBackground.toColorInt()
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun getConfigs(): List<Config>? {
        val configFile = File(configFilePath)
        if (configFile.exists()) {
            kotlin.runCatching {
                val json = configFile.readText()
                return GSON.fromJsonArray<Config>(json).getOrThrow()
            }.onFailure {
                it.printOnDebug()
            }
        }
        return null
    }

    fun applyConfig(context: Context, config: Config) {
        try {
            val primary = Color.parseColor(config.primaryColor)
            val accent = Color.parseColor(config.accentColor)
            val background = Color.parseColor(config.backgroundColor)
            val bBackground = Color.parseColor(config.bottomBackground)
            if (config.isNightTheme) {
                context.putPrefInt(PreferKey.cNPrimary, primary)
                context.putPrefInt(PreferKey.cNAccent, accent)
                context.putPrefInt(PreferKey.cNBackground, background)
                context.putPrefInt(PreferKey.cNBBackground, bBackground)
            } else {
                context.putPrefInt(PreferKey.cPrimary, primary)
                context.putPrefInt(PreferKey.cAccent, accent)
                context.putPrefInt(PreferKey.cBackground, background)
                context.putPrefInt(PreferKey.cBBackground, bBackground)
            }
            AppConfig.isNightTheme = config.isNightTheme
            applyDayNight(context)
        } catch (e: Exception) {
            AppLog.put("设置主题出错\n$e", e, true)
        }
    }

    fun saveDayTheme(context: Context, name: String) {
        val primary =
            context.getPrefInt(PreferKey.cPrimary, context.getCompatColor(R.color.design_orange))
        val accent =
            context.getPrefInt(PreferKey.cAccent, context.getCompatColor(R.color.design_orange))
        val background =
            context.getPrefInt(PreferKey.cBackground, context.getCompatColor(R.color.md_grey_100))
        val bBackground =
            context.getPrefInt(PreferKey.cBBackground, context.getCompatColor(R.color.day_bottom_nav))
        val config = Config(
            themeName = name,
            isNightTheme = false,
            primaryColor = "#${primary.hexString}",
            accentColor = "#${accent.hexString}",
            backgroundColor = "#${background.hexString}",
            bottomBackground = "#${bBackground.hexString}",
        )
        addConfig(config)
    }

    fun saveNightTheme(context: Context, name: String) {
        val primary =
            context.getPrefInt(
                PreferKey.cNPrimary,
                context.getCompatColor(R.color.design_orange),
            )
        val accent =
            context.getPrefInt(
                PreferKey.cNAccent,
                context.getCompatColor(R.color.design_orange),
            )
        val background =
            context.getPrefInt(PreferKey.cNBackground, context.getCompatColor(R.color.md_grey_900))
        val bBackground =
            context.getPrefInt(PreferKey.cNBBackground, context.getCompatColor(R.color.night_bottom_nav))
        val config = Config(
            themeName = name,
            isNightTheme = true,
            primaryColor = "#${primary.hexString}",
            accentColor = "#${accent.hexString}",
            backgroundColor = "#${background.hexString}",
            bottomBackground = "#${bBackground.hexString}",
        )
        addConfig(config)
    }

    /**
     * 更新主题
     */
    fun applyTheme(context: Context) = with(context) {
        when {
            AppConfig.isEInkMode -> {
                ThemeStore.editTheme(this)
                    .primaryColor(Color.WHITE)
                    .accentColor(Color.BLACK)
                    .backgroundColor(Color.WHITE)
                    .bottomBackground(Color.WHITE)
                    .apply()
            }

            AppConfig.isNightTheme -> {
                // 被排除的历史默认值（上游旧配色，可能被旧版本/恢复备份写入 prefs）
                // 按未设置处理，并回写为实际生效的兜底色——否则设置页色板仍显示
                // 历史值而运行时已换成兜底色，两边对不上。
                // 「未设置」哨兵用 MIN_VALUE 而非 -1：#FFFFFF 的 Int 值恰为 -1，
                // 用 -1 会把用户设的纯白强调色（如 A屏黑）当成未设置兜底成橙色
                val unset = Int.MIN_VALUE
                val primary = getPrefInt(PreferKey.cNPrimary, unset)
                    .takeIf {
                        it != unset && it != getCompatColor(R.color.md_blue_grey_600) &&
                            it != getCompatColor(R.color.md_red_600)
                    }
                    ?: getCompatColor(R.color.design_orange).also {
                        putPrefInt(PreferKey.cNPrimary, it)
                    }
                val accent = getPrefInt(PreferKey.cNAccent, unset)
                    .takeIf {
                        it != unset && it != getCompatColor(R.color.md_deep_orange_800) &&
                            it != getCompatColor(R.color.md_red_600)
                    }
                    ?: getCompatColor(R.color.design_orange).also {
                        putPrefInt(PreferKey.cNAccent, it)
                    }
                var background = getPrefInt(PreferKey.cNBackground, unset)
                    .takeIf { it != unset && it != getCompatColor(R.color.md_grey_900) }
                    ?: getCompatColor(R.color.night_surface).also {
                        putPrefInt(PreferKey.cNBackground, it)
                    }
                if (ColorUtils.isColorLight(background)) {
                    background = getCompatColor(R.color.night_surface)
                    putPrefInt(PreferKey.cNBackground, background)
                }
                val bBackground = getPrefInt(PreferKey.cNBBackground, unset)
                    .takeIf {
                        it != unset && it != getCompatColor(R.color.md_grey_850) &&
                            it != getCompatColor(R.color.legacy_bottom_night)
                    }
                    ?: getCompatColor(R.color.night_bottom_nav).also {
                        putPrefInt(PreferKey.cNBBackground, it)
                    }
                ThemeStore.editTheme(this)
                    .primaryColor(ColorUtils.withAlpha(primary, 1f))
                    .accentColor(ColorUtils.withAlpha(accent, 1f))
                    .backgroundColor(ColorUtils.withAlpha(background, 1f))
                    .bottomBackground(ColorUtils.withAlpha(bBackground, 1f))
                    .apply()
            }

            else -> {
                // 同夜间分支：历史默认值回写兜底色，色板与运行时一致；
                // 未设置哨兵用 MIN_VALUE（-1 与纯白 #FFFFFF 冲突）
                val unset = Int.MIN_VALUE
                val primary = getPrefInt(PreferKey.cPrimary, unset)
                    .takeIf {
                        it != unset && it != getCompatColor(R.color.md_brown_500) &&
                            it != getCompatColor(R.color.md_red_600)
                    }
                    ?: getCompatColor(R.color.design_orange).also {
                        putPrefInt(PreferKey.cPrimary, it)
                    }
                val accent = getPrefInt(PreferKey.cAccent, unset)
                    .takeIf { it != unset && it != getCompatColor(R.color.md_red_600) }
                    ?: getCompatColor(R.color.design_orange).also {
                        putPrefInt(PreferKey.cAccent, it)
                    }
                var background = getPrefInt(PreferKey.cBackground, unset)
                    .takeIf { it != unset && it != getCompatColor(R.color.md_grey_100) }
                    ?: getCompatColor(R.color.day_surface).also {
                        putPrefInt(PreferKey.cBackground, it)
                    }
                if (!ColorUtils.isColorLight(background)) {
                    background = getCompatColor(R.color.day_surface)
                    putPrefInt(PreferKey.cBackground, background)
                }
                val bBackground = getPrefInt(PreferKey.cBBackground, unset)
                    .takeIf {
                        it != unset && it != getCompatColor(R.color.md_grey_200) &&
                            it != getCompatColor(R.color.legacy_bottom_day)
                    }
                    ?: getCompatColor(R.color.day_bottom_nav).also {
                        putPrefInt(PreferKey.cBBackground, it)
                    }
                ThemeStore.editTheme(this)
                    .primaryColor(ColorUtils.withAlpha(primary, 1f))
                    .accentColor(ColorUtils.withAlpha(accent, 1f))
                    .backgroundColor(ColorUtils.withAlpha(background, 1f))
                    .bottomBackground(ColorUtils.withAlpha(bBackground, 1f))
                    .apply()
            }
        }
    }

    @Keep
    data class Config(
        var themeName: String,
        var isNightTheme: Boolean,
        var primaryColor: String,
        var accentColor: String,
        var backgroundColor: String,
        var bottomBackground: String,
    ) {

        override fun hashCode(): Int = GSON.toJson(this).hashCode()

        override fun equals(other: Any?): Boolean {
            other ?: return false
            if (other is Config) {
                return other.themeName == themeName &&
                    other.isNightTheme == isNightTheme &&
                    other.primaryColor == primaryColor &&
                    other.accentColor == accentColor &&
                    other.backgroundColor == backgroundColor &&
                    other.bottomBackground == bottomBackground
            }
            return false
        }
    }
}
