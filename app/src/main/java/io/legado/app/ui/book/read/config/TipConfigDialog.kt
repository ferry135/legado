package io.legado.app.ui.book.read.config

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogTipConfigBinding
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadTipConfig
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.InkGradient
import io.legado.app.lib.theme.SheetInk
import io.legado.app.lib.theme.sheetInk
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.applyBottomSheetWindow
import io.legado.app.utils.checkByIndex
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getIndexById
import io.legado.app.utils.hexString
import io.legado.app.utils.observeEvent
import io.legado.app.utils.postEvent
import io.legado.app.utils.setSheetBackground
import io.legado.app.utils.viewbindingdelegate.viewBinding

class TipConfigDialog : BaseDialogFragment(R.layout.dialog_tip_config) {
    companion object {
        const val TIP_COLOR = 7897
        const val TIP_DIVIDER_COLOR = 7898
    }

    private val binding by viewBinding(DialogTipConfigBinding::bind)

    // 14-reader-brightness#06：自适应 ink 派生三元组（行卡/值胶囊/色点描边取 SheetInk 档位，
    // 同 ReadStyleDialog 口径；派生块收敛至 lib/theme SheetInk）
    private lateinit var sheetInk: SheetInk

    override fun onStart() {
        super.onStart()
        // 14-reader-brightness#06：底部弹层窗口形态（透明窗底 + scrim_dialog 50% + 底部重力），
        // 宽 MATCH_PARENT；高度保持原 WRAP_CONTENT 语义
        dialog?.window?.applyBottomSheetWindow(ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        initView()
        initEvent()
        observeEvent<String>(EventBus.TIP_COLOR) {
            upTvTipColor()
            upTvTipDividerColor()
        }
    }

    private fun initView() {
        upViewStyle()
        binding.rgTitleMode.checkByIndex(ReadBookConfig.titleMode)
        binding.dsbTitleSize.progress = ReadBookConfig.titleSize
        binding.dsbTitleTop.progress = ReadBookConfig.titleTopSpacing
        binding.dsbTitleBottom.progress = ReadBookConfig.titleBottomSpacing

        binding.tvHeaderShow.text =
            ReadTipConfig.getHeaderModes(requireContext())[ReadTipConfig.headerMode]
        binding.tvFooterShow.text =
            ReadTipConfig.getFooterModes(requireContext())[ReadTipConfig.footerMode]

        ReadTipConfig.run {
            tipNames.let { tipNames ->
                binding.tvHeaderLeft.text =
                    tipNames.getOrElse(tipValues.indexOf(tipHeaderLeft)) { tipNames[none] }
                binding.tvHeaderMiddle.text =
                    tipNames.getOrElse(tipValues.indexOf(tipHeaderMiddle)) { tipNames[none] }
                binding.tvHeaderRight.text =
                    tipNames.getOrElse(tipValues.indexOf(tipHeaderRight)) { tipNames[none] }
                binding.tvFooterLeft.text =
                    tipNames.getOrElse(tipValues.indexOf(tipFooterLeft)) { tipNames[none] }
                binding.tvFooterMiddle.text =
                    tipNames.getOrElse(tipValues.indexOf(tipFooterMiddle)) { tipNames[none] }
                binding.tvFooterRight.text =
                    tipNames.getOrElse(tipValues.indexOf(tipFooterRight)) { tipNames[none] }
            }
        }
        upTvTipColor()
        upTvTipDividerColor()
    }

    /**
     * 14-reader-brightness#06：16dp 顶圆角弹层底 + .prow 行形态随换肤
     * （分组标签 on_surface_variant、组间 hairline 12% ink、行卡 8%、值胶囊 10%；
     * 档位构建器走 SheetInk.divider/rowCard/chip，档位归 InkGradient 梯度表）
     */
    private fun upViewStyle() {
        sheetInk = requireContext().sheetInk()
        binding.root.setSheetBackground(sheetInk.bg, topOnly = true)
        listOf(
            binding.tvTipGroupTitle,
            binding.tvTipGroupHeader,
            binding.tvTipGroupFooter,
            binding.tvTipGroupHf,
        ).forEach { it.setTextColor(sheetInk.secondary) }
        listOf(
            binding.vwTipDivider,
            binding.vwTipDividerFooter,
            binding.vwTipDividerColor,
        ).forEach { it.background = sheetInk.divider() }
        listOf(
            binding.llHeaderShow,
            binding.llHeaderLeft,
            binding.llHeaderMiddle,
            binding.llHeaderRight,
            binding.llFooterShow,
            binding.llFooterLeft,
            binding.llFooterMiddle,
            binding.llFooterRight,
            binding.llTipColor,
            binding.llTipDividerColor,
        ).forEach {
            // 整行可点：行卡底叠 selectableItemBackground 按压态，触控反馈不因静态卡覆盖而丢失
            it.background = sheetInk.rowCardSelectable()
        }
        // 每视图独立实例：共享可变 GradientDrawable 的 bounds 会被兄弟视图按各自尺寸互相覆盖，
        // 未改尺寸的重绘会以他人 bounds 绘制背景（同 PaddingConfigDialog 口径）
        listOf(
            binding.tvHeaderShow,
            binding.tvHeaderLeft,
            binding.tvHeaderMiddle,
            binding.tvHeaderRight,
            binding.tvFooterShow,
            binding.tvFooterLeft,
            binding.tvFooterMiddle,
            binding.tvFooterRight,
            binding.llTipColorChip,
            binding.llTipDividerColorChip,
        ).forEach { it.background = sheetInk.chip() }
    }

    private fun upTvTipColor() {
        val tipColorNames = ReadTipConfig.tipColorNames
        val tipColor = ReadTipConfig.tipColor
        binding.tvTipColor.text =
            if (tipColor == 0) {
                tipColorNames.first()
            } else {
                "#${tipColor.hexString}"
            }
        // 14-reader-brightness#06：值胶囊内 12dp 色点仅自定义 hex 时出现（「正文颜色」无点）
        binding.vwTipColorDot.isVisible = tipColor != 0
        if (tipColor != 0) {
            upColorDot(binding.vwTipColorDot, tipColor)
        }
    }

    private fun upTvTipDividerColor() {
        val tipDividerColorNames = ReadTipConfig.tipDividerColorNames
        val tipDividerColor = ReadTipConfig.tipDividerColor
        binding.tvTipDividerColor.text =
            when (tipDividerColor) {
                -1, 0 -> tipDividerColorNames[tipDividerColor + 1]
                else -> "#${tipDividerColor.hexString}"
            }
        // 14-reader-brightness#06：-1/0（正文/分隔线颜色）无点，>0 自定义 hex 显点
        binding.vwTipDividerColorDot.isVisible = tipDividerColor > 0
        if (tipDividerColor > 0) {
            upColorDot(binding.vwTipDividerColorDot, tipDividerColor)
        }
    }

    private fun upColorDot(
        dot: View,
        color: Int,
    ) {
        dot.background =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(1.dpToPx(), ColorUtils.adjustAlpha(sheetInk.ink, InkGradient.STROKE_MUTED))
            }
    }

