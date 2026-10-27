package com.rork.gpssimulator.ui.map

import com.rork.gpssimulator.data.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One-shot visual handoff across navigation. Only explicit user relocations publish here. */
class RelocationAnimations(private val nowNanos: () -> Long = System::nanoTime) {
    data class Pending(val request: RabbitHopRequest, val createdNanos: Long)
    private var sequence = 0L
    private val mutablePending = MutableStateFlow<Pending?>(null)
    val pending = mutablePending.asStateFlow()

    fun request(from: LatLng?, to: LatLng) {
        if (from == null || from == to || !from.isValid() || !to.isValid()) return
        mutablePending.value = Pending(RabbitHopRequest(++sequence, from, to), nowNanos())
    }

    fun take(id: Long): RabbitHopRequest? {
        val next = mutablePending.value ?: return null
        if (next.request.id != id) return null // A stale collector must not consume a newer teleport.
        mutablePending.value = null
        val age = nowNanos() - next.createdNanos
        return next.request.takeIf { age in 0..5_000_000_000L }
    }

    fun clear() { mutablePending.value = null }
}
