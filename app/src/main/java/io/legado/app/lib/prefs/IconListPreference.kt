package io.legado.app.lib.prefs

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.FragmentActivity
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemIconPreferenceBinding
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.getCompatDrawable
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

class IconListPreference(
    context: Context,
    attrs: AttributeSet,
) : ListPreference(context, attrs) {
    private var iconNames: Array<CharSequence>
    private val mEntryDrawables = arrayListOf<Drawable?>()

    init {
        layoutResource = R.layout.view_preference
        widgetLayoutResource = R.layout.view_icon

        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.IconListPreference, 0, 0)

        iconNames =
            try {
                a.getTextArray(R.styleable.IconListPreference_icons)
            } finally {
                a.recycle()
            }

        for (iconName in iconNames) {
            val resId =
                context.resources
                    .getIdentifier(iconName.toString(), "mipmap", context.packageName)
            var d: Drawable? = null
            kotlin.runCatching {
                d = context.getCompatDrawable(resId)
            }
            mEntryDrawables.add(d)
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val v =
            bindView<ImageView>(
                holder,
                widgetLayoutResource,
                R.id.preview,
                50,
                50,
            )
        if (v is ImageView) {
            val selectedIndex = findIndexOfValue(value)
            if (selectedIndex >= 0) {
                val drawable = mEntryDrawables[selectedIndex]
                v.setImageDrawable(drawable)
            }
        }
    }

    override fun onClick() {
        getActivity()?.let {
            val dialog =
                IconDialog().apply {
                    val args = Bundle()
                    args.putString("value", value)
                    args.putCharSequenceArray("entries", entries)
                    args.putCharSequenceArray("entryValues", entryValues)
                    args.putCharSequenceArray("iconNames", iconNames)
                    arguments = args
                    onChanged = { value ->
                        this@IconListPreference.value = value
                    }
                }
            it.supportFragmentManager
                .beginTransaction()
                .add(dialog, getFragmentTag())
                .commitAllowingStateLoss()
        }
    }

    override fun onAttached() {
        super.onAttached()
        val fragment =
            getActivity()?.supportFragmentManager?.findFragmentByTag(getFragmentTag()) as IconDialog?
        fragment?.onChanged = { value ->
            this@IconListPreference.value = value
        }
    }

    private fun getActivity(): FragmentActivity? {
        val context = context
        if (context is FragmentActivity) {
            return context
        } else if (context is ContextWrapper) {
            val baseContext = context.baseContext
            if (baseContext is FragmentActivity) {
                return baseContext
            }
        }
        return null
    }

    private fun getFragmentTag(): String = "icon_$key"

    class IconDialog : BaseDialogFragment(R.layout.dialog_recycler_view) {
        var onChanged: ((value: String) -> Unit)? = null
        var dialogValue: String? = null
        var dialogEntries: Array<CharSequence>? = null
        var dialogEntryValues: Array<CharSequence>? = null
        var dialogIconNames: Array<CharSequence>? = null
        private val binding by viewBinding(DialogRecyclerViewBinding::bind)

        override fun onStart() {
            super.onStart()
            setLayout(0.8f, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        override fun onFragmentCreated(
            view: View,
            savedInstanceState: Bundle?,
        ) {
            // 09稿：与 ThemeListDialog 等一致，去 primaryColor 涂色工具栏——标题走 on_surface，
            // 避免运行时主色偏浅/偏深时标题对比失守（09稿F区同类问题）
            binding.toolBar.setTitle(R.string.change_icon)
            binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
            val adapter = Adapter(requireContext())
            binding.recyclerView.adapter = adapter
            arguments?.let {
                dialogValue = it.getString("value")
                dialogEntries = it.getCharSequenceArray("entries")
                dialogEntryValues = it.getCharSequenceArray("entryValues")
                dialogIconNames = it.getCharSequenceArray("iconNames")
                dialogEntryValues?.let { values ->
                    adapter.setItems(values.toList())
                }
            }
        }

        inner class Adapter(
            context: Context,
        ) : RecyclerAdapter<CharSequence, ItemIconPreferenceBinding>(context) {
            override fun getViewBinding(parent: ViewGroup): ItemIconPreferenceBinding = ItemIconPreferenceBinding.inflate(inflater, parent, false)

            override fun convert(
                holder: ItemViewHolder,
                binding: ItemIconPreferenceBinding,
                item: CharSequence,
                payloads: MutableList<Any>,
            ) {
                binding.run {
                    val index = findIndexOfValue(item.toString())
                    dialogEntries?.let {
                        label.text = it[index]
                    }
                    dialogIconNames?.let {
                        val resId =
                            context.resources
                                .getIdentifier(it[index].toString(), "mipmap", context.packageName)
                        val d =
                            try {
                                context.getCompatDrawable(resId)
                            } catch (e: Exception) {
                                null
                            }
                        d?.let {
                            icon.setImageDrawable(d)
                        }
                    }
                    // 通用列表弹窗稿 §02/§06#7：布局去 CheckedTextView 后由 label.isChecked
                    // 改绑行尾 22dp 圆形单选；选中态圈+点运行时 ThemeStore.primaryColor 着色
                    // （MASTER §4 禁 XML 固化 primary），isSelected 保留选中语义
                    radio.isSelected = item.toString() == dialogValue
                    radio.background = if (radio.isSelected) radioOnBg() else radioOffBg()
                    root.setOnClickListener {
                        onChanged?.invoke(item.toString())
                        this@IconDialog.dismissAllowingStateLoss()
                    }
                }
            }

            override fun registerListener(
                holder: ItemViewHolder,
                binding: ItemIconPreferenceBinding,
            ) {
                // 通用列表弹窗稿 §06#8：此处原「仅 onChanged 无 dismiss」的行点击与 convert 的
                // root 点击重复（onBindViewHolder 先调本方法、convert 再设 root 监听即被同视图
                // 覆盖，属死代码），按稿清理；行点击统一由 convert 内 root 监听承担
                // （onChanged + dismiss，行为不变）
            }

            // 单选圈按次新建实例：共享可变 GradientDrawable 的 bounds 会被兄弟视图按各自
            // 尺寸互相覆盖（TipConfigDialog.kt:125 同注）。未选：outline 2dp 描边圆（§02 稿
            // 「未选 outline 2dp 描边」，中性令牌）；选中：主色 2dp 圈 + 4dp 内缩主色点
            // （CSS .rd.on / ::after inset:4px），主色取运行时 ThemeStore.primaryColor
            private fun radioOffBg(): Drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(2.dpToPx(), context.getCompatColor(R.color.outline))
            }

            private fun radioOnBg(): Drawable {
                val primary = ThemeStore.primaryColor(context)
                val ring =
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.TRANSPARENT)
                        setStroke(2.dpToPx(), primary)
                    }
                val dot =
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(primary)
                    }
                return LayerDrawable(arrayOf(ring, dot)).apply {
                    val inset = 4.dpToPx()
                    setLayerInset(1, inset, inset, inset, inset)
                }
            }

            private fun findIndexOfValue(value: String?): Int {
                dialogEntryValues?.let { values ->
                    for (i in values.indices.reversed()) {
                        if (values[i] == value) {
                            return i
                        }
                    }
                }
                return -1
            }
        }
    }
}