    private fun initEvent() = binding.run {
        rgTitleMode.setOnCheckedChangeListener { _, checkedId ->
            ReadBookConfig.titleMode = rgTitleMode.getIndexById(checkedId)
            postEvent(EventBus.UP_CONFIG, arrayListOf(5))
        }
        dsbTitleSize.onChanged = {
            ReadBookConfig.titleSize = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        dsbTitleTop.onChanged = {
            ReadBookConfig.titleTopSpacing = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        dsbTitleBottom.onChanged = {
            ReadBookConfig.titleBottomSpacing = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        llHeaderShow.setOnClickListener {
            val headerModes = ReadTipConfig.getHeaderModes(requireContext())
            context?.selector(items = headerModes.values.toList()) { _, i ->
                ReadTipConfig.headerMode = headerModes.keys.toList()[i]
                tvHeaderShow.text = headerModes[ReadTipConfig.headerMode]
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            }
        }
        llFooterShow.setOnClickListener {
            val footerModes = ReadTipConfig.getFooterModes(requireContext())
            context?.selector(items = footerModes.values.toList()) { _, i ->
                ReadTipConfig.footerMode = footerModes.keys.toList()[i]
                tvFooterShow.text = footerModes[ReadTipConfig.footerMode]
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            }
        }
        llHeaderLeft.setOnClickListener {
            context?.selector(items = ReadTipConfig.tipNames) { _, i ->
                val tipValue = ReadTipConfig.tipValues[i]
                clearRepeat(tipValue)
                ReadTipConfig.tipHeaderLeft = tipValue
                tvHeaderLeft.text = ReadTipConfig.tipNames[i]
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
            }
        }
        llHeaderMiddle.setOnClickListener {
            context?.selector(items = ReadTipConfig.tipNames) { _, i ->
                val tipValue = ReadTipConfig.tipValues[i]
                clearRepeat(tipValue)
                ReadTipConfig.tipHeaderMiddle = tipValue
                tvHeaderMiddle.text = ReadTipConfig.tipNames[i]
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
            }
        }
        llHeaderRight.setOnClickListener {
            context?.selector(items = ReadTipConfig.tipNames) { _, i ->
                val tipValue = ReadTipConfig.tipValues[i]
                clearRepeat(tipValue)
                ReadTipConfig.tipHeaderRight = tipValue
                tvHeaderRight.text = ReadTipConfig.tipNames[i]
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
            }
        }
        llFooterLeft.setOnClickListener {
            context?.selector(items = ReadTipConfig.tipNames) { _, i ->
                val tipValue = ReadTipConfig.tipValues[i]
                clearRepeat(tipValue)
                ReadTipConfig.tipFooterLeft = tipValue
                tvFooterLeft.text = ReadTipConfig.tipNames[i]
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
            }
        }
        llFooterMiddle.setOnClickListener {
            context?.selector(items = ReadTipConfig.tipNames) { _, i ->
                val tipValue = ReadTipConfig.tipValues[i]
                clearRepeat(tipValue)
                ReadTipConfig.tipFooterMiddle = tipValue
                tvFooterMiddle.text = ReadTipConfig.tipNames[i]
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
            }
        }
        llFooterRight.setOnClickListener {
            context?.selector(items = ReadTipConfig.tipNames) { _, i ->
                val tipValue = ReadTipConfig.tipValues[i]
                clearRepeat(tipValue)
                ReadTipConfig.tipFooterRight = tipValue
                tvFooterRight.text = ReadTipConfig.tipNames[i]
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
            }
        }
        llTipColor.setOnClickListener {
            context?.selector(items = ReadTipConfig.tipColorNames) { _, i ->
                when (i) {
                    0 -> {
                        ReadTipConfig.tipColor = 0
                        upTvTipColor()
                        postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                    }

                    1 ->
                        ColorPickerDialog
                            .newBuilder()
                            .setShowAlphaSlider(false)
                            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                            .setDialogId(TIP_COLOR)
                            .show(requireActivity())
                }
            }
        }
        llTipDividerColor.setOnClickListener {
            context?.selector(items = ReadTipConfig.tipDividerColorNames) { _, i ->
                when (i) {
                    0, 1 -> {
                        ReadTipConfig.tipDividerColor = i - 1
                        upTvTipDividerColor()
                        postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                    }

                    2 ->
                        ColorPickerDialog
                            .newBuilder()
                            .setShowAlphaSlider(false)
                            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                            .setDialogId(TIP_DIVIDER_COLOR)
                            .show(requireActivity())
                }
            }
        }
    }

    private fun clearRepeat(repeat: Int) = ReadTipConfig.apply {
        if (repeat != none) {
            if (tipHeaderLeft == repeat) {
                tipHeaderLeft = none
                binding.tvHeaderLeft.text = tipNames[none]
            }
            if (tipHeaderMiddle == repeat) {
                tipHeaderMiddle = none
                binding.tvHeaderMiddle.text = tipNames[none]
            }
            if (tipHeaderRight == repeat) {
                tipHeaderRight = none
                binding.tvHeaderRight.text = tipNames[none]
            }
            if (tipFooterLeft == repeat) {
                tipFooterLeft = none
                binding.tvFooterLeft.text = tipNames[none]
            }
            if (tipFooterMiddle == repeat) {
                tipFooterMiddle = none
                binding.tvFooterMiddle.text = tipNames[none]
            }
            if (tipFooterRight == repeat) {
                tipFooterRight = none
                binding.tvFooterRight.text = tipNames[none]
            }
        }
    }
}
