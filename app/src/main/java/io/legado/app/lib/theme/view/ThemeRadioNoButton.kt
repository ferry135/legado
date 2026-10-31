package io.legado.app.lib.theme.view

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.TooltipCompat
import io.legado.app.R
import io.legado.app.lib.theme.InkGradient
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor

class ThemeRadioNoButton(context: Context, attrs: AttributeSet) :
    AppCompatRadioButton(context, attrs) {

    private val isBottomBackground: Boolean
    private val cornerRadius: Int

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.ThemeRadioNoButton)
        isBottomBackground =
            typedArray.getBoolean(R.styleable.ThemeRadioNoButton_isBottomBackground, false)
        // 13-read-style#13：胶囊单选组圆角 attr，默认 2dp 保持旧行为（dialog_search_scope 等不受影响）
        cornerRadius =
            typedArray.getDimensionPixelSize(
                R.styleable.ThemeRadioNoButton_cornerRadius,
                2.dpToPx()
            )
        typedArray.recycle()
        initTheme()
        TooltipCompat.setTooltipText(this, text)
    }

    private fun initTheme() {
        when {
            isInEditMode -> Unit
            isBottomBackground -> {
                val accentColor = context.accentColor
                val isLight = ColorUtils.isColorLight(context.bottomBackground)
                val textColor = context.getPrimaryTextColor(isLight)
                val checkedTextColor = if (ColorUtils.isColorLight(accentColor)) {
                    Color.BLACK
                } else {
                    Color.WHITE
                }
                // 13-read-style#13：胶囊单选——未选 = ink 8% 底 + 自适应 primaryText 字（8% 胶囊叠层上
                // 62% ink/secondary 实算 4.18/4.25:1 < 4.5，故不弱化字色，与 dialog_search_scope 未选口径一致），
                // 选中 = 主色实底 + 白字（accent 过亮回落黑字，逻辑保留），均无描边，替换旧 2dp 直角描边块
                background = Selector.shapeBuild()
                    .setCornerRadius(cornerRadius)
                    .setDefaultBgColor(ColorUtils.adjustAlpha(textColor, InkGradient.CONTAINER))
                    .setCheckedBgColor(accentColor)
                    .create()
                setTextColor(
                    Selector.colorBuild()
                        .setDefaultColor(textColor)
                        .setCheckedColor(checkedTextColor)
                        .create()
                )
                // 13-read-style#13：选中加粗、未选常规，随勾选态切换（设计稿 650 字重，Typeface.BOLD 为 minSdk 21 下平台最接近实现）
                setOnCheckedChangeListener { button, isChecked ->
                    button.setTypeface(button.typeface, if (isChecked) Typeface.BOLD else Typeface.NORMAL)
                }
            }
            else -> {
                val accentColor = context.accentColor
                val defaultTextColor = context.getCompatColor(R.color.primaryText)
                val checkedTextColor = if (ColorUtils.isColorLight(accentColor)) {
                    Color.BLACK
                } else {
                    Color.WHITE
                }
                background = Selector.shapeBuild()
                    .setCornerRadius(cornerRadius)
                    .setStrokeWidth(2.dpToPx())
                    .setCheckedBgColor(accentColor)
                    .setCheckedStrokeColor(accentColor)
                    .setDefaultStrokeColor(defaultTextColor)
                    .create()
                setTextColor(
                    Selector.colorBuild()
                        .setDefaultColor(defaultTextColor)
                        .setCheckedColor(checkedTextColor)
                        .create()
                )
            }
        }

    }

}
