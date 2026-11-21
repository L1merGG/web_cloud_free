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
 *    (Workspace boundary containment, canonical path checks, blocked commands list, null-byte rejection,
 *    undecodable encoded commands, shell obfuscation, batch script prohibition).
 * 2. **Permission Enforcement**: Static capability gates defined by [SecurityPermissions]
 *    (allowReadFile, allowWriteFile, allowDeleteFile, allowExecuteCommand, allowArbitraryCommands).
 *    Note: when allowDeleteFile=false, file deletion is strictly rejected (DENIED) across all tools and terminals.
 * 3. **Approval Policy**: Dynamic interaction rules defined by [SecurityPreset]
 *    (REQUEST_REVIEW, DEFAULT, AUTO, TURBO) determining when user confirmation is required.
 *
 * Note on execution model limitations:
 * The allowlist and argument filters restrict CLI commands, but do NOT provide kernel-level container
 * isolation. Runtimes capable of arbitrary code execution (Python, Node, Java, Gradle) can perform actions
 * within the operating system user context. Full OS sandboxing must be provided at the container level.
 */
class SecurityPolicy {

    companion object {
        val FILE_DELETION_COMMANDS: Set<String> = setOf(
            "rm", "rmdir", "del", "erase", "rd", "remove-item", "ri", "unlink"
        )

        val DESTRUCTIVE_GIT_SUBCOMMANDS: Set<String> = setOf(
            "reset", "clean", "restore"
        )

        val KNOWN_SAFE_GIT_SUBCOMMANDS: Set<String> = setOf(
            "status", "diff", "log", "show", "branch", "checkout", "switch",
            "add", "commit", "fetch", "pull", "merge", "rebase", "tag", "stash",
            "rev-parse", "check-ref-format", "version", "help", "describe", "remote",
            "init", "clone", "archive", "grep", "ls-files", "ls-tree", "shortlog", "whatchanged",
            "cherry-pick", "revert", "format-patch", "am", "bundle"
        )

        val SHELL_LAUNCHERS: Set<String> = setOf(
            "cmd", "powershell", "pwsh", "sh", "bash", "zsh", "ksh", "csh"
        )

        val WRAPPER_COMMANDS: Set<String> = setOf(
            "sudo", "doas", "busybox", "env", "nohup", "pkexec", "su", "xargs"
        )
    }

    data class GitParsedInvocation(
        val subcommand: String?,
        val subcommandArgs: List<String>,
        val configs: List<String>,
        val definesAlias: Boolean,
        val aliasDefinesShellOrDeletion: Boolean,
    )

