package com.limer.antigooglity.coordinator

import com.limer.antigooglity.tools.terminal.TerminalCommand

enum class SecurityDecisionKind {
    ALLOWED_IMMEDIATE,
    REQUIRES_APPROVAL,
    DENIED,
    UNKNOWN_TOOL,
}

data class SecurityDecision(
    val kind: SecurityDecisionKind,
    val reason: String = "",
    val approvalCommand: TerminalCommand? = null,
) {
    val isAllowedImmediate: Boolean get() = kind == SecurityDecisionKind.ALLOWED_IMMEDIATE
    val requiresApproval: Boolean get() = kind == SecurityDecisionKind.REQUIRES_APPROVAL
    val isDenied: Boolean get() = kind == SecurityDecisionKind.DENIED
    val isUnknown: Boolean get() = kind == SecurityDecisionKind.UNKNOWN_TOOL
}
