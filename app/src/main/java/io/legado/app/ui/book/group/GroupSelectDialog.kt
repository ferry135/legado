package io.legado.app.ui.book.group

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.DialogBookGroupPickerBinding
import io.legado.app.databinding.ItemGroupSelectBinding
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.ui.widget.recycler.LiftItemTouchCallback
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.applyTint
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.launchCollectWithLifecycle
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

class GroupSelectDialog() : BaseDialogFragment(R.layout.dialog_book_group_picker),
    Toolbar.OnMenuItemClickListener {

    constructor(groupId: Long, requestCode: Int = -1) : this() {
        arguments = Bundle().apply {
            putLong("groupId", groupId)
            putInt("requestCode", requestCode)
        }
    }

    private val binding by viewBinding(DialogBookGroupPickerBinding::bind)
    private var requestCode: Int = -1
    private val viewModel: GroupViewModel by viewModels()
    private val adapter by lazy { GroupAdapter(requireContext()) }
    private val callBack get() = (activity as? CallBack)
    private var groupId: Long = 0

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        arguments?.let {
            groupId = it.getLong("groupId")
            requestCode = it.getInt("requestCode", -1)
        }
        initView()
        initData()
    }

    private fun initView() {
        binding.toolBar.title = getString(R.string.group_select)
        binding.toolBar.inflateMenu(R.menu.book_group_manage)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        // 分割线 1dp outline_variant，左缩进 10dp 对齐文本起点（稿 .vd margin-left:10px）
        binding.recyclerView.addItemDecoration(VerticalDivider(requireContext()).apply {
            ContextCompat.getDrawable(requireContext(), R.drawable.ic_divider)?.let {
                setDrawable(InsetDrawable(it, 10.dpToPx(), 0, 0, 0))
            }
        })
        binding.recyclerView.adapter = adapter
        // 拖拽浮起态（稿 .lift）：12dp 圆角 + surface_container + 8dp 抬升（公共 LiftItemTouchCallback）；
        // 拖拽中顶部提示行切换为「松手保存顺序」（12-missed-2 #s01 拖拽态小字）
        val itemTouchCallback =
            object : LiftItemTouchCallback(
                adapter, cornerRadiusDp = 12,
                bgColorRes = R.color.surface_container, liftZDp = 8
            ) {
                override fun onSelectedChanged(
                    viewHolder: RecyclerView.ViewHolder?,
                    actionState: Int
                ) {
                    super.onSelectedChanged(viewHolder, actionState)
                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                        binding.tvHint.setText(R.string.group_picker_dragging)
                    }
                }

                override fun clearView(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ) {
                    super.clearView(recyclerView, viewHolder)
                    binding.tvHint.setText(R.string.group_picker_hint)
                }
            }
        itemTouchCallback.isCanDrag = true
        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(binding.recyclerView)
        // 空态「添加分组」与 menu_add 同一入口（GroupEditDialog 新建态）
        binding.btnAddGroup.setOnClickListener {
            showGroupEdit()
        }
        binding.tvCancel.setOnClickListener {
            dismissAllowingStateLoss()
        }
        // 底栏「确定」：亮色主色文字压浅弹层（setSheetBackground(backgroundColor)）约 2.8:1 不达 AA，
        // 按 12-missed-2 #s29 同方案（口径同 GroupManageDialog/SourcePickerDialog）：
        // 亮底改深主色 on_primary_soft，暗底仍随 ThemeStore.accentColor 换肤
        binding.tvOk.setTextColor(
            if (ColorUtils.isColorLight(requireContext().backgroundColor)) {
                requireContext().getCompatColor(R.color.on_primary_soft)
            } else {
                requireContext().accentColor
            }
        )
        binding.tvOk.setOnClickListener {
            callBack?.upGroup(requestCode, groupId)
            dismissAllowingStateLoss()
        }
        updateSelectedCount()
    }

    private fun initData() {
        launchCollectWithLifecycle {
            appDb.bookGroupDao.flowSelect().conflate().collect {
                adapter.setItems(it)
                binding.llEmpty.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
            }

        }
    }

    private fun showGroupEdit() {
        showDialogFragment(
            GroupEditDialog()
        )
    }

    // 已选计数：位掩码 popcount（仅展示层，勾选即时刷新）
    private fun updateSelectedCount() {
        binding.tvSelectCount.text =
            getString(R.string.group_selected_count, java.lang.Long.bitCount(groupId))
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_add -> showGroupEdit()
        }
        return true
    }

    private inner class GroupAdapter(context: Context) :
        RecyclerAdapter<BookGroup, ItemGroupSelectBinding>(context),
        ItemTouchCallback.Callback {

        private var isMoved: Boolean = false

        override fun getViewBinding(parent: ViewGroup): ItemGroupSelectBinding {
            return ItemGroupSelectBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemGroupSelectBinding,
            item: BookGroup,
            payloads: MutableList<Any>
        ) {
            binding.run {
                root.setBackgroundColor(context.backgroundColor)
                tvGroup.text = item.groupName
                cbGroup.isChecked = (groupId and item.groupId) > 0
                ivCheck.visibility = if (cbGroup.isChecked) View.VISIBLE else View.GONE
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemGroupSelectBinding) {
            // 复选框视觉（稿 .cb）：22dp、6dp 圆角、2dp 描边 on_surface_variant，
            // 选中实底主色——运行时取色，布局不硬编码 primary；白勾由 iv_check 叠层显示
            binding.cbGroup.background = Selector.shapeBuild()
                .setCornerRadius(6.dpToPx())
                .setStrokeWidth(2.dpToPx())
                .setDefaultBgColor(Color.TRANSPARENT)
                .setDefaultStrokeColor(
                    ContextCompat.getColor(context, R.color.on_surface_variant)
                )
                .setCheckedBgColor(context.primaryColor)
                .setCheckedStrokeColor(context.primaryColor)
                .create()
            // 勾选热区扩到整行（原仅复选框 isPressed 响应）：点击翻转掩码，编辑按钮独立热区
            binding.root.setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    val checked = (groupId and it.groupId) > 0
                    groupId = if (checked) {
                        groupId - it.groupId
                    } else {
                        groupId + it.groupId
                    }
                    binding.cbGroup.isChecked = !checked
                    binding.ivCheck.visibility = if (!checked) View.VISIBLE else View.GONE
                    updateSelectedCount()
                }
            }
            binding.tvEdit.setOnClickListener {
                showDialogFragment(
                    GroupEditDialog(getItem(holder.layoutPosition))
                )
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
                    item.order = index + 1
                }
                viewModel.upGroup(*getItems().toTypedArray())
            }
            isMoved = false
        }
    }

    interface CallBack {
        fun upGroup(requestCode: Int, groupId: Long)
    }
}