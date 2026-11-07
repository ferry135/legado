package io.legado.app.ui.dict

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.DictRule
import io.legado.app.help.coroutine.Coroutine

class DictViewModel(application: Application) : BaseViewModel(application) {

    private var dictJob: Coroutine<String>? = null

    fun initData(onSuccess: (List<DictRule>) -> Unit) {
        execute {
            appDb.dictRuleDao.enabled
        }.onSuccess {
            onSuccess.invoke(it)
        }
    }

    // 成功/失败分流回调：错误消息由调用方按 error 色呈现，不再与正文混排
    fun dict(
        dictRule: DictRule,
        word: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        dictJob?.cancel()
        dictJob = execute {
            dictRule.search(word)
        }.onSuccess {
            onSuccess.invoke(it)
        }.onError {
            onError.invoke(it.localizedMessage ?: "ERROR")
        }
    }


}