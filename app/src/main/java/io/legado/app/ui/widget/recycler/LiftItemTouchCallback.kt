package io.legado.app.ui.widget.recycler

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.utils.dpToPx

/**
 * 拖拽浮起态公共实现：拖起时给条目换浮起背景（圆角 + 表面色）并抬升 translationZ，松手恢复原背景。
 * 圆角/表面色/抬升高度由各稿件差异传入：
 * - 分组管理（group-manage 稿 .grow.drag）：10dp 圆角 + surface_container_high + 4dp 抬升
 * - 分组选择（group-select 稿 .lift）：12dp 圆角 + surface_container + 8dp 抬升
 * 条目 order 持久化仍走 ItemTouchCallback.clearView → callback.onClearView
 */
open class LiftItemTouchCallback(
    callback: Callback,
    private val cornerRadiusDp: Int,
    @ColorRes private val bgColorRes: Int,
    private val liftZDp: Int,
) : ItemTouchCallback(callback) {
    private var liftedBackground: Drawable? = null

    override fun onSelectedChanged(
        viewHolder: RecyclerView.ViewHolder?,
        actionState: Int,
    ) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            viewHolder?.itemView?.let { view ->
                liftedBackground = view.background
                view.background =
                    GradientDrawable().apply {
                        cornerRadius = cornerRadiusDp.dpToPx().toFloat()
                        setColor(ContextCompat.getColor(view.context, bgColorRes))
                    }
                view.translationZ = liftZDp.dpToPx().toFloat()
            }
        }
    }

    override fun clearView(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
    ) {
        viewHolder.itemView.let { view ->
            liftedBackground?.let(view::setBackground)
            view.translationZ = 0f
            liftedBackground = null
        }
        super.clearView(recyclerView, viewHolder)
    }
}
