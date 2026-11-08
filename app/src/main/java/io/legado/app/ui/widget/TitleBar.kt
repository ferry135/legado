package io.legado.app.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Menu
import android.view.View
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.StyleRes
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.alpha
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import com.google.android.material.appbar.AppBarLayout
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.elevation
import io.legado.app.utils.activity
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import splitties.views.bottomPadding
import splitties.views.topPadding

@Suppress("unused", "MemberVisibilityCanBePrivate")
class TitleBar
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppBarLayout(context, attrs) {
    val toolbar: Toolbar
    val menu: Menu
        get() = toolbar.menu

    var title: CharSequence?
        get() = toolbar.title
        set(title) {
            if (toolbar.title != title) {
                toolbar.title = title
            }
        }

    var subtitle: CharSequence?
        get() = toolbar.subtitle
        set(subtitle) {
            if (toolbar.subtitle != subtitle) {
                toolbar.subtitle = subtitle
            }
        }

    private val displayHomeAsUp: Boolean
    private val navigationIconTint: ColorStateList?
    private val navigationIconTintMode: Int
    private val fitStatusBar: Boolean
    private val fitNavigationBar: Boolean
    private val attachToActivity: Boolean
    private val themeMode: Int

    init {
        val a =
            context.obtainStyledAttributes(
                attrs,
                R.styleable.TitleBar,
                R.attr.titleBarStyle,
                0,
            )
        navigationIconTint = a.getColorStateList(R.styleable.TitleBar_navigationIconTint)
        navigationIconTintMode = a.getInt(R.styleable.TitleBar_navigationIconTintMode, 9)
        attachToActivity = a.getBoolean(R.styleable.TitleBar_attachToActivity, true)
        displayHomeAsUp = a.getBoolean(R.styleable.TitleBar_displayHomeAsUp, true)
        fitStatusBar = a.getBoolean(R.styleable.TitleBar_fitStatusBar, true)
        fitNavigationBar = a.getBoolean(R.styleable.TitleBar_fitNavigationBar, false)

        val navigationIcon = a.getDrawable(R.styleable.TitleBar_navigationIcon)
        val navigationContentDescription =
            a.getText(R.styleable.TitleBar_navigationContentDescription)
        val titleText = a.getString(R.styleable.TitleBar_title)
        val subtitleText = a.getString(R.styleable.TitleBar_subtitle)

        themeMode = a.getInt(R.styleable.TitleBar_themeMode, 0)
        when (themeMode) {
            1 -> inflate(context, R.layout.view_title_bar_dark, this)
            else -> inflate(context, R.layout.view_title_bar, this)
        }
        toolbar = findViewById(R.id.toolbar)

        toolbar.apply {
            navigationIcon?.let {
                this.navigationIcon = it
                this.navigationContentDescription = navigationContentDescription
            }

            if (a.hasValue(R.styleable.TitleBar_titleTextAppearance)) {
                this.setTitleTextAppearance(
                    context,
                    a.getResourceId(R.styleable.TitleBar_titleTextAppearance, 0),
                )
            }

            if (a.hasValue(R.styleable.TitleBar_titleTextColor)) {
                this.setTitleTextColor(a.getColor(R.styleable.TitleBar_titleTextColor, -0x1))
            }

            if (a.hasValue(R.styleable.TitleBar_subtitleTextAppearance)) {
                this.setSubtitleTextAppearance(
                    context,
                    a.getResourceId(R.styleable.TitleBar_subtitleTextAppearance, 0),
                )
            }

            if (a.hasValue(R.styleable.TitleBar_subtitleTextColor)) {
                this.setSubtitleTextColor(a.getColor(R.styleable.TitleBar_subtitleTextColor, -0x1))
            }

            if (a.hasValue(R.styleable.TitleBar_contentInsetLeft) ||
                a.hasValue(R.styleable.TitleBar_contentInsetRight)
            ) {
                this.setContentInsetsAbsolute(
                    a.getDimensionPixelSize(R.styleable.TitleBar_contentInsetLeft, 0),
                    a.getDimensionPixelSize(R.styleable.TitleBar_contentInsetRight, 0),
                )
            }

            if (a.hasValue(R.styleable.TitleBar_contentInsetStart) ||
                a.hasValue(R.styleable.TitleBar_contentInsetEnd)
            ) {
                this.setContentInsetsRelative(
                    a.getDimensionPixelSize(R.styleable.TitleBar_contentInsetStart, 0),
                    a.getDimensionPixelSize(R.styleable.TitleBar_contentInsetEnd, 0),
                )
            }

            if (a.hasValue(R.styleable.TitleBar_contentInsetStartWithNavigation)) {
                this.contentInsetStartWithNavigation =
                    a.getDimensionPixelOffset(
                        R.styleable.TitleBar_contentInsetStartWithNavigation,
                        0,
                    )
            }

            if (a.hasValue(R.styleable.TitleBar_contentInsetEndWithActions)) {
                this.contentInsetEndWithActions =
                    a.getDimensionPixelOffset(
                        R.styleable.TitleBar_contentInsetEndWithActions,
                        0,
                    )
            }

            if (!titleText.isNullOrBlank()) {
                this.title = titleText
            }

            if (!subtitleText.isNullOrBlank()) {
                this.subtitle = subtitleText
            }

            if (a.hasValue(R.styleable.TitleBar_contentLayout)) {
                inflate(context, a.getResourceId(R.styleable.TitleBar_contentLayout, 0), this)
            }
        }

        if (!isInEditMode) {
//            if (fitStatusBar) {
//                setPadding(paddingLeft, context.statusBarHeight, paddingRight, paddingBottom)
//            }
//
//            if (fitNavigationBar) {
//                setPadding(paddingLeft, paddingTop, paddingRight, context.navigationBarHeight)
//            }

            if (fitStatusBar || fitNavigationBar) {
                setOnApplyWindowInsetsListenerCompat { _, windowInsets ->
                    val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                    if (fitStatusBar) {
                        topPadding = insets.top
                    }
                    if (fitNavigationBar) {
                        bottomPadding = insets.bottom
                    }
                    windowInsets
                }
            }

            upTheme()

            stateListAnimator = null
            elevation = if (themeMode == 3) 0f else context.elevation
        }
        a.recycle()
    }

    fun upTheme() {
        if (isInEditMode) return
        if (AppConfig.isEInkMode) {
            setBackgroundResource(R.drawable.bg_eink_border_bottom)
        } else if (themeMode == 1) {
            // 沉浸式：封面/播放器大图穿透工具栏
            setBackgroundColor(Color.TRANSPARENT)
        } else if (themeMode == 3) {
            // 融入式：与页面背景同色，状态栏自然过渡到内容区
            setBackgroundColor(context.getCompatColor(R.color.surface))
            toolbar.setTitleTextColor(context.getCompatColor(R.color.on_surface))
            toolbar.setSubtitleTextColor(context.getCompatColor(R.color.on_surface_variant))
            val iconTint = ColorStateList.valueOf(context.getCompatColor(R.color.on_surface))
            toolbar.navigationIcon?.setTintList(iconTint)
            toolbar.overflowIcon?.setTintList(iconTint)
        } else {
            // 浅色工具栏：白天浅色表面，夜间深色表面，主题色只作点缀（起点读书/QQ阅读风格）
            setBackgroundColor(context.getCompatColor(R.color.surface_container))
            toolbar.setTitleTextColor(context.getCompatColor(R.color.on_surface))
            toolbar.setSubtitleTextColor(context.getCompatColor(R.color.on_surface_variant))
            val iconTint = ColorStateList.valueOf(context.getCompatColor(R.color.on_surface))
            toolbar.navigationIcon?.setTintList(iconTint)
            toolbar.overflowIcon?.setTintList(iconTint)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachToActivity()
    }

    fun setNavigationOnClickListener(clickListener: ((View) -> Unit)) {
        toolbar.setNavigationOnClickListener(clickListener)
    }

    fun setTitle(titleId: Int) {
        toolbar.setTitle(titleId)
    }

    fun setSubTitle(subtitleId: Int) {
        toolbar.setSubtitle(subtitleId)
    }

    fun setTitleTextColor(
        @ColorInt color: Int,
    ) {
        toolbar.setTitleTextColor(color)
    }

    fun setTitleTextAppearance(
        @StyleRes resId: Int,
    ) {
        toolbar.setTitleTextAppearance(context, resId)
    }

    fun setSubTitleTextColor(
        @ColorInt color: Int,
    ) {
        toolbar.setSubtitleTextColor(color)
    }

    fun setSubTitleTextAppearance(
        @StyleRes resId: Int,
    ) {
        toolbar.setSubtitleTextAppearance(context, resId)
    }

    fun setTextColor(
        @ColorInt color: Int,
    ) {
        setTitleTextColor(color)
        setSubTitleTextColor(color)
    }

    fun setColorFilter(
        @ColorInt color: Int,
    ) {
        val colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        toolbar.children
            .firstOrNull { it is ImageView }
            ?.background
            ?.colorFilter = colorFilter
        toolbar.navigationIcon?.colorFilter = colorFilter
        toolbar.overflowIcon?.colorFilter = colorFilter
        toolbar.menu.children.forEach {
            it.icon?.colorFilter = colorFilter
        }
    }

    override fun setBackgroundColor(color: Int) {
        if (color.alpha < 255) {
            // 这里不能改为0f,改为0f在横屏模式下文字和图标颜色会变
            elevation = 0.1f
        }
        super.setBackgroundColor(color)
    }

    override fun setBackground(background: Drawable?) {
        if (background is ColorDrawable) {
            if (background.alpha < 255) {
                // 这里不能改为0f,改为0f在横屏模式下文字和图标颜色会变
                elevation = 0.1f
            }
        }
        super.setBackground(background)
    }

    fun onMultiWindowModeChanged(
        isInMultiWindowMode: Boolean,
        fullScreen: Boolean,
    ) {
//        if (fitStatusBar) {
//            val topPadding = if (!isInMultiWindowMode && fullScreen) context.statusBarHeight else 0
//            setPadding(paddingLeft, topPadding, paddingRight, paddingBottom)
//        }
    }

    private fun attachToActivity() {
        if (attachToActivity) {
            activity?.let {
                it.setSupportActionBar(toolbar)
                it.supportActionBar?.setDisplayHomeAsUpEnabled(displayHomeAsUp)
            }
        }
    }
}
