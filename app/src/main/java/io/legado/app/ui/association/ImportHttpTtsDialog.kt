package io.legado.app.ui.association

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.HttpTTS
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemSourceImportBinding
import io.legado.app.ui.widget.dialog.CodeDialog
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.utils.GSON
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import splitties.views.onClick

class ImportHttpTtsDialog() :
    BaseDialogFragment(R.layout.dialog_recycler_view),
    CodeDialog.Callback {
    constructor(source: String, finishOnDismiss: Boolean = false) : this() {
        arguments =
            Bundle().apply {
                putString("source", source)
                putBoolean("finishOnDismiss", finishOnDismiss)
            }
    }

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val viewModel by viewModels<ImportHttpTtsViewModel>()
    private val adapter by lazy { SourcesAdapter(requireContext()) }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (arguments?.getBoolean("finishOnDismiss") == true) {
            activity?.finish()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onFragmentCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        binding.toolBar.setTitle(R.string.import_tts)
        binding.rotateLoading.visible()
        // generic-list-dialogs 稿 §05/§06 #2：壳 tv_msg 默认已改中性消息层令牌，本页 tv_msg 仅用于
        // 错误/格式错误文案（errorLiveData / wrong_format），错误语义色由本页覆盖为 error
        binding.tvMsg.setTextColor(requireContext().getCompatColor(R.color.error))
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.tvCancel.visible()
        binding.tvCancel.setOnClickListener {
            dismissAllowingStateLoss()
        }
        binding.tvOk.visible()
        binding.tvOk.setOnClickListener {
            val waitDialog = WaitDialog(requireContext())
            waitDialog.show()
            viewModel.importSelect {
                waitDialog.dismiss()
                dismissAllowingStateLoss()
            }
        }
        binding.tvFooterLeft.visible()
        binding.tvFooterLeft.setOnClickListener {
            val selectAll = viewModel.isSelectAll
            viewModel.selectStatus.forEachIndexed { index, b ->
                if (b != !selectAll) {
                    viewModel.selectStatus[index] = !selectAll
                }
            }
            adapter.notifyDataSetChanged()
            upSelectText()
        }
        viewModel.errorLiveData.observe(this) {
            binding.rotateLoading.gone()
            binding.tvMsg.apply {
                // 壳 tv_msg 已改消息层默认观感（generic-list-dialogs 稿 §05），错误语义色由本页覆盖
                setTextColor(context.getCompatColor(R.color.error))
                text = it
                visible()
            }
        }
        viewModel.successLiveData.observe(this) {
            binding.rotateLoading.gone()
            if (it > 0) {
                adapter.setItems(viewModel.allSources)
                upSelectText()
            } else {
                binding.tvMsg.apply {
                    setTextColor(context.getCompatColor(R.color.error))
                    setText(R.string.wrong_format)
                    visible()
                }
            }
        }
        val source = arguments?.getString("source")
        if (source.isNullOrEmpty()) {
            dismiss()
            return
        }
        viewModel.importSource(source)
    }

    private fun upSelectText() {
        if (viewModel.isSelectAll) {
            binding.tvFooterLeft.text =
                getString(
                    R.string.select_cancel_count,
                    viewModel.selectCount,
                    viewModel.allSources.size,
                )
        } else {
            binding.tvFooterLeft.text =
                getString(
                    R.string.select_all_count,
                    viewModel.selectCount,
                    viewModel.allSources.size,
                )
        }
    }

    inner class SourcesAdapter(
        context: Context,
    ) : RecyclerAdapter<HttpTTS, ItemSourceImportBinding>(context) {
        override fun getViewBinding(parent: ViewGroup): ItemSourceImportBinding = ItemSourceImportBinding.inflate(inflater, parent, false)

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemSourceImportBinding,
            item: HttpTTS,
            payloads: MutableList<Any>,
        ) {
            binding.apply {
                cbSourceName.isChecked = viewModel.selectStatus[holder.bindingAdapterPosition]
                // 拆名后 cb 不再承载文字：名称移至 tv_source_name（item_source_import 映射 3），
                // 无障碍走 contentDescription 方案（NEED-DECISION ③），朗读合并 TTS 源名
                cbSourceName.contentDescription = item.name
                tvSourceName.text = item.name
                val localSource = viewModel.checkSources[holder.bindingAdapterPosition]
                tvSourceState.text =
                    when {
                        localSource == null -> "新增"
                        item.lastUpdateTime > localSource.lastUpdateTime -> "更新"
                        else -> "已有"
                    }
            }
        }

        override fun registerListener(
            holder: ItemViewHolder,
            binding: ItemSourceImportBinding,
        ) {
            binding.apply {
                cbSourceName.setOnCheckedChangeListener { buttonView, isChecked ->
                    if (buttonView.isPressed) {
                        holder.bindingAdapterPosition.takeIf { it >= 0 }?.let {
                            viewModel.selectStatus[it] = isChecked
                        }
                        upSelectText()
                    }
                }
                root.onClick {
                    cbSourceName.isChecked = !cbSourceName.isChecked
                    holder.bindingAdapterPosition.takeIf { it >= 0 }?.let {
                        viewModel.selectStatus[it] = cbSourceName.isChecked
                    }
                    upSelectText()
                }
                tvOpen.setOnClickListener {
                    val position = holder.bindingAdapterPosition
                    if (position < 0) return@setOnClickListener
                    val source = viewModel.allSources[position]
                    showDialogFragment(
                        CodeDialog(
                            GSON.toJson(source),
                            disableEdit = false,
                            requestId = position.toString(),
                        ),
                    )
                }
            }
        }
    }

    override fun onCodeSave(
        code: String,
        requestId: String?,
    ) {
        requestId?.toInt()?.let {
            HttpTTS.fromJson(code).getOrNull()?.let { source ->
                viewModel.allSources[it] = source
                adapter.setItem(it, source)
            }
        }
    }
}
