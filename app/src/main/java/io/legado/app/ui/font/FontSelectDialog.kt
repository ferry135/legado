package io.legado.app.ui.font

import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.DialogFontSelectBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.lib.theme.sheetInk
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.RealPathUtil
import io.legado.app.utils.applyTint
import io.legado.app.utils.applyTransparentScrimWindow
import io.legado.app.utils.cnCompare
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.list
import io.legado.app.utils.listFileDocs
import io.legado.app.utils.putPrefString
import io.legado.app.utils.setLayout
import io.legado.app.utils.setSheetBackground
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.launch
import java.io.File

/**
 * 字体选择对话框
 */
class FontSelectDialog :
    BaseDialogFragment(R.layout.dialog_font_select),
    Toolbar.OnMenuItemClickListener,
    FontAdapter.CallBack {
    private val fontRegex = Regex("(?i).*\\.[ot]tf")
    private val binding by viewBinding(DialogFontSelectBinding::bind)
    private val adapter by lazy {
        val curFontPath = callBack?.curFontPath ?: ""
        FontAdapter(requireContext(), curFontPath, this)
    }
    private val selectFontDir =
        registerForActivityResult(HandleFileContract()) {
            it.uri?.let { uri ->
                if (uri.isContentScheme()) {
                    putPrefString(PreferKey.fontFolder, uri.toString())
                    upFontDirLabel()
                    val doc = DocumentFile.fromTreeUri(requireContext(), uri)
                    if (doc != null) {
                        loadFontFiles(FileDoc.fromDocumentFile(doc))
                    } else {
                        RealPathUtil.getPath(requireContext(), uri)?.let { path ->
                            loadFontFilesByPermission(path)
                        }
                    }
                } else {
                    uri.path?.let { path ->
                        putPrefString(PreferKey.fontFolder, path)
                        upFontDirLabel()
                        loadFontFilesByPermission(path)
                    }
                }
            }
        }

    override fun onStart() {
        super.onStart()
        // 10-reader-prefs#07 注⑤：统一 scrim_dialog 遮罩（applyDialogScrim = 50% 黑）；
        // 14-reader-brightness#04：16dp 圆角卡须透明窗口底（applyTransparentScrimWindow 已含遮罩
        // 语义），否则不透明窗底垫在圆角缺口后填成直角，同 ReadAloudConfigDialog 先例
        dialog?.window?.applyTransparentScrimWindow()
        setLayout(0.9f, 0.9f)
    }

    override fun onFragmentCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        // 12-missed-1 #20：工具栏透明置于 surface 卡底，标题/图标/文字动作项走 on_surface
        // （原主色实底按 isColorLight 取字，默认主色 #FF6B2C 判暗→白字仅 2.84:1，明暗均不达标；
        // 文字动作项随 actionBarStyle DayNight 覆盖层取字色，与卡底明暗天然匹配）
        // 14-reader-brightness#04：自适应 ink 派生三元组（路径胶囊取 SheetInk.chip 档位，同
        // ReadStyleDialog 口径；仅本方法使用故用局部 val，与 PaddingConfigDialog 一致）
        val ink = requireContext().sheetInk()
        // 14-reader-brightness#04：0.9×0.9 卡片形态保留，底换 16dp 圆角卡（corner_radius_lg +
        // bottomBackground 填充，与 13 弹层底色同源随换肤；覆盖 BaseDialogFragment 的
        // backgroundColor 卡底，同 MoreConfigDialog/ReadStyleDialog 做法）
        binding.root.setSheetBackground(ink.bg)
        binding.toolBar.setTitle(R.string.select_font)
        binding.toolBar.inflateMenu(R.menu.font_select)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter
        // 14-reader-brightness#04：目录行=值胶囊行，整行可点换目录（menu_other 兼容保留）；
        // 路径胶囊 ink 10% 底随换肤（档位取 InkGradient）；去掉行分隔线，用留白与状态层
        binding.llFontDir.setOnClickListener { openFolder() }
        binding.tvFontDirPath.background = ink.chip()
        // 10-reader-prefs#07：字体目录路径条，换目录即时刷新
        upFontDirLabel()

        val fontPath = getPrefString(PreferKey.fontFolder)
        if (fontPath.isNullOrEmpty()) {
            openFolder()
        } else {
            if (fontPath.isContentScheme()) {
                val doc = DocumentFile.fromTreeUri(requireContext(), Uri.parse(fontPath))
                if (doc?.canRead() == true) {
                    loadFontFiles(FileDoc.fromDocumentFile(doc))
                } else {
                    openFolder()
                }
            } else {
                loadFontFilesByPermission(fontPath)
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_default -> {
                val requireContext = requireContext()
                alert(titleResource = R.string.system_typeface) {
                    items(
                        requireContext.resources.getStringArray(R.array.system_typefaces).toList(),
                    ) { _, i ->
                        AppConfig.systemTypefaces = i
                        onDefaultFontChange()
                        dismissAllowingStateLoss()
                    }
                }
            }
            R.id.menu_other -> {
                openFolder()
            }
        }
        return true
    }

    private fun openFolder() {
        lifecycleScope.launch {
            val defaultPath = "SD${File.separator}Fonts"
            selectFontDir.launch {
                otherActions = arrayListOf(SelectItem(defaultPath, -1))
            }
        }
    }

    private fun upFontDirLabel() {
        val path =
            getPrefString(PreferKey.fontFolder)
                ?.let { if (it.isContentScheme()) Uri.decode(it) else it }
                ?: "SD/Fonts"
        // 14-reader-brightness#04：label 与值分置两视图，文案仍走 font_dir 资源（占位传空串取前缀）
        binding.tvFontDir.text = getString(R.string.font_dir, "").trimEnd('：', ':', ' ')
        binding.tvFontDirPath.text = path
    }

    private fun getLocalFonts(): ArrayList<FileDoc> {
        val path = FileUtils.getPath(requireContext().externalFiles, "font")
        return File(path).listFileDocs {
            it.name.matches(fontRegex)
        }
    }

    private fun loadFontFilesByPermission(path: String) {
        PermissionsCompat
            .Builder()
            .addPermissions(*Permissions.Group.STORAGE)
            .rationale(R.string.tip_perm_request_storage)
            .onGranted {
                loadFontFiles(
                    FileDoc.fromFile(File(path)),
                )
            }.request()
    }

    private fun loadFontFiles(fileDoc: FileDoc) {
        execute {
            val fontItems =
                fileDoc.list {
                    it.name.matches(fontRegex)
                } ?: ArrayList()
            mergeFontItems(fontItems, getLocalFonts())
        }.onSuccess {
            adapter.setItems(it)
        }.onError {
            AppLog.put("加载字体文件失败\n${it.localizedMessage}", it)
            toastOnUi("getFontFiles:${it.localizedMessage}")
        }
    }

    private fun mergeFontItems(
        items1: ArrayList<FileDoc>,
        items2: ArrayList<FileDoc>,
    ): List<FileDoc> {
        val items = ArrayList(items1)
        items2.forEach { item2 ->
            var isInFirst = false
            items1.forEach for1@{ item1 ->
                if (item2.name == item1.name) {
                    isInFirst = true
                    return@for1
                }
            }
            if (!isInFirst) {
                items.add(item2)
            }
        }
        return items.sortedWith { o1, o2 ->
            o1.name.cnCompare(o2.name)
        }
    }

    override fun onFontSelect(docItem: FileDoc) {
        execute {
            callBack?.selectFont(docItem.toString())
        }.onSuccess {
            dismissAllowingStateLoss()
        }
    }

    private fun onDefaultFontChange() {
        callBack?.selectFont("")
    }

    private val callBack: CallBack?
        get() = (parentFragment as? CallBack) ?: (activity as? CallBack)

    interface CallBack {
        fun selectFont(path: String)

        val curFontPath: String
    }
}
