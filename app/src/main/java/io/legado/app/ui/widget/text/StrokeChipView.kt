package io.legado.app.ui.widget.text

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import io.legado.app.R

/**
 * 填充胶囊描边按钮：bg_chip 填充 + StrokeTextView 1dp 描边叠加
 * （暗色下纯描边对比不足 AA，故双层），圆角 8dp 与 padding 13/7 固定。
 * 统一 activity_book_info_edit 三按钮（选择封面/更换封面/刷新封面）的单一实现，
 * 文本经 android:text 注入，点击监听直接设置在本 View 上。
 */
class StrokeChipView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    init {
        setBackgroundResource(R.drawable.bg_chip)
        View.inflate(context, R.layout.view_stroke_chip, this)
        context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.text)).apply {
            getText(0)?.let { findViewById<StrokeTextView>(R.id.chip_text).text = it }
            recycle()
        }
    }

}
