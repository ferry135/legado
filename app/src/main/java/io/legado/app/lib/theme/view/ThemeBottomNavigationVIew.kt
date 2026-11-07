package io.legado.app.lib.theme.view

import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.legado.app.databinding.ViewNavigationBadgeBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.text.BadgeView
import io.legado.app.utils.ColorUtils

class ThemeBottomNavigationVIew(context: Context, attrs: AttributeSet) :
    BottomNavigationView(context, attrs) {

    private var lastUiMode = context.resources.configuration.uiMode

    init {
        upTheme()

        if (AppConfig.isEInkMode) {
            isItemHorizontalTranslationEnabled = false
            itemBackground = ColorDrawable(Color.TRANSPARENT)
        }

        ViewCompat.setOnApplyWindowInsetsListener(this, null)
    }

    fun upTheme() {
        val bgColor = context.bottomBackground
        setBackgroundColor(bgColor)
        //与页面保持同层，无投影分层感
        elevation = 0f
        val textIsDark = ColorUtils.isColorLight(bgColor)
        val textColor = context.getSecondaryTextColor(textIsDark)
        // 选中态随主色调（M3 语义；上游原为强调色——M3 重构后主色调在全应用几乎无
        // 可见消费者，改主色无从体现）。墨水屏维持上游强调色（applyTheme 墨水屏
        // 分支主色为白，选中态需保持深字对比）
        val selectedColor = if (AppConfig.isEInkMode) {
            ThemeStore.accentColor(context)
        } else {
            context.primaryColor
        }
        val colorStateList = Selector.colorBuild()
            .setDefaultColor(textColor)
            .setSelectedColor(selectedColor).create()
        itemIconTintList = colorStateList
        itemTextColor = colorStateList
        // 胶囊指示条 12% 主色
        if (!AppConfig.isEInkMode) {
            itemActiveIndicatorColor = ColorStateList.valueOf(
                ColorUtils.withAlpha(selectedColor, 0.12f)
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        //声明了 uiMode configChanges 的 Activity 不会被重建，深浅模式切换时在这里重新着色
        if (newConfig.uiMode != lastUiMode) {
            lastUiMode = newConfig.uiMode
            upTheme()
        }
    }

    fun addBadgeView(index: Int): BadgeView {
        //获取底部菜单view
        val menuView = getChildAt(0) as ViewGroup
        //获取第index个itemView
        val itemView = menuView.getChildAt(index) as ViewGroup
        val badgeBinding = ViewNavigationBadgeBinding.inflate(LayoutInflater.from(context))
        itemView.addView(badgeBinding.root)
        return badgeBinding.viewBadge
    }

}