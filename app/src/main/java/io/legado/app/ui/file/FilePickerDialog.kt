package io.legado.app.ui.file

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.databinding.DialogFileChooserBinding
import io.legado.app.databinding.ItemFilePickerBinding
import io.legado.app.databinding.ItemPathPickerBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.file.HandleFileContract.Companion.FILE
import io.legado.app.ui.file.utils.FilePickerIcon
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.ConvertUtils
import io.legado.app.utils.FileUtils
import io.legado.app.utils.applyTint
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.setLayout
import io.legado.app.utils.sheetBackground
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import java.io.File

class FilePickerDialog :
    BaseDialogFragment(R.layout.dialog_file_chooser),
    Toolbar.OnMenuItemClickListener {

    companion object {
        const val tag = "FileChooserDialog"

        fun show(
            manager: FragmentManager,
            mode: Int = FILE,
            title: String? = null,
            initPath: String? = null,
            isShowHideDir: Boolean = false,
            allowExtensions: Array<String>? = null,
        ) {
            FilePickerDialog().apply {
                val bundle = Bundle()
                bundle.putInt("mode", mode)
                bundle.putString("title", title)
                bundle.putBoolean("isShowHideDir", isShowHideDir)
                bundle.putString("initPath", initPath)
                bundle.putStringArray("allowExtensions", allowExtensions)
                arguments = bundle
            }.show(manager, tag)
        }
    }

    private val binding by viewBinding(DialogFileChooserBinding::bind)
    private val viewModel by viewModels<FilePickerViewModel>()
    private val dirParent = ".."
    private val pathAdapter by lazy { PathAdapter() }
    private val fileAdapter by lazy { FileAdapter() }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.8f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        // 12-missed-1 #01：去掉 primaryColor 实底（换肤浅主色时标题/图标白字不可读），
        // 工具栏透明置于 surface 卡底上，标题色由 app:titleTextColor=@color/on_surface 保证。
        // file-picker #03 修正：原 setBackgroundResource 平色直角会覆盖基类 setSheetBackground
        // （BaseDialogFragment.kt:84，corner_radius_lg 16dp 圆角容器，且先于本回调执行），
        // 复用同一令牌函数注入 surface_container 卡面（ViewExtensions.kt:329 sheetBackground）
        view.background = view.context.sheetBackground(getCompatColor(R.color.surface_container))
        initMenu()
        initContentView()
        viewModel.filesLiveData.observe(viewLifecycleOwner) {
            fileAdapter.selectFile = null
            fileAdapter.setItems(it)
            updateEmptyState(it)
            updatePickHint(null)
        }
        viewModel.initData(arguments)
        binding.toolBar.title = arguments?.getString("title") ?: let {
            if (viewModel.isSelectDir) {
                getString(R.string.folder_chooser)
            } else {
                getString(R.string.file_chooser)
            }
        }
    }

    private fun initMenu() {
        binding.toolBar.inflateMenu(R.menu.file_chooser)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener(this)
    }

    private fun initContentView() {
        binding.rvPath.layoutManager = LinearLayoutManager(activity, RecyclerView.HORIZONTAL, false)
        binding.rvPath.adapter = pathAdapter

        binding.rvFile.addItemDecoration(VerticalDivider(requireContext()))
        binding.rvFile.layoutManager = LinearLayoutManager(activity)
        binding.rvFile.adapter = fileAdapter

        binding.tvOk.setOnClickListener {
            if (viewModel.isSelectDir) {
                viewModel.lastDir?.let {
                    setResultData(it.path)
                    dismissAllowingStateLoss()
                }
            } else {
                val file = fileAdapter.selectFile
                if (file == null) {
                    toastOnUi("请选择文件")
                } else {
                    setResultData(file.path)
                    dismissAllowingStateLoss()
                }
            }
        }
    }

    // 面包屑统一刷新（稿 §05 新增 自动滚动）：RecyclerAdapter.setItems 非 open 不可重写
    // （RecyclerAdapter.kt:96），封私有方法替换原四处 setItems 调用；setItems 后滚到末段——
    // itemCount 含 headerView（RecyclerAdapter.kt:350），末位即当前目录段
    private fun upPath(items: List<File>) {
        pathAdapter.setItems(items)
        binding.rvPath.scrollToPosition(pathAdapter.itemCount - 1)
    }

    // 空态判定（稿 §03 映射 #9）：onSuccess 后「列表为空（根目录）或仅剩 .. 行（子目录）」——
    // upFiles 非根目录恒把 parentFile 插在列表头（FilePickerViewModel.kt:44-51），空子目录
    // 返回的是仅含一行 .. 的列表，单用 isEmpty 判不到；加载中先 post 空列表（:52-53），
    // 按 isLoading 标志位防闪烁（「..」行保留在空态上方，见 dialog_file_chooser.xml tv_empty）
    private fun updateEmptyState(items: List<File>) {
        val isEmpty = !viewModel.isLoading &&
            (items.isEmpty() || (items.size == 1 && items[0] == viewModel.lastDir))
        binding.tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    // 底栏提示行（稿 §01/§02 selinfo）：FILE 模式绑选中文件名（未选择显示占位，点确定
    // 仍走 toast「请选择文件」，行为不变）；DIR 模式绑当前目录绝对路径，与确定按钮
    // 回传的 lastDir.path 严格一致
    private fun updatePickHint(selected: File?) {
        binding.tvPickHint.text = when {
            viewModel.isSelectDir -> viewModel.lastDir?.let {
                getString(R.string.file_picker_current_dir_fmt, it.path)
            }
            selected != null -> getString(R.string.file_picker_selected_fmt, selected.name)
            else -> getString(R.string.no_file_selected)
        } ?: getString(R.string.no_file_selected)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_create -> alert(R.string.create_folder) {
                val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                    editView.hint = "文件夹名"
                }
                customView { alertBinding.root }
                okButton {
                    val text = alertBinding.editView.text?.toString()
                    if (text.isNullOrBlank()) {
                        toastOnUi("文件夹名不能为空")
                    } else {
                        viewModel.createFolder(text.trim())
                    }
                }
                cancelButton()
            }
        }
        return true
    }

    private fun setResultData(path: String) {
        val data = Intent().setData(Uri.fromFile(File(path)))
        (parentFragment as? CallBack)?.onResult(data)
        (activity as? CallBack)?.onResult(data)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        activity?.finish()
    }

    @SuppressLint("SetTextI18n")
    inner class PathAdapter : RecyclerAdapter<File, ItemPathPickerBinding>(requireContext()) {

        private val arrowIcon = ConvertUtils.toDrawable(FilePickerIcon.getArrow())

        init {
            addHeaderView {
                ItemPathPickerBinding.inflate(inflater, it, false).apply {
                    textView.text = "root"
                    imageView.setImageDrawable(arrowIcon)
                    root.setOnClickListener {
                        viewModel.subDocs.clear()
                        upPath(viewModel.subDocs)
                        viewModel.upFiles(viewModel.rootDoc)
                    }
                }
            }
        }

        override fun getViewBinding(parent: ViewGroup): ItemPathPickerBinding = ItemPathPickerBinding.inflate(inflater, parent, false).apply {
            imageView.setImageDrawable(arrowIcon)
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemPathPickerBinding) {
            binding.root.setOnClickListener {
                viewModel.subDocs = viewModel.subDocs.subList(0, holder.layoutPosition)
                upPath(viewModel.subDocs)
                viewModel.upFiles(viewModel.subDocs.lastOrNull())
            }
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemPathPickerBinding,
            item: File,
            payloads: MutableList<Any>,
        ) {
            binding.textView.text = item.name
            // 当前段 = 末段（layoutPosition==itemCount-1，itemCount 含 headerView 偏移）：
            // on_surface + 加粗，历史段 on_surface_variant（稿 .cseg.cur）
            val isCurrent = holder.layoutPosition == itemCount - 1
            binding.textView.setTextColor(
                getCompatColor(if (isCurrent) R.color.on_surface else R.color.on_surface_variant),
            )
            binding.textView.typeface = if (isCurrent) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    inner class FileAdapter : RecyclerAdapter<File, ItemFilePickerBinding>(requireContext()) {
        // 行文字色改令牌（稿 §05）：on_surface 随明暗切换；置灰维持 on_surface_variant 全色
        // （12-missed-1 #01 已落地，亮 5.87:1 / 暗 7.96:1），不叠透明度
        private val normalTextColor = context.getCompatColor(R.color.on_surface)
        private val disabledTextColor = context.getCompatColor(R.color.on_surface_variant)
        private val upIcon = ConvertUtils.toDrawable(FilePickerIcon.getUpDir())!!
        private val folderIcon = ConvertUtils.toDrawable(FilePickerIcon.getFolder())!!
        private val fileIcon = ConvertUtils.toDrawable(FilePickerIcon.getFile())!!

        // 选中态（稿 §05 重绘）：弃 shape_radius_1dp 描边框，改 8dp 圆角 accent 12% 软底
        // （运行时着色随换肤，DECISIONS §8 疗法）；尾部对勾在 convert 按 selectFile 切换
        private val selectDrawable = GradientDrawable().apply {
            cornerRadius = 8.dpToPx().toFloat()
            setColor(ColorUtils.withAlpha(context.accentColor, 0.12f))
        }

        // 稿面 .frow.sel .fname{font-weight:650}（审查 #3）：选中行文件名加粗；未选还原
        // sans-serif-medium 基准（XML android:fontFamily，程序化 setTypeface 后需显式还原，
        // 不能用 Typeface.DEFAULT 回退——会丢 52dp 行规格的 medium 字重）
        private val mediumTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        private val mediumBoldTypeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        var selectFile: File? = null

        override fun getViewBinding(parent: ViewGroup): ItemFilePickerBinding = ItemFilePickerBinding.inflate(inflater, parent, false).apply {
            // 对勾运行时 primaryColor 着色（DECISIONS §8，XML 不固化 primary，同 FontAdapter）
            ivCheck.setColorFilter(context.primaryColor)
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemFilePickerBinding) {
            binding.root.setOnClickListener {
                val item = getItemByLayoutPosition(holder.layoutPosition)
                item?.let {
                    if (item == viewModel.lastDir) {
                        viewModel.subDocs.removeLastOrNull()
                        upPath(viewModel.subDocs)
                        viewModel.upFiles(viewModel.subDocs.lastOrNull() ?: viewModel.rootDoc)
                    } else if (item.isDirectory) {
                        viewModel.subDocs.add(item)
                        upPath(viewModel.subDocs)
                        viewModel.upFiles(item)
                    } else if (viewModel.isSelectFile) {
                        viewModel.allowExtensions.let {
                            if (it.isNullOrEmpty() || it.contains(FileUtils.getExtension(item.path))) {
                                selectFile = item
                                notifyItemRangeChanged(getHeaderCount(), itemCount, "selectFile")
                                updatePickHint(item)
                            }
                        }
                    }
                }
            }
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemFilePickerBinding,
            item: File,
            payloads: MutableList<Any>,
        ) {
            if (payloads.isEmpty()) {
                if (item == viewModel.lastDir) {
                    binding.imageView.setImageDrawable(upIcon)
                    binding.textView.text = dirParent
                } else if (item.isDirectory) {
                    binding.imageView.setImageDrawable(folderIcon)
                    binding.textView.text = item.name
                } else {
                    binding.imageView.setImageDrawable(fileIcon)
                    binding.textView.text = item.name
                }
                if (item.isDirectory) {
                    binding.textView.setTextColor(normalTextColor)
                } else {
                    if (viewModel.isSelectDir) {
                        binding.textView.setTextColor(disabledTextColor)
                    } else {
                        viewModel.allowExtensions?.let {
                            if (it.isEmpty() || it.contains(FileUtils.getExtension(item.path))) {
                                binding.textView.setTextColor(normalTextColor)
                            } else {
                                binding.textView.setTextColor(disabledTextColor)
                            }
                        } ?: binding.textView.setTextColor(normalTextColor)
                    }
                }
            }
            binding.root.isSelected = item == selectFile
            if (item == selectFile) {
                binding.root.background = selectDrawable
                binding.ivCheck.visibility = View.VISIBLE
                binding.textView.typeface = mediumBoldTypeface
            } else {
                binding.root.setBackgroundColor(getCompatColor(R.color.transparent))
                binding.ivCheck.visibility = View.GONE
                binding.textView.typeface = mediumTypeface
            }
        }
    }

    interface CallBack {
        fun onResult(data: Intent)
    }
}
