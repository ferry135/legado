package io.legado.app.ui.book.read.config

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogReadAloudBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.widget.seekbar.SeekBarChangeListener
import io.legado.app.utils.*
import io.legado.app.utils.viewbindingdelegate.viewBinding


class ReadAloudDialog : BaseDialogFragment(R.layout.dialog_read_aloud) {
    private val callBack: CallBack? get() = activity as? CallBack
    private val binding by viewBinding(DialogReadAloudBinding::bind)

    override fun onStart() {
        super.onStart()
        // 10-reader-prefs#08 注⑤：底部弹层窗口形态收口（透明底 + scrim_dialog 50% 遮罩 + 底部重力）
        dialog?.window?.applyBottomSheetWindow(ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as ReadBookActivity).bottomDialog--
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val bottomDialog = (activity as ReadBookActivity).bottomDialog++
        if (bottomDialog > 0) {
            dismiss()
            return
        }
        val bg = requireContext().bottomBackground
        val isLight = ColorUtils.isColorLight(bg)
        val textColor = requireContext().getPrimaryTextColor(isLight)
        binding.run {
            // 10-reader-prefs#08：弹层顶部 16dp 圆角（bg_read_sheet_top 形态），底色仍随运行时换肤
            rootView.setSheetBackground(bg, topOnly = true)
            tvPre.setTextColor(textColor)
            tvNext.setTextColor(textColor)
            ivPlayPrev.setColorFilter(textColor)
            // 播放键圆底+图标由 upPlayState() 按主题强调色统一着色，不走 textColor
            ivPlayNext.setColorFilter(textColor)
            ivStop.setColorFilter(textColor)
            ivTimer.setColorFilter(textColor)
            tvTimer.setTextColor(textColor)
            ivTtsSpeechReduce.setColorFilter(textColor)
            tvTtsSpeed.setTextColor(textColor)
            // 语速值为中性值胶囊（bg_chip 底 + on_surface 字，10-reader-prefs#08 注②同 AutoReadDialog）
            tvTtsSpeedValue.setTextColor(getCompatColor(R.color.on_surface))
            ivTtsSpeechAdd.setColorFilter(textColor)
            ivCatalog.setColorFilter(textColor)
            tvCatalog.setTextColor(textColor)
            ivMainMenu.setColorFilter(textColor)
            tvMainMenu.setTextColor(textColor)
            ivToBackstage.setColorFilter(textColor)
            tvToBackstage.setTextColor(textColor)
            ivSetting.setColorFilter(textColor)
            tvSetting.setTextColor(textColor)
            cbTtsFollowSys.setTextColor(textColor)
        }
        initData()
        initEvent()
    }

    private fun initData() = binding.run {
        upPlayState()
        upTimerText(BaseReadAloudService.timeMinute)
        cbTtsFollowSys.isChecked = requireContext().getPrefBoolean("ttsFollowSys", true)
        upTtsSpeechRateEnabled(!cbTtsFollowSys.isChecked)
        upSeekTimer()
    }

