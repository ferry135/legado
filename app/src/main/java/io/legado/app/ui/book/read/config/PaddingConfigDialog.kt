package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogReadPaddingBinding
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.lib.theme.sheetInk
import io.legado.app.utils.applyBottomSheetWindow
import io.legado.app.utils.postEvent
import io.legado.app.utils.setSheetBackground
import io.legado.app.utils.viewbindingdelegate.viewBinding

class PaddingConfigDialog : BaseDialogFragment(R.layout.dialog_read_padding) {

    private val binding by viewBinding(DialogReadPaddingBinding::bind)

    override fun onStart() {
        super.onStart()
        // 14-reader-brightness#05：底部弹层窗口收口（透明窗底 + scrim_dialog 50% + 底部重力），
        // 替换原 clearFlags(FLAG_DIM_BEHIND) + dimAmount=0 无遮罩浮窗；高 80% 屏防超长（内部滚动）
        dialog?.window?.applyBottomSheetWindow(
            (resources.displayMetrics.heightPixels * 0.8f).toInt()
        )
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initData()
        initView()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        ReadBookConfig.save()
    }

    private fun initData() = binding.run {
        //正文
        dsbPaddingTop.progress = ReadBookConfig.paddingTop
        dsbPaddingBottom.progress = ReadBookConfig.paddingBottom
        dsbPaddingLeft.progress = ReadBookConfig.paddingLeft
        dsbPaddingRight.progress = ReadBookConfig.paddingRight
        //页眉
        dsbHeaderPaddingTop.progress = ReadBookConfig.headerPaddingTop
        dsbHeaderPaddingBottom.progress = ReadBookConfig.headerPaddingBottom
        dsbHeaderPaddingLeft.progress = ReadBookConfig.headerPaddingLeft
        dsbHeaderPaddingRight.progress = ReadBookConfig.headerPaddingRight
        //页脚
        dsbFooterPaddingTop.progress = ReadBookConfig.footerPaddingTop
        dsbFooterPaddingBottom.progress = ReadBookConfig.footerPaddingBottom
        dsbFooterPaddingLeft.progress = ReadBookConfig.footerPaddingLeft
        dsbFooterPaddingRight.progress = ReadBookConfig.footerPaddingRight
        cbShowTopLine.isChecked = ReadBookConfig.showHeaderLine
        cbShowBottomLine.isChecked = ReadBookConfig.showFooterLine
    }

    private fun initView() = binding.run {
        // 14-reader-brightness#05：16dp 顶圆角弹层底 + 分组标签/hairline/开关行卡随换肤
        // （ink 令牌同 13-read-style 语言：标签 on_surface_variant、hairline 12%、行卡 8%；
        // 派生三元组与行卡构建器收敛至 lib/theme SheetInk，与 TipConfig/ReadStyle/FontSelect 同源）
        val ink = requireContext().sheetInk()
        root.setSheetBackground(ink.bg, topOnly = true)
        listOf(tvHeaderPadding, tvBodyPadding, tvFooterPadding).forEach {
            it.setTextColor(ink.secondary)
        }
        listOf(vwPaddingDivider, vwPaddingDividerFooter).forEach {
            it.background = ink.divider()
        }
        listOf(llShowTopLine, llShowBottomLine).forEach {
            // 整行可点：行卡底叠 selectableItemBackground 按压态，触控反馈不因静态卡覆盖而丢失
            it.background = ink.rowCardSelectable()
        }
        // 显示分隔线整行可点（label 与开关同触控域）
        llShowTopLine.setOnClickListener { cbShowTopLine.toggle() }
        llShowBottomLine.setOnClickListener { cbShowBottomLine.toggle() }
        //正文
        dsbPaddingTop.onChanged = {
            ReadBookConfig.paddingTop = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(10, 5))
        }
        dsbPaddingBottom.onChanged = {
            ReadBookConfig.paddingBottom = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(10, 5))
        }
        dsbPaddingLeft.onChanged = {
            ReadBookConfig.paddingLeft = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(10, 5))
        }
        dsbPaddingRight.onChanged = {
            ReadBookConfig.paddingRight = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(10, 5))
        }
        //页眉
        dsbHeaderPaddingTop.onChanged = {
            ReadBookConfig.headerPaddingTop = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        }
        dsbHeaderPaddingBottom.onChanged = {
            ReadBookConfig.headerPaddingBottom = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        }
        dsbHeaderPaddingLeft.onChanged = {
            ReadBookConfig.headerPaddingLeft = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        }
        dsbHeaderPaddingRight.onChanged = {
            ReadBookConfig.headerPaddingRight = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        }
        //页脚
        dsbFooterPaddingTop.onChanged = {
            ReadBookConfig.footerPaddingTop = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        }
        dsbFooterPaddingBottom.onChanged = {
            ReadBookConfig.footerPaddingBottom = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        }
        dsbFooterPaddingLeft.onChanged = {
            ReadBookConfig.footerPaddingLeft = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        }
        dsbFooterPaddingRight.onChanged = {
            ReadBookConfig.footerPaddingRight = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        }
        // 14-reader-brightness#05：SmoothCheckBox→ThemeSwitch（SwitchCompat），监听换标准 CompoundButton API
        cbShowTopLine.setOnCheckedChangeListener { _, isChecked ->
            ReadBookConfig.showHeaderLine = isChecked
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        }
        cbShowBottomLine.setOnCheckedChangeListener { _, isChecked ->
            ReadBookConfig.showFooterLine = isChecked
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        }
    }

}
