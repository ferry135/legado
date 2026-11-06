package io.legado.app.ui.rss.subscription

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.core.view.isGone
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.RuleSub
import io.legado.app.databinding.ActivityRuleSubBinding
import io.legado.app.databinding.DialogRuleSubEditBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.association.ImportBookSourceDialog
import io.legado.app.ui.association.ImportReplaceRuleDialog
import io.legado.app.ui.association.ImportRssSourceDialog
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.launchCollectWithLifecycle
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 规则订阅界面
 */
class RuleSubActivity : BaseActivity<ActivityRuleSubBinding>(),
    RuleSubAdapter.Callback {

    override val binding by viewBinding(ActivityRuleSubBinding::inflate)
    private val adapter by lazy { RuleSubAdapter(this, this) }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initView()
        initData()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.source_subscription, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_add -> {
                val order = appDb.ruleSubDao.maxOrder + 1
                editSubscription(RuleSub(customOrder = order))
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initView() {
        binding.recyclerView.adapter = adapter
        binding.recyclerView.applyNavigationBarPadding()
        val itemTouchCallback = ItemTouchCallback(adapter)
        itemTouchCallback.isCanDrag = true
        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(binding.recyclerView)
    }

    private fun initData() {
        launchCollectWithLifecycle {
            appDb.ruleSubDao.flowAll().catch {
                AppLog.put("规则订阅界面获取数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).conflate().collect {
                binding.emptyTip.isGone = it.isNotEmpty()
                adapter.setItems(it)
            }

        }
    }

    override fun openSubscription(ruleSub: RuleSub) {
        when (ruleSub.type) {
            0 -> showDialogFragment(
                ImportBookSourceDialog(ruleSub.url)
            )
            1 -> showDialogFragment(
                ImportRssSourceDialog(ruleSub.url)
            )
            2 -> showDialogFragment(
                ImportReplaceRuleDialog(ruleSub.url)
            )
        }
    }

    override fun editSubscription(ruleSub: RuleSub) {
        alert(R.string.rule_subscription) {
            val alertBinding = DialogRuleSubEditBinding.inflate(layoutInflater).apply {
                spType.setSelection(ruleSub.type)
                etName.setText(ruleSub.name)
                etUrl.setText(ruleSub.url)
                // 三张 field 卡聚焦/按压态运行时注入（BookmarkDialog.kt:58-66 先例）：纯 XML selector
                // 的描边色会固化、不随 ThemeStore 换肤；默认/聚焦同 surface_container_high 底，
                // 仅聚焦叠 1.5dp 运行时 accentColor 描边（rule-sub-edit 稿 §04·6）；按压态向
                // on_surface 混合 10% 作整卡触控反馈（UpdateDialog.kt:74 同为运行时算色思路，
                // 亮色变深/暗色变浅随主题自动反转），纯视觉层不影响行为与换肤
                val fieldBaseColor = getCompatColor(R.color.surface_container_high)
                val fieldPressedColor = ColorUtils.blendColors(
                    fieldBaseColor,
                    getCompatColor(R.color.on_surface),
                    0.10f
                )
                val fieldBackground = Selector.shapeBuild()
                    .setCornerRadius(10.dpToPx())
                    .setStrokeWidth(1.5f.dpToPx().roundToInt())
                    .setDefaultBgColor(fieldBaseColor)
                    .setPressedBgColor(fieldPressedColor)
                    .setFocusedBgColor(fieldBaseColor)
                    .setFocusedStrokeColor(accentColor)
                    .create()
                vwFieldType.background = fieldBackground
                vwFieldName.background = fieldBackground
                vwFieldUrl.background = fieldBackground
                // 整卡触控桥接（rule-sub-edit 稿 §04·4）：AppCompat Spinner 仅响应自身 bounds
                // 点击，点 label/箭头/卡空白不弹下拉，分发卡 root 点击转 spType.performClick()
                vwFieldType.setOnClickListener { spType.performClick() }
            }
            customView { alertBinding.root }
            okButton {
                lifecycleScope.launch {
                    ruleSub.type = alertBinding.spType.selectedItemPosition
                    ruleSub.name = alertBinding.etName.text?.toString() ?: ""
                    ruleSub.url = alertBinding.etUrl.text?.toString() ?: ""
                    val rs = withContext(IO) {
                        appDb.ruleSubDao.findByUrl(ruleSub.url)
                    }
                    if (rs != null && rs.id != ruleSub.id) {
                        toastOnUi("${getString(R.string.url_already)}(${rs.name})")
                        return@launch
                    }
                    withContext(IO) {
                        appDb.ruleSubDao.insert(ruleSub)
                    }
                }
            }
            cancelButton()
        }
    }

    override fun delSubscription(ruleSub: RuleSub) {
        lifecycleScope.launch(IO) {
            appDb.ruleSubDao.delete(ruleSub)
        }
    }

    override fun updateSourceSub(vararg ruleSub: RuleSub) {
        lifecycleScope.launch(IO) {
            appDb.ruleSubDao.update(*ruleSub)
        }
    }

    override fun upOrder() {
        lifecycleScope.launch(IO) {
            val sourceSubs = appDb.ruleSubDao.all
            for ((index: Int, ruleSub: RuleSub) in sourceSubs.withIndex()) {
                ruleSub.customOrder = index + 1
            }
            appDb.ruleSubDao.update(*sourceSubs.toTypedArray())
        }
    }

}