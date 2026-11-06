package io.legado.app.ui.book.read.config

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.DialogPageKeyBinding
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.getPrefString
import io.legado.app.utils.hideSoftInput
import io.legado.app.utils.putPrefString
import io.legado.app.utils.showSoftInput
import io.legado.app.utils.setLayout
import splitties.views.onClick
import kotlin.math.roundToInt


class PageKeyDialog(context: Context) : Dialog(context) {

    private val binding = DialogPageKeyBinding.inflate(layoutInflater)

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    init {
        // window 透明让 root 圆角底（16dp GradientDrawable）可见，不露出平台默认不透明 dialog 背景
        // （WaitDialog.kt:17 / BaseDialogFragment.kt:42-43 / UrlOptionDialog.kt:31 同一手法）
        window?.setBackgroundDrawableResource(R.color.transparent)
        setContentView(binding.root)
        binding.run {
            // 弹窗底运行时圆角化（DECISIONS §4，corner_radius_lg=16dp）：底色仍取 ThemeStore 运行时 backgroundColor
            contentView.background = GradientDrawable().apply {
                setColor(context.backgroundColor)
                cornerRadius = 16.dpToPx().toFloat()
            }
            // field 卡聚焦描边运行时注入（BookmarkDialog.kt:63 同法）：默认/聚焦同 surface_container_high 底，
            // 仅聚焦叠 1.5dp 主色描边；accentColor 随 ThemeStore 换肤（MASTER.md §1.2）。
            // 两卡各自 create 一份：StateListDrawable 选中态挂实例上，共享会串描边
            fun newFieldBackground() = Selector.shapeBuild()
                .setCornerRadius(10.dpToPx())
                .setStrokeWidth(1.5f.dpToPx().roundToInt())
                .setDefaultBgColor(context.getCompatColor(R.color.surface_container_high))
                .setFocusedBgColor(context.getCompatColor(R.color.surface_container_high))
                .setFocusedStrokeColor(context.accentColor)
                .create()
            vwFieldPrev.background = newFieldBackground()
            vwFieldNext.background = newFieldBackground()
            // field 卡可点转焦（VariableDialog.kt:70-72 同法）：点击整卡转焦输入框并弹软键盘，
            // 补足 ≥48dp 触控目标（showSoftInput = requestFocus + showSoftInput）
            vwFieldPrev.setOnClickListener { etPrev.showSoftInput() }
            vwFieldNext.setOnClickListener { etNext.showSoftInput() }
            etPrev.setText(context.getPrefString(PreferKey.prevKeys))
            etNext.setText(context.getPrefString(PreferKey.nextKeys))
            tvReset.onClick {
                etPrev.setText("")
                etNext.setText("")
            }
            tvOk.setOnClickListener {
                context.putPrefString(PreferKey.prevKeys, etPrev.text?.toString())
                context.putPrefString(PreferKey.nextKeys, etNext.text?.toString())
                dismiss()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_DEL) {
            if (binding.etPrev.hasFocus()) {
                val editableText = binding.etPrev.editableText
                if (editableText.isEmpty() or editableText.endsWith(",")) {
                    editableText.append(keyCode.toString())
                } else {
                    editableText.append(",").append(keyCode.toString())
                }
                return true
            } else if (binding.etNext.hasFocus()) {
                val editableText = binding.etNext.editableText
                if (editableText.isEmpty() or editableText.endsWith(",")) {
                    editableText.append(keyCode.toString())
                } else {
                    editableText.append(",").append(keyCode.toString())
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun dismiss() {
        super.dismiss()
        currentFocus?.hideSoftInput()
    }

}
