package io.legado.app.ui.book.manage

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.databinding.DialogSourcePickerBinding
import io.legado.app.databinding.ItemBookSourceBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.applyTint
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.launchCollectWithLifecycle
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import splitties.views.onClick

/**
 * 书源选择
 */
class SourcePickerDialog :
    BaseDialogFragment(R.layout.dialog_source_picker),
    Toolbar.OnMenuItemClickListener {
    private val binding by viewBinding(DialogSourcePickerBinding::bind)
    private val searchView: SearchView by lazy {
        binding.toolBar.findViewById(R.id.search_view)
    }
    private val toolBar: Toolbar by lazy {
        binding.toolBar.toolbar
    }
    private val adapter by lazy {
        SourceAdapter(requireContext())
    }
    private var sourceFlowJob: Job? = null

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        initView()
        initData()
        initMenu()
    }

    private fun initView() {
        // 12-missed-2 #29-A 同模式扩展：去 primaryColor 涂色工具栏，标题走 actionBarStyle/on_surface
        // （夜间 Dark 白字压主色橙 2.84:1，且换用户浅主色时前景不可控——MASTER §4 对全部弹窗生效）
        binding.toolBar.title = "选择书源"
        // 稿面标题 18sp/650：取既有 BarTitle 令牌（18sp 加粗，minSdk 21 下平台最接近实现）
        toolBar.setTitleTextAppearance(requireContext(), R.style.TextAppearance_Legado_BarTitle)
        // 右上「×」关闭（全屏弹窗屏内退出，与底部「取消」等价）：navigationIcon 落点最省（ask 建议）
        toolBar.navigationIcon =
            ContextCompat.getDrawable(requireContext(), R.drawable.ic_close)
        toolBar.setNavigationOnClickListener {
            dismissAllowingStateLoss()
        }
        // 底部左钮「换源间隔 · N 秒」常显（与 ⋮ 菜单项同行为）；亮色主色文字 #FF6B2C 压纸底
        // 2.8:1 不达 AA → 深主色 on_primary_soft #B34A15（12-missed-2#s29 同方案），暗色随 accentColor
        upChangeSourceDelayText()
        binding.tvFooterLeft.setTextColor(
            if (ColorUtils.isColorLight(requireContext().backgroundColor)) {
                requireContext().getCompatColor(R.color.on_primary_soft)
            } else {
                requireContext().accentColor
            },
        )
        binding.tvFooterLeft.onClick {
            showChangeSourceDelayPicker()
        }
        binding.tvCancel.onClick {
            dismissAllowingStateLoss()
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        searchView.applyTint(primaryTextColor)
        searchView.isSubmitButtonEnabled = true
        searchView.queryHint = getString(R.string.search_book_source)
        searchView.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false

                override fun onQueryTextChange(newText: String?): Boolean {
                    initData(newText)
                    return false
                }
            },
        )
    }

    private fun initData(searchKey: String? = null) {
        sourceFlowJob?.cancel()
        sourceFlowJob =
            launchCollectWithLifecycle {
                when {
                    searchKey.isNullOrEmpty() -> appDb.bookSourceDao.flowEnabled()
                    else -> appDb.bookSourceDao.flowSearchEnabled(searchKey)
                }.catch {
                    AppLog.put("书源选择界面获取书源数据失败\n${it.localizedMessage}", it)
                }.flowOn(IO)
                    .collect {
                        adapter.setItems(it)
                        // 空态（搜索无命中/无启用书源）：tv_msg 布局占位启用
                        binding.tvMsg.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
                    }
            }
    }

    private fun initMenu() {
        toolBar.setOnMenuItemClickListener(this)
        toolBar.inflateMenu(R.menu.source_picker)
        toolBar.menu.applyTint(requireContext())
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_change_source_delay -> showChangeSourceDelayPicker()
        }
        return true
    }

    // 换源间隔选择：⋮ 菜单项与底部左钮双入口同行为（NumberPickerDialog 0~9999 秒，写回偏好）
    private fun showChangeSourceDelayPicker() {
        NumberPickerDialog(requireContext())
            .setTitle(getString(R.string.change_source_delay))
            .setMaxValue(9999)
            .setMinValue(0)
            .setValue(AppConfig.batchChangeSourceDelay)
            .show {
                AppConfig.batchChangeSourceDelay = it
                upChangeSourceDelayText()
            }
    }

    private fun upChangeSourceDelayText() {
        binding.tvFooterLeft.text = getString(R.string.change_source_delay) + " · " +
            AppConfig.batchChangeSourceDelay + " " + getString(R.string.seconds)
    }

    inner class SourceAdapter(
        context: Context,
    ) : RecyclerAdapter<BookSourcePart, ItemBookSourceBinding>(context) {
        override fun getViewBinding(parent: ViewGroup): ItemBookSourceBinding = ItemBookSourceBinding.inflate(inflater, parent, false).apply {
            // [NEED-DECISION]①：本弹窗「点行即换」单选语义，勾选框无功能 → 只留文字（15sp 不变）
            cbBookSource.buttonDrawable = null
            // 本弹窗未接线的控件 → gone（swt/编辑/更多/探索点/调试行/宿主行）
            swtEnabled.visibility = View.GONE
            ivEdit.visibility = View.GONE
            ivMenuMore.visibility = View.GONE
            ivExplore.visibility = View.GONE
            ivDebugText.visibility = View.GONE
            tvHostText.visibility = View.GONE
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemBookSourceBinding,
            item: BookSourcePart,
            payloads: MutableList<Any>,
        ) {
            binding.cbBookSource.text = item.getDisPlayNameGroup()
        }

        override fun registerListener(
            holder: ItemViewHolder,
            binding: ItemBookSourceBinding,
        ) {
            binding.root.onClick {
                getItemByLayoutPosition(holder.layoutPosition)?.let {
                    it.getBookSource()?.let { source ->
                        callback?.sourceOnClick(source)
                    }
                    dismissAllowingStateLoss()
                }
            }
        }
    }

    private val callback: Callback?
        get() {
            return (parentFragment as? Callback) ?: activity as? Callback
        }

    interface Callback {
        fun sourceOnClick(source: BookSource)
    }
}
