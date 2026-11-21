package com.limer.antigooglity.coordinator

import com.limer.antigooglity.tools.ToolCall
import com.limer.antigooglity.tools.fs.ToolArgsParser
import com.limer.antigooglity.tools.fs.WorkspacePathResolver
import com.limer.antigooglity.tools.terminal.TerminalCommand
import com.limer.antigooglity.tools.terminal.TerminalConfig
import com.limer.antigooglity.tools.terminal.TerminalToolExecutor
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/**
 * Deterministic security policy engine evaluating [ToolCall] requests.
 *
 * Pipeline: ToolCall -> Security Policy -> Permission Check -> Approval Decision -> Tool Execution
 *
 * Security guarantees are divided into three distinct levels:
 * 1. **Hard Enforcement**: Boundaries that can NEVER be bypassed by any preset or permission
 *    (Workspace boundary containment, canonical path checks, blocked commands list, null-byte rejection).
 * 2. **Permission Enforcement**: Static capability gates defined by [SecurityPermissions]
 *    (allowReadFile, allowWriteFile, allowDeleteFile, allowExecuteCommand, allowArbitraryCommands).
 * 3. **Approval Policy**: Dynamic interaction rules defined by [SecurityPreset]
 *    (REQUEST_REVIEW, DEFAULT, AUTO, TURBO) determining when user confirmation is required.
 */
class SecurityPolicy {

    companion object {
        val FILE_DELETION_COMMANDS: Set<String> = setOf(
            "rm", "rmdir", "del", "erase", "rd", "remove-item", "ri", "unlink"
        )

        val DESTRUCTIVE_GIT_SUBCOMMANDS: Set<String> = setOf(
            "reset", "clean", "restore"
        )

        val SHELL_LAUNCHERS: Set<String> = setOf(
            "cmd", "powershell", "pwsh", "sh", "bash", "zsh", "ksh", "csh"
        )
    }

    data class GitParsedInvocation(
        val subcommand: String?,
        val subcommandArgs: List<String>,
    )

    fun evaluate(
        call: ToolCall,
        permissions: SecurityPermissions,
        preset: SecurityPreset,
        workspaceRoot: Path,
    ): SecurityDecision {
        return when (call.name) {
            "read_file" -> evaluateReadFile(call, permissions, preset, workspaceRoot)
            "list_directory" -> evaluateListDirectory(call, permissions, preset, workspaceRoot)
            "write_file" -> evaluateWriteFile(call, permissions, preset, workspaceRoot)
            "create_directory" -> evaluateCreateDirectory(call, permissions, preset, workspaceRoot)
            "delete_file" -> evaluateDeleteFile(call, permissions, preset, workspaceRoot)
            "execute_command" -> evaluateExecuteCommand(call, permissions, preset, workspaceRoot)
            else -> SecurityDecision(
                kind = SecurityDecisionKind.UNKNOWN_TOOL,
                reason = "Unknown tool '${call.name}' is not recognized or supported by security policy",
            )
        }
    }

    private fun evaluateReadFile(
        call: ToolCall,
        permissions: SecurityPermissions,
        preset: SecurityPreset,
        workspaceRoot: Path,
    ): SecurityDecision {
        if (!permissions.allowReadFile) {
            return SecurityDecision(
                kind = SecurityDecisionKind.DENIED,
                reason = "Reading files is disabled in security permissions",
            )
        }

        val pathStr = extractPathArg(call)
            ?: return SecurityDecision(SecurityDecisionKind.DENIED, "Missing required argument 'path'")

        val pathCheck = validatePathBoundary(pathStr, workspaceRoot, mustExist = false)
        if (pathCheck != null) return pathCheck

        val approvalCmd = TerminalCommand(
            command = "read_file",
            args = listOf(pathStr),
            workingDirectory = ".",
        )

        return when (preset) {
            SecurityPreset.REQUEST_REVIEW -> SecurityDecision(
                kind = SecurityDecisionKind.REQUIRES_APPROVAL,
                reason = "Request Review mode: confirmation required for reading file '$pathStr'",
                approvalCommand = approvalCmd,
            )
            SecurityPreset.DEFAULT,
            SecurityPreset.AUTO,
            SecurityPreset.TURBO -> SecurityDecision(
                kind = SecurityDecisionKind.ALLOWED_IMMEDIATE,
            )
        }
    }

