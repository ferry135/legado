package io.legado.app.ui.book.read.config

import android.content.Context
import android.util.AttributeSet
import io.legado.app.R
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.widget.text.StrokeTextView


class TextFontWeightConverter(context: Context, attrs: AttributeSet?) :
    StrokeTextView(context, attrs) {

    // 13-read-style#06：当前段加粗表达状态，未选段 secondary 令牌；span 管线复用 BoldMutedSegments，仅保留区间映射
    private val segments = BoldMutedSegments(this, context.getString(R.string.font_weight_text))
    private var onChanged: (() -> Unit)? = null

    init {
        if (!isInEditMode) {
            upUi(ReadBookConfig.textBold)
        }
        setOnClickListener {
            selectType()
        }
    }

    // ReadStyleDialog.upView() 外部调用，保持 public
    @Suppress("MemberVisibilityCanBePrivate")
    fun upUi(type: Int) {
        val boldRange = when (type) {
            0 -> 0 to 1
            1 -> 2 to 3
            else -> 4 to 5
        }
        val mutedRanges = when (type) {
            0 -> listOf(2 to 3, 4 to 5)
            1 -> listOf(0 to 1, 4 to 5)
            else -> listOf(0 to 1, 2 to 3)
        }
        segments.apply(boldRange, mutedRanges)
    }

    private fun selectType() {
        context.alert(titleResource = R.string.text_font_weight_converter) {
            items(context.resources.getStringArray(R.array.text_font_weight).toList()) { _, i ->
                ReadBookConfig.textBold = i
                upUi(i)
                onChanged?.invoke()
            }
        }
    }

    fun onChanged(unit: () -> Unit) {
        onChanged = unit
    }
}
