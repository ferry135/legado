package io.legado.app.ui.book.read.config

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.TextView
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getOnSurfaceVariant
import io.legado.app.utils.ColorUtils

/**
 * 13-read-style#06 分段状态渲染管线：当前段 [StyleSpan]（BOLD）表达选中、未选段 mutedColor 前置色。
 * 封装 span 注册表的清理重建（remove 旧 enabledSpan → 清空 mutedSpans → 按区间重设），
 * ChineseConverter / TextFontWeightConverter 仅保留各自的区间映射与持久化（审查第1轮去重）。
 *
 * mutedColor 取 on_surface_variant 角色令牌（getOnSurfaceVariant，随 bottomBackground 明暗分支；
 * chip 底透明落在弹层上 4.98:1+，62% ink 在 #EEECE9 上 4.38:1 不达标，勿回退 alpha 派生）。
 * 区间为 [Pair]<start, endExclusive>，与 [Spannable.setSpan] 语义一致，SPAN_INCLUSIVE_EXCLUSIVE。
 */
class BoldMutedSegments(
    private val host: TextView,
    text: CharSequence,
) {
    private val spannableString = SpannableString(text)
    private var enabledSpan: StyleSpan? = null
    private val mutedSpans = mutableListOf<ForegroundColorSpan>()

    init {
        host.text = spannableString
    }

    fun apply(
        boldRange: Pair<Int, Int>?,
        mutedRanges: List<Pair<Int, Int>>,
    ) {
        enabledSpan?.let { spannableString.removeSpan(it) }
        enabledSpan = null
        mutedSpans.forEach { spannableString.removeSpan(it) }
        mutedSpans.clear()
        boldRange?.let { range ->
            val span = StyleSpan(Typeface.BOLD)
            spannableString.setSpan(span, range.first, range.second, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
            enabledSpan = span
        }
        val isLight = ColorUtils.isColorLight(host.context.bottomBackground)
        val color = host.context.getOnSurfaceVariant(isLight)
        mutedRanges.forEach { range ->
            val span = ForegroundColorSpan(color)
            mutedSpans.add(span)
            spannableString.setSpan(span, range.first, range.second, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
        }
        host.text = spannableString
    }
}