    private fun evaluateListDirectory(
        call: ToolCall,
        permissions: SecurityPermissions,
        preset: SecurityPreset,
        workspaceRoot: Path,
    ): SecurityDecision {
        if (!permissions.allowReadFile) {
            return SecurityDecision(
                kind = SecurityDecisionKind.DENIED,
                reason = "Reading/listing directory contents is disabled in security permissions",
            )
        }

        val pathStr = extractPathArg(call) ?: "."
        val pathCheck = validatePathBoundary(pathStr, workspaceRoot, mustExist = false)
        if (pathCheck != null) return pathCheck

        val approvalCmd = TerminalCommand(
            command = "list_directory",
            args = listOf(pathStr),
            workingDirectory = ".",
        )

        return when (preset) {
            SecurityPreset.REQUEST_REVIEW -> SecurityDecision(
                kind = SecurityDecisionKind.REQUIRES_APPROVAL,
                reason = "Request Review mode: confirmation required for listing directory '$pathStr'",
                approvalCommand = approvalCmd,
            )
            SecurityPreset.DEFAULT,
            SecurityPreset.AUTO,
            SecurityPreset.TURBO -> SecurityDecision(
                kind = SecurityDecisionKind.ALLOWED_IMMEDIATE,
            )
        }
    }

    private fun evaluateWriteFile(
        call: ToolCall,
        permissions: SecurityPermissions,
        preset: SecurityPreset,
        workspaceRoot: Path,
    ): SecurityDecision {
        if (!permissions.allowWriteFile) {
            return SecurityDecision(
                kind = SecurityDecisionKind.DENIED,
                reason = "Writing files is disabled in security permissions",
            )
        }

        val pathStr = extractPathArg(call)
            ?: return SecurityDecision(SecurityDecisionKind.DENIED, "Missing required argument 'path'")

        val pathCheck = validatePathBoundary(pathStr, workspaceRoot, mustExist = false)
        if (pathCheck != null) return pathCheck

        val approvalCmd = TerminalCommand(
            command = "write_file",
            args = listOf(pathStr),
            workingDirectory = ".",
        )

        val fileAlreadyExists = try {
            val resolver = WorkspacePathResolver(workspaceRoot)
            val resolved = resolver.resolve(pathStr, mustExist = false)
            Files.exists(resolved)
        } catch (_: Exception) {
            false
        }

        return when (preset) {
            SecurityPreset.REQUEST_REVIEW -> SecurityDecision(
                kind = SecurityDecisionKind.REQUIRES_APPROVAL,
                reason = "Request Review mode: confirmation required for writing file '$pathStr'",
                approvalCommand = approvalCmd,
            )
            SecurityPreset.DEFAULT -> SecurityDecision(
                // Preserves baseline agent behavior: file write operations run directly
                kind = SecurityDecisionKind.ALLOWED_IMMEDIATE,
            )
            SecurityPreset.AUTO -> {
                // Auto mode: overwriting existing file requires review; creating new file is low-risk
                if (fileAlreadyExists) {
                    SecurityDecision(
                        kind = SecurityDecisionKind.REQUIRES_APPROVAL,
                        reason = "Auto mode: overwriting existing file '$pathStr' requires user review",
                        approvalCommand = approvalCmd,
                    )
                } else {
                    SecurityDecision(
                        kind = SecurityDecisionKind.ALLOWED_IMMEDIATE,
                    )
                }
            }
            SecurityPreset.TURBO -> SecurityDecision(
                kind = SecurityDecisionKind.ALLOWED_IMMEDIATE,
            )
        }
    }

    private fun evaluateCreateDirectory(
        call: ToolCall,
        permissions: SecurityPermissions,
        preset: SecurityPreset,
        workspaceRoot: Path,
    ): SecurityDecision {
        if (!permissions.allowWriteFile) {
            return SecurityDecision(
                kind = SecurityDecisionKind.DENIED,
                reason = "Creating directories is disabled in security permissions (requires write permission)",
            )
        }

        val pathStr = extractPathArg(call)
            ?: return SecurityDecision(SecurityDecisionKind.DENIED, "Missing required argument 'path'")

        val pathCheck = validatePathBoundary(pathStr, workspaceRoot, mustExist = false)
        if (pathCheck != null) return pathCheck

        val approvalCmd = TerminalCommand(
            command = "create_directory",
            args = listOf(pathStr),
            workingDirectory = ".",
        )

        return when (preset) {
            SecurityPreset.REQUEST_REVIEW -> SecurityDecision(
                kind = SecurityDecisionKind.REQUIRES_APPROVAL,
                reason = "Request Review mode: confirmation required for creating directory '$pathStr'",
                approvalCommand = approvalCmd,
            )
            SecurityPreset.DEFAULT,
            SecurityPreset.AUTO,
            SecurityPreset.TURBO -> SecurityDecision(
                kind = SecurityDecisionKind.ALLOWED_IMMEDIATE,
            )
        }
    }

