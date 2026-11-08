package io.legado.app.utils

import android.app.Dialog
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.core.view.forEach
import androidx.fragment.app.DialogFragment
import io.legado.app.R
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.alertCardBackground
import splitties.systemservices.windowManager

/**
 * 统一弹窗遮罩 scrim_dialog（#80000000 = 50% 黑）：dimAmount 单一出口，10-reader-prefs 全稿共用。
 * EInk 模式下 BaseDialogFragment.onStart 已清除 FLAG_DIM_BEHIND，此值不生效。
 */
fun Window.applyDialogScrim() {
    val attr = attributes
    attr.dimAmount = 0.5f
    attributes = attr
}

/**
 * 透明窗口底 + decorView padding 清零 + scrim_dialog 统一遮罩，圆角弹窗共用收口。
 * 窗口背景须透明：不透明底（与弹层近同色）会把 16dp 顶圆角的缺口填成直角，圆角不可见。
 */
fun Window.applyTransparentScrimWindow() {
    setBackgroundDrawableResource(R.color.transparent)
    decorView.setPadding(0, 0, 0, 0)
    applyDialogScrim()
}

/**
 * 底部弹层窗口形态：applyTransparentScrimWindow + 底部重力 + MATCH_PARENT × [height] 一处收口。
 * [height] 传像素高度：固定高度（如 360.dpToPx()）或 WRAP_CONTENT 自适应。
 */
fun Window.applyBottomSheetWindow(height: Int) {
    applyTransparentScrimWindow()
    val attr = attributes
    attr.gravity = Gravity.BOTTOM
    attributes = attr
    setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height)
}

fun AlertDialog.applyTint(): AlertDialog {
    window?.setBackgroundDrawable(context.alertCardBackground)
    val colorStateList =
        Selector
            .colorBuild()
            .setDefaultColor(ThemeStore.accentColor(context))
            .setPressedColor(ColorUtils.darkenColor(ThemeStore.accentColor(context)))
            .create()
    if (getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
        getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(colorStateList)
    }
    if (getButton(AlertDialog.BUTTON_POSITIVE) != null) {
        getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(colorStateList)
    }
    if (getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
        getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(colorStateList)
    }
    window?.decorView?.post {
        listView?.forEach {
            it.applyTint(context.accentColor)
        }
    }
    return this
}

fun AlertDialog.requestInputMethod() {
    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
}

fun DialogFragment.setLayout(
    widthMix: Float,
    heightMix: Float,
) {
    dialog?.setLayout(widthMix, heightMix)
}

fun Dialog.setLayout(
    widthMix: Float,
    heightMix: Float,
) {
    val dm = context.windowManager.windowSize
    window?.setLayout(
        (dm.widthPixels * widthMix).toInt(),
        (dm.heightPixels * heightMix).toInt(),
    )
}

fun DialogFragment.setLayout(
    width: Int,
    heightMix: Float,
) {
    dialog?.setLayout(width, heightMix)
}

fun Dialog.setLayout(
    width: Int,
    heightMix: Float,
) {
    val dm = context.windowManager.windowSize
    window?.setLayout(
        width,
        (dm.heightPixels * heightMix).toInt(),
    )
}

fun DialogFragment.setLayout(
    widthMix: Float,
    height: Int,
) {
    dialog?.setLayout(widthMix, height)
}

fun Dialog.setLayout(
    widthMix: Float,
    height: Int,
) {
    val dm = context.windowManager.windowSize
    window?.setLayout(
        (dm.widthPixels * widthMix).toInt(),
        height,
    )
}

fun DialogFragment.setLayout(
    width: Int,
    height: Int,
) {
    dialog?.setLayout(width, height)
}

fun Dialog.setLayout(
    width: Int,
    height: Int,
) {
    window?.setLayout(width, height)
}
