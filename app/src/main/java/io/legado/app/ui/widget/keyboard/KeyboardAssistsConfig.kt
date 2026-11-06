package io.legado.app.ui.widget.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.databinding.DialogMultipleEditTextBinding
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemKeyboardAssistKeyBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.applyTint
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.launchCollectWithLifecycle
import io.legado.app.utils.setLayout
import io.legado.app.utils.sheetBackground
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * 辅助按键配置
 */
class KeyboardAssistsConfig : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val adapter by lazy { KeyAdapter(requireContext()) }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        applyDialogSurface(view)
        binding.toolBar.setTitle(R.string.assists_key_config)
        initView()
        initMenu()
        initData()
    }

    /**
     * 弹窗卡面：surface_container 运行时资源色 + outline_variant 1dp 描边
     * （config-form-dialogs §06 弹窗容器行：卡面由基类默认的 ThemeStore.backgroundColor()（= surface，
     * 与页面底色同色）改取 surface_container，与页面底色分层）。基类为多弹窗共用不动，
     * 仅本页覆写；EInk 模式沿用基类 bg_eink_border_* 体系，不注入。
     */
    private fun applyDialogSurface(view: View) {
        if (AppConfig.isEInkMode) return
        val context = view.context
        view.background = context.sheetBackground(
            ContextCompat.getColor(context, R.color.surface_container)
        ).apply {
            setStroke(1.dpToPx(), ContextCompat.getColor(context, R.color.outline_variant))
        }
    }

    private fun initView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        binding.recyclerView.adapter = adapter
        val itemTouchCallback = ItemTouchCallback(adapter)
        itemTouchCallback.isCanDrag = true
        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(binding.recyclerView)

    }

    private fun initMenu() {
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.toolBar.inflateMenu(R.menu.keyboard_assists_config)
        binding.toolBar.menu.applyTint(requireContext())
    }

    private fun initData() {
        launchCollectWithLifecycle {
            appDb.keyboardAssistsDao.flowAll.catch {
                AppLog.put("辅助按键配置获取数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).collect {
                adapter.setItems(it)
            }

        }
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_add -> editKey(null)
        }
        return false
    }

    private fun editKey(keyboardAssist: KeyboardAssist?) {
        val alertDialog = alert {
            setTitle("辅助按键")
            val alertBinding = DialogMultipleEditTextBinding.inflate(layoutInflater).apply {
                layout1.hint = "key"
                edit1.setText(keyboardAssist?.key)
                layout2.hint = "value"
                layout2.visible()
                edit2.setText(keyboardAssist?.value)
            }
            setCustomView(alertBinding.root)
            cancelButton()
            okButton {
                lifecycleScope.launch(IO) {
                    val newKeyboardAssist = KeyboardAssist(
                        key = alertBinding.edit1.text.toString(),
                        value = alertBinding.edit2.text.toString()
                    )
                    if (keyboardAssist == null) {
                        newKeyboardAssist.serialNo = appDb.keyboardAssistsDao.maxSerialNo + 1
                        appDb.keyboardAssistsDao.insert(newKeyboardAssist)
                    } else {
                        newKeyboardAssist.serialNo = keyboardAssist.serialNo
                        appDb.keyboardAssistsDao.delete(keyboardAssist)
                        appDb.keyboardAssistsDao.insert(newKeyboardAssist)
                    }
                }
            }
            // §06 输入域 FILLED 运行时着色（XML 禁固化 accent，MASTER §4）
            applyInputTint(alertBinding)
        }
        // config-form-dialogs §06 底栏：逐弹窗重着色（不改全局 applyTint）——
        // 取消（NEGATIVE）=on_surface_variant 次要色、确定（POSITIVE）=运行时 accentColor，
        // 两钮 min-height 48dp
        alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            setTextColor(requireContext().getCompatColor(R.color.on_surface_variant))
            minHeight = 48.dpToPx()
        }
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(requireContext().accentColor)
            minHeight = 48.dpToPx()
        }
    }

    /**
     * 输入域运行时着色（§06 输入域行 FILLED 路线）：聚焦描边取 ThemeStore accentColor
     * （换肤安全，XML 不固化 accent）；浮标 hint 收敛 on_surface_variant
     * （默认态/聚焦态一致，主色只出现在描边与光标）。
     */
    private fun applyInputTint(alertBinding: DialogMultipleEditTextBinding) {
        val context = context ?: return
        val hintColors = ColorStateList.valueOf(
            context.getCompatColor(R.color.on_surface_variant)
        )
        val strokeColors = Selector.colorBuild()
            .setDefaultColor(Color.TRANSPARENT)
            .setFocusedColor(context.accentColor)
            .create()
        for (layout in listOf(alertBinding.layout1, alertBinding.layout2)) {
            layout.defaultHintTextColor = hintColors
            // Material 1.12 无 focusedHintTextColor setter：setHintTextColor 即聚焦态浮标色
            // （与 DirectLinkUploadConfig.applyFieldTint / CacheActivity.applyFilledFieldSpec 同款）
            layout.hintTextColor = hintColors
            layout.setBoxStrokeColorStateList(strokeColors)
        }
    }

    private inner class KeyAdapter(context: Context) :
        RecyclerAdapter<KeyboardAssist, ItemKeyboardAssistKeyBinding>(context),
        ItemTouchCallback.Callback {

        private var isMoved = false

        override fun getViewBinding(parent: ViewGroup): ItemKeyboardAssistKeyBinding {
            return ItemKeyboardAssistKeyBinding.inflate(inflater, parent, false).apply {
                ivDelete.visible()
            }
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemKeyboardAssistKeyBinding,
            item: KeyboardAssist,
            payloads: MutableList<Any>
        ) {
            binding.textView.text = item.key
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemKeyboardAssistKeyBinding) {
            binding.root.setOnClickListener {
                getItemByLayoutPosition(holder.layoutPosition)?.let { keyboardAssist ->
                    editKey(keyboardAssist)
                }
            }
            binding.ivDelete.setOnClickListener {
                getItemByLayoutPosition(holder.layoutPosition)?.let { keyboardAssist ->
                    lifecycleScope.launch(IO) {
                        appDb.keyboardAssistsDao.delete(keyboardAssist)
                    }
                }
            }
        }

        override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
            swapItem(srcPosition, targetPosition)
            isMoved = true
            return true
        }

        override fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            if (isMoved) {
                for ((index, item) in getItems().withIndex()) {
                    item.serialNo = index + 1
                }
                lifecycleScope.launch(IO) {
                    appDb.keyboardAssistsDao.update(*getItems().toTypedArray())
                }
            }
            isMoved = false
        }
    }
}