    private fun evaluateDeleteFile(
        call: ToolCall,
        permissions: SecurityPermissions,
        preset: SecurityPreset,
        workspaceRoot: Path,
    ): SecurityDecision {
        if (!permissions.allowDeleteFile) {
            return SecurityDecision(
                kind = SecurityDecisionKind.DENIED,
                reason = "Deleting files is disabled in security permissions (allowDeleteFile=false)",
            )
        }

        val pathStr = extractPathArg(call)
            ?: return SecurityDecision(SecurityDecisionKind.DENIED, "Missing required argument 'path'")

        val pathCheck = validatePathBoundary(pathStr, workspaceRoot, mustExist = false)
        if (pathCheck != null) return pathCheck

        val approvalCmd = TerminalCommand(
            command = "delete_file",
            args = listOf(pathStr),
            workingDirectory = ".",
        )

        return when (preset) {
            SecurityPreset.REQUEST_REVIEW,
            SecurityPreset.DEFAULT,
            SecurityPreset.AUTO -> SecurityDecision(
                kind = SecurityDecisionKind.REQUIRES_APPROVAL,
                reason = "Deleting files is a sensitive operation requiring user confirmation",
                approvalCommand = approvalCmd,
            )
            SecurityPreset.TURBO -> SecurityDecision(
                kind = SecurityDecisionKind.ALLOWED_IMMEDIATE,
            )
        }
    }

