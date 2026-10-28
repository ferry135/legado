package io.legado.app.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.widget.TextViewCompat
import io.legado.app.R
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor

/**
 * 空态提示（图标+主行+副行）单一实现，替代此前 5-6 份逐份拷贝的空态块：
 * - drawableTop 描边图标变体：dialog_book_change_source / dialog_chapter_change_source /
 *   dialog_change_cover / fragment_bookmark
 * - 圆底图标变体（emptyIconCircled）：fragment_rss / activity_rule_sub
 * 显隐由使用方按数据为空控制（isGone），文案/图标经 emptyTitle/emptyTip/emptyIcon 注入。
 * 宿主布局统一 id：empty_tip（中性命名，不误导实际类型 LinearLayout）。
 */
class EmptyTipView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        View.inflate(context, R.layout.view_empty_tip, this)
        val a = context.obtainStyledAttributes(attrs, R.styleable.EmptyTipView)
        val iconRes = a.getResourceId(R.styleable.EmptyTipView_empty_icon, 0)
        val iconCircled = a.getBoolean(R.styleable.EmptyTipView_empty_icon_circled, false)
        val title = a.getText(R.styleable.EmptyTipView_empty_title)
        val tip = a.getText(R.styleable.EmptyTipView_empty_tip)
        a.recycle()
        val titleView = findViewById<TextView>(R.id.tv_empty_title)
        val tipView = findViewById<TextView>(R.id.tv_empty_tip_text)
        title?.let { titleView.text = it }
        tip?.let { tipView.text = it }
        if (iconRes != 0) {
            if (iconCircled) {
                findViewById<AppCompatImageView>(R.id.iv_empty_icon).apply {
                    visibility = View.VISIBLE
                    setImageResource(iconRes)
                }
            } else {
                titleView.setCompoundDrawablesWithIntrinsicBounds(0, iconRes, 0, 0)
                titleView.compoundDrawablePadding = 12.dpToPx()
                TextViewCompat.setCompoundDrawableTintList(
                    titleView,
                    ColorStateList.valueOf(context.getCompatColor(R.color.outline))
                )
            }
        }
    }

}
