package io.legado.app.ui.book.read.config

import android.content.Context
import android.util.AttributeSet
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.widget.text.StrokeTextView

class ChineseConverter(
    context: Context,
    attrs: AttributeSet?,
) : StrokeTextView(context, attrs) {
    // 13-read-style#06：当前段加粗表达状态，未选段 secondary 令牌；span 管线复用 BoldMutedSegments，仅保留区间映射
    private val segments = BoldMutedSegments(this, context.getString(R.string.chinese_converter_text))
    private var onChanged: (() -> Unit)? = null

    init {
        if (!isInEditMode) {
            upUi(AppConfig.chineseConverterType)
        }
        setOnClickListener {
            selectType()
        }
    }

    private fun upUi(type: Int) {
        val boldRange =
            when (type) {
                1 -> 0 to 1
                2 -> 2 to 3
                else -> null
            }
        val mutedRanges =
            when (type) {
                1 -> listOf(2 to 3)
                2 -> listOf(0 to 1)
                else -> listOf(0 to 1, 2 to 3)
            }
        segments.apply(boldRange, mutedRanges)
    }

    private fun selectType() {
        context.alert(titleResource = R.string.chinese_converter) {
            items(context.resources.getStringArray(R.array.chinese_mode).toList()) { _, i ->
                AppConfig.chineseConverterType = i
                upUi(i)
                onChanged?.invoke()
            }
        }
    }

    fun onChanged(unit: () -> Unit) {
        onChanged = unit
    }
}
