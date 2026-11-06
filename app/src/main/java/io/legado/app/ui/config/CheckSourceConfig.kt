package io.legado.app.ui.config

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.DialogCheckSourceConfigBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.model.CheckSource
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.putPrefString
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import splitties.views.onClick

class CheckSourceConfig : BaseDialogFragment(R.layout.dialog_check_source_config) {

    private val binding by viewBinding(DialogCheckSourceConfigBinding::bind)

    //允许的最小超时时间，秒
    private val minTimeout = 0L

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        // 弹窗与页面底色分层（config-form-dialogs §06 弹窗容器行）：BaseDialogFragment 默认涂
        // ThemeStore.backgroundColor()（=页面底色），本页改涂 surface_container 运行时资源色 +
        // outline_variant 1dp 描边（读资源色，随明暗主题自动取值）；e-ink 模式维持基类描边逻辑
        if (!AppConfig.isEInkMode) {
            view.background = GradientDrawable().apply {
                cornerRadius =
                    view.context.resources.getDimensionPixelSize(R.dimen.corner_radius_lg).toFloat()
                setColor(view.context.getCompatColor(R.color.surface_container))
                setStroke(1.dpToPx(), view.context.getCompatColor(R.color.outline_variant))
            }
        }
        binding.run {
            // 浮标恒次要色 on_surface_variant（§06 输入域行：浮标 on_surface_variant；自定义控件
            // TextInputLayout init 默认 defaultHintTextColor=accentColor，需在此覆盖；
            // 写法同组内 CacheActivity.applyFilledFieldSpec / DirectLinkUploadConfig）
            val hintColor = ColorStateList.valueOf(
                requireContext().getCompatColor(R.color.on_surface_variant)
            )
            tilCheckSourceTimeout.defaultHintTextColor = hintColor
            tilCheckSourceTimeout.hintTextColor = hintColor
            // 聚焦描边运行时 accentColor（§06 输入域行：boxStrokeColor 运行时 accentColor，
            // 非聚焦描边 0dp 不显示，不固化主色到 XML）
            tilCheckSourceTimeout.setBoxStrokeColorStateList(
                ColorStateList.valueOf(requireContext().accentColor)
            )
            checkSearch.onClick {
                if (!checkSearch.isChecked && !checkDiscovery.isChecked) {
                    checkDiscovery.isChecked = true
                }
            }
            checkDiscovery.onClick {
                if (!checkSearch.isChecked && !checkDiscovery.isChecked) {
                    checkSearch.isChecked = true
                }
            }
            checkInfo.onClick {
                if (!checkInfo.isChecked) {
                    checkCategory.isChecked = false
                    checkContent.isChecked = false
                    checkCategory.isEnabled = false
                    checkContent.isEnabled = false
                } else {
                    checkCategory.isEnabled = true
                }
                syncCascadeAlpha()
            }
            checkCategory.onClick {
                if (!checkCategory.isChecked) {
                    checkContent.isChecked = false
                    checkContent.isEnabled = false
                } else {
                    checkContent.isEnabled = true
                }
                syncCascadeAlpha()
            }
        }
        CheckSource.run {
            binding.checkSourceTimeout.setText((timeout / 1000).toString())
            binding.checkSearch.isChecked = checkSearch
            binding.checkDiscovery.isChecked = checkDiscovery
            binding.checkInfo.isChecked = checkInfo
            binding.checkCategory.isChecked = checkCategory
            binding.checkContent.isChecked = checkContent
            binding.checkCategory.isEnabled = checkInfo
            binding.checkContent.isEnabled = checkCategory
            //级联禁用态补渲染（§06 级联禁用行：禁用 42% 透明），初始使能随存值
            syncCascadeAlpha()
            binding.tvCancel.onClick {
                dismiss()
            }
            binding.tvOk.onClick {
                val text = binding.checkSourceTimeout.text.toString()
                when {
                    text.isBlank() -> {
                        toastOnUi("${getString(R.string.timeout)}${getString(R.string.cannot_empty)}")
                        return@onClick
                    }
                    text.toLong() <= minTimeout -> {
                        toastOnUi(
                            "${getString(R.string.timeout)}${getString(R.string.less_than)}${minTimeout}${
                                getString(
                                    R.string.seconds
                                )
                            }"
                        )
                        return@onClick
                    }
                    else -> timeout = text.toLong() * 1000
                }
                checkSearch = binding.checkSearch.isChecked
                checkDiscovery = binding.checkDiscovery.isChecked
                checkInfo = binding.checkInfo.isChecked
                checkCategory = binding.checkCategory.isChecked
                checkContent = binding.checkContent.isChecked
                putConfig()
                putPrefString(PreferKey.checkSource, summary)
                dismiss()
            }
        }
    }

    //详情→目录→正文级联禁用渲染：禁用行 42% 透明（行为零改动，仅展示层）
    private fun syncCascadeAlpha() {
        binding.checkCategory.alpha = if (binding.checkCategory.isEnabled) 1f else ALPHA_DISABLED
        binding.checkContent.alpha = if (binding.checkContent.isEnabled) 1f else ALPHA_DISABLED
    }

    companion object {
        //§06 级联禁用行：禁用态透明度 42%
        private const val ALPHA_DISABLED = 0.42f
    }
}
