package io.legado.app.ui.book.changesource

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import io.legado.app.R
import io.legado.app.base.adapter.DiffRecyclerAdapter
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.data.entities.SearchBook
import io.legado.app.databinding.ItemChangeSourceBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.gone
import io.legado.app.utils.invisible
import io.legado.app.utils.visible
import splitties.views.onLongClick

class ChangeChapterSourceAdapter(
    context: Context,
    val viewModel: ChangeChapterSourceViewModel,
    val callBack: CallBack,
) : DiffRecyclerAdapter<SearchBook, ItemChangeSourceBinding>(context) {
    override val diffItemCallback =
        object : DiffUtil.ItemCallback<SearchBook>() {
            override fun areItemsTheSame(
                oldItem: SearchBook,
                newItem: SearchBook,
            ): Boolean = oldItem.bookUrl == newItem.bookUrl

            override fun areContentsTheSame(
                oldItem: SearchBook,
                newItem: SearchBook,
            ): Boolean = oldItem.originName == newItem.originName &&
                oldItem.getDisplayLastChapterTitle() == newItem.getDisplayLastChapterTitle()
        }

    override fun getViewBinding(parent: ViewGroup): ItemChangeSourceBinding = ItemChangeSourceBinding.inflate(inflater, parent, false)

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemChangeSourceBinding,
        item: SearchBook,
        payloads: MutableList<Any>,
    ) {
        binding.apply {
            if (payloads.isEmpty()) {
                tvOrigin.text = item.originName
                tvAuthor.text = item.author
                tvLast.text = item.getDisplayLastChapterTitle()
                tvCurrentChapterWordCount.text = item.chapterWordCountText
                tvRespondTime.text = context.getString(R.string.respondTime, item.respondTime)
                if (callBack.oldBookUrl == item.bookUrl) {
                    ivChecked.visible()
                } else {
                    ivChecked.invisible()
                }
            } else {
                for (i in payloads.indices) {
                    val bundle = payloads[i] as Bundle
                    bundle.keySet().forEach {
                        when (it) {
                            "name" -> tvOrigin.text = item.originName
                            "latest" -> tvLast.text = item.getDisplayLastChapterTitle()
                            "upCurSource" ->
                                if (callBack.oldBookUrl == item.bookUrl) {
                                    ivChecked.visible()
                                } else {
                                    ivChecked.invisible()
                                }
                        }
                    }
                }
            }
            tintVoteIcons(binding.ivGood, binding.ivBad, callBack.getBookScore(item))

            if (AppConfig.changeSourceLoadWordCount && !item.chapterWordCountText.isNullOrBlank()) {
                tvCurrentChapterWordCount.visible()
            } else {
                tvCurrentChapterWordCount.gone()
            }

            if (AppConfig.changeSourceLoadWordCount && item.respondTime >= 0) {
                tvRespondTime.visible()
            } else {
                tvRespondTime.gone()
            }
        }
    }

    override fun registerListener(
        holder: ItemViewHolder,
        binding: ItemChangeSourceBinding,
    ) {
        binding.ivGood.setOnClickListener {
            if (binding.ivBad.isVisible) {
                tintVoteIcons(binding.ivGood, binding.ivBad, 1)
                getItem(holder.bindingAdapterPosition)?.let {
                    callBack.setBookScore(it, 1)
                }
            } else {
                tintVoteIcons(binding.ivGood, binding.ivBad, 0)
                getItem(holder.bindingAdapterPosition)?.let {
                    callBack.setBookScore(it, 0)
                }
            }
        }
        binding.ivBad.setOnClickListener {
            if (binding.ivGood.isVisible) {
                tintVoteIcons(binding.ivGood, binding.ivBad, -1)
                getItem(holder.bindingAdapterPosition)?.let {
                    callBack.setBookScore(it, -1)
                }
            } else {
                tintVoteIcons(binding.ivGood, binding.ivBad, 0)
                getItem(holder.bindingAdapterPosition)?.let {
                    callBack.setBookScore(it, 0)
                }
            }
        }
        holder.itemView.setOnClickListener {
            getItem(holder.bindingAdapterPosition)?.let {
                callBack.openToc(it)
            }
        }
        holder.itemView.onLongClick {
            showMenu(holder.itemView, getItem(holder.bindingAdapterPosition))
        }
    }

    private fun showMenu(
        view: View,
        searchBook: SearchBook?,
    ) {
        searchBook ?: return
        val popupMenu = PopupMenu(context, view)
        popupMenu.inflate(R.menu.change_source_item)
        popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_top_source -> {
                    callBack.topSource(searchBook)
                }
                R.id.menu_bottom_source -> {
                    callBack.bottomSource(searchBook)
                }
                R.id.menu_edit_source -> {
                    callBack.editSource(searchBook)
                }
                R.id.menu_disable_source -> {
                    callBack.disableSource(searchBook)
                }
                R.id.menu_delete_source -> {
                    callBack.deleteSource(searchBook)
                    updateItems(0, itemCount, listOf<Int>())
                }
            }
            true
        }
        popupMenu.show()
    }

    interface CallBack {
        val oldBookUrl: String?

        fun openToc(searchBook: SearchBook)

        fun topSource(searchBook: SearchBook)

        fun bottomSource(searchBook: SearchBook)

        fun editSource(searchBook: SearchBook)

        fun disableSource(searchBook: SearchBook)

        fun deleteSource(searchBook: SearchBook)

        fun setBookScore(
            searchBook: SearchBook,
            score: Int,
        )

        fun getBookScore(searchBook: SearchBook): Int
    }
}
