package io.legado.app.ui.widget.dialog

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemLogBinding
import io.legado.app.utils.dpToPx
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

@Suppress("unused")
class TextListDialog() : BaseDialogFragment(R.layout.dialog_recycler_view) {
    constructor(title: String, values: ArrayList<String>) : this() {
        arguments =
            Bundle().apply {
                putString("title", title)
                putStringArrayList("values", values)
            }
    }

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val adapter by lazy { TextAdapter(requireContext()) }
    private var values: ArrayList<String>? = null

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.9f)
    }

    override fun onFragmentCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) = binding.run {
        arguments?.let {
            toolBar.title = it.getString("title")
            values = it.getStringArrayList("values")
        }
        // 弹窗内水平 16dp 留白（generic-list-dialogs 稿 §06 #4）：item_log 的横向边距原本依赖宿主
        // activity_source_debug 的 RecyclerView padding 提供，壳内 recycler_view 无水平 padding，
        // 弹窗形态文字贴边；仅本页运行时设置，不动共用壳与 item_log（避免宿主页双重留白）
        recyclerView.setPadding(
            16.dpToPx(),
            recyclerView.paddingTop,
            16.dpToPx(),
            recyclerView.paddingBottom,
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        adapter.setItems(values)
    }

    class TextAdapter(
        context: Context,
    ) : RecyclerAdapter<String, ItemLogBinding>(context) {
        override fun getViewBinding(parent: ViewGroup): ItemLogBinding = ItemLogBinding.inflate(inflater, parent, false)

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemLogBinding,
            item: String,
            payloads: MutableList<Any>,
        ) {
            binding.apply {
                if (textView.getTag(R.id.tag1) == null) {
                    val listener =
                        object : View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: View) {
                                textView.isCursorVisible = false
                                textView.isCursorVisible = true
                            }

                            override fun onViewDetachedFromWindow(v: View) {}
                        }
                    textView.addOnAttachStateChangeListener(listener)
                    textView.setTag(R.id.tag1, listener)
                }
                textView.text = item
            }
        }

        override fun registerListener(
            holder: ItemViewHolder,
            binding: ItemLogBinding,
        ) {
            // nothing
        }
    }
}