    private fun evaluateExecuteCommand(
        call: ToolCall,
        permissions: SecurityPermissions,
        preset: SecurityPreset,
        workspaceRoot: Path,
    ): SecurityDecision {
        if (!permissions.allowExecuteCommand) {
            return SecurityDecision(
                kind = SecurityDecisionKind.DENIED,
                reason = "Executing terminal commands is disabled in security permissions",
            )
        }

        val cmd = try {
            TerminalToolExecutor.parseCommand(call.arguments)
        } catch (e: Exception) {
            return SecurityDecision(
                kind = SecurityDecisionKind.DENIED,
                reason = "Invalid command JSON arguments: ${e.message}",
            )
        }

        val raw = cmd.command.trim()
        if (raw.isEmpty()) {
            return SecurityDecision(SecurityDecisionKind.DENIED, "Command name must not be empty")
        }
        if (raw.contains('\u0000')) {
            return SecurityDecision(SecurityDecisionKind.DENIED, "Null byte detected in command string")
        }

        // Validate batch scripts on Windows
        val isWindows = System.getProperty("os.name")?.lowercase(Locale.ROOT)?.contains("win") == true
        val lowerRaw = raw.lowercase(Locale.ROOT)
        if (isWindows && (lowerRaw.endsWith(".bat") || lowerRaw.endsWith(".cmd"))) {
            return SecurityDecision(
                kind = SecurityDecisionKind.DENIED,
                reason = "Execution of Windows batch scripts (.bat, .cmd) is disabled by security policy",
            )
        }

        // Validate working directory containment
        val wdCheck = validatePathBoundary(cmd.workingDirectory, workspaceRoot, mustExist = false)
        if (wdCheck != null) return wdCheck

        val cmdName = Path.of(raw.replace('\\', '/')).fileName?.toString() ?: raw
        val normalizedName = if (isWindows) {
            cmdName.lowercase(Locale.ROOT).removeSuffix(".exe").removeSuffix(".cmd").removeSuffix(".bat")
        } else {
            cmdName
        }

        // 1. Strictly blocked commands (hard boundary in all presets)
        if (cmdName in TerminalConfig.DEFAULT_BLOCKED_COMMANDS || normalizedName in TerminalConfig.DEFAULT_BLOCKED_COMMANDS) {
            return SecurityDecision(
                kind = SecurityDecisionKind.DENIED,
                reason = "Command '$raw' is strictly forbidden by terminal security policy",
            )
        }

        // Check if shell launcher arguments contain blocked commands or obfuscations
        val isShellLauncher = normalizedName in SHELL_LAUNCHERS
        val shellTokens = if (isShellLauncher) {
            val (shellBlockedDecision, tokens) = inspectShellLauncher(cmd)
            if (shellBlockedDecision != null) {
                return shellBlockedDecision
            }
            tokens
        } else {
            emptySet()
        }

        // 2. Check file deletion via terminal (hard boundary when allowDeleteFile=false)
        val isDeletionCommand = isTerminalDeletionCommand(normalizedName, cmd, shellTokens)
        if (isDeletionCommand && !permissions.allowDeleteFile) {
            return SecurityDecision(
                kind = SecurityDecisionKind.DENIED,
                reason = "Deleting files via terminal is disabled in security permissions (allowDeleteFile=false)",
            )
        }

        // 3. Check destructive Git operations
        val isDestructiveGit = isDestructiveGitCommand(normalizedName, cmd)

        // 4. Check dangerous commands
        val isDangerous = isDangerousCommand(cmdName, normalizedName, cmd, isDestructiveGit, isDeletionCommand)

        val isAllowlisted = cmdName in TerminalConfig.DEFAULT_ALLOWED_COMMANDS ||
            normalizedName in TerminalConfig.DEFAULT_ALLOWED_COMMANDS

        // In Turbo mode:
        if (preset == SecurityPreset.TURBO) {
            if (!permissions.allowArbitraryCommands && !isAllowlisted) {
                return SecurityDecision(
                    kind = SecurityDecisionKind.DENIED,
                    reason = "Command '$raw' is not in the allowed commands list and allowArbitraryCommands is disabled",
                )
            }
            // Destructive Git operations must NEVER be auto-approved in Turbo
            if (isDestructiveGit) {
                return SecurityDecision(
                    kind = SecurityDecisionKind.REQUIRES_APPROVAL,
                    reason = "Destructive Git operation '${cmd.command}' requires user confirmation even in Turbo mode",
                    approvalCommand = cmd,
                )
            }
            // Terminal file deletion commands cannot be auto-approved in Turbo
            if (isDeletionCommand) {
                return SecurityDecision(
                    kind = SecurityDecisionKind.REQUIRES_APPROVAL,
                    reason = "Terminal file deletion command '${cmd.command}' requires user confirmation even in Turbo mode",
                    approvalCommand = cmd,
                )
            }
            // Interactive shell launchers cannot be auto-approved in Turbo
            if (isShellLauncher) {
                return SecurityDecision(
                    kind = SecurityDecisionKind.REQUIRES_APPROVAL,
                    reason = "Shell launcher '${cmd.command}' requires user confirmation in Turbo mode",
                    approvalCommand = cmd,
                )
            }
            // Allowlisted/permitted non-destructive commands in Turbo execute immediately
            return SecurityDecision(kind = SecurityDecisionKind.ALLOWED_IMMEDIATE)
        }

        // In Request Review mode: every permitted command requires confirmation
        if (preset == SecurityPreset.REQUEST_REVIEW) {
            if (!permissions.allowArbitraryCommands && !isAllowlisted) {
                return SecurityDecision(
                    kind = SecurityDecisionKind.DENIED,
                    reason = "Command '$raw' is not in the allowed commands list and allowArbitraryCommands is disabled",
                )
            }
            return SecurityDecision(
                kind = SecurityDecisionKind.REQUIRES_APPROVAL,
                reason = "Request Review mode: confirmation required for command '${cmd.command}'",
                approvalCommand = cmd,
            )
        }

        // In Auto mode:
        // Command execution does not automatically execute; requires confirmation
        if (preset == SecurityPreset.AUTO) {
            if (!permissions.allowArbitraryCommands && !isAllowlisted) {
                return SecurityDecision(
                    kind = SecurityDecisionKind.DENIED,
                    reason = "Command '$raw' is not in the allowed commands list and allowArbitraryCommands is disabled",
                )
            }
            return SecurityDecision(
                kind = SecurityDecisionKind.REQUIRES_APPROVAL,
                reason = "Auto mode: terminal command '${cmd.command}' requires user confirmation",
                approvalCommand = cmd,
            )
        }

        // In Default mode:
        // Preserves previous agent behavior: safe allowlisted commands run immediately, dangerous commands require approval
        if (isDangerous && permissions.requireApprovalForDangerous) {
            return SecurityDecision(
                kind = SecurityDecisionKind.REQUIRES_APPROVAL,
                reason = "Command '${cmd.command}' is marked as dangerous and requires user confirmation",
                approvalCommand = cmd,
            )
        }

        // Safe command: check allowlist
        if (!permissions.allowArbitraryCommands && !isAllowlisted) {
            return SecurityDecision(
                kind = SecurityDecisionKind.DENIED,
                reason = "Command '$raw' is not in the allowed commands list and allowArbitraryCommands is disabled",
            )
        }

        return SecurityDecision(kind = SecurityDecisionKind.ALLOWED_IMMEDIATE)
    }

