package io.legado.app.ui.book.manga.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogMangaColorFilterBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

class MangaColorFilterDialog : BaseDialogFragment(R.layout.dialog_manga_color_filter) {
    private val binding by viewBinding(DialogMangaColorFilterBinding::bind)
    private val mConfig =
        GSON.fromJsonObject<MangaColorFilterConfig>(AppConfig.mangaColorFilter).getOrNull()
            ?: MangaColorFilterConfig()
    private val callback get() = activity as? Callback

    override fun onStart() {
        super.onStart()
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initData()
        initView()
    }

    private fun initData() {
        binding.run {
            dsbBrightness.progress = mConfig.l
            dsbR.progress = mConfig.r
            dsbG.progress = mConfig.g
            dsbB.progress = mConfig.b
            dsbA.progress = mConfig.a
            // 12-missed-2 #15：R/G/B/A 滑杆进度与滑块按通道色相着色（背景轨道保持主题色）
            dsbR.progressThumbTint = getCompatColor(R.color.md_red_500)
            dsbG.progressThumbTint = getCompatColor(R.color.md_green_500)
            dsbB.progressThumbTint = getCompatColor(R.color.md_blue_500)
            dsbA.progressThumbTint = getCompatColor(R.color.md_grey_500)
        }
    }

    private fun initView() {
        binding.run {
            dsbBrightness.onChanged = {
                mConfig.l = it
                callback?.updateColorFilter(mConfig)
            }
            dsbR.onChanged = {
                mConfig.r = it
                callback?.updateColorFilter(mConfig)
            }
            dsbG.onChanged = {
                mConfig.g = it
                callback?.updateColorFilter(mConfig)
            }
            dsbB.onChanged = {
                mConfig.b = it
                callback?.updateColorFilter(mConfig)
            }
            dsbA.onChanged = {
                mConfig.a = it
                callback?.updateColorFilter(mConfig)
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        AppConfig.mangaColorFilter = mConfig.toJson()
    }

    interface Callback {
        fun updateColorFilter(config: MangaColorFilterConfig)
    }

}