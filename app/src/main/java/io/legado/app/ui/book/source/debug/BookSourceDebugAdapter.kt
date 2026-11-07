package io.legado.app.ui.book.source.debug

import android.content.Context
import android.view.View
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ItemLogLineBinding
import org.apache.commons.lang3.time.FastDateFormat

/**
 * 调试日志行（06-rss 稿 S7 .log .ln 双列）：时间戳 + 消息。
 * 时间在 addItem 时刻取（消息即到即显，接收时间≈发生时间）。
 */
data class DebugLogLine(val time: Long, val msg: String) {
    companion object {
        val format: FastDateFormat = FastDateFormat.getInstance("HH:mm:ss")
    }
}

class BookSourceDebugAdapter(context: Context) :
    RecyclerAdapter<DebugLogLine, ItemLogLineBinding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemLogLineBinding {
        return ItemLogLineBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemLogLineBinding,
        item: DebugLogLine,
        payloads: MutableList<Any>
    ) {
        binding.apply {
            if (tvMsg.getTag(R.id.tag1) == null) {
                val listener = object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        tvMsg.isCursorVisible = false
                        tvMsg.isCursorVisible = true
                    }

                    override fun onViewDetachedFromWindow(v: View) {}
                }
                tvMsg.addOnAttachStateChangeListener(listener)
                tvMsg.setTag(R.id.tag1, listener)
            }
            tvTime.text = DebugLogLine.format.format(item.time)
            tvMsg.text = item.msg
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemLogLineBinding) {
        //nothing
    }
}
