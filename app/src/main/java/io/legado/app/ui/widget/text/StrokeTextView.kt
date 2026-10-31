package io.legado.app.ui.widget.text

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import io.legado.app.R
import io.legado.app.lib.theme.*
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor

@Suppress("unused")
open class StrokeTextView(context: Context, attrs: AttributeSet?) :
    AppCompatTextView(context, attrs) {

    private var radius = 1.dpToPx()
    private var softStroke = false
    private val isBottomBackground: Boolean

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.StrokeTextView)
        radius = typedArray.getDimensionPixelOffset(R.styleable.StrokeTextView_radius, radius)
        softStroke = typedArray.getBoolean(R.styleable.StrokeTextView_softStroke, false)
        isBottomBackground =
            typedArray.getBoolean(R.styleable.StrokeTextView_isBottomBackground, false)
        typedArray.recycle()
        upBackground()
    }

    fun setRadius(radius: Int) {
        this.radius = radius.dpToPx()
        upBackground()
    }

    private fun upBackground() {
        when {
            isInEditMode -> {
                background = Selector.shapeBuild()
                    .setCornerRadius(radius)
                    .setStrokeWidth(1.dpToPx())
                    .setDisabledStrokeColor(context.getCompatColor(R.color.md_grey_500))
                    .setDefaultStrokeColor(context.getCompatColor(R.color.secondaryText))
                    .setSelectedStrokeColor(context.getCompatColor(R.color.accent))
                    .setPressedBgColor(context.getCompatColor(R.color.transparent30))
                    .create()
                setTextColor(
                    Selector.colorBuild()
                        .setDefaultColor(context.getCompatColor(R.color.secondaryText))
                        .setSelectedColor(context.getCompatColor(R.color.accent))
                        .setDisabledColor(context.getCompatColor(R.color.md_grey_500))
                        .create()
                )
            }
            isBottomBackground -> {
                val isLight = ColorUtils.isColorLight(context.bottomBackground)
                // 13-read-style#05：softStroke 静息描边降为 25% ink（六入口 chip 用，档位取 InkGradient），其余不变
                val strokeColor = if (softStroke) {
                    ColorUtils.adjustAlpha(context.getPrimaryTextColor(isLight), InkGradient.STROKE_MUTED)
                } else {
                    context.getPrimaryTextColor(isLight)
                }
                background = Selector.shapeBuild()
                    .setCornerRadius(radius)
                    .setStrokeWidth(1.dpToPx())
                    .setDisabledStrokeColor(context.getCompatColor(R.color.md_grey_500))
                    .setDefaultStrokeColor(strokeColor)
                    .setSelectedStrokeColor(context.accentColor)
                    .setPressedBgColor(context.getCompatColor(R.color.transparent30))
                    .create()
                setTextColor(
                    Selector.colorBuild()
                        .setDefaultColor(context.getPrimaryTextColor(isLight))
                        .setSelectedColor(context.accentColor)
                        .setDisabledColor(context.getCompatColor(R.color.md_grey_500))
                        .create()
                )
            }
            else -> {
                background = Selector.shapeBuild()
                    .setCornerRadius(radius)
                    .setStrokeWidth(1.dpToPx())
                    .setDisabledStrokeColor(context.getCompatColor(R.color.md_grey_500))
                    .setDefaultStrokeColor(ThemeStore.textColorSecondary(context))
                    .setSelectedStrokeColor(ThemeStore.accentColor(context))
                    .setPressedBgColor(context.getCompatColor(R.color.transparent30))
                    .create()
                setTextColor(
                    Selector.colorBuild()
                        .setDefaultColor(ThemeStore.textColorSecondary(context))
                        .setSelectedColor(ThemeStore.accentColor(context))
                        .setDisabledColor(context.getCompatColor(R.color.md_grey_500))
                        .create()
                )
            }
        }
    }
}
