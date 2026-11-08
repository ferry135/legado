package io.legado.app.ui.book.read.config

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.EventBus
import io.legado.app.databinding.ItemBgImageBinding
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.glide.ImageLoader
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.dpToPx
import io.legado.app.utils.externalFiles
import io.legado.app.utils.postEvent
import java.io.File

class BgAdapter(
    context: Context,
    val textColor: Int,
) : RecyclerAdapter<String, ItemBgImageBinding>(context) {
    companion object {
        // 稿 .ph 8dp 圆角：缩略图容器统一圆角裁剪，普通格位图/选中环/虚线头格三态圆角一致；
        // 容器无背景（背景 outline alpha 0 不参与裁剪），以显式 outlineProvider 提供圆角轮廓。
        // 无状态单实例（按目标 view 尺寸实时取轮廓，跨视图共享安全），BgTextConfigDialog
        // 头格「选择图片」复用同一实现，圆角值单点维护
        val thumbOutline =
            object : ViewOutlineProvider() {
                override fun getOutline(
                    view: View,
                    outline: Outline,
                ) {
                    outline.setRoundRect(0, 0, view.width, view.height, 8.dpToPx().toFloat())
                }
            }
    }

    override fun getViewBinding(parent: ViewGroup): ItemBgImageBinding = ItemBgImageBinding.inflate(inflater, parent, false).apply {
        vwBgThumb.outlineProvider = thumbOutline
        vwBgThumb.clipToOutline = true
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemBgImageBinding,
        item: String,
        payloads: MutableList<Any>,
    ) {
        binding.run {
            // read-bg-text-config#05「自定义图入列」：assets/bg ∪ externalFiles/bg 合并数据源，
            // convert 按 externalFiles 优先、assets 兜底双分支读图（NEED-DECISION ⑤，可回退）
            val bgFile = File(context.externalFiles, "bg${File.separator}$item")
            if (bgFile.exists()) {
                ImageLoader
                    .load(context, bgFile)
                    .centerCrop()
                    .into(ivBg)
            } else {
                // 合并数据源后自定义图可能在 setItems 之后被外部删除（弹层停留期间滚动触发
                // 重绑定），assets.open 对不存在名抛 FileNotFoundException（绑定期崩溃）；
                // bind 时捕获兜底——upBgImages 过滤仍有列表后被删的 TOCTOU 窗口，此处彻底关闭
                val assetBytes =
                    kotlin
                        .runCatching {
                            context.assets.open("bg${File.separator}$item").readBytes()
                        }.getOrNull()
                if (assetBytes != null) {
                    ImageLoader
                        .load(context, assetBytes)
                        .centerCrop()
                        .into(ivBg)
                } else {
                    // 名字已无法解析（assets 与 externalFiles 均不存在）：清空回收复用视图的旧图
                    ivBg.setImageDrawable(null)
                }
            }
            tvName.setTextColor(textColor)
            tvName.text = item.substringBeforeLast(".")
            // read-bg-text-config#02「选中态」（新增）：bgType=1 内置图名相等 / bgType=2 自定义
            // 文件名相等 → 主色覆盖环 + 对勾角标（色一律 ThemeStore.primaryColor 运行时取色，
            // 禁布局固化 primary，MASTER §4）；bgType=0（纯色）图片列表无选中项，
            // 由「背景颜色」值胶囊主色描边指示当前模式
            val durConfig = ReadBookConfig.durConfig
            // 历史「全路径」型 bgStr（存量/导入配置，ReadBookConfig.curBgDrawable 对含分隔符
            // bgStr 有专门分支）取文件名归一化后再比对，否则此类数据永不显示选中环
            val curBgName = durConfig.curBgStr().substringAfterLast(File.separator)
            val selected = durConfig.curBgType() != 0 && curBgName == item
            if (selected) {
                val primary = context.primaryColor
                // 覆盖环叠缩略图上（勿用 View#setForeground：API 23+ 而 minSdk 21、item 根 LinearLayout）
                vwRing.visibility = View.VISIBLE
                vwRing.background =
                    GradientDrawable().apply {
                        cornerRadius = 8.dpToPx().toFloat()
                        setColor(Color.TRANSPARENT)
                        setStroke(1.5f.dpToPx().toInt(), primary)
                    }
                vwBadge.visibility = View.VISIBLE
                vwBadge.background =
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(primary)
                    }
            } else {
                vwRing.visibility = View.GONE
                vwBadge.visibility = View.GONE
            }
        }
    }

    override fun registerListener(
        holder: ItemViewHolder,
        binding: ItemBgImageBinding,
    ) {
        holder.itemView.apply {
            this.setOnClickListener {
                getItemByLayoutPosition(holder.layoutPosition)?.let {
                    // 合并数据源后按落点分支：externalFiles/bg → bgType=2，assets/bg → bgType=1
                    val isCustom = File(context.externalFiles, "bg${File.separator}$it").exists()
                    ReadBookConfig.durConfig.setCurBg(if (isCustom) 2 else 1, it)
                    postEvent(EventBus.UP_CONFIG, arrayListOf(1))
                    // 选中态展示刷新（仅展示层，事件与存储语义不变）
                    notifyDataSetChanged()
                }
            }
        }
    }
}
