package io.legado.app.lib.theme

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx

/**
 * 底部弹层 ink 派生三元组（弹层底 [bg] / 主文字 [ink] / 次要文字 [secondary]），
 * 收敛 ReadStyleDialog / TipConfigDialog / PaddingConfigDialog / FontSelectDialog 各自重复的
 * 「bottomBackground → isColorLight → getPrimaryTextColor/getOnSurfaceVariant → setSheetBackground」
 * 派生块（13-read-style#22 语言：标签 on_surface_variant、hairline 12%、行卡 8%/12dp、值胶囊 10%/8dp）。
 *
 * 高频档位 Drawable 构建器（[divider]/[rowCard]/[chip]）一并复用：档位仍取 [InkGradient] 梯度表，
 * 调整档位只改梯度表与构造器两处。弹层底色仍由调用方 setSheetBackground(bg) 指定圆角形态。
 *
 * [context] 记录构建方（Context.sheetInk() 的接收者），供 [rowCardSelectable] 内部解析按压态，
 * 调用点不再各自 ThemeUtils.resolveDrawable。
 */
class SheetInk(
    private val context: Context,
    val bg: Int,
    val ink: Int,
    val secondary: Int,
) {
    /** 组间 hairline：ink 12%（InkGradient.DIVIDER） */
    fun divider(): ColorDrawable = ColorDrawable(ColorUtils.adjustAlpha(ink, InkGradient.DIVIDER))

    /** 行卡：ink 8% 容器底 + 12dp 圆角（InkGradient.CONTAINER） */
    fun rowCard(): GradientDrawable = inkContainer(12, InkGradient.CONTAINER)

    /**
     * 可点行卡：行卡底叠主题按压态 selectableItemBackground（LayerList），
     * 保留整行可点行的触控反馈（同 13-read-style#17 行语言）；解析失败时仅行卡底。
     * 按压态以构建本 SheetInk 的 Context 解析——各弹窗行视图与弹层同源 context，结果一致。
     */
    fun rowCardSelectable(): Drawable {
        val press = ThemeUtils.resolveDrawable(context, android.R.attr.selectableItemBackground)
        if (press == null) return rowCard()
        return LayerDrawable(arrayOf(rowCard(), press))
    }

    /** 值胶囊：ink 10% 容器底 + 8dp 圆角（InkGradient.CHIP） */
    fun chip(): GradientDrawable = inkContainer(8, InkGradient.CHIP)

    private fun inkContainer(
        radiusDp: Int,
        alpha: Float,
    ) = GradientDrawable().apply {
        cornerRadius = radiusDp.dpToPx().toFloat()
        setColor(ColorUtils.adjustAlpha(ink, alpha))
    }
}

fun Context.sheetInk(): SheetInk {
    val bg = bottomBackground
    val isLight = ColorUtils.isColorLight(bg)
    return SheetInk(
        context = this,
        bg = bg,
        ink = getPrimaryTextColor(isLight),
        secondary = getOnSurfaceVariant(isLight),
    )
}
