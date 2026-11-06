package io.legado.app.ui.widget.dialog

import android.app.Application
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.BaseViewModel
import io.legado.app.databinding.DialogVariableBinding
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.applyTint
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.setLayout
import io.legado.app.utils.showSoftInput
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlin.math.roundToInt

class VariableDialog() : BaseDialogFragment(R.layout.dialog_variable, true),
    Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogVariableBinding::bind)
    private val viewModel by viewModels<ViewModel>()

    constructor(title: String, key: String, variable: String?, comment: String) : this() {
        arguments = Bundle().apply {
            putString("title", title)
            putString("key", key)
            putString("variable", variable)
            putString("comment", comment)
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        arguments?.let {
            binding.toolBar.title = it.getString("title")
            viewModel.init(it) {
                binding.tvComment.text = viewModel.comment
                binding.tvVariable.setText(viewModel.variable)
            }
        } ?: let {
            dismiss()
            return
        }
        // variable field 卡聚焦描边运行时注入（BookmarkDialog.kt:58-66 先例）：?attr/colorAccent 在
        // 主题中固化不随 ThemeStore 换肤（MASTER.md §1.2）；默认/聚焦同 surface_container_high 底，
        // 仅聚焦叠 1.5dp 主色描边，布局不固化 primary/accent
        val fieldBackground = Selector.shapeBuild()
            .setCornerRadius(10.dpToPx())
            .setStrokeWidth(1.5f.dpToPx().roundToInt())
            .setDefaultBgColor(getCompatColor(R.color.surface_container_high))
            .setFocusedBgColor(getCompatColor(R.color.surface_container_high))
            .setFocusedStrokeColor(requireContext().accentColor)
            .create()
        binding.vwVariableCard.background = fieldBackground
        // field 卡可点转焦（url-variable-dialogs §04·11）：点击转焦 tv_variable 弹软键盘，
        // 补足 ≥48dp 触控目标并阻断点击冒泡到 root dismiss（BaseDialogFragment.kt:78-82）
        binding.vwVariableCard.setOnClickListener {
            binding.tvVariable.showSoftInput()
        }
        binding.toolBar.inflateMenu(R.menu.save)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener(this)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_save -> {
                callback?.setVariable(
                    viewModel.key ?: "",
                    binding.tvVariable.text?.toString()
                )
                dismissAllowingStateLoss()
            }
        }
        return true
    }

    val callback get() = (parentFragment as? Callback) ?: (activity as? Callback)

    class ViewModel(application: Application) : BaseViewModel(application) {

        var key: String? = null
        var comment: String? = null
        var variable: String? = null

        fun init(arguments: Bundle, onFinally: () -> Unit) {
            if (key != null) return
            execute {
                key = arguments.getString("key")
                comment = arguments.getString("comment")
                variable = arguments.getString("variable")
            }.onFinally {
                onFinally.invoke()
            }
        }

    }

    interface Callback {

        fun setVariable(key: String, variable: String?)

    }

}