package com.limer.antigooglity.tools.terminal

import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Interface for confirming execution of dangerous terminal commands.
 *
 * Implementations can prompt the user via UI or check security policies.
 * If null, any command marked as dangerous will be rejected by default.
 */
fun interface CommandApprover {
    suspend fun requestApproval(command: TerminalCommand): Boolean
}

/**
 * Configuration for the terminal execution environment and security boundaries.
 *
 * NOTE: The [allowedCommands] list restricts the set of executables that the agent is permitted
 * to start. However, tools such as 'python', 'node', 'java', 'gradle', etc., inherently execute
 * code within their own language runtimes. The allowlist is not an OS-level sandbox (such as
 * Docker, gVisor, or seccomp). Full environment isolation must be provided at the container or OS level.
 */
data class TerminalConfig(
    val rootDirectory: Path,
    val allowedCommands: Set<String> = DEFAULT_ALLOWED_COMMANDS,
    val blockedCommands: Set<String> = DEFAULT_BLOCKED_COMMANDS,
    val dangerousCommands: Set<String> = DEFAULT_DANGEROUS_COMMANDS,
    val trustedCommandPaths: Map<String, Path> = emptyMap(),
    val allowArbitraryCommands: Boolean = false,
    val allowBatchScripts: Boolean = false,
    val requireApprovalForDangerous: Boolean = true,
    val commandApprover: CommandApprover? = null,
    val timeout: Duration = 30.seconds,
    val maxStdoutSizeBytes: Long = 100 * 1024L,
    val maxStderrSizeBytes: Long = 100 * 1024L,
    val environment: Map<String, String> = emptyMap(),
) {
    init {
        require(timeout.isPositive()) { "Timeout must be positive, but was $timeout" }
        require(maxStdoutSizeBytes > 0) { "maxStdoutSizeBytes must be positive, but was $maxStdoutSizeBytes" }
        require(maxStderrSizeBytes > 0) { "maxStderrSizeBytes must be positive, but was $maxStderrSizeBytes" }
    }

    companion object {
        val DEFAULT_ALLOWED_COMMANDS: Set<String> = setOf(
            "git",
            "python",
            "python3",
            "py",
            "java",
            "javac",
            "gradle",
            "gradlew",
            "kotlinc",
            "node",
            "npm",
            "cargo",
            "rustc",
            "go",
            "pytest",
        )

        val DEFAULT_BLOCKED_COMMANDS: Set<String> = setOf(
            "format",
            "fdisk",
            "mkfs",
            "dd",
            "shutdown",
            "reboot",
            "poweroff",
            "diskpart",
            "reg",
            "regedit",
            "vssadmin",
            "bcdedit",
            "net",
            "useradd",
            "usermod",
            "userdel",
            "passwd",
        )

        val DEFAULT_DANGEROUS_COMMANDS: Set<String> = setOf(
            "rm",
            "del",
            "erase",
            "rmdir",
            "rd",
            "apt",
            "apt-get",
            "yum",
            "dnf",
            "pacman",
            "brew",
            "choco",
            "winget",
            "pip",
            "pip3",
            "sudo",
            "su",
            "chmod",
            "chown",
        )
    }
}
