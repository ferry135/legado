package io.legado.app.ui.book.audio

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.SeekBar
import io.legado.app.R
import io.legado.app.databinding.PopupSeekBarBinding
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.model.AudioPlay
import io.legado.app.service.AudioPlayService
import io.legado.app.ui.widget.seekbar.SeekBarChangeListener
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor

class TimerSliderPopup(
    private val context: Context,
) : PopupWindow(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {
    private val binding = PopupSeekBarBinding.inflate(LayoutInflater.from(context))

    // 快捷预设芯片（popup_seek_bar 芯片行）：value=0 即「关闭」取消定时（AudioPlayService 现状语义）
    private val chipValues by lazy {
        listOf(
            binding.chipTimer10 to 10,
            binding.chipTimer30 to 30,
            binding.chipTimer60 to 60,
            binding.chipTimer90 to 90,
            binding.chipTimerOff to 0,
        )
    }

    // 芯片选中态同步簿记：须在 init 之前声明（Kotlin 属性初始化按声明序执行，
    // init 内 syncChips(0) 首刷会写入这些字段，声明在后会被初始化器覆盖回 null/0）
    private var lastSyncedProgress: Int? = null
    private var lastSelectedValue: Int? = null
    private var cachedAccentColor = 0
    private var selectedBackground: LayerDrawable? = null

    init {
        contentView = binding.root

        isTouchable = true
        isOutsideTouchable = false
        isFocusable = true

        binding.seekBar.max = 180
        setProcessTextValue(binding.seekBar.progress)
        syncChips(binding.seekBar.progress)
        binding.seekBar.setOnSeekBarChangeListener(
            object : SeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    setProcessTextValue(progress)
                    // 拖动（fromUser=true）与芯片回显（fromUser=false）共用同一芯片选中态刷新；
                    // 非预设值（如 45）全部中性、不近似取整（audio-timer-popup §02 芯片选中态同步规则）
                    syncChips(progress)
                    if (fromUser) {
                        AudioPlay.setTimer(progress)
                    }
                }
            },
        )
        chipValues.forEach { (chip, value) ->
            chip.setOnClickListener { applyTimer(value) }
        }
    }

    override fun showAsDropDown(
        anchor: View?,
        xoff: Int,
        yoff: Int,
        gravity: Int,
    ) {
        super.showAsDropDown(anchor, xoff, yoff, gravity)
        binding.seekBar.progress = AudioPlayService.timeMinute
    }

    override fun showAtLocation(
        parent: View?,
        gravity: Int,
        x: Int,
        y: Int,
    ) {
        super.showAtLocation(parent, gravity, x, y)
        binding.seekBar.progress = AudioPlayService.timeMinute
    }

    private fun applyTimer(value: Int) {
        // 芯片点击直接调 setTimer（同 :34 fromUser 分支调用），不能只走 seekBar.progress=value：
        // 程序化 setProgress 的 onProgressChanged fromUser=false 会被下方 if (fromUser) 拦截，
        // 芯片只会改「N 分钟」文字而从不设定时（audio-timer-popup §02 快捷预设注）
        AudioPlay.setTimer(value)
        binding.seekBar.progress = value
        setProcessTextValue(value)
        syncChips(value)
    }

    // 芯片选中态同步：命中 10/30/60/90 高亮对应芯片、progress==0 高亮「关闭」，
    // 非预设值（如 45）全部中性、不近似取整。选中 = 运行时 accentColor 胶囊实底、文字自动黑/白
    // （DECISIONS §8 疗法，同 FontAdapter.convert / ReadAloudDialog.upPlayState 先例，换肤安全）；
    // LayerDrawable 垂直 inset 8dp 与芯片 drawable 同口径，48dp 触控高内保持 32dp 视觉胶囊
    @Suppress("DEPRECATION")
    private fun syncChips(progress: Int) {
        val accentColor = ThemeStore.accentColor(context)
        val accentChanged = accentColor != cachedAccentColor
        if (progress == lastSyncedProgress && !accentChanged) return
        if (accentChanged || selectedBackground == null) {
            cachedAccentColor = accentColor
            // setLayerInset 自 API 1 可用（23 起有替代、本函数仍全版本生效），minSdk 21 兼容
            selectedBackground =
                LayerDrawable(
                    arrayOf(
                        GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = 32.dpToPx() / 2f
                            setColor(accentColor)
                        },
                    ),
                ).apply { setLayerInset(0, 0, 8.dpToPx(), 0, 8.dpToPx()) }
        }
        chipValues.forEach { (chip, value) ->
            when {
                // 命中且此前未选中：仅此时重设选中背景/文字色；accent 变化时对已选中芯片强制刷新
                value == progress && (lastSelectedValue != value || accentChanged) -> {
                    chip.background = selectedBackground
                    chip.setTextColor(
                        if (ColorUtils.isColorLight(accentColor)) Color.BLACK else Color.WHITE,
                    )
                }
                // 此前选中、现已脱离：仅此时还原中性底与次要文字色
                value != progress && lastSelectedValue == value -> {
                    chip.setBackgroundResource(R.drawable.bg_book_audio_timer_slider_popup_chip)
                    chip.setTextColor(context.getCompatColor(R.color.on_surface_variant))
                }
            }
        }
        lastSyncedProgress = progress
        lastSelectedValue = if (chipValues.any { it.second == progress }) progress else null
    }

    private fun setProcessTextValue(process: Int) {
        binding.tvSeekValue.text = context.getString(R.string.timer_m, process)
    }
}
