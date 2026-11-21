package com.limer.antigooglity.tools.terminal

import com.limer.antigooglity.tools.fs.WorkspacePathResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.TimeUnit

open class TerminalException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class ProcessStartException(message: String, cause: Throwable? = null) : TerminalException(message, cause)

class TerminalExecutor(
    val config: TerminalConfig,
) {
    val workspaceResolver = WorkspacePathResolver(config.rootDirectory)

    suspend fun execute(command: TerminalCommand): TerminalResult {
        val resolved = validateSecurity(command)

        val workingDir = resolveWorkingDirectory(command.workingDirectory)

        val fullCommandList = listOf(resolved.executable) + command.args

        return executeProcess(
            commandList = fullCommandList,
            commandDisplay = resolved.display,
            workingDirectory = workingDir,
        )
    }

    private data class ResolvedCommand(
        val executable: String,
        val display: String,
    )

    private suspend fun validateSecurity(command: TerminalCommand): ResolvedCommand {
        val raw = command.command.trim()
        if (raw.isEmpty()) {
            throw IllegalArgumentException("Command must not be empty")
        }
        if (raw.contains('\u0000')) {
            throw SecurityException("Null byte detected in command string")
        }

        val isWindows = System.getProperty("os.name")?.lowercase(Locale.ROOT)?.contains("win") == true
        val normalizedRaw = raw.replace('\\', '/')

        // 1. Prohibit Windows batch scripts (.bat, .cmd) by default to prevent shell injection via cmd.exe
        val lowerRaw = raw.lowercase(Locale.ROOT)
        if (isWindows && (lowerRaw.endsWith(".bat") || lowerRaw.endsWith(".cmd"))) {
            if (!config.allowBatchScripts) {
                throw SecurityException(
                    "Execution of Windows batch scripts (.bat, .cmd) is disabled by security policy. " +
                        "Enable allowBatchScripts in TerminalConfig only if strictly required."
                )
            }
        }

        val hasPathSeparators = normalizedRaw.contains('/')

        val (initialExecutable, commandIdentifier) = if (hasPathSeparators) {
            val rawPath = Path.of(raw)
            if (rawPath.isAbsolute) {
                val isTrustedPath = config.trustedCommandPaths.values.any {
                    try { it.toRealPath() == rawPath.toRealPath() } catch (_: Exception) { it.normalize() == rawPath.normalize() }
                }
                // Absolute paths outside workspace are forbidden unless arbitrary commands are explicitly enabled or it's a trusted path
                if (!config.allowArbitraryCommands && !isTrustedPath) {
                    throw SecurityException(
                        "Absolute executable path '$raw' is forbidden when allowArbitraryCommands is disabled. " +
                            "Use bare allowed command names or configure trustedCommandPaths."
                    )
                }
                Pair(rawPath.normalize().toString(), rawPath.fileName?.toString() ?: raw)
            } else {
                // Relative path inside workspace
                val resolvedPath = workspaceResolver.resolve(raw, mustExist = true)
                if (Files.isDirectory(resolvedPath)) {
                    throw IllegalArgumentException("Executable path '$raw' points to a directory, not an executable file")
                }
                val relativeStr = workspaceResolver.toRelativeString(resolvedPath)
                if (!config.allowArbitraryCommands && relativeStr !in config.allowedCommands && raw !in config.allowedCommands) {
                    throw SecurityException(
                        "Workspace executable '$raw' is not in the allowed commands list. " +
                            "Add '$relativeStr' to allowedCommands or enable allowArbitraryCommands."
                    )
                }
                Pair(resolvedPath.toAbsolutePath().toString(), relativeStr)
            }
        } else {
            // Bare command name
            Pair(raw, raw)
        }

        val cmdName = Path.of(commandIdentifier).fileName?.toString() ?: commandIdentifier
        val normalizedName = if (isWindows) {
            cmdName.lowercase(Locale.ROOT).removeSuffix(".exe").removeSuffix(".cmd").removeSuffix(".bat")
        } else {
            cmdName
        }

        // 2. Strictly blocked commands
        if (cmdName in config.blockedCommands || normalizedName in config.blockedCommands || commandIdentifier in config.blockedCommands) {
            throw SecurityException("Command '$raw' is strictly forbidden by terminal security policy")
        }

        // 3. Dangerous commands requiring approval
        val isDangerous = cmdName in config.dangerousCommands || normalizedName in config.dangerousCommands || commandIdentifier in config.dangerousCommands
        if (isDangerous && config.requireApprovalForDangerous) {
            val approver = config.commandApprover
                ?: throw SecurityException("Command requires user approval: '$raw' is marked as dangerous")
            val approved = approver.requestApproval(command)
            if (!approved) {
                throw SecurityException("Command requires user approval: execution rejected by user for '$raw'")
            }
        }

        // 4. Allowlist check for bare command names
        if (!hasPathSeparators && !config.allowArbitraryCommands) {
            val isAllowed = cmdName in config.allowedCommands || normalizedName in config.allowedCommands
            if (!isAllowed) {
                throw SecurityException("Command '$raw' is not in the allowed commands list")
            }
        }

        // 5. Executable resolution: resolve bare command names to trusted absolute paths
        val finalExecutable = if (!hasPathSeparators) {
            resolveSystemExecutable(
                raw = raw,
                cmdName = cmdName,
                normalizedName = normalizedName,
                isWindows = isWindows,
            )
        } else {
            initialExecutable
        }

        return ResolvedCommand(
            executable = finalExecutable,
            display = (listOf(commandIdentifier) + command.args).joinToString(" "),
        )
    }

    private fun resolveSystemExecutable(
        raw: String,
        cmdName: String,
        normalizedName: String,
        isWindows: Boolean,
    ): String {
        // 1. Check pinned trusted paths in config
        val trustedPath = config.trustedCommandPaths[raw]
            ?: config.trustedCommandPaths[cmdName]
            ?: config.trustedCommandPaths[normalizedName]
        if (trustedPath != null) {
            val realTrusted = try {
                trustedPath.toRealPath()
            } catch (e: Exception) {
                throw ProcessStartException("Trusted command path '$trustedPath' does not exist or cannot be accessed", e)
            }
            if (!Files.isRegularFile(realTrusted) || !Files.isExecutable(realTrusted)) {
                throw ProcessStartException("Trusted command path '$trustedPath' is not an executable file")
            }
            return realTrusted.toString()
        }

        // 2. Runtime pinned executables (e.g. current JVM java/javac)
        if (normalizedName == "java" || normalizedName == "javac") {
            val javaHome = System.getProperty("java.home")
            if (javaHome != null) {
                val exeName = if (isWindows) "$normalizedName.exe" else normalizedName
                val javaBin = Path.of(javaHome, "bin", exeName)
                if (Files.isRegularFile(javaBin) && Files.isExecutable(javaBin)) {
                    val realJava = try { javaBin.toRealPath() } catch (_: Exception) { javaBin.toAbsolutePath() }
                    return realJava.toString()
                }
            }
        }

        // 3. Search system PATH, strictly excluding workspace directories
        val pathVar = config.environment["PATH"] ?: System.getenv("PATH") ?: ""
        val candidateNames = if (isWindows) {
            if (cmdName.lowercase(Locale.ROOT).endsWith(".exe")) {
                listOf(cmdName)
            } else {
                listOf("$cmdName.exe", cmdName)
            }
        } else {
            listOf(cmdName)
        }

        val pathEntries = pathVar.split(File.pathSeparatorChar)
        for (entry in pathEntries) {
            val trimmed = entry.trim()
            if (trimmed.isEmpty()) continue
            val dirPath = try {
                Path.of(trimmed)
            } catch (_: Exception) {
                continue
            }
            if (!Files.isDirectory(dirPath)) continue

            // Exclude workspace directory from PATH resolution to prevent binary planting!
            val realDir = try {
                dirPath.toRealPath()
            } catch (_: Exception) {
                dirPath.toAbsolutePath().normalize()
            }
            if (realDir.startsWith(workspaceResolver.realRoot)) {
                continue
            }

            for (candidateName in candidateNames) {
                val candidatePath = dirPath.resolve(candidateName)
                if (Files.isRegularFile(candidatePath) && Files.isExecutable(candidatePath)) {
                    val realCandidate = try {
                        candidatePath.toRealPath()
                    } catch (_: Exception) {
                        candidatePath.toAbsolutePath().normalize()
                    }
                    if (realCandidate.startsWith(workspaceResolver.realRoot)) {
                        throw SecurityException("Resolved executable '$raw' is located inside the workspace, which is forbidden for bare system commands")
                    }
                    return realCandidate.toString()
                }
            }
        }

        // 4. If not found in trusted paths or system PATH
        if (!config.allowArbitraryCommands) {
            throw ProcessStartException("Executable not found on system PATH: '$raw'")
        }
        return raw
    }

    private fun resolveWorkingDirectory(workingDirectory: String): Path {
        val dir = workspaceResolver.resolve(workingDirectory, mustExist = true)
        if (!Files.isDirectory(dir)) {
            throw IllegalArgumentException("Working directory '${workspaceResolver.toRelativeString(dir)}' is not a directory")
        }
        return dir
    }

    private suspend fun executeProcess(
        commandList: List<String>,
        commandDisplay: String,
        workingDirectory: Path,
    ): TerminalResult = coroutineScope {
        val processBuilder = ProcessBuilder(commandList)
        processBuilder.directory(workingDirectory.toFile())
        if (config.environment.isNotEmpty()) {
            processBuilder.environment().putAll(config.environment)
        }

        val process: Process = withContext(Dispatchers.IO) {
            try {
                processBuilder.start()
            } catch (e: IOException) {
                throw ProcessStartException(
                    "Executable not found or failed to start '${commandList.first()}': ${e.message}",
                    e,
                )
            }
        }

        val stdoutDeferred = async(Dispatchers.IO) {
            val reader = LimitedStreamReader(process.inputStream, config.maxStdoutSizeBytes)
            val text = reader.readAll()
            Pair(text, reader.isTruncated)
        }

        val stderrDeferred = async(Dispatchers.IO) {
            val reader = LimitedStreamReader(process.errorStream, config.maxStderrSizeBytes)
            val text = reader.readAll()
            Pair(text, reader.isTruncated)
        }

        val cancelRegistration = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause != null) {
                destroyProcessTree(process)
            }
        }

        var timedOut = false
        try {
            withTimeout(config.timeout) {
                withContext(Dispatchers.IO) {
                    process.waitFor()
                }
            }
        } catch (e: TimeoutCancellationException) {
            timedOut = true
            destroyProcessTree(process)
        } catch (e: CancellationException) {
            destroyProcessTree(process)
            throw e
        } finally {
            cancelRegistration?.dispose()
            try { process.inputStream.close() } catch (_: Exception) {}
            try { process.errorStream.close() } catch (_: Exception) {}
            try { process.outputStream.close() } catch (_: Exception) {}
        }

        val (stdoutText, stdoutTruncated) = if (timedOut) {
            withTimeoutOrNull(500) { stdoutDeferred.await() } ?: Pair("", false)
        } else {
            stdoutDeferred.await()
        }

        val (stderrText, stderrTruncated) = if (timedOut) {
            withTimeoutOrNull(500) { stderrDeferred.await() } ?: Pair("", false)
        } else {
            stderrDeferred.await()
        }

        val exitCode = if (timedOut) -1 else process.exitValue()
        val outputTruncated = stdoutTruncated || stderrTruncated
        val success = exitCode == 0 && !timedOut

        TerminalResult(
            command = commandDisplay,
            exitCode = exitCode,
            stdout = stdoutText,
            stderr = stderrText,
            timedOut = timedOut,
            outputTruncated = outputTruncated,
            success = success,
        )
    }

    private fun destroyProcessTree(process: Process) {
        try {
            val descendants = try {
                process.descendants().toList()
            } catch (_: Exception) {
                emptyList()
            }

            for (desc in descendants) {
                try {
                    desc.destroyForcibly()
                } catch (_: Exception) {}
            }

            process.destroyForcibly()

            try { process.inputStream.close() } catch (_: Exception) {}
            try { process.errorStream.close() } catch (_: Exception) {}
            try { process.outputStream.close() } catch (_: Exception) {}

            process.waitFor(500, TimeUnit.MILLISECONDS)

            try {
                process.descendants().forEach { desc ->
                    try { desc.destroyForcibly() } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    private class LimitedStreamReader(
        private val inputStream: InputStream,
        private val maxBytes: Long,
    ) {
        private val baos = ByteArrayOutputStream()
        @Volatile
        var isTruncated = false
            private set

        fun readAll(): String {
            val buffer = ByteArray(8192)
            var totalBytesRead = 0L
            try {
                inputStream.use { stream ->
                    while (true) {
                        val count = stream.read(buffer)
                        if (count == -1) break
                        totalBytesRead += count
                        if (baos.size() < maxBytes) {
                            val toWrite = minOf(count.toLong(), maxBytes - baos.size()).toInt()
                            baos.write(buffer, 0, toWrite)
                        }
                        if (totalBytesRead > maxBytes) {
                            isTruncated = true
                        }
                    }
                }
            } catch (_: IOException) {
                // Stream closed on process termination/cancellation
            }

            val rawBytes = baos.toByteArray()
            if (rawBytes.isEmpty()) return ""

            val validByteLength = if (isTruncated) {
                findValidUtf8Length(rawBytes, rawBytes.size)
            } else {
                rawBytes.size
            }

            if (validByteLength <= 0) return ""

            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
            val byteBuffer = ByteBuffer.wrap(rawBytes, 0, validByteLength)
            val charBuffer = CharBuffer.allocate(validByteLength)
            decoder.decode(byteBuffer, charBuffer, true)
            decoder.flush(charBuffer)
            charBuffer.flip()

            return charBuffer.toString()
        }

        private fun findValidUtf8Length(bytes: ByteArray, length: Int): Int {
            if (length <= 0) return 0
            var i = length - 1
            var continuationCount = 0
            while (i >= 0 && (bytes[i].toInt() and 0xC0) == 0x80) {
                continuationCount++
                i--
            }
            if (i < 0) {
                return length
            }
            val leadByte = bytes[i].toInt() and 0xFF
            val expectedLength = when {
                (leadByte and 0x80) == 0 -> 1
                (leadByte and 0xE0) == 0xC0 -> 2
                (leadByte and 0xF0) == 0xE0 -> 3
                (leadByte and 0xF8) == 0xF0 -> 4
                else -> -1
            }
            if (expectedLength > 1) {
                val actualLength = 1 + continuationCount
                if (actualLength < expectedLength) {
                    return i
                }
            }
            return length
        }
    }
}
