package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogAutoReadBinding
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.BaseReadBookActivity
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.widget.seekbar.SeekBarChangeListener
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.applyBottomSheetWindow
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.setSheetBackground
import io.legado.app.utils.viewbindingdelegate.viewBinding
import java.util.Locale

class AutoReadDialog : BaseDialogFragment(R.layout.dialog_auto_read) {
    private val binding by viewBinding(DialogAutoReadBinding::bind)
    private val callBack: CallBack? get() = activity as? CallBack

    override fun onStart() {
        super.onStart()
        // 10-reader-prefs#08 注③：底部弹层窗口形态收口（透明底 + scrim_dialog 50% 遮罩 + 底部重力）
        dialog?.window?.applyBottomSheetWindow(ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as ReadBookActivity).bottomDialog--
    }

    override fun onFragmentCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) = binding.run {
        val bottomDialog = (activity as ReadBookActivity).bottomDialog++
        if (bottomDialog > 0) {
            dismiss()
            return
        }
        val bg = requireContext().bottomBackground
        val isLight = ColorUtils.isColorLight(bg)
        val textColor = requireContext().getPrimaryTextColor(isLight)
        // 10-reader-prefs#08：弹层顶部 16dp 圆角（bg_read_sheet_top 形态），底色仍随运行时换肤
        root.setSheetBackground(bg, topOnly = true)
        tvReadSpeedTitle.setTextColor(textColor)
        // 速度值为中性值胶囊（bg_chip 底 + on_surface 字），不再随 textColor 染色——
        // 主色字压 surface2 亮色仅 2.41:1 违 MASTER §4（10-reader-prefs#08 注②）
        tvReadSpeed.setTextColor(getCompatColor(R.color.on_surface))
        ivCatalog.setColorFilter(textColor, PorterDuff.Mode.SRC_IN)
        tvCatalog.setTextColor(textColor)
        ivMainMenu.setColorFilter(textColor, PorterDuff.Mode.SRC_IN)
        tvMainMenu.setTextColor(textColor)
        ivAutoPageStop.setColorFilter(textColor, PorterDuff.Mode.SRC_IN)
        tvAutoPageStop.setTextColor(textColor)
        ivSetting.setColorFilter(textColor, PorterDuff.Mode.SRC_IN)
        tvSetting.setTextColor(textColor)
        initOnChange()
        initData()
        initEvent()
    }

    private fun initData() {
        val speed = if (ReadBookConfig.autoReadSpeed < 1) 1 else ReadBookConfig.autoReadSpeed
        binding.tvReadSpeed.text = String.format(Locale.ROOT, "%ds", speed)
        binding.seekAutoRead.progress = speed
    }

    private fun initOnChange() {
        binding.seekAutoRead.setOnSeekBarChangeListener(
            object : SeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    val speed = if (progress < 1) 1 else progress
                    binding.tvReadSpeed.text = String.format(Locale.ROOT, "%ds", speed)
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    ReadBookConfig.autoReadSpeed =
                        if (binding.seekAutoRead.progress < 1) 1 else binding.seekAutoRead.progress
                    upTtsSpeechRate()
                }
            },
        )
    }

    private fun initEvent() {
        binding.llMainMenu.setOnClickListener {
            callBack?.showMenuBar()
            dismissAllowingStateLoss()
        }
        binding.llSetting.setOnClickListener {
            (activity as BaseReadBookActivity).showPageAnimConfig {
                (activity as ReadBookActivity).upPageAnim()
                ReadBook.loadContent(false)
            }
        }
        binding.llCatalog.setOnClickListener { callBack?.openChapterList() }
        binding.llAutoPageStop.setOnClickListener {
            callBack?.autoPageStop()
            binding.llAutoPageStop.post {
                dismissAllowingStateLoss()
            }
        }
    }

    private fun upTtsSpeechRate() {
        ReadAloud.upTtsSpeechRate(requireContext())
        if (!BaseReadAloudService.pause) {
            ReadAloud.pause(requireContext())
            ReadAloud.resume(requireContext())
        }
    }

    interface CallBack {
        fun showMenuBar()

        fun openChapterList()

        fun autoPageStop()
    }
}
