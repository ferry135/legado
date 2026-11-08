package io.legado.app.ui.book.changesource

import android.widget.ImageView
import androidx.core.graphics.drawable.DrawableCompat
import io.legado.app.R
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.gone
import io.legado.app.utils.visible
import splitties.init.appCtx

/**
 * 换源列表项 赞/踩 图标三态着色与可见性（设计稿02④⑤）：
 * 中性态两个图标均显示并着 on_surface_variant；赞激活（score > 0）显示赞并着 primaryColor，隐藏踩；
 * 踩激活（score < 0）显示踩并着 warning，隐藏赞。
 * 供 ChangeBookSourceAdapter 与 ChangeChapterSourceAdapter 共用，着色分支与列表定位方式
 * （layoutPosition / bindingAdapterPosition）无关。配色按 v3 稿面更新为 primary/warning/on_surface_variant，解析方式沿用原实现。
 */
internal fun tintVoteIcons(
    ivGood: ImageView,
    ivBad: ImageView,
    score: Int,
) {
    when {
        score > 0 -> {
            ivBad.gone()
            ivGood.visible()
            DrawableCompat.setTint(ivGood.drawable, appCtx.primaryColor)
            DrawableCompat.setTint(
                ivBad.drawable,
                appCtx.getCompatColor(R.color.on_surface_variant),
            )
        }
        score < 0 -> {
            ivGood.gone()
            ivBad.visible()
            DrawableCompat.setTint(
                ivGood.drawable,
                appCtx.getCompatColor(R.color.on_surface_variant),
            )
            DrawableCompat.setTint(
                ivBad.drawable,
                appCtx.getCompatColor(R.color.warning),
            )
        }
        else -> {
            ivGood.visible()
            ivBad.visible()
            DrawableCompat.setTint(
                ivGood.drawable,
                appCtx.getCompatColor(R.color.on_surface_variant),
            )
            DrawableCompat.setTint(
                ivBad.drawable,
                appCtx.getCompatColor(R.color.on_surface_variant),
            )
        }
    }
}
