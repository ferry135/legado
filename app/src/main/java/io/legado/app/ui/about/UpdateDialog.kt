package io.legado.app.ui.about

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogUpdateBinding
import io.legado.app.help.update.AppUpdate
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.Download
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin

class UpdateDialog() : BaseDialogFragment(R.layout.dialog_update) {
    constructor(updateInfo: AppUpdate.UpdateInfo) : this() {
        arguments =
            Bundle().apply {
                putString("newVersion", updateInfo.tagName)
                putString("updateBody", updateInfo.updateLog)
                putString("url", updateInfo.downloadUrl)
                putString("name", updateInfo.fileName)
            }
    }

    val binding by viewBinding(DialogUpdateBinding::bind)

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        binding.toolBar.title = arguments?.getString("newVersion")
        val updateBody = arguments?.getString("updateBody")
        if (updateBody == null) {
            toastOnUi("没有数据")
            dismiss()
            return
        }
        binding.textView.post {
            Markwon
                .builder(requireContext())
                .usePlugin(GlideImagesPlugin.create(requireContext()))
                .usePlugin(HtmlPlugin.create())
                .usePlugin(TablePlugin.create(requireContext()))
                .build()
                .setMarkdown(binding.textView, updateBody)
        }
        binding.toolBar.inflateMenu(R.menu.app_update)
        binding.toolBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_download -> startDownload()
            }
            return@setOnMenuItemClickListener true
        }
        // 12-missed-2 #06：底部「下载 APK」主按钮（与顶栏下载菜单同触达）
        // 主 CTA 可读配色：AccentBgTextView 填充主色白字压默认主色橙仅 2.84:1（亮暗均如此），
        // 仿 SpeakEngineDialog tonal 胶囊运行时自适应（亮 primary 14% 底 + shift 0.6 深主色字
        // 实测 5.6:1 / 暗 primary 18% 底 + 主色字），且跟随用户换肤
        val primary = primaryColor
        val isLightBg = ColorUtils.isColorLight(requireContext().backgroundColor)
        binding.btnDownload.background =
            Selector
                .shapeBuild()
                .setCornerRadius(10.dpToPx())
                .setDefaultBgColor(ColorUtils.adjustAlpha(primary, if (isLightBg) 0.14f else 0.18f))
                .setPressedBgColor(ColorUtils.adjustAlpha(primary, if (isLightBg) 0.24f else 0.28f))
                .create()
        binding.btnDownload.setTextColor(
            if (isLightBg) ColorUtils.shiftColor(primary, 0.6f) else primary,
        )
        binding.btnDownload.setOnClickListener { startDownload() }
    }

    private fun startDownload() {
        val url = arguments?.getString("url")
        val name = arguments?.getString("name")
        if (url != null && name != null) {
            Download.start(requireContext(), url, name)
            toastOnUi(R.string.download_start)
        }
    }
}
