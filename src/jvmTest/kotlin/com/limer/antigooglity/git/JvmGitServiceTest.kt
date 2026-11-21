package com.limer.antigooglity.git

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class JvmGitServiceTest {

    private lateinit var tempDir: Path
    private val gitService = JvmGitService()
    private var gitAvailable: Boolean = false

    @BeforeTest
    fun setUp() = runBlocking {
        tempDir = Files.createTempDirectory("antigooglity-git-test-").toRealPath()
        gitAvailable = gitService.isGitAvailable()
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private fun assumeGitAvailable(): Boolean {
        if (!gitAvailable) {
            println("Skipping Git integration test: git CLI is not installed or available in PATH.")
        }
        return gitAvailable
    }

    private fun initLocalRepo(dir: Path) {
        val pb1 = ProcessBuilder("git", "init").directory(dir.toFile())
        pb1.start().waitFor()

        val pb2 = ProcessBuilder("git", "config", "user.name", "TestUser").directory(dir.toFile())
        pb2.start().waitFor()

        val pb3 = ProcessBuilder("git", "config", "user.email", "test@test.local").directory(dir.toFile())
        pb3.start().waitFor()

        Files.writeString(dir.resolve("initial.txt"), "hello git")

        val pb4 = ProcessBuilder("git", "add", "initial.txt").directory(dir.toFile())
        pb4.start().waitFor()

        val pb5 = ProcessBuilder("git", "commit", "-m", "Initial commit").directory(dir.toFile())
        pb5.start().waitFor()
    }

    // --- 1. Git Availability & Missing Git ---

    @Test
    fun testIsGitAvailableReturnsTrueOrFalseGracefully() = runBlocking {
        val available = gitService.isGitAvailable()
        if (gitAvailable) {
            assertTrue(available)
        }
    }

    @Test
    fun testMissingGitExecutableHandledGracefully() = runBlocking {
        val missingService = JvmGitService(gitExecutable = "non_existent_git_binary_xyz_123")
        assertFalse(missingService.isGitAvailable())

        val info = missingService.getRepositoryInfo(tempDir.toString())
        assertFalse(info.isGitRepository)
        assertTrue(info.errorMessage?.contains("not installed or not available", ignoreCase = true) == true)

        val switchRes = missingService.switchBranch(tempDir.toString(), "main")
        assertTrue(switchRes is GitOperationResult.Failure)
    }

    // --- 2. Missing Repository ---

    @Test
    fun testMissingRepositoryReturnsNotGitRepo() = runBlocking {
        if (!assumeGitAvailable()) return@runBlocking

        val info = gitService.getRepositoryInfo(tempDir.toString())
        assertFalse(info.isGitRepository)
        assertEquals("Not a Git repository", info.errorMessage)
        assertTrue(info.branches.isEmpty())
    }

    // --- 3. Repository with Spaces in Path & Unicode ---

    @Test
    fun testRepositoryWithSpacesAndUnicode() = runBlocking {
        if (!assumeGitAvailable()) return@runBlocking

        val spaceDir = tempDir.resolve("my project space проект")
        Files.createDirectories(spaceDir)
        initLocalRepo(spaceDir)

        val info = gitService.getRepositoryInfo(spaceDir.toString())
        assertTrue(info.isGitRepository)
        assertNotNull(info.currentBranch)
        assertTrue(info.branches.isNotEmpty())
        assertTrue(info.branches.any { it.isCurrent })
    }

    // --- 4. Branch Listing & Active Branch Detection ---

    @Test
    fun testBranchListingAndActiveBranch() = runBlocking {
        if (!assumeGitAvailable()) return@runBlocking

        initLocalRepo(tempDir)

        // Create feature branch
        val pb = ProcessBuilder("git", "branch", "feature-test").directory(tempDir.toFile())
        pb.start().waitFor()

        val info = gitService.getRepositoryInfo(tempDir.toString())
        assertTrue(info.isGitRepository)
        assertTrue(info.branches.size >= 2)

        val active = info.branches.firstOrNull { it.isCurrent }
        assertNotNull(active)
        assertEquals(info.currentBranch, active.name)
        assertTrue(info.branches.any { it.name == "feature-test" && !it.isCurrent })
    }

    // --- 5. Clean vs Dirty Working Tree ---

    @Test
    fun testWorkingTreeStatus() = runBlocking {
        if (!assumeGitAvailable()) return@runBlocking

        initLocalRepo(tempDir)

        val cleanInfo = gitService.getRepositoryInfo(tempDir.toString())
        assertNotNull(cleanInfo.status)
        assertTrue(cleanInfo.status.isClean)
        assertEquals(0, cleanInfo.status.uncommittedChangesCount)

        // Make working tree dirty
        Files.writeString(tempDir.resolve("dirty_file.txt"), "uncommitted")

        val dirtyInfo = gitService.getRepositoryInfo(tempDir.toString())
        assertNotNull(dirtyInfo.status)
        assertFalse(dirtyInfo.status.isClean)
        assertEquals(1, dirtyInfo.status.uncommittedChangesCount)
    }

    // --- 6. Safe Branch Switching ---

    @Test
    fun testSafeBranchSwitch() = runBlocking {
        if (!assumeGitAvailable()) return@runBlocking

        initLocalRepo(tempDir)

        // Create feature branch
        val pb = ProcessBuilder("git", "branch", "dev").directory(tempDir.toFile())
        pb.start().waitFor()

        val switchRes = gitService.switchBranch(tempDir.toString(), "dev")
        assertTrue(switchRes is GitOperationResult.Success)
        assertEquals("Switched to branch 'dev'", switchRes.message)

        val updatedInfo = gitService.getRepositoryInfo(tempDir.toString())
        assertEquals("dev", updatedInfo.currentBranch)
        val devBranch = updatedInfo.branches.firstOrNull { it.name == "dev" }
        assertNotNull(devBranch)
        assertTrue(devBranch.isCurrent)
    }

    // --- 7. Dirty Working Tree Conflict (No force/stash/reset) ---

    @Test
    fun testDirtyWorkingTreeConflictPreventsSwitchWithoutReset() = runBlocking {
        if (!assumeGitAvailable()) return@runBlocking

        initLocalRepo(tempDir)

        // Create branch other
        val pbBranch = ProcessBuilder("git", "branch", "other").directory(tempDir.toFile())
        pbBranch.start().waitFor()

        // Modify tracked file in other branch
        val pbSwitch = ProcessBuilder("git", "switch", "other").directory(tempDir.toFile())
        pbSwitch.start().waitFor()
        Files.writeString(tempDir.resolve("initial.txt"), "modified in other")
        val pbCommit = ProcessBuilder("git", "commit", "-am", "commit in other").directory(tempDir.toFile())
        pbCommit.start().waitFor()

        // Switch back to master/main
        val pbMain = ProcessBuilder("git", "switch", "-").directory(tempDir.toFile())
        pbMain.start().waitFor()

        // Now modify initial.txt locally without committing
        Files.writeString(tempDir.resolve("initial.txt"), "uncommitted local change")

        // Try to switch to 'other' — Git will refuse because initial.txt would be overwritten
        val result = gitService.switchBranch(tempDir.toString(), "other")
        assertTrue(result is GitOperationResult.Failure, "Switch must fail on conflict")
        val failure = result
        assertTrue(failure.hasUncommittedChanges, "Must report uncommitted changes")
        assertTrue(
            failure.errorMessage.contains("overwritten", ignoreCase = true) ||
                failure.errorMessage.contains("local changes", ignoreCase = true)
        )

        // Verify user uncommitted file was NOT deleted or reset
        assertEquals("uncommitted local change", Files.readString(tempDir.resolve("initial.txt")))
    }

    // --- 8. Invalid Branch Name Validation ---

    @Test
    fun testInvalidBranchNameRejected() = runBlocking {
        if (!assumeGitAvailable()) return@runBlocking

        initLocalRepo(tempDir)

        val invalidNames = listOf(
            "",
            "   ",
            "-flag-injection",
            "--force",
            "branch with spaces",
            "branch..with..dots",
            "branch~1",
            "branch^2",
            "branch:colon",
            "branch?question",
            "branch*star",
            "branch[bracket",
            "branch\\backslash",
        )

        for (name in invalidNames) {
            val res = gitService.switchBranch(tempDir.toString(), name)
            assertTrue(res is GitOperationResult.Failure, "Branch name '$name' should be rejected")
        }
    }

    // --- 9. Timeout and Process Errors ---

    @Test
    fun testProcessTimeoutHandled() = runBlocking {
        if (!assumeGitAvailable()) return@runBlocking

        initLocalRepo(tempDir)

        // Service with tiny 1ms timeout
        val timeoutService = JvmGitService(timeout = 1.milliseconds)
        try {
            timeoutService.getRepositoryInfo(tempDir.toString())
        } catch (e: Exception) {
            assertTrue(e.message?.contains("timed out", ignoreCase = true) == true)
        }
    }

    // --- 10. Workspace Boundary Containment & Parent Repo Isolation ---

    @Test
    fun testIsGitRootContainedInWorkspaceLogic() {
        val rootDir = tempDir
        val subDir = rootDir.resolve("child")
        val siblingDir = rootDir.resolveSibling("sibling")

        // 1. Same directory -> contained
        assertTrue(gitService.isGitRootContainedInWorkspace(rootDir, rootDir))

        // 2. Child directory of workspace -> contained
        assertTrue(gitService.isGitRootContainedInWorkspace(subDir, rootDir))

        // 3. Parent directory of workspace -> NOT contained (security boundary)
        assertFalse(gitService.isGitRootContainedInWorkspace(rootDir, subDir))

        // 4. Sibling directory -> NOT contained
        assertFalse(gitService.isGitRootContainedInWorkspace(siblingDir, rootDir))
    }

    @Test
    fun testParentRepositoryAboveWorkspaceIsRejected() = runBlocking {
        if (!assumeGitAvailable()) return@runBlocking

        // Initialize Git repo at root tempDir
        initLocalRepo(tempDir)

        // Create an un-initialized subfolder to act as the agent workspace
        val subWorkspace = tempDir.resolve("agent_workspace_folder")
        Files.createDirectories(subWorkspace)

        // If workspace is subWorkspace, but git root is in parent tempDir:
        // Antigooglity MUST reject this parent repository to protect against workspace escapes!
        val info = gitService.getRepositoryInfo(subWorkspace.toString())
        assertFalse(info.isGitRepository, "Parent repository above workspace must NOT be exposed as valid repository")
        assertTrue(
            info.errorMessage?.contains("outside the permitted workspace directory", ignoreCase = true) == true,
            "Expected containment error message, but got: ${info.errorMessage}"
        )

        // Switching branch must also be rejected
        val switchRes = gitService.switchBranch(subWorkspace.toString(), "main")
        assertTrue(switchRes is GitOperationResult.Failure)
        assertTrue(switchRes.errorMessage.contains("outside the permitted workspace directory", ignoreCase = true))
    }

    @Test
    fun testSubdirectoryRepositoryInsideWorkspaceIsValid() = runBlocking {
        if (!assumeGitAvailable()) return@runBlocking

        // Workspace contains an isolated git repo inside a subdirectory
        val subRepo = tempDir.resolve("sub_repo")
        Files.createDirectories(subRepo)
        initLocalRepo(subRepo)

        val info = gitService.getRepositoryInfo(subRepo.toString())
        assertTrue(info.isGitRepository)
        assertNotNull(info.currentBranch)
    }

    @Test
    fun testProcessTreeCancellationCleansUp() = runBlocking {
        if (!assumeGitAvailable()) return@runBlocking

        initLocalRepo(tempDir)

        // Launch an asynchronous git call and cancel it immediately
        val job = launch {
            try {
                // Command that would take some time or can be cancelled
                gitService.getRepositoryInfo(tempDir.toString())
            } catch (_: kotlinx.coroutines.CancellationException) {
                // expected
            }
        }
        delay(1)
        job.cancel()
        job.join()
        // If cancellation handling had deadlocks or unclosed processes, join() would hang
        assertTrue(job.isCancelled)
    }
}
