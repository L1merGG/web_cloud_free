package com.limer.antigooglity.coordinator

/**
 * Security permissions for file system and terminal tools.
 *
 * Safe defaults:
 * - Read file: enabled
 * - Write file: enabled
 * - Delete file: DISABLED by default
 * - Execute commands: enabled
 * - Arbitrary commands: DISABLED by default (only allowlisted commands)
 * - Require approval for dangerous commands: ENABLED by default
 */
data class SecurityPermissions(
    val allowReadFile: Boolean = true,
    val allowWriteFile: Boolean = true,
    val allowDeleteFile: Boolean = false,
    val allowExecuteCommand: Boolean = true,
    val allowArbitraryCommands: Boolean = false,
    val requireApprovalForDangerous: Boolean = true,
)
