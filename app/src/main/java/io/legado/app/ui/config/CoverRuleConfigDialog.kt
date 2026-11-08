package io.legado.app.ui.config

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogCoverRuleConfigBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.model.BookCover
import io.legado.app.utils.GSON
import io.legado.app.utils.dpToPx
import io.legado.app.utils.setLayout
import io.legado.app.utils.sheetBackground
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.views.onClick

class CoverRuleConfigDialog : BaseDialogFragment(R.layout.dialog_cover_rule_config) {
    val binding by viewBinding(DialogCoverRuleConfigBinding::bind)

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        applyDialogSurface(view)
        applyInputTint()
        initData()
        binding.tvCancel.onClick {
            dismissAllowingStateLoss()
        }
        binding.tvOk.onClick {
            val enable = binding.cbEnable.isChecked
            val searchUrl = binding.editSearchUrl.text?.toString()
            val coverRule = binding.editCoverUrlRule.text?.toString()
            if (searchUrl.isNullOrBlank() || coverRule.isNullOrBlank()) {
                toastOnUi("搜索url和cover规则不能为空")
            } else {
                BookCover.CoverRule(enable, searchUrl, coverRule).let { config ->
                    BookCover.saveCoverRule(config)
                }
                dismissAllowingStateLoss()
            }
        }
        binding.tvFooterLeft.onClick {
            BookCover.delCoverRule()
            dismissAllowingStateLoss()
        }
    }

    /**
     * 弹窗卡面：surface_container 运行时资源色 + outline_variant 1dp 描边
     * （config-form-dialogs §06 弹窗容器行：卡面由基类默认的 ThemeStore.backgroundColor()（= surface，
     * 与页面底色同色）改取 surface_container，与页面底色分层）。基类为多弹窗共用不动，
     * 仅本页覆写；EInk 模式沿用基类 bg_eink_border_* 体系，不注入。
     */
    private fun applyDialogSurface(view: View) {
        if (AppConfig.isEInkMode) return
        val context = view.context
        view.background =
            context
                .sheetBackground(
                    ContextCompat.getColor(context, R.color.surface_container),
                ).apply {
                    setStroke(1.dpToPx(), ContextCompat.getColor(context, R.color.outline_variant))
                }
    }

    /**
     * 输入域运行时着色（§06 输入域行 FILLED 路线）：聚焦描边取 ThemeStore accentColor
     * （换肤安全，XML 不固化 accent）；浮标 hint 收敛 on_surface_variant
     * （默认态/聚焦态一致，主色只出现在描边与光标）。
     */
    private fun applyInputTint() {
        val context = context ?: return
        val hintColors =
            ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.on_surface_variant),
            )
        val strokeColors =
            Selector
                .colorBuild()
                .setDefaultColor(Color.TRANSPARENT)
                .setFocusedColor(context.accentColor)
                .create()
        for (layout in listOf(binding.tilSearchUrl, binding.tilCoverRule)) {
            layout.defaultHintTextColor = hintColors
            layout.hintTextColor = hintColors
            layout.setBoxStrokeColorStateList(strokeColors)
        }
    }

    private fun initData() {
        lifecycleScope.launch {
            val rule =
                withContext(IO) {
                    BookCover.getCoverRule()
                }
            Log.e("coverRule", GSON.toJson(rule))
            binding.cbEnable.isChecked = rule.enable
            binding.editSearchUrl.setText(rule.searchUrl)
            binding.editCoverUrlRule.setText(rule.coverRule)
        }
    }
}
