package com.limer.antigooglity.git

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

open class GitException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class GitProcessStartException(message: String, cause: Throwable? = null) : GitException(message, cause)
class GitTimeoutException(message: String, cause: Throwable? = null) : GitException(message, cause)

class JvmGitService(
    private val gitExecutable: String = "git",
    private val timeout: Duration = 10.seconds,
    private val maxOutputSizeBytes: Long = 256 * 1024L,
) : GitService {

    data class ProcessOutput(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    override suspend fun isGitAvailable(): Boolean {
        return try {
            val res = runGit(listOf("--version"), workingDir = null)
            res.exitCode == 0 && res.stdout.contains("git version", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks whether the discovered [repoRoot] is contained within (or identical to) [workspace].
     *
     * If [repoRoot] is located above [workspace] (i.e. parent directory contains .git) or
     * outside [workspace] entirely, returns false.
     */
    fun isGitRootContainedInWorkspace(repoRoot: Path, workspace: Path): Boolean {
        val canonicalRepo = try {
            if (Files.exists(repoRoot)) repoRoot.toRealPath() else repoRoot.toAbsolutePath().normalize()
        } catch (_: Exception) {
            repoRoot.toAbsolutePath().normalize()
        }
        val canonicalWorkspace = try {
            if (Files.exists(workspace)) workspace.toRealPath() else workspace.toAbsolutePath().normalize()
        } catch (_: Exception) {
            workspace.toAbsolutePath().normalize()
        }

        val isWindows = System.getProperty("os.name")?.lowercase(Locale.ROOT)?.contains("win") == true
        return if (isWindows) {
            val repoStr = canonicalRepo.toString().lowercase(Locale.ROOT).replace('\\', '/')
            val wsStr = canonicalWorkspace.toString().lowercase(Locale.ROOT).replace('\\', '/')
            repoStr == wsStr || repoStr.startsWith(if (wsStr.endsWith('/')) wsStr else "$wsStr/")
        } else {
            canonicalRepo == canonicalWorkspace || canonicalRepo.startsWith(canonicalWorkspace)
        }
    }

    override suspend fun getRepositoryInfo(workspacePath: String): GitRepositoryInfo {
        val path = try {
            val p = Path.of(workspacePath)
            if (Files.exists(p)) p.toRealPath() else p.toAbsolutePath().normalize()
        } catch (e: Exception) {
            return GitRepositoryInfo(
                isGitRepository = false,
                errorMessage = "Invalid workspace path: ${e.message}",
            )
        }

        if (!Files.isDirectory(path)) {
            return GitRepositoryInfo(
                isGitRepository = false,
                errorMessage = "Workspace path is not a directory: $workspacePath",
            )
        }

        if (!isGitAvailable()) {
            return GitRepositoryInfo(
                isGitRepository = false,
                errorMessage = "Git CLI is not installed or not available in PATH",
            )
        }

        // 1. Check if git repository and get toplevel
        val topLevelRes = try {
            runGit(listOf("rev-parse", "--show-toplevel"), workingDir = path)
        } catch (e: Exception) {
            return GitRepositoryInfo(
                isGitRepository = false,
                errorMessage = "Git command failed: ${e.message}",
            )
        }

        if (topLevelRes.exitCode != 0) {
            return GitRepositoryInfo(
                isGitRepository = false,
                errorMessage = "Not a Git repository",
            )
        }

        val rawRoot = topLevelRes.stdout.trim()
        val repoRoot = try {
            val rp = Path.of(rawRoot)
            if (Files.exists(rp)) rp.toRealPath() else rp.toAbsolutePath().normalize()
        } catch (_: Exception) {
            Path.of(rawRoot).toAbsolutePath().normalize()
        }

        // Security containment check: repoRoot MUST be within or equal to permitted workspace
        if (!isGitRootContainedInWorkspace(repoRoot, path)) {
            return GitRepositoryInfo(
                isGitRepository = false,
                errorMessage = "Git repository root '$repoRoot' is outside the permitted workspace directory '$path'",
            )
        }

        // 2. Get current branch
        val branchRes = runGit(listOf("branch", "--show-current"), workingDir = repoRoot)
        var currentBranch: String? = branchRes.stdout.trim().ifEmpty { null }

        // If in detached HEAD state, get short commit hash
        if (currentBranch == null) {
            val headRes = runGit(listOf("rev-parse", "--short", "HEAD"), workingDir = repoRoot)
            if (headRes.exitCode == 0 && headRes.stdout.isNotBlank()) {
                currentBranch = "HEAD (${headRes.stdout.trim()})"
            }
        }

        // 3. Get local branches
        val listRes = runGit(listOf("branch", "--format=%(refname:short)|%(HEAD)"), workingDir = repoRoot)
        val branches = mutableListOf<GitBranch>()
        if (listRes.exitCode == 0) {
            listRes.stdout.lines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    val parts = trimmed.split("|")
                    val name = parts[0].trim()
                    val isCurrent = parts.getOrNull(1)?.contains("*") == true || name == currentBranch
                    branches.add(GitBranch(name = name, isCurrent = isCurrent))
                }
            }
        }

        // 4. Get working tree status
        val statusRes = runGit(listOf("status", "--porcelain=v1", "--branch"), workingDir = repoRoot)
        val status = if (statusRes.exitCode == 0) {
            val lines = statusRes.stdout.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val dirtyLines = lines.filterNot { it.startsWith("##") }
            val uncommittedCount = dirtyLines.size
            GitStatus(
                isClean = uncommittedCount == 0,
                uncommittedChangesCount = uncommittedCount,
                branch = currentBranch,
                summary = if (uncommittedCount == 0) "Clean" else "$uncommittedCount uncommitted changes",
            )
        } else {
            null
        }

        return GitRepositoryInfo(
            isGitRepository = true,
            rootPath = repoRoot.toString(),
            currentBranch = currentBranch,
            branches = branches,
            status = status,
        )
    }

    override suspend fun switchBranch(workspacePath: String, branchName: String): GitOperationResult {
        val trimmedBranch = branchName.trim()
        if (trimmedBranch.isEmpty()) {
            return GitOperationResult.Failure("Branch name cannot be empty")
        }

        // Security check: prohibit flag injection
        if (trimmedBranch.startsWith("-")) {
            return GitOperationResult.Failure("Invalid branch name '$trimmedBranch': cannot start with '-'")
        }

        // Security check: reject null bytes and control characters
        if (trimmedBranch.contains('\u0000')) {
            return GitOperationResult.Failure("Branch name contains null byte")
        }

        val path = try {
            val p = Path.of(workspacePath)
            if (Files.exists(p)) p.toRealPath() else p.toAbsolutePath().normalize()
        } catch (e: Exception) {
            return GitOperationResult.Failure("Invalid workspace path: ${e.message}")
        }

        if (!isGitAvailable()) {
            return GitOperationResult.Failure("Git CLI is not installed or not available in PATH")
        }

        // Verify repo root
        val topLevelRes = runGit(listOf("rev-parse", "--show-toplevel"), workingDir = path)
        if (topLevelRes.exitCode != 0) {
            return GitOperationResult.Failure("Not a Git repository: $workspacePath")
        }

        val rawRoot = topLevelRes.stdout.trim()
        val repoRoot = try {
            val rp = Path.of(rawRoot)
            if (Files.exists(rp)) rp.toRealPath() else rp.toAbsolutePath().normalize()
        } catch (_: Exception) {
            Path.of(rawRoot).toAbsolutePath().normalize()
        }

        // Security check: ensure repo root does not escape permitted workspace
        if (!isGitRootContainedInWorkspace(repoRoot, path)) {
            return GitOperationResult.Failure(
                "Repository root '$repoRoot' is outside the permitted workspace directory '$path'"
            )
        }

        // Validate branch name using git check-ref-format --branch
        val checkRef = runGit(listOf("check-ref-format", "--branch", trimmedBranch), workingDir = repoRoot)
        if (checkRef.exitCode != 0) {
            return GitOperationResult.Failure("Branch name '$trimmedBranch' is invalid according to git check-ref-format")
        }

        // Check if branch exists in local branches
        val branchListRes = runGit(listOf("branch", "--format=%(refname:short)"), workingDir = repoRoot)
        if (branchListRes.exitCode != 0) {
            return GitOperationResult.Failure("Failed to list local branches")
        }
        val existingBranches = branchListRes.stdout.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (trimmedBranch !in existingBranches) {
            return GitOperationResult.Failure("Local branch '$trimmedBranch' does not exist")
        }

        // Check current branch
        val curBranchRes = runGit(listOf("branch", "--show-current"), workingDir = repoRoot)
        if (curBranchRes.exitCode == 0 && curBranchRes.stdout.trim() == trimmedBranch) {
            return GitOperationResult.Success("Already on branch '$trimmedBranch'")
        }

        // Check status of working tree
        val statusRes = runGit(listOf("status", "--porcelain=v1"), workingDir = repoRoot)
        val hasUncommitted = statusRes.exitCode == 0 && statusRes.stdout.lines().any { it.trim().isNotEmpty() }

        // Execute safe git switch without force/stash/reset
        val switchRes = runGit(listOf("switch", trimmedBranch), workingDir = repoRoot)
        return if (switchRes.exitCode == 0) {
            GitOperationResult.Success("Switched to branch '$trimmedBranch'")
        } else {
            val err = switchRes.stderr.ifBlank { switchRes.stdout }.trim()
            GitOperationResult.Failure(
                errorMessage = err.ifBlank { "Git switch exited with code ${switchRes.exitCode}" },
                hasUncommittedChanges = hasUncommitted,
            )
        }
    }

    private suspend fun runGit(args: List<String>, workingDir: Path?): ProcessOutput = coroutineScope {
        val command = listOf(gitExecutable) + args
        val pb = ProcessBuilder(command)
        if (workingDir != null) {
            pb.directory(workingDir.toFile())
        }
        pb.environment()["LC_ALL"] = "C"

        val process = try {
            pb.start()
        } catch (e: Exception) {
            throw GitProcessStartException("Failed to start git process '$command': ${e.message}", e)
        }

        val job = coroutineContext[Job]
        val cancelRegistration = job?.invokeOnCompletion { cause ->
            if (cause != null) {
                destroyProcessTree(process)
            }
        }

        try {
            withTimeout(timeout) {
                val stdoutDeferred = async(Dispatchers.IO) {
                    readStreamBounded(process.inputStream, maxOutputSizeBytes)
                }
                val stderrDeferred = async(Dispatchers.IO) {
                    readStreamBounded(process.errorStream, maxOutputSizeBytes)
                }

                val exitCode = withContext(Dispatchers.IO) {
                    process.waitFor()
                }

                val stdout = stdoutDeferred.await()
                val stderr = stderrDeferred.await()

                ProcessOutput(
                    exitCode = exitCode,
                    stdout = stdout,
                    stderr = stderr,
                )
            }
        } catch (e: TimeoutCancellationException) {
            destroyProcessTree(process)
            throw GitTimeoutException("Git command '${command.joinToString(" ")}' timed out after $timeout", e)
        } catch (e: CancellationException) {
            destroyProcessTree(process)
            throw e
        } catch (e: Exception) {
            destroyProcessTree(process)
            throw e
        } finally {
            cancelRegistration?.dispose()
            try { process.inputStream.close() } catch (_: Exception) {}
            try { process.errorStream.close() } catch (_: Exception) {}
            try { process.outputStream.close() } catch (_: Exception) {}
        }
    }

    private fun destroyProcessTree(process: Process) {
        try {
            val descendants = try {
                process.descendants().toList()
            } catch (_: Exception) {
                emptyList()
            }
            descendants.forEach { desc ->
                try {
                    desc.destroyForcibly()
                } catch (_: Exception) {}
            }
            process.destroyForcibly()
            process.waitFor(200, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: Exception) {}
    }

    private fun readStreamBounded(stream: InputStream, maxBytes: Long): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var totalStored = 0L
        stream.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                val toStore = minOf(read.toLong(), maxOf(0L, maxBytes - totalStored)).toInt()
                if (toStore > 0) {
                    out.write(buffer, 0, toStore)
                    totalStored += toStore
                }
                // Continue reading until EOF to prevent child process from blocking on full pipe
            }
        }
        return out.toString(Charsets.UTF_8)
    }
}
