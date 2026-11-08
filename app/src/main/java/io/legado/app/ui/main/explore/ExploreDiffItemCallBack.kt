package io.legado.app.ui.main.explore

import androidx.recyclerview.widget.DiffUtil
import io.legado.app.data.entities.BookSourcePart

class ExploreDiffItemCallBack : DiffUtil.ItemCallback<BookSourcePart>() {
    override fun areItemsTheSame(
        oldItem: BookSourcePart,
        newItem: BookSourcePart,
    ): Boolean = oldItem == newItem

    override fun areContentsTheSame(
        oldItem: BookSourcePart,
        newItem: BookSourcePart,
    ): Boolean = oldItem.bookSourceName == newItem.bookSourceName
}
