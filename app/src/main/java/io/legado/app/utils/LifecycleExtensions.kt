package io.legado.app.utils

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 在 lifecycleScope 中启动协程，并在生命周期达到 state 时执行、低于时取消，
 * 等价于 lifecycleScope.launch { repeatOnLifecycle(state) { ... } }
 */
fun LifecycleOwner.launchCollectWithLifecycle(
    state: Lifecycle.State = Lifecycle.State.STARTED,
    block: suspend CoroutineScope.() -> Unit
): Job = lifecycleScope.launch {
    repeatOnLifecycle(state) {
        block()
    }
}