    private fun inspectShellLauncher(cmd: TerminalCommand): Pair<SecurityDecision?, Set<String>> {
        val extractedTokens = mutableSetOf<String>()
        val textSnippets = mutableListOf<String>()

        var i = 0
        while (i < cmd.args.size) {
            val arg = cmd.args[i]

            // Check for PowerShell encoded command flags (-e, -enc, -encodedcommand)
            val encMatch = Regex("^-(?:e|enc|encoded|encodedcommand)(?:[:=](.+))?$", RegexOption.IGNORE_CASE).find(arg)
            if (encMatch != null) {
                val inlinePayload = encMatch.groupValues[1]
                val base64Payload = if (inlinePayload.isNotEmpty()) {
                    inlinePayload
                } else if (i + 1 < cmd.args.size) {
                    i++
                    cmd.args[i]
                } else {
                    null
                }

                if (base64Payload != null) {
                    val decodedText = try {
                        val bytes = java.util.Base64.getDecoder().decode(base64Payload.trim())
                        // PowerShell uses UTF-16LE for encoded commands
                        val utf16 = String(bytes, java.nio.charset.StandardCharsets.UTF_16LE).trim()
                        if (utf16.any { it != '\u0000' && it.isDefined() }) {
                            utf16
                        } else {
                            String(bytes, java.nio.charset.StandardCharsets.UTF_8).trim()
                        }
                    } catch (_: Exception) {
                        return Pair(
                            SecurityDecision(
                                kind = SecurityDecisionKind.DENIED,
                                reason = "Undecodable or invalid encoded shell command argument",
                            ),
                            emptySet(),
                        )
                    }
                    textSnippets.add(decodedText)
                }
            } else {
                textSnippets.add(arg)
            }
            i++
        }

        // Collect tokens from raw args and decoded snippets
        for (snippet in textSnippets) {
            val lowerSnippet = snippet.lowercase(Locale.ROOT)
            for (blocked in TerminalConfig.DEFAULT_BLOCKED_COMMANDS) {
                if (lowerSnippet.contains("\\b$blocked\\b".toRegex()) || lowerSnippet.contains(blocked)) {
                    return Pair(
                        SecurityDecision(
                            kind = SecurityDecisionKind.DENIED,
                            reason = "Shell launcher argument contains strictly forbidden command: $blocked",
                        ),
                        emptySet(),
                    )
                }
            }

            // Also check unquoted version to defeat quote-splitting obfuscation (e.g. f"o"r"m"a"t or r'm')
            val unquoted = snippet.replace("\"", "").replace("'", "")
            val unquotedLower = unquoted.lowercase(Locale.ROOT)
            for (blocked in TerminalConfig.DEFAULT_BLOCKED_COMMANDS) {
                if (unquotedLower.contains("\\b$blocked\\b".toRegex()) || unquotedLower.contains(blocked)) {
                    return Pair(
                        SecurityDecision(
                            kind = SecurityDecisionKind.DENIED,
                            reason = "Shell launcher argument contains obfuscated forbidden command: $blocked",
                        ),
                        emptySet(),
                    )
                }
            }

            // Split into individual tokens for deletion / subcommand inspection
            val tokens = unquoted.split(Regex("[\\s;&|`$<>(){}]+"))
            for (t in tokens) {
                val clean = t.trim().lowercase(Locale.ROOT)
                if (clean.isNotEmpty()) {
                    extractedTokens.add(clean)
                }
            }
        }

        return Pair(null, extractedTokens)
    }

