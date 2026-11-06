package io.legado.app.ui.book.read.config

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.databinding.DialogReadBgTextBinding
import io.legado.app.databinding.ItemBgImageBinding
import io.legado.app.help.DefaultData
import io.legado.app.help.book.isImage
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.InkGradient
import io.legado.app.lib.theme.SheetInk
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.sheetInk
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.widget.seekbar.SeekBarChangeListener
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.SelectImageContract
import io.legado.app.utils.applyBottomSheetWindow
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.createFileReplace
import io.legado.app.utils.createFolderReplace
import io.legado.app.utils.dpToPx
import io.legado.app.utils.externalCache
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.utils.inputStream
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.launch
import io.legado.app.utils.longToast
import io.legado.app.utils.observeEvent
import io.legado.app.utils.openOutputStream
import io.legado.app.utils.outputStream
import io.legado.app.utils.parseToUri
import io.legado.app.utils.postEvent
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.readBytes
import io.legado.app.utils.readUri
import io.legado.app.utils.setSheetBackground
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream

class BgTextConfigDialog : BaseDialogFragment(R.layout.dialog_read_bg_text) {

    companion object {
        const val TEXT_COLOR = 121
        const val BG_COLOR = 122
    }

    private val binding by viewBinding(DialogReadBgTextBinding::bind)
    private val configFileName = "readConfig.zip"

