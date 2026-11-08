package io.legado.app.ui.widget.dialog

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogCodeViewBinding
import io.legado.app.help.IntentData
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.ui.widget.code.addJsonPattern
import io.legado.app.ui.widget.code.addLegadoPattern
import io.legado.app.utils.applyTint
import io.legado.app.utils.disableEdit
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

class CodeDialog() : BaseDialogFragment(R.layout.dialog_code_view) {
    constructor(code: String, disableEdit: Boolean = true, requestId: String? = null) : this() {
        arguments =
            Bundle().apply {
                putBoolean("disableEdit", disableEdit)
                putString("code", IntentData.put(code))
                putString("requestId", requestId)
            }
    }

    val binding by viewBinding(DialogCodeViewBinding::bind)

    override fun onStart() {
        super.onStart()
        // widget-viewer-dialogs §05①：window 设透明，根 View 的 16dp 圆角底
        // （BaseDialogFragment.kt:84 setSheetBackground）才不会被不透明 window 填平四角，
        // 遮罩可透出；先例 BaseDialogFragment.kt:42-43 adaptationSoftKeyboard 分支
        dialog?.window?.setBackgroundDrawableResource(R.color.transparent)
        setLayout(1f, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        if (arguments?.getBoolean("disableEdit") == true) {
            binding.toolBar.title = "code view"
            binding.codeView.disableEdit()
            // widget-viewer-dialogs §05⑧：「只读」徽标仅查看态显示（展示层增强，删去即回现状）
            binding.tvReadOnlyBadge.visibility = View.VISIBLE
        } else {
            initMenu()
        }
        binding.codeView.addLegadoPattern()
        binding.codeView.addJsonPattern()
        binding.codeView.addJsPattern()
        arguments?.getString("code")?.let {
            binding.codeView.text = IntentData.get(it)
        }
    }

    private fun initMenu() {
        binding.toolBar.inflateMenu(R.menu.code_edit)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_save -> {
                    binding.codeView.text?.toString()?.let { code ->
                        val requestId = arguments?.getString("requestId")
                        (parentFragment as? Callback)?.onCodeSave(code, requestId)
                            ?: (activity as? Callback)?.onCodeSave(code, requestId)
                    }
                    dismiss()
                }
            }
            return@setOnMenuItemClickListener true
        }
    }

    interface Callback {
        fun onCodeSave(
            code: String,
            requestId: String?,
        )
    }
}
