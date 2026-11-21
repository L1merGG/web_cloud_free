package com.limer.antigooglity.coordinator

import com.limer.antigooglity.tools.terminal.CommandApprover
import com.limer.antigooglity.tools.terminal.TerminalCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class PendingApproval(
    val id: String = UUID.randomUUID().toString(),
    val command: TerminalCommand,
    val deferred: CompletableDeferred<Boolean>,
)

class UiCommandApprover(
    private val onApprovalRequested: ((PendingApproval) -> Unit)? = null,
) : CommandApprover {
    private val _currentPending = MutableStateFlow<PendingApproval?>(null)
    val currentPending: StateFlow<PendingApproval?> = _currentPending.asStateFlow()

    override suspend fun requestApproval(command: TerminalCommand): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        val pending = PendingApproval(command = command, deferred = deferred)
        _currentPending.value = pending
        onApprovalRequested?.invoke(pending)
        return try {
            deferred.await()
        } finally {
            if (_currentPending.value?.id == pending.id) {
                _currentPending.value = null
            }
        }
    }

    fun approve(id: String? = null): Boolean {
        val pending = _currentPending.value ?: return false
        if (id != null && pending.id != id) return false
        val completed = pending.deferred.complete(true)
        _currentPending.value = null
        return completed
    }

    fun reject(id: String? = null): Boolean {
        val pending = _currentPending.value ?: return false
        if (id != null && pending.id != id) return false
        val completed = pending.deferred.complete(false)
        _currentPending.value = null
        return completed
    }

    fun cancel(): Boolean {
        val pending = _currentPending.value ?: return false
        val completed = pending.deferred.complete(false)
        _currentPending.value = null
        return completed
    }
}
