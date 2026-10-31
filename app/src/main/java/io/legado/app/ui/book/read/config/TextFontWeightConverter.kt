package io.legado.app.ui.book.read.config

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import io.legado.app.R
import io.legado.app.databinding.DialogFontWeightBinding
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.widget.text.StrokeTextView
import io.legado.app.utils.checkByIndex
import io.legado.app.utils.getIndexById


class TextFontWeightConverter(context: Context, attrs: AttributeSet?) :
    StrokeTextView(context, attrs) {

    // 13-read-style#06：当前段加粗表达状态，未选段 secondary 令牌；span 管线复用 BoldMutedSegments，仅保留区间映射
    private val segments = BoldMutedSegments(this, context.getString(R.string.font_weight_text))
    private var onChanged: (() -> Unit)? = null

    init {
        if (!isInEditMode) {
            upUi(ReadBookConfig.textBold)
        }
        setOnClickListener {
            selectType()
        }
    }

    // ReadStyleDialog.upView() 外部调用，保持 public
    @Suppress("MemberVisibilityCanBePrivate")
    fun upUi(type: Int) {
        val boldRange = when (type) {
            0 -> 0 to 1
            1 -> 2 to 3
            else -> 4 to 5
        }
        val mutedRanges = when (type) {
            0 -> listOf(2 to 3, 4 to 5)
            1 -> listOf(0 to 1, 4 to 5)
            else -> listOf(0 to 1, 2 to 3)
        }
        segments.apply(boldRange, mutedRanges)
    }

    private fun selectType() {
        // 14-reader-brightness#07：弹窗卡片化——16dp 圆角 alert 卡（全局 alertCardBackground 通道）
        // + 三枚胶囊单选（ThemeRadioNoButton），替换原裸 items 列表；
        // 选项文案仍取 R.array.text_font_weight，存储键 textBold 与 BoldMutedSegments 管线不变
        val binding = DialogFontWeightBinding.inflate(LayoutInflater.from(context))
        val weights = context.resources.getStringArray(R.array.text_font_weight)
        binding.rbFontWeight0.text = weights[0]
        binding.rbFontWeight1.text = weights[1]
        binding.rbFontWeight2.text = weights[2]
        // textBold 可经导入的配置 JSON 直写且无校验（ReadBookConfig GSON.fromJson*），
        // checkByIndex 无越界防护（get(index).id），先 coerce 进 0..2 再选中，口径与 upUi else 分支一致
        binding.rgFontWeight.checkByIndex(ReadBookConfig.textBold.coerceIn(0, 2))
        // Context.alert 有 title/titleResource/init 三个重载，裸 lambda 会重载二义；
        // 显式命名空 title/message 锁定 CharSequence 重载（标题在卡片 customView 内，不加 alert 标题）
        val dialog = context.alert(title = null, message = null) {
            customView { binding.root }
        }
        binding.rgFontWeight.setOnCheckedChangeListener { group, checkedId ->
            val index = group.getIndexById(checkedId)
            ReadBookConfig.textBold = index
            upUi(index)
            onChanged?.invoke()
            dialog.dismiss()
        }
    }

    fun onChanged(unit: () -> Unit) {
        onChanged = unit
    }
}
