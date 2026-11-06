package io.legado.app.ui.file

import android.app.Application
import android.os.Bundle
import android.os.Environment
import androidx.lifecycle.MutableLiveData
import io.legado.app.base.BaseViewModel
import io.legado.app.exception.NoStackTraceException
import io.legado.app.utils.toastOnUi
import java.io.File

class FilePickerViewModel(application: Application) : BaseViewModel(application) {

    var rootDoc: File? = Environment.getExternalStorageDirectory()
    var subDocs = mutableListOf<File>()
    val filesLiveData = MutableLiveData<List<File>>()
    // 加载标志位（稿 §03 防闪烁）：upFiles 开始先 post 空列表（onStart），UI 判空态需
    // 区分「加载中」与「onSuccess 后真空」，见 FilePickerDialog.updateEmptyState
    var isLoading: Boolean = false
        private set
    var mode: Int = HandleFileContract.FILE
    var isShowHideDir: Boolean = false
    var allowExtensions: Array<String>? = null
    val isSelectDir: Boolean get() = mode == HandleFileContract.DIR
    val isSelectFile: Boolean get() = mode == HandleFileContract.FILE
    val lastDir: File? get() = subDocs.lastOrNull() ?: rootDoc

    fun initData(arguments: Bundle?) {
        arguments?.let {
            mode = it.getInt("mode", HandleFileContract.FILE)
            isShowHideDir = it.getBoolean("isShowHideDir")
            it.getString("initPath")?.let { path ->
                rootDoc = File(path)
            }
            allowExtensions = it.getStringArray("allowExtensions")
        }
        upFiles(rootDoc)
    }

    // 请求序号守卫（审查 #1）：upFiles 可并发重入（快速连点目录/面包屑/「..」），旧请求的
    // onSuccess 晚于新请求的 onStart 到达主线程（回调均在 Main，Coroutine.kt:170-186），
    // 会把 isLoading 置 false 并 post 旧列表，造成空态/旧目录列表瞬时抖动——过期结果直接
    // 丢弃。reqSeq 自增（upFiles 调用点）与回调均在主线程串行，无跨线程可见性问题
    private var reqSeq = 0

    fun upFiles(parentFile: File?) {
        val seq = ++reqSeq
        execute {
            parentFile ?: return@execute emptyList()
            if (parentFile == rootDoc) {
                parentFile.listFiles()?.sortedWith(
                    compareBy({ it.isFile }, { it.name })
                )
            } else {
                val list = arrayListOf(parentFile)
                parentFile.listFiles()?.sortedWith(
                    compareBy({ it.isFile }, { it.name })
                )?.let {
                    list.addAll(it)
                }
                list
            }
        }.onStart {
            if (seq != reqSeq) return@onStart
            isLoading = true
            filesLiveData.postValue(emptyList())
        }.onSuccess {
            if (seq != reqSeq) return@onSuccess
            isLoading = false
            filesLiveData.postValue(it ?: emptyList())
        }.onError {
            if (seq != reqSeq) return@onError
            isLoading = false
            context.toastOnUi(it.localizedMessage)
        }
    }

    fun createFolder(name: String) {
        execute {
            val dir = lastDir ?: throw NoStackTraceException("父文件夹不存在")
            val folder = File(dir, name)
            if (!folder.canonicalPath.contains(dir.canonicalPath)) {
                throw NoStackTraceException("非法文件名")
            }
            folder.mkdir()
        }.onSuccess {
            upFiles(lastDir)
        }.onError {
            context.toastOnUi(it.localizedMessage)
        }
    }

}