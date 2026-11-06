package io.legado.app.ui.widget.dialog

import android.app.Dialog
import android.content.Context
import io.legado.app.R
import io.legado.app.databinding.DialogWaitBinding


@Suppress("unused")
class WaitDialog(context: Context) : Dialog(context) {

    val binding = DialogWaitBinding.inflate(layoutInflater)

    init {
        // widget-viewer-dialogs §05③：window 透明让 root 圆角底（bg_widget_dialog_wait_dialog_card）
        // 透出遮罩；与 BaseDialogFragment adaptationSoftKeyboard 分支（BaseDialogFragment.kt:42-43）同一手法
        window?.setBackgroundDrawableResource(R.color.transparent)
        setCanceledOnTouchOutside(false)
        setContentView(binding.root)
    }

    fun setText(text: String): WaitDialog {
        binding.tvMsg.text = text
        return this
    }

    fun setText(res: Int): WaitDialog {
        binding.tvMsg.setText(res)
        return this
    }

}