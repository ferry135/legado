package io.legado.app.ui.widget.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.databinding.DialogUrlOptionEditBinding
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.GSON
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.setLayout
import io.legado.app.utils.showSoftInput
import kotlin.math.roundToInt

class UrlOptionDialog(
    context: Context,
    private val success: (String) -> Unit,
) : Dialog(context) {
    val binding = DialogUrlOptionEditBinding.inflate(layoutInflater)

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.MATCH_PARENT)
        window?.setBackgroundDrawableResource(R.color.transparent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.root.setOnClickListener { dismiss() }
        binding.vwBg.setOnClickListener(null)
        binding.editMethod.setFilterValues("POST", "GET")
        binding.editCharset.setFilterValues(AppConst.charsets)
        // field 卡聚焦描边运行时注入（BookmarkDialog 先例）：默认/聚焦同 surface_container_high 底，
        // 聚焦叠 1.5dp 主色描边；主色取 accentColor 随 ThemeStore 换肤，布局不固化 primary。
        // 每卡独立 StateListDrawable 实例——共享实例会随单一视图的焦点态串卡
        val fieldCards =
            mapOf(
                binding.vwFieldMethod to binding.editMethod,
                binding.vwFieldCharset to binding.editCharset,
                binding.vwFieldHeaders to binding.editHeaders,
                binding.vwFieldBody to binding.editBody,
                binding.vwFieldType to binding.editType,
                binding.vwFieldRetry to binding.editRetry,
                binding.vwFieldWebJs to binding.editWebJs,
                binding.vwFieldJs to binding.editJs,
            )
        fieldCards.forEach { (card, edit) ->
            card.background =
                Selector
                    .shapeBuild()
                    .setCornerRadius(10.dpToPx())
                    .setStrokeWidth(1.5f.dpToPx().roundToInt())
                    .setDefaultBgColor(context.getCompatColor(R.color.surface_container_high))
                    .setFocusedBgColor(context.getCompatColor(R.color.surface_container_high))
                    .setFocusedStrokeColor(context.accentColor)
                    .create()
            // field 卡点击转焦内部输入框：卡体可点补足 ≥48dp 触控目标，
            // 并阻断点击冒泡到 root dismiss 防误关丢已输内容
            card.setOnClickListener { edit.showSoftInput() }
        }
        // useWebView 方框视觉（稿 .cb / item_group_select.xml:45-68 组件语言）：
        // 22dp 方框 6dp 圆角 / 2dp 描边 on_surface_variant，选中实底 primaryColor 运行时注入；
        // ThemeCheckBox 的 applyTint 只能着色系统勾选框 drawable，落不出方框形态，随 button=@null 弃用
        binding.cbUseWebView.background =
            Selector
                .shapeBuild()
                .setCornerRadius(6.dpToPx())
                .setStrokeWidth(2.dpToPx())
                .setDefaultBgColor(Color.TRANSPARENT)
                .setDefaultStrokeColor(ContextCompat.getColor(context, R.color.on_surface_variant))
                .setCheckedBgColor(context.primaryColor)
                .setCheckedStrokeColor(context.primaryColor)
                .create()
        syncCheckIcon()
        // 勾选热区扩到整行（GroupSelectDialog.kt:184-196 同模式）：整行可点翻转勾选态
        binding.vwUseWebView.setOnClickListener {
            binding.cbUseWebView.toggle()
            syncCheckIcon()
        }
        binding.tvOk.setOnClickListener {
            success.invoke(GSON.toJson(getUrlOption()))
            dismiss()
        }
    }

    private fun syncCheckIcon() {
        binding.ivCheck.visibility =
            if (binding.cbUseWebView.isChecked) View.VISIBLE else View.GONE
    }

    private fun getUrlOption(): AnalyzeUrl.UrlOption {
        val urlOption = AnalyzeUrl.UrlOption()
        urlOption.useWebView(binding.cbUseWebView.isChecked)
        urlOption.setMethod(binding.editMethod.text.toString())
        urlOption.setCharset(binding.editCharset.text.toString())
        urlOption.setHeaders(binding.editHeaders.text.toString())
        urlOption.setBody(binding.editBody.text.toString())
        urlOption.setRetry(binding.editRetry.text.toString())
        urlOption.setType(binding.editType.text.toString())
        urlOption.setWebJs(binding.editWebJs.text.toString())
        urlOption.setJs(binding.editJs.text.toString())
        return urlOption
    }
}
