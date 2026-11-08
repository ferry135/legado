package io.legado.app.ui.dict

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.entities.DictRule
import io.legado.app.databinding.DialogDictBinding
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.ui.dict.rule.DictRuleActivity
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.gone
import io.legado.app.utils.invisible
import io.legado.app.utils.setHtml
import io.legado.app.utils.setLayout
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible

/**
 * 词典
 */
class DictDialog() : BaseDialogFragment(R.layout.dialog_dict) {
    constructor(word: String) : this() {
        arguments =
            Bundle().apply {
                putString("word", word)
            }
    }

    private val viewModel by viewModels<DictViewModel>()
    private val binding by viewBinding(DialogDictBinding::bind)

    private var word: String? = null
    private var dictRules: List<DictRule> = emptyList()
    private var selectIndex = -1

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        binding.tvDict.movementMethod = LinkMovementMethod()
        binding.tvDict.setLinkTextColor(accentColor)
        word = arguments?.getString("word")
        if (word.isNullOrEmpty()) {
            toastOnUi(R.string.cannot_empty)
            dismiss()
            return
        }
        binding.tvWord.text = word
        // 管理词典规则入口（同「我的」页 MyFragment.kt:149 目标）
        binding.ivDictManage.setOnClickListener {
            startActivity<DictRuleActivity>()
        }
        binding.tvEmptyManage.setOnClickListener {
            startActivity<DictRuleActivity>()
        }
        // 空态 ghost 钮：1px accent 描边 + 9dp 圆角，主色运行时取 accentColor（不固化，MASTER §4）
        binding.tvEmptyManage.background =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 9.dpToPx().toFloat()
                setStroke(1.dpToPx(), accentColor)
                setColor(Color.TRANSPARENT)
            }
        binding.tvEmptyManage.setTextColor(accentColor)
        viewModel.initData { dictRules ->
            if (dictRules.isEmpty()) {
                // 空态：未启用任何词典规则（dict.html 03）
                binding.ptabs.gone()
                binding.llEmpty.visible()
                return@initData
            }
            this.dictRules = dictRules
            dictRules.forEachIndexed { index, rule ->
                binding.llTabs.addView(newPill(rule.name, index))
            }
            setupTabMode(dictRules.size)
            selectTab(0)
        }
    }

    private fun newPill(
        name: String,
        index: Int,
    ): TextView = TextView(requireContext()).apply {
        text = name
        textSize = 13f
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        minHeight = 32.dpToPx()
        setPadding(14.dpToPx(), 0, 14.dpToPx(), 0)
        setBackgroundResource(R.drawable.bg_pill_tab)
        setOnClickListener { selectTab(index) }
    }

    // 根据已启用词典数动态选取布局（dict.html #01/#02：≤4 等宽铺满 / >4 单行滚动+右缘 30dp 渐隐，
    // 对应原 MODE_FIXED+GRAVITY_FILL / MODE_SCROLLABLE+GRAVITY_CENTER）
    private fun setupTabMode(dictCount: Int) {
        val fixed = dictCount <= 4
        if (fixed) {
            binding.dfade.gone()
            binding.svTabs.post {
                binding.llTabs.layoutParams =
                    FrameLayout.LayoutParams(
                        binding.svTabs.width,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
            }
        } else {
            // 右缘渐隐至弹层底色，提示可滑；不足整行时容器内居中
            binding.dfade.background =
                GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.TRANSPARENT, backgroundColor),
                )
            binding.llTabs.layoutParams =
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL,
                )
        }
        for (i in 0 until binding.llTabs.childCount) {
            val child = binding.llTabs.getChildAt(i)
            val lp = child.layoutParams as LinearLayout.LayoutParams
            lp.width = if (fixed) 0 else ViewGroup.LayoutParams.WRAP_CONTENT
            lp.weight = if (fixed) 1f else 0f
            lp.marginEnd = if (i == binding.llTabs.childCount - 1) 0 else 8.dpToPx()
            child.layoutParams = lp
        }
    }

    private fun selectTab(index: Int) {
        val dictRule = dictRules.getOrNull(index) ?: return
        if (index == selectIndex) {
            return
        }
        selectIndex = index
        // 选中=主色填充白字；未选中=field 底+次要文字（药丸底由 bg_pill_tab + backgroundTintList 承担）
        val accent = accentColor
        val field = requireContext().getCompatColor(R.color.pill_field)
        val secondary = requireContext().getCompatColor(R.color.on_surface_variant)
        for (i in 0 until binding.llTabs.childCount) {
            val pill = binding.llTabs.getChildAt(i) as TextView
            if (i == index) {
                pill.setBackgroundTintList(ColorStateList.valueOf(accent))
                pill.setTextColor(Color.WHITE)
                pill.setTypeface(null, Typeface.BOLD)
            } else {
                pill.setBackgroundTintList(ColorStateList.valueOf(field))
                pill.setTextColor(secondary)
                pill.setTypeface(null, Typeface.NORMAL)
            }
        }
        binding.rotateLoading.visible()
        viewModel.dict(
            dictRule,
            word!!,
            onSuccess = {
                binding.rotateLoading.invisible()
                // 成功态恢复正文色（上一条错误可能已染 error 色）
                binding.tvDict.setTextColor(requireContext().getCompatColor(R.color.on_surface))
                binding.tvDict.setHtml(it)
            },
            onError = {
                binding.rotateLoading.invisible()
                // 错误消息 error 色（#BA1A1A），与词典正文区分
                binding.tvDict.setTextColor(requireContext().getCompatColor(R.color.error))
                binding.tvDict.text = it
            },
        )
    }
}