    // read-bg-text-config#05：自适应 ink 派生三元组（行卡/值胶囊/色点描边取 SheetInk 档位，
    // 同 PaddingConfig/TipConfig 口径）；adapter 取色亦走此令牌（on_surface_variant 角色色）
    private lateinit var sheetInk: SheetInk
    private val adapter by lazy { BgAdapter(requireContext(), sheetInk.secondary) }
    private val importFormNet = "网络导入"
    private val selectBgImage = registerForActivityResult(SelectImageContract()) {
        it.uri?.let { uri ->
            setBgFromUri(uri)
        }
    }
    private val selectExportDir = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            exportConfig(uri)
        }
    }
    private val selectImportDoc = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            if (uri.toString() == importFormNet) {
                importNetConfigAlert()
            } else {
                importConfig(uri)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // read-bg-text-config#01/§05：现状平底无遮罩浮窗（clearFlags(FLAG_DIM_BEHIND) +
        // dimAmount=0 + @color/background）→ 16dp 顶圆角底部弹层（透明窗底 + scrim_dialog 50% +
        // 底部重力，PaddingConfigDialog 同款）；高 80% 屏防溢出（内部 NestedScrollView 滚动）
        dialog?.window?.applyBottomSheetWindow(
            (resources.displayMetrics.heightPixels * 0.8f).toInt()
        )
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        (activity as ReadBookActivity).bottomDialog++
        initView()
        initData()
        initEvent()
        // read-bg-text-config#05：取色回调在 ReadBookActivity、恢复预设/自定义图导入改写
        // durConfig 后经 UP_CONFIG 回流，刷新值胶囊与图片列表选中态（纯展示，不改事件语义）；
        // 纯透明度事件 [3]（拖动逐帧派发）不触及胶囊/列表，跳过以免逐帧重建
        observeEvent<ArrayList<Int>>(EventBus.UP_CONFIG) { list ->
            if (list.any { it != 3 }) {
                upColorChips()
                if (list.any { it == 1 || it == 2 || it == 5 }) {
                    upBgImages()
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        ReadBookConfig.save()
        (activity as ReadBookActivity).bottomDialog--
    }

    private fun initView() = binding.run {
        // read-bg-text-config#05「既有绑定迁移」：原 11 处 setTextColor/setColorFilter
        // 收敛为 sheetInk.ink/secondary 统一设色（iv_edit 改 48dp 图标钮后对内层 iv_edit_icon
        // 着色；tv_bg_image 升格为组 lab tv_group_image；开关/操作行文字色改静态语义令牌，
        // iv_import/iv_export/iv_delete id 移交操作格容器，kt 监听引用不变）
        sheetInk = requireContext().sheetInk()
        root.setSheetBackground(sheetInk.bg, topOnly = true)
        tvNameTitle.setTextColor(sheetInk.secondary)
        tvName.setTextColor(sheetInk.ink)
        ivEditIcon.setColorFilter(sheetInk.secondary, PorterDuff.Mode.SRC_IN)
        tvRestore.setTextColor(sheetInk.ink)
        listOf(tvGroupCommon, tvGroupColor, tvGroupImage).forEach {
            it.setTextColor(sheetInk.secondary)
        }
        listOf(vwDividerCommon, vwDividerActions).forEach {
            it.background = sheetInk.divider()
        }
        listOf(llDarkStatusIcon, llUnderline, tvTextColor, tvBgColor).forEach {
            // 整行可点：行卡底叠 selectableItemBackground 按压态（PaddingConfigDialog 口径）
            it.background = sheetInk.rowCardSelectable()
        }
        llBgAlpha.background = sheetInk.rowCard()
        listOf(llTextColorChip, tvBgAlphaValue).forEach {
            it.background = sheetInk.chip()
        }
        upColorChips()
        // 漫画书（book.isImage）时隐藏整行（含 label 与开关）：新布局开关与文案分离，
        // 只隐藏开关会留下可点行卡（行级 toggle 会隐形改写 underline 并派发 UP_CONFIG）；
        // 旧行为 = 隐藏整个可见元素，无任何可点入口
        llUnderline.isGone = ReadBook.book?.isImage == true
        recyclerView.adapter = adapter
        adapter.addHeaderView {
            ItemBgImageBinding.inflate(layoutInflater, it, false).apply {
                // read-bg-text-config#05「背景图条规格」：头格「选择图片」ic_image 24dp +
                // 虚线描边（ink 30%、3/3dp，ReadStyleDialog footer 加号格同款）
                tvName.setTextColor(sheetInk.secondary)
                tvName.text = getString(R.string.select_image)
                ivBg.setImageResource(R.drawable.ic_image)
                ivBg.setColorFilter(sheetInk.secondary, PorterDuff.Mode.SRC_IN)
                ivBg.layoutParams = FrameLayout.LayoutParams(
                    24.dpToPx(),
                    24.dpToPx(),
                    Gravity.CENTER
                )
                vwBgThumb.background = GradientDrawable().apply {
                    cornerRadius = 8.dpToPx().toFloat()
                    setColor(Color.TRANSPARENT)
                    setStroke(
                        1.dpToPx(),
                        ColorUtils.adjustAlpha(sheetInk.ink, InkGradient.STROKE_GHOST),
                        3.dpToPx().toFloat(),
                        3.dpToPx().toFloat()
                    )
                }
                vwBgThumb.outlineProvider = BgAdapter.thumbOutline
                vwBgThumb.clipToOutline = true
                root.setOnClickListener {
                    selectBgImage.launch()
                }
            }
        }
        upBgImages()
    }

    @SuppressLint("InflateParams")
    private fun initData() = with(ReadBookConfig.durConfig) {
        binding.tvName.text = name.ifBlank { "文字" }
        binding.swDarkStatusIcon.isChecked = curStatusIconDark()
        binding.swUnderline.isChecked = underline
        binding.sbBgAlpha.progress = bgAlpha
        upColorChips()
        upBgAlphaValue()
    }

    @SuppressLint("InflateParams")
    private fun initEvent() = with(ReadBookConfig.durConfig) {
        // read-bg-text-config#05「开关行卡片化」：整行可点（label+开关同触控域），
        // toggle 触发原监听，行为不变
        binding.llDarkStatusIcon.setOnClickListener { binding.swDarkStatusIcon.toggle() }
        binding.llUnderline.setOnClickListener { binding.swUnderline.toggle() }
        binding.ivEdit.setOnClickListener {
            alert(R.string.style_name) {
                val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                    editView.hint = "name"
                    editView.setText(ReadBookConfig.durConfig.name)
                }
                customView { alertBinding.root }
                okButton {
                    alertBinding.editView.text?.toString()?.let {
                        binding.tvName.text = it
                        ReadBookConfig.durConfig.name = it
                    }
                }
                cancelButton()
            }
        }
        binding.tvRestore.setOnClickListener {
            val defaultConfigs = DefaultData.readConfigs
            val layoutNames = defaultConfigs.map { it.name }
            context?.selector("选择预设布局", layoutNames) { _, i ->
                if (i >= 0) {
                    ReadBookConfig.durConfig = defaultConfigs[i].copy()
                    initData()
                    postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
                }
            }
        }
        binding.swDarkStatusIcon.setOnCheckedChangeListener { _, isChecked ->
            setCurStatusIconDark(isChecked)
            (activity as? ReadBookActivity)?.upSystemUiVisibility()
        }
        binding.swUnderline.setOnCheckedChangeListener { _, isChecked ->
            underline = isChecked
            postEvent(EventBus.UP_CONFIG, arrayListOf(6, 9, 11))
        }
        binding.tvTextColor.setOnClickListener {
            ColorPickerDialog.newBuilder()
                .setColor(curTextColor())
                .setShowAlphaSlider(false)
                .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                .setDialogId(TEXT_COLOR)
                .show(requireActivity())
        }
        binding.tvBgColor.setOnClickListener {
            val bgColor =
                if (curBgType() == 0) Color.parseColor(curBgStr())
                else Color.parseColor("#015A86")
            ColorPickerDialog.newBuilder()
                .setColor(bgColor)
                .setShowAlphaSlider(false)
                .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                .setDialogId(BG_COLOR)
                .show(requireActivity())
        }
        // TooltipCompat.setTooltipText(tv_bg_color, text)（:228-230）随行容器化迁移至
        // 值胶囊 TextView tv_bg_color_chip（行容器无 getText，编译会错），见 upColorChips()
        binding.ivImport.setOnClickListener {
            selectImportDoc.launch {
                mode = HandleFileContract.FILE
                title = getString(R.string.import_str)
                allowExtensions = arrayOf("zip")
                otherActions = arrayListOf(SelectItem(importFormNet, -1))
            }
        }
        binding.ivExport.setOnClickListener {
            selectExportDir.launch {
                title = getString(R.string.export_str)
            }
        }
        binding.ivDelete.setOnClickListener {
            if (ReadBookConfig.deleteDur()) {
                postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
                dismissAllowingStateLoss()
            } else {
                toastOnUi("数量已是最少,不能删除.")
            }
        }
        binding.sbBgAlpha.setOnSeekBarChangeListener(object : SeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                ReadBookConfig.bgAlpha = progress
                // read-bg-text-config#05「透明度行」：右侧值胶囊随 progress 同步（纯展示）
                upBgAlphaValue()
                postEvent(EventBus.UP_CONFIG, arrayListOf(3))
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                postEvent(EventBus.UP_CONFIG, arrayListOf(3))
            }
        })
    }

    /**
     * read-bg-text-config#05「颜色行」：值胶囊显示当前色值 + 12dp 色点；bgType=0（纯色）时
     * 「背景颜色」值胶囊主色描边（.chip.act 1.5dp primary）指示当前模式（bgType=1/2 由图片
     * 列表选中环表达）；bgType≠0 时胶囊尾注「图片时不生效」显隐。取色器打开仍以 #015A86 兜底
     * （:217-220 同源）。
     */
    private fun upColorChips() {
        if (!::sheetInk.isInitialized) return
        with(ReadBookConfig.durConfig) {
            binding.run {
                val textColor = curTextColor()
                tvTextColorChip.text = textColor.toHexString6()
                upColorDot(vwTextDot, textColor)
                val bgType = curBgType()
                val bgColor = if (bgType == 0) {
                    Color.parseColor(curBgStr())
                } else {
                    Color.parseColor("#015A86")
                }
                tvBgColorChip.text = bgColor.toHexString6()
                upColorDot(vwBgDot, bgColor)
                tvBgColorNote.isVisible = bgType != 0
                llBgColorChip.background = if (bgType == 0) {
                    // 每视图独立实例：共享可变 GradientDrawable 的 bounds 会被兄弟视图互相覆盖
                    // （TipConfigDialog 口径）
                    sheetInk.chip().apply {
                        setStroke(1.5f.dpToPx().toInt(), requireContext().primaryColor)
                    }
                } else {
                    sheetInk.chip()
                }
                // 「既有绑定迁移」：原 TooltipCompat.setTooltipText(tv_bg_color, text)（:228-230）
                // 迁移至值胶囊 TextView（行容器无 getText）
                TooltipCompat.setTooltipText(tvBgColorChip, tvBgColorChip.text)
            }
        }
    }

    /** 值胶囊内 12dp 色点（TipConfigDialog.upColorDot 同款：色底 + ink 25% 细描边） */
    private fun upColorDot(dot: View, color: Int) {
        dot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(1.dpToPx(), ColorUtils.adjustAlpha(sheetInk.ink, InkGradient.STROKE_MUTED))
        }
    }

    /** read-bg-text-config#05「透明度行」：值胶囊「n%」随 sb_bg_alpha progress 同步 */
    private fun upBgAlphaValue() {
        binding.tvBgAlphaValue.text = "${binding.sbBgAlpha.progress}%"
    }

    /**
     * read-bg-text-config#05「自定义图入列」（NEED-DECISION ⑤，可回退）：
     * 数据源 assets/bg ∪ externalFiles/bg 合并后 setItems（深色稿自定义图格的前提），
     * 不合并则 bgType=2 时列表无任何选中表达。
     */
    private fun upBgImages() {
        val assets = requireContext().assets.list("bg")?.toList().orEmpty()
        val customs = File(requireContext().externalFiles, "bg")
            .listFiles()?.map { it.name }.orEmpty()
        adapter.setItems((assets + customs).distinct())
    }

    private fun Int.toHexString6(): String = "#%06X".format(this and 0xFFFFFF)

    private fun exportConfig(uri: Uri) {
        val exportFileName = if (ReadBookConfig.config.name.isBlank()) {
            configFileName
        } else {
            "${ReadBookConfig.config.name}.zip"
        }
        execute {
            val exportFiles = arrayListOf<File>()
            val configDir = requireContext().externalCache.getFile("readConfig")
            configDir.createFolderReplace()
            val configFile = configDir.getFile("readConfig.json")
            configFile.createFileReplace()
            configFile.writeText(GSON.toJson(ReadBookConfig.getExportConfig()))
            exportFiles.add(configFile)
            val fontPath = ReadBookConfig.textFont
            if (fontPath.isNotEmpty()) {
                val fontName = FileUtils.getName(fontPath)
                val fontInputStream =
                    fontPath.parseToUri().inputStream(requireContext()).getOrNull()
                fontInputStream?.use {
                    val fontExportFile = FileUtils.createFileIfNotExist(configDir, fontName)
                    it.copyTo(fontExportFile.outputStream())
                    exportFiles.add(fontExportFile)
                }
            }
            if (ReadBookConfig.durConfig.bgType == 2) {
                val bgName = FileUtils.getName(ReadBookConfig.durConfig.bgStr)
                val bgFile = File(ReadBookConfig.durConfig.bgStr)
                if (bgFile.exists()) {
                    val bgExportFile = File(FileUtils.getPath(configDir, bgName))
                    if (!bgExportFile.exists()) {
                        bgFile.copyTo(bgExportFile)
                        exportFiles.add(bgExportFile)
                    }
                }
            }
            if (ReadBookConfig.durConfig.bgTypeNight == 2) {
                val bgName = FileUtils.getName(ReadBookConfig.durConfig.bgStrNight)
                val bgFile = File(ReadBookConfig.durConfig.bgStrNight)
                if (bgFile.exists()) {
                    val bgExportFile = File(FileUtils.getPath(configDir, bgName))
                    if (!bgExportFile.exists()) {
                        bgFile.copyTo(bgExportFile)
                        exportFiles.add(bgExportFile)
                    }
                }
            }
            if (ReadBookConfig.durConfig.bgTypeEInk == 2) {
                val bgName = FileUtils.getName(ReadBookConfig.durConfig.bgStrEInk)
                val bgFile = File(ReadBookConfig.durConfig.bgStrEInk)
                if (bgFile.exists()) {
                    val bgExportFile = File(FileUtils.getPath(configDir, bgName))
                    if (!bgExportFile.exists()) {
                        bgFile.copyTo(bgExportFile)
                        exportFiles.add(bgExportFile)
                    }
                }
            }
            val configZipPath = FileUtils.getPath(requireContext().externalCache, configFileName)
            if (ZipUtils.zipFiles(exportFiles, File(configZipPath))) {
                if (uri.isContentScheme()) {
                    DocumentFile.fromTreeUri(requireContext(), uri)?.let { treeDoc ->
                        treeDoc.findFile(exportFileName)?.delete()
                        val out = treeDoc.createFile("", exportFileName)?.openOutputStream()
                        out?.use {
                            File(configZipPath).inputStream().use {
                                it.copyTo(out)
                            }
                        }
                    }
                } else {
                    val exportPath = FileUtils.getPath(File(uri.path!!), exportFileName)
                    FileUtils.delete(exportPath)
                    File(configZipPath).copyTo(FileUtils.createFileIfNotExist(exportPath))
                }
            }
        }.onSuccess {
            toastOnUi("导出成功, 文件名为 $exportFileName")
        }.onError {
            it.printOnDebug()
            AppLog.put("导出失败:${it.localizedMessage}", it)
            longToast("导出失败:${it.localizedMessage}")
        }
    }

    @SuppressLint("InflateParams")
    private fun importNetConfigAlert() {
        alert("输入地址") {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater)
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let { url ->
                    importNetConfig(url)
                }
            }
            cancelButton()
        }
    }

    private fun importNetConfig(url: String) {
        execute {
            okHttpClient.newCallResponseBody {
                url(url)
            }.bytes().let {
                importConfig(it)
            }
        }.onError {
            longToast(it.stackTraceStr)
        }
    }

    private fun importConfig(uri: Uri) {
        execute {
            importConfig(uri.readBytes(requireContext()))
        }.onError {
            it.printOnDebug()
            longToast("导入失败:${it.localizedMessage}")
        }
    }

    private fun importConfig(byteArray: ByteArray) {
        execute {
            ReadBookConfig.import(byteArray)
        }.onSuccess {
            ReadBookConfig.durConfig = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
            toastOnUi("导入成功")
        }.onError {
            it.printOnDebug()
            longToast("导入失败:${it.localizedMessage}")
        }
    }

    private fun setBgFromUri(uri: Uri) {
        readUri(uri) { fileDoc, inputStream ->
            kotlin.runCatching {
                var file = requireContext().externalFiles
                val suffix = fileDoc.name.substringAfterLast(".")
                val fileName = uri.inputStream(requireContext()).getOrThrow().use {
                    MD5Utils.md5Encode(it) + ".$suffix"
                }
                file = FileUtils.createFileIfNotExist(file, "bg", fileName)
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                ReadBookConfig.durConfig.setCurBg(2, fileName)
                postEvent(EventBus.UP_CONFIG, arrayListOf(1))
            }.onFailure {
                appCtx.toastOnUi(it.localizedMessage)
            }
        }
    }
}