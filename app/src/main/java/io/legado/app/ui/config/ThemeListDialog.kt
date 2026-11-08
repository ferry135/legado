package io.legado.app.ui.config

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemThemeConfigBinding
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.*
import io.legado.app.utils.viewbindingdelegate.viewBinding

// 预览块装饰纹理/描边的 ink 基色（稿面 rgba(32,36,43)；随预览明度派生，非主题令牌，明暗预览通用）
private val PREVIEW_INK: Int = 0xFF20242B.toInt()

class ThemeListDialog :
    BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val adapter by lazy { Adapter(requireContext()) }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setTitle(R.string.theme_list)
        initView()
        initMenu()
        initData()
    }

    private fun initView() = binding.run {
        // 稿面顶栏 17sp/650：取既有 BarTitle 令牌（18sp 加粗，平台最接近实现；共享布局不动）
        toolBar.setTitleTextAppearance(requireContext(), R.style.TextAppearance_Legado_BarTitle)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        recyclerView.adapter = adapter
    }

    private fun initMenu() = binding.run {
        toolBar.setOnMenuItemClickListener(this@ThemeListDialog)
        toolBar.inflateMenu(R.menu.theme_list)
        toolBar.menu.applyTint(requireContext())
    }

    fun initData() {
        adapter.setItems(ThemeConfig.configList)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_import -> {
                requireContext().getClipText()?.let {
                    if (ThemeConfig.addConfig(it)) {
                        initData()
                    } else {
                        toastOnUi("格式不对,添加失败")
                    }
                }
            }
        }
        return true
    }

    fun delete(index: Int) {
        alert(R.string.delete, R.string.sure_del) {
            yesButton {
                ThemeConfig.delConfig(index)
                initData()
            }
            noButton()
        }
    }

    fun share(index: Int) {
        val json = GSON.toJson(ThemeConfig.configList[index])
        requireContext().share(json, "主题分享")
    }

    inner class Adapter(context: Context) : RecyclerAdapter<ThemeConfig.Config, ItemThemeConfigBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemThemeConfigBinding = ItemThemeConfigBinding.inflate(inflater, parent, false)

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemThemeConfigBinding,
            item: ThemeConfig.Config,
            payloads: MutableList<Any>,
        ) {
            binding.apply {
                tvName.text = item.themeName
                // 预览块恒显配置四色（真实预览，不受弹窗主题影响）；描边/裁切走弹窗令牌口径
                try {
                    val primary = Color.parseColor(item.primaryColor)
                    val accent = Color.parseColor(item.accentColor)
                    val configBg = Color.parseColor(item.backgroundColor)
                    val bottom = Color.parseColor(item.bottomBackground)
                    vPrevTop.setBackgroundColor(primary)
                    vPrevBody.setBackgroundColor(configBg)
                    vPrevBottom.setBackgroundColor(bottom)
                    vPrevDot.background = roundBg(accent, 2f)
                    val prevLight = ColorUtils.isColorLight(configBg)
                    val tx = ColorUtils.adjustAlpha(
                        if (prevLight) PREVIEW_INK else Color.WHITE,
                        if (prevLight) 0.36f else 0.40f,
                    )
                    vPrevAcc.background = roundBg(accent, 1.5f)
                    vPrevTx1.background = roundBg(tx, 1.5f)
                    vPrevTx2.background = roundBg(tx, 1.5f)
                    val stroke = ColorUtils.adjustAlpha(
                        if (prevLight) PREVIEW_INK else Color.WHITE,
                        0.25f,
                    )
                    flPreview.background = GradientDrawable().apply {
                        setColor(Color.TRANSPARENT)
                        cornerRadius = 8.dpToPx().toFloat()
                        setStroke(1.dpToPx(), stroke)
                    }
                    flPreview.clipToOutline = true
                } catch (_: Exception) {
                }
                // 元信息：白天/夜间 + 主色 hex（复用 day/night/theme/primary 既有 string）
                ivNight.visibility = if (item.isNightTheme) View.VISIBLE else View.GONE
                val prefix = getString(
                    if (item.isNightTheme) R.string.night else R.string.day,
                ) + getString(R.string.theme)
                tvMeta.text = "$prefix · ${getString(R.string.primary)} ${item.primaryColor}"
                // 使用中：ThemeStore 当前四色与 config 四色全等（applyConfig 写入键同口径），
                // 行底 --sel 高亮 + pill；行底按下 10% ink/white 状态层
                val inUse = isInUse(item)
                tvInUse.visibility = if (inUse) View.VISIBLE else View.GONE
                val bgLight = ColorUtils.isColorLight(context.backgroundColor)
                val press = ColorUtils.adjustAlpha(
                    if (bgLight) PREVIEW_INK else Color.WHITE,
                    0.10f,
                )
                val sel = if (bgLight) {
                    ColorUtils.adjustAlpha(context.primaryColor, 0.08f)
                } else {
                    context.getCompatColor(R.color.primary_soft)
                }
                root.background = RippleDrawable(
                    ColorStateList.valueOf(press),
                    ColorDrawable(if (inUse) sel else Color.TRANSPARENT),
                    null,
                )
            }
        }

        private fun isInUse(item: ThemeConfig.Config): Boolean = try {
            context.primaryColor == Color.parseColor(item.primaryColor) &&
                context.accentColor == Color.parseColor(item.accentColor) &&
                context.backgroundColor == Color.parseColor(item.backgroundColor) &&
                context.bottomBackground == Color.parseColor(item.bottomBackground)
        } catch (_: Exception) {
            false
        }

        private fun roundBg(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp.dpToPx()
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemThemeConfigBinding) {
            binding.apply {
                root.setOnClickListener {
                    ThemeConfig.applyConfig(context, ThemeConfig.configList[holder.layoutPosition])
                }
                ivShare.setOnClickListener {
                    share(holder.layoutPosition)
                }
                ivDelete.setOnClickListener {
                    delete(holder.layoutPosition)
                }
            }
        }
    }
}
