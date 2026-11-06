package io.legado.app.ui.rss.source.manage

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.appDb
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemGroupManageBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.applyTint
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.launchCollectWithLifecycle
import io.legado.app.utils.requestInputMethod
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

class GroupManageDialog : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val viewModel: RssSourceViewModel by activityViewModels()
    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val adapter by lazy { GroupAdapter(requireContext()) }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = binding.run {
        toolBar.title = getString(R.string.group_manage)
        toolBar.inflateMenu(R.menu.group_manage)
        toolBar.menu.applyTint(requireContext())
        toolBar.setOnMenuItemClickListener(this@GroupManageDialog)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        recyclerView.adapter = adapter
        tvOk.setTextColor(requireContext().accentColor)
        tvOk.visible()
        tvOk.setOnClickListener {
            dismissAllowingStateLoss()
        }
        // 底栏计数「共 n 组」：tv_footer_left 是 AccentTextView（init 强制 accentColor），
        // XML 设色会被覆盖，须在 kt 内补次要色（稿 §05；AccentTextView.kt:13-16）
        tvFooterLeft.setTextColor(requireContext().getCompatColor(R.color.on_surface_variant))
        // 空态引导钮（稿 §04）：ghost 描边 + accent 文案运行时注入（MASTER §4 布局不固化 primary），
        // 点击走既有 menu_add → addGroup 同一 alert 路径，不新增代码路径
        tvAddGroup.setTextColor(requireContext().accentColor)
        tvAddGroup.background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(1.dpToPx(), requireContext().accentColor)
            cornerRadius = 9.dpToPx().toFloat()
        }
        tvAddGroup.setOnClickListener {
            addGroup()
        }
        initData()
    }

    private fun initData() {
        launchCollectWithLifecycle {
            appDb.rssSourceDao.flowGroups().conflate().collect { groups ->
                adapter.setItems(groups)
                // 空态引导显隐（空表 VISIBLE / 非空 GONE，不残留 INVISIBLE）+ 底栏计数（稿 §04/§05，纯展示层）
                binding.vwEmptyGroup.visibility = if (groups.isEmpty()) View.VISIBLE else View.GONE
                binding.tvFooterLeft.text = getString(R.string.group_total_count, groups.size)
                binding.tvFooterLeft.visible()
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_add -> addGroup()
        }
        return true
    }

    @SuppressLint("InflateParams")
    private fun addGroup() {
        alert(title = getString(R.string.add_group)) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.setHint(R.string.group_name)
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let {
                    if (it.isNotBlank()) {
                        viewModel.addGroup(it)
                    }
                }
            }
            cancelButton()
        }.requestInputMethod()
    }

    @SuppressLint("InflateParams")
    private fun editGroup(group: String) {
        alert(title = getString(R.string.group_edit)) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.setHint(R.string.group_name)
                editView.setText(group)
            }
            customView { alertBinding.root }
            okButton {
                viewModel.upGroup(group, alertBinding.editView.text?.toString())
            }
            cancelButton()
        }.requestInputMethod()
    }

    private inner class GroupAdapter(context: Context) :
        RecyclerAdapter<String, ItemGroupManageBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemGroupManageBinding {
            return ItemGroupManageBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemGroupManageBinding,
            item: String,
            payloads: MutableList<Any>
        ) {
            binding.run {
                root.setBackgroundColor(context.backgroundColor)
                tvGroup.text = item
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemGroupManageBinding) {
            binding.apply {
                tvEdit.setOnClickListener {
                    getItem(holder.layoutPosition)?.let {
                        editGroup(it)
                    }
                }

                tvDel.setOnClickListener {
                    getItem(holder.layoutPosition)?.let {
                        viewModel.delGroup(it)
                    }
                }
            }
        }
    }

}