    data class ParsedCommandChain(
        val effectiveCommand: String,
        val effectiveNormalized: String,
        val effectiveArgs: List<String>,
        val isWrapped: Boolean,
        val wrappers: List<String>,
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

        // Unwrap wrapper commands (sudo, doas, env, busybox, nohup, pkexec, su, xargs)
        val unwrapped = unwrapCommand(cmdName, normalizedName, cmd.args)
        val effCmdName = unwrapped.effectiveCommand
        val effNormalized = unwrapped.effectiveNormalized
        val effArgs = unwrapped.effectiveArgs
        val isWrapped = unwrapped.isWrapped

        // 1. Strictly blocked commands (hard boundary in all presets)
        if (cmdName in TerminalConfig.DEFAULT_BLOCKED_COMMANDS ||
            normalizedName in TerminalConfig.DEFAULT_BLOCKED_COMMANDS ||
            effCmdName in TerminalConfig.DEFAULT_BLOCKED_COMMANDS ||
            effNormalized in TerminalConfig.DEFAULT_BLOCKED_COMMANDS
        ) {
            return SecurityDecision(
                kind = SecurityDecisionKind.DENIED,
                reason = "Command '$raw' is strictly forbidden by terminal security policy",
            )
        }

        // Check if shell launcher arguments contain blocked commands or obfuscations
        val isShellLauncher = normalizedName in SHELL_LAUNCHERS || effNormalized in SHELL_LAUNCHERS
        val shellTokens = if (isShellLauncher) {
            val (shellBlockedDecision, tokens) = inspectShellLauncher(cmd)
            if (shellBlockedDecision != null) {
                return shellBlockedDecision
            }
            tokens
        } else {
            emptySet()
        }

        // Check command substitution in shell launchers when deletion is disabled
        if (isShellLauncher && !permissions.allowDeleteFile) {
            val hasSubstitution = cmd.args.any { it.contains("$(") || it.contains("`") }
            if (hasSubstitution) {
                return SecurityDecision(
                    kind = SecurityDecisionKind.DENIED,
                    reason = "Shell launcher with command substitution cannot be verified safe when allowDeleteFile is disabled",
                )
            }
        }

        // Parse Git arguments if git
        val isGit = normalizedName == "git" || effNormalized == "git"
        val parsedGit = if (isGit) {
            parseGitArgs(if (effNormalized == "git") effArgs else cmd.args)
        } else {
            null
        }

        // 2. Check file deletion via terminal (hard boundary when allowDeleteFile=false)
        val isDeletionCommand = isTerminalDeletionCommand(normalizedName, cmd, shellTokens, parsedGit) ||
            (isWrapped && isTerminalDeletionCommand(effNormalized, TerminalCommand(effCmdName, effArgs, cmd.workingDirectory), shellTokens, parsedGit))

        if (isDeletionCommand && !permissions.allowDeleteFile) {
            return SecurityDecision(
                kind = SecurityDecisionKind.DENIED,
                reason = "Deleting files via terminal is disabled in security permissions (allowDeleteFile=false)",
            )
        }

        // 3. Check destructive Git operations
        val isDestructiveGit = if (isGit && parsedGit != null) {
            isDestructiveGitCommand("git", cmd, parsedGit)
        } else {
            false
        }

        // Check unknown Git subcommand
        val isUnknownGit = isGit && parsedGit != null && parsedGit.subcommand != null &&
            parsedGit.subcommand !in KNOWN_SAFE_GIT_SUBCOMMANDS &&
            parsedGit.subcommand !in DESTRUCTIVE_GIT_SUBCOMMANDS

        // 4. Check dangerous commands
        val isDangerous = isDangerousCommand(
            cmdName = cmdName,
            normalizedName = normalizedName,
            cmd = cmd,
            isDestructiveGit = isDestructiveGit,
            isDeletion = isDeletionCommand,
            isWrapped = isWrapped,
            parsedGit = parsedGit,
        )

        val isAllowlisted = (cmdName in TerminalConfig.DEFAULT_ALLOWED_COMMANDS ||
            normalizedName in TerminalConfig.DEFAULT_ALLOWED_COMMANDS) && !isWrapped

        // In Turbo mode:
        if (preset == SecurityPreset.TURBO) {
            if (!permissions.allowArbitraryCommands && !isAllowlisted) {
                return SecurityDecision(
                    kind = SecurityDecisionKind.DENIED,
                    reason = "Command '$raw' is not in the allowed commands list and allowArbitraryCommands is disabled",
                )
            }
            if (isDestructiveGit) {
                return SecurityDecision(
                    kind = SecurityDecisionKind.REQUIRES_APPROVAL,
                    reason = "Destructive Git operation '${cmd.command}' requires user confirmation even in Turbo mode",
                    approvalCommand = cmd,
                )
            }
            if (isDeletionCommand) {
                return SecurityDecision(
                    kind = SecurityDecisionKind.REQUIRES_APPROVAL,
                    reason = "Terminal file deletion command '${cmd.command}' requires user confirmation even in Turbo mode",
                    approvalCommand = cmd,
                )
            }
            if (isShellLauncher) {
                return SecurityDecision(
                    kind = SecurityDecisionKind.REQUIRES_APPROVAL,
                    reason = "Shell launcher '${cmd.command}' requires user confirmation in Turbo mode",
                    approvalCommand = cmd,
                )
            }
            if (isUnknownGit) {
                return SecurityDecision(
                    kind = SecurityDecisionKind.REQUIRES_APPROVAL,
                    reason = "Unknown Git subcommand or alias '${parsedGit.subcommand}' requires user confirmation in Turbo mode",
                    approvalCommand = cmd,
                )
            }
            if (parsedGit?.definesAlias == true) {
                return SecurityDecision(
                    kind = SecurityDecisionKind.REQUIRES_APPROVAL,
                    reason = "Git invocation defining or modifying aliases requires user confirmation in Turbo mode",
                    approvalCommand = cmd,
                )
            }
            if (isDangerous) {
                return SecurityDecision(
                    kind = SecurityDecisionKind.REQUIRES_APPROVAL,
                    reason = "Dangerous command '${cmd.command}' requires user confirmation even in Turbo mode",
                    approvalCommand = cmd,
                )
            }
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
        // Shell launchers ALWAYS require confirmation in Default mode regardless of requireApprovalForDangerous
        if (isShellLauncher) {
            return SecurityDecision(
                kind = SecurityDecisionKind.REQUIRES_APPROVAL,
                reason = "Shell launcher '${cmd.command}' requires user confirmation in Default mode",
                approvalCommand = cmd,
            )
        }

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

            // Check for PowerShell encoded command flags (-e, -enc, -Encoded, -EncodedCommand, etc.)
            val encMatch = Regex("^-(?:e|enc|enco|encod|encode|encoded|encodedcommand)(?:[:=](.+))?$", RegexOption.IGNORE_CASE).find(arg)
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

            // Check unquoted and unescaped version to defeat obfuscation (e.g. f"o"r"m"a"t, f\ormat, r\m)
            val unescaped = snippet.replace("\\", "").replace("\"", "").replace("'", "")
            val unescapedLower = unescaped.lowercase(Locale.ROOT)
            for (blocked in TerminalConfig.DEFAULT_BLOCKED_COMMANDS) {
                if (unescapedLower.contains("\\b$blocked\\b".toRegex()) || unescapedLower.contains(blocked)) {
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
            val tokens = unescaped.split(Regex("[\\s;&|`$<>(){}]+"))
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
        val optionsWithSeparateArg = setOf(
            "-C", "-c", "--git-dir", "--work-tree", "--namespace", "--super-prefix", "--config-env"
        )
        val configs = mutableListOf<String>()
        var subcmd: String? = null
        var subArgs = emptyList<String>()
        var i = 0

        while (i < args.size) {
            val arg = args[i]

            // Check -c config option
            if (arg == "-c" || arg == "--config-env") {
                if (i + 1 < args.size) {
                    configs.add(args[i + 1])
                    i += 2
                    continue
                }
            } else if (arg.startsWith("-c") && arg.length > 2 && !arg.startsWith("--")) {
                configs.add(arg.substring(2))
                i++
                continue
            } else if (arg.startsWith("--config-env=")) {
                configs.add(arg.substringAfter("="))
                i++
                continue
            }

            // Options taking a separate argument
            if (arg in optionsWithSeparateArg) {
                i += 2
                continue
            }

            // Options with inline argument =
            if (arg.startsWith("--") && arg.contains("=")) {
                i++
                continue
            }

            // Standalone flags like --no-pager or --exec-path or -p
            if (arg.startsWith("-")) {
                i++
                continue
            }

            // First non-option token is the subcommand
            subcmd = arg.lowercase(Locale.ROOT)
            subArgs = if (i + 1 < args.size) args.subList(i + 1, args.size) else emptyList()
            break
        }

        // Analyze configs for alias definitions and dangerous commands
        var definesAlias = false
        var aliasDefinesShellOrDeletion = false
        for (cfg in configs) {
            val lowerCfg = cfg.lowercase(Locale.ROOT)
            if (lowerCfg.contains("alias.") || lowerCfg.startsWith("alias")) {
                definesAlias = true
                val valPart = cfg.substringAfter("=", "")
                if (valPart.startsWith("!") || valPart.contains("!") ||
                    FILE_DELETION_COMMANDS.any { del -> valPart.contains("\\b$del\\b".toRegex()) || valPart.contains(del) } ||
                    TerminalConfig.DEFAULT_BLOCKED_COMMANDS.any { b -> valPart.contains(b) }
                ) {
                    aliasDefinesShellOrDeletion = true
                }
            }
        }

        // Also check if subcommand is "config" and modifies an alias
        if (subcmd == "config") {
            val fullConfigArgs = subArgs.joinToString(" ").lowercase(Locale.ROOT)
            if (fullConfigArgs.contains("alias.") || fullConfigArgs.contains("alias ")) {
                definesAlias = true
                if (fullConfigArgs.contains("!") ||
                    FILE_DELETION_COMMANDS.any { del -> fullConfigArgs.contains("\\b$del\\b".toRegex()) || fullConfigArgs.contains(del) } ||
                    TerminalConfig.DEFAULT_BLOCKED_COMMANDS.any { b -> fullConfigArgs.contains(b) }
                ) {
                    aliasDefinesShellOrDeletion = true
                }
            }
        }

        return GitParsedInvocation(
            subcommand = subcmd,
            subcommandArgs = subArgs,
            configs = configs,
            definesAlias = definesAlias,
            aliasDefinesShellOrDeletion = aliasDefinesShellOrDeletion,
        )
    }

    private fun unwrapCommand(rawCommand: String, normalizedName: String, args: List<String>): ParsedCommandChain {
        var curCommand = rawCommand
        var curNormalized = normalizedName
        var curArgs = args
        val wrappers = mutableListOf<String>()

        while (curNormalized in WRAPPER_COMMANDS) {
            wrappers.add(curNormalized)
            val next = findWrappedTarget(curNormalized, curArgs) ?: break
            curCommand = next.first
            curArgs = next.second
            val isWin = System.getProperty("os.name")?.lowercase(Locale.ROOT)?.contains("win") == true
            val name = Path.of(curCommand.replace('\\', '/')).fileName?.toString() ?: curCommand
            curNormalized = if (isWin) {
                name.lowercase(Locale.ROOT).removeSuffix(".exe").removeSuffix(".cmd").removeSuffix(".bat")
            } else {
                name.lowercase(Locale.ROOT)
            }
        }

        return ParsedCommandChain(
            effectiveCommand = curCommand,
            effectiveNormalized = curNormalized,
            effectiveArgs = curArgs,
            isWrapped = wrappers.isNotEmpty(),
            wrappers = wrappers,
        )
    }

    private fun findWrappedTarget(wrapper: String, args: List<String>): Pair<String, List<String>>? {
        if (args.isEmpty()) return null
        var i = 0

        when (wrapper) {
            "sudo", "doas", "pkexec" -> {
                val optsWithArg = setOf("-u", "-g", "-p", "-c", "-C", "-r", "-t", "--user")
                while (i < args.size) {
                    val a = args[i]
                    if (a == "--") {
                        i++
                        break
                    }
                    if (a in optsWithArg) {
                        i += 2
                        continue
                    }
                    if (a.startsWith("-")) {
                        i++
                        continue
                    }
                    break
                }
            }
            "env" -> {
                val optsWithArg = setOf("-u", "-C")
                while (i < args.size) {
                    val a = args[i]
                    if (a == "--") {
                        i++
                        break
                    }
                    if (a in optsWithArg) {
                        i += 2
                        continue
                    }
                    if (a.startsWith("-")) {
                        i++
                        continue
                    }
                    // Skip VAR=val environment assignments
                    if (a.contains("=") && !a.startsWith("=")) {
                        i++
                        continue
                    }
                    break
                }
            }
            "busybox" -> {
                while (i < args.size) {
                    val a = args[i]
                    if (a.startsWith("-")) {
                        i++
                        continue
                    }
                    break
                }
            }
            "nohup" -> {
                while (i < args.size) {
                    val a = args[i]
                    if (a == "--") {
                        i++
                        break
                    }
                    if (a.startsWith("-")) {
                        i++
                        continue
                    }
                    break
                }
            }
            "su" -> {
                val optsWithArg = setOf("-c", "-s")
                while (i < args.size) {
                    val a = args[i]
                    if (a in optsWithArg) {
                        if (a == "-c" && i + 1 < args.size) {
                            val cmdString = args[i + 1]
                            val parts = cmdString.trim().split(Regex("\\s+"))
                            if (parts.isNotEmpty()) {
                                return Pair(parts[0], parts.drop(1))
                            }
                        }
                        i += 2
                        continue
                    }
                    if (a.startsWith("-")) {
                        i++
                        continue
                    }
                    // username
                    i++
                    break
                }
            }
            else -> {
                while (i < args.size && args[i].startsWith("-")) {
                    i++
                }
            }
        }

        if (i < args.size) {
            val target = args[i]
            val targetArgs = if (i + 1 < args.size) args.subList(i + 1, args.size) else emptyList()
            return Pair(target, targetArgs)
        }
        return null
    }

    private fun isTerminalDeletionCommand(
        normalizedName: String,
        cmd: TerminalCommand,
        shellTokens: Set<String> = emptySet(),
        parsedGit: GitParsedInvocation? = null,
    ): Boolean {
        if (normalizedName in FILE_DELETION_COMMANDS) return true
        if (normalizedName == "git") {
            val git = parsedGit ?: parseGitArgs(cmd.args)
            if (git.subcommand == "clean") return true
            if (git.aliasDefinesShellOrDeletion) return true
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

    private fun isDestructiveGitCommand(
        normalizedName: String,
        cmd: TerminalCommand,
        parsedGit: GitParsedInvocation = parseGitArgs(cmd.args),
    ): Boolean {
        if (normalizedName != "git") return false
        val subcmd = parsedGit.subcommand ?: return false
        if (subcmd in DESTRUCTIVE_GIT_SUBCOMMANDS) return true
        if (subcmd == "checkout") {
            if (parsedGit.subcommandArgs.any { it == "-f" || it == "--force" || it == "-B" || it == "--discard-changes" }) return true
        }
        if (subcmd == "switch") {
            if (parsedGit.subcommandArgs.any { it == "-f" || it == "--force" || it == "-C" || it == "--discard-changes" }) return true
        }
        if (subcmd == "branch") {
            val hasDelete = parsedGit.subcommandArgs.any { it == "-D" || it == "-d" || it == "--delete" }
            val hasForce = parsedGit.subcommandArgs.any { it == "-D" || it == "-f" || it == "--force" }
            if (parsedGit.subcommandArgs.any { it == "-D" } || (hasDelete && hasForce)) return true
        }
        return false
    }

    private fun isDangerousCommand(
        cmdName: String,
        normalizedName: String,
        cmd: TerminalCommand,
        isDestructiveGit: Boolean,
        isDeletion: Boolean,
        isWrapped: Boolean,
        parsedGit: GitParsedInvocation?,
    ): Boolean {
        if (isWrapped) return true
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
        if (normalizedName == "git" && parsedGit != null) {
            if (parsedGit.definesAlias) return true
            val subcmd = parsedGit.subcommand
            if (subcmd != null && subcmd !in KNOWN_SAFE_GIT_SUBCOMMANDS && subcmd !in DESTRUCTIVE_GIT_SUBCOMMANDS) {
                return true
            }
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
