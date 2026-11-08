package io.legado.app.ui.config

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogDirectLinkUploadConfigBinding
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.GSON
import io.legado.app.utils.applyTint
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getClipText
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import splitties.init.appCtx
import splitties.views.onClick

class DirectLinkUploadConfig :
    BaseDialogFragment(R.layout.dialog_direct_link_upload_config),
    Toolbar.OnMenuItemClickListener {
    private val binding by viewBinding(DialogDirectLinkUploadConfigBinding::bind)

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        // config-form-dialogs §06 弹窗容器：本页卡面 surface_container + outline_variant 描边，
        // 与页面底色 surface 分层（全局 BaseDialogFragment 路径不动，逐页覆盖）；e-ink 模式
        // 维持基类 bg_eink_border_* 描边逻辑（BaseDialogFragment onStart 内再涂）
        if (!AppConfig.isEInkMode) {
            view.setBackgroundResource(R.drawable.bg_config_direct_link_upload_dialog_card)
        }
        binding.toolBar.inflateMenu(R.menu.direct_link_upload_config)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener(this)
        // config-form-dialogs §06 输入域 FILLED：浮标恒次要色（稿 .fl 恒次要色，与组内其余四页一致），
        // 聚焦描边运行时 accentColor
        applyFieldTint()
        upView(DirectLinkUpload.getRule())
        binding.tvCancel.onClick {
            dismiss()
        }
        binding.tvFooterLeft.onClick {
            test()
        }
        binding.tvOk.onClick {
            getRule()?.let { rule ->
                DirectLinkUpload.putConfig(rule)
                dismiss()
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_import_default -> importDefault()
            R.id.menu_copy_rule ->
                getRule()?.let { rule ->
                    requireContext().sendToClip(GSON.toJson(rule))
                }

            R.id.menu_paste_rule ->
                runCatching {
                    requireContext().getClipText()!!.let {
                        val rule = GSON.fromJsonObject<DirectLinkUpload.Rule>(it).getOrThrow()
                        upView(rule)
                    }
                }.onFailure {
                    toastOnUi("剪贴板为空或格式不对")
                }
        }
        return true
    }

    // config-form-dialogs §06 输入域：浮标恒次要色（稿 .fl 恒次要色，组内同款；主色只出现在
    // 聚焦描边/勾选框/确认钮）。Material 1.12 无 focusedHintTextColor setter：写法同组内
    // CacheActivity.applyFilledFieldSpec——defaultHintTextColor=非聚焦浮标、hintTextColor=聚焦
    // 浮标，两槽位均取 on_surface_variant（setDefaultHintTextColor 在 Material 1.12 会连写聚焦
    // 槽位，显式双写以防上游行为变化）；聚焦描边 boxStrokeColor 运行时 accentColor（实底
    // surface_container_high、10dp 圆角由 Widget.Legado.FilledField 样式承担，聚焦宽度 1.5dp
    // 由布局 inline 覆盖）；仅本页三域，不改全局样式与共享控件
    private fun applyFieldTint() {
        val accent = requireContext().accentColor
        val hintColor = requireContext().getCompatColor(R.color.on_surface_variant)
        listOf(binding.tilUploadUrl, binding.tilDownloadUrlRule, binding.tilSummary).forEach { til ->
            til.defaultHintTextColor = ColorStateList.valueOf(hintColor)
            til.hintTextColor = ColorStateList.valueOf(hintColor)
            til.boxStrokeColor = accent
        }
    }

    private fun upView(rule: DirectLinkUpload.Rule) {
        binding.editUploadUrl.setText(rule.uploadUrl)
        binding.editDownloadUrlRule.setText(rule.downloadUrlRule)
        binding.editSummary.setText(rule.summary)
        binding.cbCompress.isChecked = rule.compress
    }

    private fun getRule(): DirectLinkUpload.Rule? {
        val uploadUrl = binding.editUploadUrl.text?.toString()
        val downloadUrlRule = binding.editDownloadUrlRule.text?.toString()
        val summary = binding.editSummary.text?.toString()
        val compress = binding.cbCompress.isChecked
        if (uploadUrl.isNullOrBlank()) {
            toastOnUi("上传Url不能为空")
            return null
        }
        if (downloadUrlRule.isNullOrBlank()) {
            toastOnUi("下载Url规则不能为空")
            return null
        }
        if (summary.isNullOrBlank()) {
            toastOnUi("注释不能为空")
            return null
        }
        return DirectLinkUpload.Rule(uploadUrl, downloadUrlRule, summary, compress)
    }

    private fun importDefault() {
        requireContext().selector(DirectLinkUpload.defaultRules) { _, rule, _ ->
            upView(rule)
        }
    }

    private fun test() {
        val rule = getRule() ?: return
        execute {
            DirectLinkUpload.upLoad("test.json", "{}", "application/json", rule)
        }.onError {
            alertTestResult(it.localizedMessage ?: "ERROR")
        }.onSuccess { result ->
            alertTestResult(result)
        }
    }

    private fun alertTestResult(result: String) {
        alert {
            setTitle("result")
            setMessage(result)
            okButton()
            negativeButton(R.string.copy_text) {
                appCtx.sendToClip(result)
            }
        }.apply {
            // config-form-dialogs §06 底栏主次色分离：逐弹窗重着色，不改全局 applyTint
            // 「拷贝内容」（NEGATIVE）次要色，「确定」（POSITIVE）运行时主色
            getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
                requireContext().getCompatColor(R.color.on_surface_variant),
            )
            getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(requireContext().accentColor)
        }
    }
}
