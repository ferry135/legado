package io.legado.app.ui.font

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.databinding.ItemFontBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.*
import java.io.File
import java.net.URLDecoder

class FontAdapter(context: Context, curFilePath: String, val callBack: CallBack) : RecyclerAdapter<FileDoc, ItemFontBinding>(context) {

    private val curName = kotlin.runCatching {
        URLDecoder.decode(curFilePath, "utf-8")
    }.getOrNull()?.substringAfterLast(File.separator)

    override fun getViewBinding(parent: ViewGroup): ItemFontBinding = ItemFontBinding.inflate(inflater, parent, false)

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemFontBinding,
        item: FileDoc,
        payloads: MutableList<Any>,
    ) {
        binding.run {
            kotlin.runCatching {
                val typeface: Typeface? = if (item.isContentScheme) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.contentResolver
                            .openFileDescriptor(item.uri, "r")?.use {
                                Typeface.Builder(it.fileDescriptor).build()
                            }
                    } else {
                        Typeface.createFromFile(RealPathUtil.getPath(context, item.uri))
                    }
                } else {
                    Typeface.createFromFile(item.uri.path!!)
                }
                tvFont.typeface = typeface
            }.onFailure {
                it.printOnDebug()
                AppLog.put("读取字体 ${item.name} 出错\n${it.localizedMessage}", it, true)
                // 加载失败时 :48 未执行：显式还原默认字型，防复用自选中行的加粗派生体
                // 经 setTypeface(tf, NORMAL) 残留粗体（style=0 仅清伪粗/斜旗标，不去字型本身）
                tvFont.typeface = null
            }
            tvFont.text = item.name
            root.setOnClickListener { callBack.onFontSelect(item) }
            // 选中项主色对勾 + 字体名同步主色 650：布局不固化 primary（MASTER §4），
            // 此处按 ThemeStore 运行时着色（14-reader-brightness#04）；
            // 650 字重映射 Typeface.BOLD（ThemeRadioNoButton 同口径），不锁定 paint 标志
            ivChecked.setColorFilter(context.primaryColor)
            if (item.name == curName) {
                ivChecked.visible()
                tvFont.setTextColor(context.primaryColor)
                tvFont.setTypeface(tvFont.typeface, Typeface.BOLD)
            } else {
                ivChecked.invisible()
                tvFont.setTextColor(context.getCompatColor(R.color.primaryText))
                tvFont.setTypeface(tvFont.typeface, Typeface.NORMAL)
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemFontBinding) {
        holder.itemView.setOnClickListener {
            getItem(holder.layoutPosition)?.let {
                callBack.onFontSelect(it)
            }
        }
    }

    interface CallBack {
        fun onFontSelect(docItem: FileDoc)
    }
}
