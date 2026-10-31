package io.legado.app.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.appcompat.widget.TooltipCompat
import io.legado.app.R
import io.legado.app.databinding.ViewDetailSeekBarBinding
import io.legado.app.lib.theme.InkGradient
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.ui.widget.seekbar.SeekBarChangeListener
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.progressAdd


class DetailSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs),
    SeekBarChangeListener {
    private var binding: ViewDetailSeekBarBinding =
        ViewDetailSeekBarBinding.inflate(LayoutInflater.from(context), this, true)
    private val isBottomBackground: Boolean

    var valueFormat: ((progress: Int) -> String)? = null
    var onChanged: ((progress: Int) -> Unit)? = null

    private var themeProgressTint: ColorStateList? = null
    private var themeThumbTint: ColorStateList? = null

    /**
     * 12-missed-2 #15：按需覆盖进度条已滑过部分与滑块着色（如漫画滤镜 R/G/B/A 通道色相）；
     * 未设或置回 null 保持/恢复 ThemeSeekBar 的运行时主题色着色，背景轨道不随之改动
     */
    var progressThumbTint: Int? = null
        set(value) {
            if (value != null && field == null) {
                // 首次覆盖前快照 ThemeSeekBar 运行时着色（applyTint(accentColor) 所设），供置回 null 时恢复
                themeProgressTint = binding.seekBar.progressTintList
                themeThumbTint = binding.seekBar.thumbTintList
            }
            field = value
            if (value != null) {
                val tintList = ColorStateList.valueOf(value)
                binding.seekBar.progressTintList = tintList
                binding.seekBar.thumbTintList = tintList
            } else {
                themeProgressTint?.let { binding.seekBar.progressTintList = it }
                themeThumbTint?.let { binding.seekBar.thumbTintList = it }
            }
        }

    var progress: Int
        get() = binding.seekBar.progress
        set(value) {
            binding.seekBar.progress = value
            upValue()
        }
    var max: Int
        get() = binding.seekBar.max
        set(value) {
            binding.seekBar.max = value
        }

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.DetailSeekBar)
        isBottomBackground =
            typedArray.getBoolean(R.styleable.DetailSeekBar_isBottomBackground, false)
        val title = typedArray.getText(R.styleable.DetailSeekBar_title)
        binding.tvSeekTitle.apply {
            text = title
            TooltipCompat.setTooltipText(this, title)
        }
        binding.seekBar.max = typedArray.getInteger(R.styleable.DetailSeekBar_max, 0)
        typedArray.recycle()
        if (isBottomBackground && !isInEditMode) {
            val isLight = ColorUtils.isColorLight(context.bottomBackground)
            val textColor = context.getPrimaryTextColor(isLight)
            binding.tvSeekTitle.setTextColor(textColor)
            binding.ivSeekPlus.setColorFilter(textColor, PorterDuff.Mode.SRC_IN)
            binding.ivSeekReduce.setColorFilter(textColor, PorterDuff.Mode.SRC_IN)
            // 13-read-style#10：行容器 = ink 8% 圆角 12dp 卡（内边距 14×6dp），随换肤自适应（档位取 InkGradient）
            binding.root.background = GradientDrawable().apply {
                cornerRadius = context.resources.getDimension(R.dimen.corner_radius_md)
                setColor(ColorUtils.adjustAlpha(textColor, InkGradient.CONTAINER))
            }
            binding.root.setPadding(14.dpToPx(), 6.dpToPx(), 14.dpToPx(), 6.dpToPx())
            // 13-read-style#11：值 = ink 10% 圆角 8dp 胶囊、文字自适应 primaryText（对齐 dialog_auto_read
            // tv_read_speed 的 on_surface 口径；62% ink / secondary 在胶囊叠层上实算 4.40/4.09:1 < 4.5，故不用弱化字色）
            binding.tvSeekValue.background = GradientDrawable().apply {
                cornerRadius = context.resources.getDimension(R.dimen.corner_radius_sm)
                setColor(ColorUtils.adjustAlpha(textColor, InkGradient.CHIP))
            }
            binding.tvSeekValue.setTextColor(textColor)
        }
        binding.ivSeekPlus.setOnClickListener {
            binding.seekBar.progressAdd(1)
            onChanged?.invoke(binding.seekBar.progress)
        }
        binding.ivSeekReduce.setOnClickListener {
            binding.seekBar.progressAdd(-1)
            onChanged?.invoke(binding.seekBar.progress)
        }
        binding.seekBar.setOnSeekBarChangeListener(this)
    }

    private fun upValue(progress: Int = binding.seekBar.progress) {
        valueFormat?.let {
            binding.tvSeekValue.text = it.invoke(progress)
        } ?: let {
            binding.tvSeekValue.text = progress.toString()
        }
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        upValue(progress)
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {

    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        onChanged?.invoke(binding.seekBar.progress)
    }

}