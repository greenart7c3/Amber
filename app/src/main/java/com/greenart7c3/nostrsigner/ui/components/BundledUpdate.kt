package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This class is designed to have a waiting time between two calls of invalidate
 */
@Stable
class BundledUpdate(
    val delay: Long,
    val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private var onlyOneInBlock = AtomicBoolean()
    private var invalidatesAgain = false

    fun invalidate(ignoreIfDoing: Boolean = false, onUpdate: suspend () -> Unit) {
        if (onlyOneInBlock.getAndSet(true)) {
            if (!ignoreIfDoing) {
                invalidatesAgain = true
            }
            return
        }

        val scope = CoroutineScope(Job() + dispatcher)
        scope.launch {
            try {
                onUpdate()
                delay(delay)
                if (invalidatesAgain) {
                    onUpdate()
                }
            } finally {
                withContext(NonCancellable) {
                    invalidatesAgain = false
                    onlyOneInBlock.set(false)
                }
            }
        }
    }
}