    private fun parseGitArgs(args: List<String>): GitParsedInvocation {
        val optionsWithParam = setOf(
            "-C", "-c", "--git-dir", "--work-tree", "--namespace", "--super-prefix", "--exec-path"
        )
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            if (arg == "-C" || arg == "-c" || arg in optionsWithParam) {
                i += 2
                continue
            }
            if (arg.startsWith("--") && arg.contains("=")) {
                i++
                continue
            }
            if (arg.startsWith("-")) {
                i++
                continue
            }
            val subcmd = arg.lowercase(Locale.ROOT)
            val subArgs = if (i + 1 < args.size) args.subList(i + 1, args.size) else emptyList()
            return GitParsedInvocation(subcmd, subArgs)
        }
        return GitParsedInvocation(null, emptyList())
    }

    private fun isTerminalDeletionCommand(
        normalizedName: String,
        cmd: TerminalCommand,
        shellTokens: Set<String> = emptySet(),
    ): Boolean {
        if (normalizedName in FILE_DELETION_COMMANDS) return true
        if (normalizedName == "git") {
            val (subcmd, _) = parseGitArgs(cmd.args)
            if (subcmd == "clean") return true
        }
        if (normalizedName in SHELL_LAUNCHERS) {
            if (FILE_DELETION_COMMANDS.any { it in shellTokens }) {
                return true
            }
            val fullArgs = cmd.args.joinToString(" ").lowercase(Locale.ROOT)
            if (FILE_DELETION_COMMANDS.any { del -> fullArgs.contains("\\b$del\\b".toRegex()) || fullArgs.contains(del) }) {
                return true
            }
        }
        return false
    }

    private fun isDestructiveGitCommand(normalizedName: String, cmd: TerminalCommand): Boolean {
        if (normalizedName != "git") return false
        val (subcmd, subArgs) = parseGitArgs(cmd.args)
        if (subcmd == null) return false
        if (subcmd in DESTRUCTIVE_GIT_SUBCOMMANDS) return true
        if (subcmd == "checkout") {
            if (subArgs.any { it == "-f" || it == "--force" || it == "-B" || it == "--discard-changes" }) return true
        }
        if (subcmd == "switch") {
            if (subArgs.any { it == "-f" || it == "--force" || it == "-C" || it == "--discard-changes" }) return true
        }
        if (subcmd == "branch") {
            val hasDelete = subArgs.any { it == "-D" || it == "-d" || it == "--delete" }
            val hasForce = subArgs.any { it == "-D" || it == "-f" || it == "--force" }
            if (subArgs.any { it == "-D" } || (hasDelete && hasForce)) return true
        }
        return false
    }

    private fun isDangerousCommand(
        cmdName: String,
        normalizedName: String,
        cmd: TerminalCommand,
        isDestructiveGit: Boolean = isDestructiveGitCommand(normalizedName, cmd),
        isDeletion: Boolean = isTerminalDeletionCommand(normalizedName, cmd),
    ): Boolean {
        if (cmdName in TerminalConfig.DEFAULT_DANGEROUS_COMMANDS || normalizedName in TerminalConfig.DEFAULT_DANGEROUS_COMMANDS) {
            return true
        }
        if (normalizedName in SHELL_LAUNCHERS) {
            return true
        }
        if (isDestructiveGit) {
            return true
        }
        if (isDeletion) {
            return true
        }
        return false
    }

    private fun extractPathArg(call: ToolCall): String? {
        return try {
            val args = ToolArgsParser.parseObject(call.arguments)
            args["path"] ?: args["filename"] ?: args["file"] ?: args["dir"] ?: args["directory"]
        } catch (_: Exception) {
            null
        }
    }

    private fun validatePathBoundary(
        pathStr: String,
        workspaceRoot: Path,
        mustExist: Boolean,
    ): SecurityDecision? {
        if (pathStr.contains('\u0000')) {
            return SecurityDecision(SecurityDecisionKind.DENIED, "Null byte detected in path string")
        }
        val resolver = WorkspacePathResolver(workspaceRoot)
        return try {
            resolver.resolve(pathStr, mustExist = mustExist)
            null
        } catch (e: SecurityException) {
            SecurityDecision(
                kind = SecurityDecisionKind.DENIED,
                reason = "Path boundary violation: ${e.message}",
            )
        } catch (e: Exception) {
            SecurityDecision(
                kind = SecurityDecisionKind.DENIED,
                reason = "Invalid path '$pathStr': ${e.message}",
            )
        }
    }
}
