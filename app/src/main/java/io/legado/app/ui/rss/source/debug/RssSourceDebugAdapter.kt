package io.legado.app.ui.rss.source.debug

import android.content.Context
import android.view.View
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ItemLogLineBinding
import io.legado.app.ui.book.source.debug.DebugLogLine

class RssSourceDebugAdapter(context: Context) :
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
