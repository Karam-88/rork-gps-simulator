package com.rork.gpssimulator.location

/**
 * Serializes asynchronous Play Services operations on the service's main thread.
 * Keeps only the newest pending fix: a slow binder must never create an unbounded
 * backlog. Stop drains the in-flight operation before disabling mock mode, so a
 * late enable/update cannot re-enable mocking after real-location recovery starts.
 */
internal class MockFusedPump<T>(
    private val setMode: (Boolean, (Exception?) -> Unit) -> Unit,
    private val send: (T, (Exception?) -> Unit) -> Unit,
    private val onFailure: (Exception) -> Unit,
) {
    private var active = false
    private var busy = false
    private var enabled = false
    private var needsCleanup = false
    private var pending: T? = null
    private val stopped = mutableListOf<(Boolean) -> Unit>()

    val isStopping: Boolean get() = stopped.isNotEmpty()

    fun submit(value: T) {
        check(!isStopping) { "Mock cleanup is still pending" }
        active = true
        pending = value
        drain()
    }

    fun stop(completed: (Boolean) -> Unit) {
        active = false
        pending = null
        stopped.add(completed)
        drain()
    }

    private fun drain() {
        if (busy) return
        if (!active) {
            if (needsCleanup) {
                busy = true
                invoke({ done -> setMode(false, done) }) { error ->
                    enabled = false
                    needsCleanup = error != null // A later stop may retry cleanup.
                    completeStop(error == null)
                }
            } else completeStop(true)
            return
        }
        if (!enabled) {
            needsCleanup = true // Even a failed/partially applied enable needs cleanup.
            busy = true
            invoke({ done -> setMode(true, done) }) { error ->
                if (error == null) {
                    enabled = true
                    drain()
                } else fail(error)
            }
            return
        }
        val value = pending ?: return
        pending = null
        busy = true
        invoke({ done -> send(value, done) }) { error ->
            if (error == null) drain() else fail(error)
        }
    }

    private fun invoke(operation: ((Exception?) -> Unit) -> Unit, completed: (Exception?) -> Unit) {
        var delivered = false
        val done: (Exception?) -> Unit = { error ->
            if (!delivered) {
                delivered = true
                busy = false
                completed(error)
            }
        }
        try { operation(done) } catch (error: Exception) { done(error) }
    }

    private fun fail(error: Exception) {
        val notify = active
        active = false
        pending = null
        // Callback may synchronously call stop(); drain remains serialized.
        if (notify) onFailure(error)
        if (stopped.isNotEmpty()) drain()
    }

    private fun completeStop(success: Boolean) {
        val callbacks = stopped.toList()
        stopped.clear()
        callbacks.forEach { it(success) }
    }
}