    private fun initEvent() = binding.run {
        llMainMenu.setOnClickListener {
            callBack?.showMenuBar()
            dismissAllowingStateLoss()
        }
        llSetting.setOnClickListener {
            ReadAloudConfigDialog().show(childFragmentManager, "readAloudConfigDialog")
        }
        tvPre.setOnClickListener { ReadBook.moveToPrevChapter(upContent = true, toLast = false) }
        tvNext.setOnClickListener { ReadBook.moveToNextChapter(true) }
        ivStop.setOnClickListener {
            ReadAloud.stop(requireContext())
            dismissAllowingStateLoss()
        }
        ivPlayPause.setOnClickListener { callBack?.onClickReadAloud() }
        ivPlayPrev.setOnClickListener { ReadAloud.prevParagraph(requireContext()) }
        ivPlayNext.setOnClickListener { ReadAloud.nextParagraph(requireContext()) }
        llCatalog.setOnClickListener { callBack?.openChapterList() }
        llToBackstage.setOnClickListener { callBack?.finish() }
        cbTtsFollowSys.setOnCheckedChangeListener { _, isChecked ->
            AppConfig.ttsFlowSys = isChecked
            upTtsSpeechRateEnabled(!isChecked)
            upTtsSpeechRate()
        }
        ivTtsSpeechReduce.setOnClickListener {
            seekTtsSpeechRate.progress = AppConfig.ttsSpeechRate - 1
            AppConfig.ttsSpeechRate -= 1
            upTtsSpeechRate()
        }
        ivTtsSpeechAdd.setOnClickListener {
            seekTtsSpeechRate.progress = AppConfig.ttsSpeechRate + 1
            AppConfig.ttsSpeechRate += 1
            upTtsSpeechRate()
        }
        ivTimer.setOnClickListener {
            AppConfig.ttsTimer = seekTimer.progress
            toastOnUi("保存设定时间成功！")
        }
        tvTimer.setOnClickListener {
            val times = intArrayOf(0, 5, 10, 15, 30, 60, 90, 180)
            val timeKeys = times.map { "$it 分钟" }
            context?.selector("设定时间", timeKeys) { _, index ->
                ReadAloud.setTimer(requireContext(), times[index])
            }
        }
        //设置保存的默认值
        seekTtsSpeechRate.progress = AppConfig.ttsSpeechRate
        seekTtsSpeechRate.setOnSeekBarChangeListener(object : SeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                super.onProgressChanged(seekBar, progress, fromUser)
                upTtsSpeechRateText(progress)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                AppConfig.ttsSpeechRate = seekBar.progress
                upTtsSpeechRate()
            }
        })
        seekTimer.setOnSeekBarChangeListener(object : SeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                upTimerText(progress)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                ReadAloud.setTimer(requireContext(), seekTimer.progress)
            }
        })
    }

    private fun upTtsSpeechRateEnabled(enabled: Boolean) {
        binding.run {
            upTtsSpeechRateText(AppConfig.ttsSpeechRate)
            tvTtsSpeedValue.visible(enabled)
            seekTtsSpeechRate.isEnabled = enabled
            ivTtsSpeechReduce.isEnabled = enabled
            ivTtsSpeechAdd.isEnabled = enabled
        }
    }

    private fun upPlayState() {
        if (!BaseReadAloudService.pause) {
            binding.ivPlayPause.setImageResource(R.drawable.ic_pause_24dp)
            binding.ivPlayPause.contentDescription = getString(R.string.pause)
        } else {
            binding.ivPlayPause.setImageResource(R.drawable.ic_play_24dp)
            binding.ivPlayPause.contentDescription = getString(R.string.audio_play)
        }
        // 播放键：圆底运行时取主题强调色（MASTER.md §4 布局不得固化 primary/accent），
        // 图标按强调色明暗取黑白保证对比（同 AccentBgTextView 逻辑）
        val accentColor = ThemeStore.accentColor(requireContext())
        binding.ivPlayPause.backgroundTintList = ColorStateList.valueOf(accentColor)
        binding.ivPlayPause.setColorFilter(
            if (ColorUtils.isColorLight(accentColor)) Color.BLACK else Color.WHITE
        )
    }

    private fun upSeekTimer() {
        binding.seekTimer.post {
            if (BaseReadAloudService.timeMinute > 0) {
                binding.seekTimer.progress = BaseReadAloudService.timeMinute
            } else {
                binding.seekTimer.progress = AppConfig.ttsTimer
            }
        }
    }

    private fun upTimerText(timeMinute: Int) {
        if (timeMinute < 0) {
            binding.tvTimer.text = requireContext().getString(R.string.timer_m, 0)
        } else {
            binding.tvTimer.text = requireContext().getString(R.string.timer_m, timeMinute)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun upTtsSpeechRateText(value: Int) {
        binding.tvTtsSpeedValue.text = ((value + 5) / 10f).toString()
    }

    private fun upTtsSpeechRate() {
        ReadAloud.upTtsSpeechRate(requireContext())
        if (!BaseReadAloudService.pause) {
            ReadAloud.pause(requireContext())
            ReadAloud.resume(requireContext())
        }
    }

    override fun observeLiveBus() {
        observeEvent<Int>(EventBus.ALOUD_STATE) { upPlayState() }
        observeEvent<Int>(EventBus.READ_ALOUD_DS) { binding.seekTimer.progress = it }
    }

    interface CallBack {
        fun showMenuBar()
        fun openChapterList()
        fun onClickReadAloud()
        fun finish()
    }
}