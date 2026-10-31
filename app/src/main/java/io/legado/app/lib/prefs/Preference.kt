package io.legado.app.lib.prefs

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.preference.PreferenceViewHolder
import io.legado.app.R
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.getCompatColor
import splitties.views.onLongClick
import kotlin.math.roundToInt

open class Preference(context: Context, attrs: AttributeSet) :
    androidx.preference.Preference(context, attrs) {

    private var onLongClick: ((preference: Preference) -> Boolean)? = null
    private val isBottomBackground: Boolean

    init {
        layoutResource = R.layout.view_preference
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.Preference)
        isBottomBackground = typedArray.getBoolean(R.styleable.Preference_isBottomBackground, false)
        typedArray.recycle()
    }

    final override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        onBindView(holder)
        onLongClick?.let { listener ->
            holder.itemView.onLongClick {
                listener.invoke(this)
            }
        }
    }

    open fun onBindView(holder: PreferenceViewHolder) {
        bindView<View>(holder, isBottomBackground = isBottomBackground)
    }

    fun onLongClick(listener: (preference: Preference) -> Boolean) {
        onLongClick = listener
    }

}

/**
 * 行级绑定：context/icon/title/summary/isEnabled 直接读偏好项自身，
 * 行级状态新增时优先在偏好项上取值再收敛到这里，避免参数面逐轮加宽
 * （isBottomBackground 是各子类各自解析的样式开关，仍由调用方传入）。
 */
fun <T : View> androidx.preference.Preference.bindView(
    viewHolder: PreferenceViewHolder?,
    weightLayoutRes: Int? = null,
    viewId: Int? = null,
    weightWidth: Int = 0,
    weightHeight: Int = 0,
    isBottomBackground: Boolean = false
): T? {
    if (viewHolder == null) return null
    val tvTitle = viewHolder.findViewById(R.id.preference_title) as? TextView
    tvTitle?.let {
        tvTitle.text = title
        tvTitle.isVisible = !title.isNullOrEmpty()
    }
    val tvSummary = viewHolder.findViewById(R.id.preference_desc) as? TextView
    tvSummary?.let {
        tvSummary.text = summary
        tvSummary.isGone = summary.isNullOrEmpty()
    }
    if (isBottomBackground && !viewHolder.itemView.isInEditMode) {
        val isLight = ColorUtils.isColorLight(context.bottomBackground)
        val pTextColor = context.getPrimaryTextColor(isLight)
        tvTitle?.setTextColor(pTextColor)
        val sTextColor = context.getSecondaryTextColor(isLight)
        tvSummary?.setTextColor(sTextColor)
    }
    val iconView = viewHolder.findViewById(R.id.preference_icon)
    if (iconView is ImageView) {
        iconView.isVisible = icon != null
        iconView.setImageDrawable(icon)
        // 08稿：偏好行图标随行次级色着色（--secondary = on_surface_variant），
        // 原为 accentColor 强调橙，与稿面「条目图标 21px 描边次级色」不符
        iconView.setColorFilter(context.getCompatColor(R.color.on_surface_variant))
    }

    // 09稿规格D：M3 禁用态整行 38%，依赖项/联动禁用行自动生效（回收复用需双向复位）
    viewHolder.itemView.alpha = if (isEnabled) 1f else 0.38f

    if (weightLayoutRes != null && weightLayoutRes != 0 && viewId != null && viewId != 0) {
        val lay = viewHolder.findViewById(R.id.preference_widget)
        if (lay is FrameLayout) {
            var needRequestLayout = false
            var v = viewHolder.itemView.findViewById<T>(viewId)
            if (v == null) {
                val inflater: LayoutInflater = LayoutInflater.from(context)
                val childView = inflater.inflate(weightLayoutRes, null)
                lay.removeAllViews()
                lay.addView(childView)
                lay.isVisible = true
                v = lay.findViewById(viewId)
            } else
                needRequestLayout = true

            if (weightWidth > 0 || weightHeight > 0) {
                val lp = lay.layoutParams
                if (weightHeight > 0)
                    lp.height =
                        (context.resources.displayMetrics.density * weightHeight).roundToInt()
                if (weightWidth > 0)
                    lp.width =
                        (context.resources.displayMetrics.density * weightWidth).roundToInt()
                lay.layoutParams = lp
            } else if (needRequestLayout)
                v.requestLayout()

            return v
        }
    }

    return null
}
