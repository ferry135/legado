package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.get
import com.github.liuyueyi.quick.transfer.constants.TransType
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogReadBookStyleBinding
import io.legado.app.databinding.ItemReadStyleBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.InkGradient
import io.legado.app.lib.theme.SheetInk
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.sheetInk
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.font.FontSelectDialog
import io.legado.app.utils.ChineseUtils
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.applyBottomSheetWindow
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getIndexById
import io.legado.app.utils.postEvent
import io.legado.app.utils.setSheetBackground
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding
import splitties.views.onLongClick

class ReadStyleDialog :
    BaseDialogFragment(R.layout.dialog_read_book_style),
    FontSelectDialog.CallBack {
    private val binding by viewBinding(DialogReadBookStyleBinding::bind)
    private val callBack get() = activity as? ReadBookActivity
    private lateinit var styleAdapter: StyleAdapter

    // 13-read-style#22：弹层只取一次自适应 ink 派生三元组（分割线/容器/胶囊档位统一取
    // InkGradient 梯度表 + SheetInk 构建器）；次要字用 on_surface_variant 角色令牌
    // （62% ink 在 #EEECE9 上 4.38:1 < 4.5，令牌 4.98:1 达标）
    private lateinit var sheetInk: SheetInk

    override fun onStart() {
        super.onStart()
        // 13-read-style#03/#04：底部弹层窗口形态收口（透明底 + scrim_dialog 50% 遮罩 + 底部重力），
        // 替换原 clearFlags(FLAG_DIM_BEHIND) + R.color.background 窗口底 + dimAmount=0 双套背景混用
        dialog?.window?.applyBottomSheetWindow(ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        (activity as ReadBookActivity).bottomDialog++
        initView()
        initData()
        initViewEvent()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        ReadBookConfig.save()
        (activity as ReadBookActivity).bottomDialog--
    }

    private fun initView() = binding.run {
        sheetInk = requireContext().sheetInk()
        // 13-read-style#02：顶部 16dp 圆角弹层底（随换肤），替代原 setBackgroundColor 直角覆盖
        rootView.setSheetBackground(sheetInk.bg, topOnly = true)
        // 13-read-style#16/#22：分割线 12% ink、次要标签 on_surface_variant 令牌，替代固定 divider_subtle 与 alpha 0.75
        vwBgFg.background = sheetInk.divider()
        vwBgFg1.background = sheetInk.divider()
        tvPageAnim.setTextColor(sheetInk.secondary)
        tvBgTs.setTextColor(sheetInk.secondary)
        tvShareLayout.setTextColor(sheetInk.ink)
        dsbTextSize.valueFormat = {
            (it + 5).toString()
        }
        dsbTextLetterSpacing.valueFormat = {
            ((it - 50) / 100f).toString()
        }
        dsbLineSize.valueFormat = { ((it - 10) / 10f).toString() }
        dsbParagraphSpacing.valueFormat = { (it / 10f).toString() }
        styleAdapter = StyleAdapter()
        rvStyle.adapter = styleAdapter
        // 13-read-style：色板右缘 44dp 渐隐提示（透明 → 弹层底色，对齐 .sw-fade 渐变）
        vwStyleFade.background =
            GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.TRANSPARENT, sheetInk.bg),
            )
        // 13-read-style#17：配色方案行整行可点（label 与开关同触控域）
        rowShareLayout.setOnClickListener { cbShareLayout.toggle() }
        styleAdapter.addFooterView {
            ItemReadStyleBinding.inflate(layoutInflater, it, false).apply {
                // 13-read-style#20：footer 48dp 圆钮内 24dp 加号；ghost 圆 = ink 8% 圆底 + 1dp 虚线描边
                // （ink 30%，3/3dp 周期对齐稿 02 节 .sw-cell.add 的 1px dashed）——CircleImageView 自身
                // border 无 dash 能力，改由背景层 GradientDrawable(OVAL) 承担虚线描边（边框归零、填充透明）
                ivStyle.setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
                ivStyle.setText(null)
                ivStyle.setColorFilter(sheetInk.secondary)
                ivStyle.borderWidth = 0
                ivStyle.circleBackgroundColor = Color.TRANSPARENT
                ivStyle.background =
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(ColorUtils.adjustAlpha(sheetInk.ink, InkGradient.CONTAINER))
                        setStroke(
                            1.dpToPx(),
                            ColorUtils.adjustAlpha(sheetInk.ink, InkGradient.STROKE_GHOST),
                            3.dpToPx().toFloat(),
                            3.dpToPx().toFloat(),
                        )
                    }
                ivStyle.setImageResource(R.drawable.ic_add)
                tvStyleName.setText(R.string.read_style_custom)
                tvStyleName.setTextColor(sheetInk.secondary)
                root.setOnClickListener {
                    ReadBookConfig.configList.add(ReadBookConfig.Config())
                    showBgTextConfig(ReadBookConfig.configList.lastIndex)
                }
            }
        }
    }

    private fun initData() {
        binding.cbShareLayout.isChecked = ReadBookConfig.shareLayout
        upView()
        styleAdapter.setItems(ReadBookConfig.configList)
    }

    private fun initViewEvent() = binding.run {
        chineseConverter.onChanged {
            ChineseUtils.unLoad(*TransType.entries.toTypedArray())
            postEvent(EventBus.UP_CONFIG, arrayListOf(5))
        }
        textFontWeightConverter.onChanged {
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 9, 6))
        }
        tvTextFont.setOnClickListener {
            showDialogFragment<FontSelectDialog>()
        }
        tvTextIndent.setOnClickListener {
            context?.selector(
                title = getString(R.string.text_indent),
                items = resources.getStringArray(R.array.indent).toList(),
            ) { _, index ->
                ReadBookConfig.paragraphIndent = "　".repeat(index)
                postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
            }
        }
        tvPadding.setOnClickListener {
            dismissAllowingStateLoss()
            callBack?.showPaddingConfig()
        }
        tvTip.setOnClickListener {
            TipConfigDialog().show(childFragmentManager, "tipConfigDialog")
        }
        rgPageAnim.setOnCheckedChangeListener { _, checkedId ->
            // 不清除本书的动画覆盖，显式设置过动画的书保留自己的设置
            ReadBookConfig.pageAnim = binding.rgPageAnim.getIndexById(checkedId)
            callBack?.upPageAnim()
            ReadBook.loadContent(false)
        }
        // 13-read-style#17：ThemeSwitch 为 SwitchCompat，监听走标准 setOnCheckedChangeListener
        cbShareLayout.setOnCheckedChangeListener { _, isChecked ->
            ReadBookConfig.shareLayout = isChecked
            upView()
            postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
        }
        dsbTextSize.onChanged = {
            ReadBookConfig.textSize = it + 5
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        dsbTextLetterSpacing.onChanged = {
            ReadBookConfig.letterSpacing = (it - 50) / 100f
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        dsbLineSize.onChanged = {
            ReadBookConfig.lineSpacingExtra = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        dsbParagraphSpacing.onChanged = {
            ReadBookConfig.paragraphSpacing = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
    }

    private fun changeBgTextConfig(index: Int) {
        val oldIndex = ReadBookConfig.styleSelect
        if (index != oldIndex) {
            ReadBookConfig.styleSelect = index
            upView()
            styleAdapter.notifyItemChanged(oldIndex)
            styleAdapter.notifyItemChanged(index)
            postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
            if (AppConfig.readBarStyleFollowPage) {
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
        }
    }

    private fun showBgTextConfig(index: Int): Boolean {
        dismissAllowingStateLoss()
        changeBgTextConfig(index)
        callBack?.showBgTextConfig()
        return true
    }

    private fun upView() = binding.run {
        textFontWeightConverter.upUi(ReadBookConfig.textBold)
        ReadBook.pageAnim().let {
            if (it >= 0 && it < rgPageAnim.childCount) {
                rgPageAnim.check(rgPageAnim[it].id)
            }
        }
        ReadBookConfig.let {
            dsbTextSize.progress = it.textSize - 5
            dsbTextLetterSpacing.progress = (it.letterSpacing * 100).toInt() + 50
            dsbLineSize.progress = it.lineSpacingExtra
            dsbParagraphSpacing.progress = it.paragraphSpacing
        }
    }

    override val curFontPath: String
        get() = ReadBookConfig.textFont

    override fun selectFont(path: String) {
        if (path != ReadBookConfig.textFont || path.isEmpty()) {
            ReadBookConfig.textFont = path
            postEvent(EventBus.UP_CONFIG, arrayListOf(2, 5))
        }
    }

    inner class StyleAdapter : RecyclerAdapter<ReadBookConfig.Config, ItemReadStyleBinding>(requireContext()) {
        override fun getViewBinding(parent: ViewGroup): ItemReadStyleBinding = ItemReadStyleBinding.inflate(inflater, parent, false)

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemReadStyleBinding,
            item: ReadBookConfig.Config,
            payloads: MutableList<Any>,
        ) {
            binding.apply {
                // 13-read-style#19：色板为方案预览——颜色型背景恒显白天配色（夜间也用 bgStr 渲染纯底色圆，
                // 消除五个预设夜间同底近黑不可辨的问题；item.textColor 不进色板，经 curTextColor() 作用于
                // 阅读正文）；图片背景仍走 curBgDrawable
                if (item.bgType == 0) {
                    ivStyle.setImageDrawable(
                        runCatching { ColorDrawable(Color.parseColor(item.bgStr)) }.getOrNull()
                            ?: item.curBgDrawable(100, 150),
                    )
                } else {
                    ivStyle.setImageDrawable(item.curBgDrawable(100, 150))
                }
                tvStyleName.text = item.name.ifBlank { tvStyleName.context.getString(R.string.text) }
                if (ReadBookConfig.styleSelect == holder.layoutPosition) {
                    // 13-read-style#18：选中 = 2dp 主色描边 + 名字主色加粗
                    ivStyle.borderWidth = 2.dpToPx()
                    ivStyle.borderColor = accentColor
                    tvStyleName.setTextColor(accentColor)
                    tvStyleName.paint.isFakeBoldText = true
                } else {
                    ivStyle.borderWidth = 1.dpToPx()
                    ivStyle.borderColor = ColorUtils.adjustAlpha(sheetInk.ink, InkGradient.STROKE_MUTED)
                    tvStyleName.setTextColor(sheetInk.secondary)
                    tvStyleName.paint.isFakeBoldText = false
                }
            }
        }

        override fun registerListener(
            holder: ItemViewHolder,
            binding: ItemReadStyleBinding,
        ) {
            binding.apply {
                // 13-read-style 02 节触控条目：色板单元格（圆+名，48+6+12=66dp）整体可点，监听绑 root
                // （旧仅绑 48dp 圆形 ivStyle，预设名 tv_style_name 为点击死区）。原 isInView 守卫是
                // CircleImageView 的圆内命中判定（48dp 方形四角拒点），整体可点后由单元格边界取代；
                // 旧 onLongClick(isInView) 实为 splitties 的 consume 参数（注册时求值，绑定时恒 false，
                // 长按未消费回落 click 造成双动作），恢复默认 consume=true
                root.setOnClickListener {
                    changeBgTextConfig(holder.layoutPosition)
                }
                root.onLongClick {
                    showBgTextConfig(holder.layoutPosition)
                }
            }
        }
    }
}
