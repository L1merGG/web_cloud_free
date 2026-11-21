package com.limer.antigooglity.coordinator

import com.limer.antigooglity.tools.ToolCall
import com.limer.antigooglity.tools.mock.MockToolExecutor
import com.limer.antigooglity.tools.terminal.TerminalCommand
import kotlinx.coroutines.runBlocking
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecurityPolicyTest {

    private lateinit var workspaceDir: Path
    private val policy = SecurityPolicy()

    @BeforeTest
    fun setUp() {
        workspaceDir = Files.createTempDirectory("antigooglity-policy-test-").toRealPath()
        Files.writeString(workspaceDir.resolve("sample.txt"), "sample content")
    }

    @AfterTest
    fun tearDown() {
        workspaceDir.toFile().deleteRecursively()
    }

    // --- 1. REQUEST REVIEW ---

    @Test
    fun testRequestReviewRequiresApprovalForReadFile() {
        val call = ToolCall(id = "1", name = "read_file", arguments = """{"path":"sample.txt"}""")
        val decision = policy.evaluate(
            call = call,
            permissions = SecurityPermissions(allowReadFile = true),
            preset = SecurityPreset.REQUEST_REVIEW,
            workspaceRoot = workspaceDir,
        )
        assertEquals(SecurityDecisionKind.REQUIRES_APPROVAL, decision.kind)
        assertTrue(decision.requiresApproval)
    }

    @Test
    fun testRequestReviewRequiresApprovalForWriteFile() {
        val call = ToolCall(id = "2", name = "write_file", arguments = """{"path":"out.txt","content":"hello"}""")
        val decision = policy.evaluate(
            call = call,
            permissions = SecurityPermissions(allowWriteFile = true),
            preset = SecurityPreset.REQUEST_REVIEW,
            workspaceRoot = workspaceDir,
        )
        assertEquals(SecurityDecisionKind.REQUIRES_APPROVAL, decision.kind)
    }

    @Test
    fun testRequestReviewRequiresApprovalForSafeCommand() {
        val call = ToolCall(id = "3", name = "execute_command", arguments = """{"command":"git","args":["status"]}""")
        val decision = policy.evaluate(
            call = call,
            permissions = SecurityPermissions(allowExecuteCommand = true),
            preset = SecurityPreset.REQUEST_REVIEW,
            workspaceRoot = workspaceDir,
        )
        assertEquals(SecurityDecisionKind.REQUIRES_APPROVAL, decision.kind)
    }

    @Test
    fun testRequestReviewDeniesForbiddenActionWithoutApproval() {
        // If reading files is disabled, it should be DENIED directly, NOT offered for approval!
        val call = ToolCall(id = "4", name = "read_file", arguments = """{"path":"sample.txt"}""")
        val decision = policy.evaluate(
            call = call,
            permissions = SecurityPermissions(allowReadFile = false),
            preset = SecurityPreset.REQUEST_REVIEW,
            workspaceRoot = workspaceDir,
        )
        assertEquals(SecurityDecisionKind.DENIED, decision.kind)
        assertTrue(decision.isDenied)
    }

    // --- 2. DEFAULT VS AUTO DISTINCTION ---

    @Test
    fun testDefaultAllowsSafeFileOperationsImmediately() {
        val readCall = ToolCall(id = "5", name = "read_file", arguments = """{"path":"sample.txt"}""")
        val writeCall = ToolCall(id = "6", name = "write_file", arguments = """{"path":"out.txt","content":"ok"}""")
        val listCall = ToolCall(id = "7", name = "list_directory", arguments = """{"path":"."}""")

        val perms = SecurityPermissions()
        assertEquals(SecurityDecisionKind.ALLOWED_IMMEDIATE, policy.evaluate(readCall, perms, SecurityPreset.DEFAULT, workspaceDir).kind)
        assertEquals(SecurityDecisionKind.ALLOWED_IMMEDIATE, policy.evaluate(writeCall, perms, SecurityPreset.DEFAULT, workspaceDir).kind)
        assertEquals(SecurityDecisionKind.ALLOWED_IMMEDIATE, policy.evaluate(listCall, perms, SecurityPreset.DEFAULT, workspaceDir).kind)
    }

    @Test
    fun testDefaultPreservesBaselineWritingToExistingFileImmediately() {
        // sample.txt exists
        val call = ToolCall(id = "d_write", name = "write_file", arguments = """{"path":"sample.txt","content":"overwritten"}""")
        val decision = policy.evaluate(call, SecurityPermissions(), SecurityPreset.DEFAULT, workspaceDir)
        assertEquals(SecurityDecisionKind.ALLOWED_IMMEDIATE, decision.kind, "Default should allow writing to existing file immediately")
    }

    @Test
    fun testDefaultAllowsSafeAllowlistedCommandsImmediately() {
        val gitCall = ToolCall(
            id = "9",
            name = "execute_command",
            arguments = """{"command":"git","args":["status"]}"""
        )
        val decision = policy.evaluate(
            call = gitCall,
            permissions = SecurityPermissions(allowExecuteCommand = true),
            preset = SecurityPreset.DEFAULT,
            workspaceRoot = workspaceDir,
        )
        assertEquals(SecurityDecisionKind.ALLOWED_IMMEDIATE, decision.kind)
    }

    @Test
    fun testDefaultRequiresApprovalForDangerousCommands() {
        val dangerCall = ToolCall(
            id = "8",
            name = "execute_command",
            arguments = """{"command":"rm","args":["--help"]}"""
        )
        val decision = policy.evaluate(
            call = dangerCall,
            permissions = SecurityPermissions(
                allowExecuteCommand = true,
                allowDeleteFile = true,
                requireApprovalForDangerous = true,
            ),
            preset = SecurityPreset.DEFAULT,
            workspaceRoot = workspaceDir,
        )
        assertEquals(SecurityDecisionKind.REQUIRES_APPROVAL, decision.kind)
    }

    @Test
    fun testAutoRequiresApprovalForWritingToExistingFile() {
        // In AUTO: modifying/overwriting existing file requires approval
        val overwriteCall = ToolCall(id = "a_ovr", name = "write_file", arguments = """{"path":"sample.txt","content":"overwritten"}""")
        val decision = policy.evaluate(overwriteCall, SecurityPermissions(), SecurityPreset.AUTO, workspaceDir)
        assertEquals(SecurityDecisionKind.REQUIRES_APPROVAL, decision.kind)
        assertTrue(decision.reason.contains("overwriting existing file", ignoreCase = true))
    }

    @Test
    fun testAutoAllowsWritingToNewFileImmediately() {
        // In AUTO: creating new file is low-risk and allowed immediately
        val newCall = ToolCall(id = "a_new", name = "write_file", arguments = """{"path":"brand_new_file.txt","content":"brand new"}""")
        val decision = policy.evaluate(newCall, SecurityPermissions(), SecurityPreset.AUTO, workspaceDir)
        assertEquals(SecurityDecisionKind.ALLOWED_IMMEDIATE, decision.kind)
    }

    @Test
    fun testAutoRequiresApprovalForTerminalCommandsEvenIfSafeAndAllowlisted() {
        // In AUTO: commands are not considered low-risk, so all commands require approval
        val gitCall = ToolCall(id = "a_cmd", name = "execute_command", arguments = """{"command":"git","args":["status"]}""")
        val decision = policy.evaluate(gitCall, SecurityPermissions(), SecurityPreset.AUTO, workspaceDir)
        assertEquals(SecurityDecisionKind.REQUIRES_APPROVAL, decision.kind)
        assertTrue(decision.reason.contains("Auto mode: terminal command", ignoreCase = true))
    }

    @Test
    fun testAutoAllowsReadOnlyOperationsImmediately() {
        val readCall = ToolCall(id = "10", name = "read_file", arguments = """{"path":"sample.txt"}""")
        val listCall = ToolCall(id = "11", name = "list_directory", arguments = """{"path":"."}""")
        val perms = SecurityPermissions(allowReadFile = true)

        assertEquals(SecurityDecisionKind.ALLOWED_IMMEDIATE, policy.evaluate(readCall, perms, SecurityPreset.AUTO, workspaceDir).kind)
        assertEquals(SecurityDecisionKind.ALLOWED_IMMEDIATE, policy.evaluate(listCall, perms, SecurityPreset.AUTO, workspaceDir).kind)
    }

    // --- 3. TERMINAL FILE DELETION & HARD DENY ---

    @Test
    fun testTerminalDeletionCommandsDeniedWhenAllowDeleteFileIsFalse() {
        val deletionCommands = listOf("rm", "rmdir", "del", "erase", "rd", "remove-item")
        for (cmd in deletionCommands) {
            val call = ToolCall(
                id = "del_$cmd",
                name = "execute_command",
                arguments = """{"command":"$cmd","args":["target_file.txt"]}"""
            )
            // Even in TURBO and even if allowArbitraryCommands=true
            val decision = policy.evaluate(
                call = call,
                permissions = SecurityPermissions(allowDeleteFile = false, allowArbitraryCommands = true),
                preset = SecurityPreset.TURBO,
                workspaceRoot = workspaceDir,
            )
            assertEquals(SecurityDecisionKind.DENIED, decision.kind, "Command '$cmd' should be DENIED when allowDeleteFile=false")
            assertTrue(decision.reason.contains("allowDeleteFile=false", ignoreCase = true))
        }
    }

    @Test
    fun testGitCleanDeniedWhenAllowDeleteFileIsFalse() {
        val call = ToolCall(
            id = "git_clean_deny",
            name = "execute_command",
            arguments = """{"command":"git","args":["clean","-fdx"]}"""
        )
        val decision = policy.evaluate(
            call = call,
            permissions = SecurityPermissions(allowDeleteFile = false),
            preset = SecurityPreset.TURBO,
            workspaceRoot = workspaceDir,
        )
        assertEquals(SecurityDecisionKind.DENIED, decision.kind, "git clean must be DENIED when allowDeleteFile=false")
        assertTrue(decision.reason.contains("allowDeleteFile=false", ignoreCase = true))
    }

    @Test
    fun testGitCleanRequiresApprovalInTurboWhenAllowDeleteFileIsTrue() {
        val call = ToolCall(
            id = "git_clean_appr",
            name = "execute_command",
            arguments = """{"command":"git","args":["clean","-fdx"]}"""
        )
        val decision = policy.evaluate(
            call = call,
            permissions = SecurityPermissions(allowDeleteFile = true),
            preset = SecurityPreset.TURBO,
            workspaceRoot = workspaceDir,
        )
        assertEquals(SecurityDecisionKind.REQUIRES_APPROVAL, decision.kind, "git clean must REQUIRE APPROVAL even in Turbo")
    }

    // --- 4. DESTRUCTIVE GIT COMMANDS IN TURBO ---

    @Test
    fun testDestructiveGitCommandsRequireApprovalInTurbo() {
        val destructiveCalls = listOf(
            """{"command":"git","args":["reset","--hard","HEAD~1"]}""",
            """{"command":"git","args":["checkout","-f","main"]}""",
            """{"command":"git","args":["switch","--force","feature"]}""",
            """{"command":"git","args":["branch","-D","obsolete"]}""",
        )

        for ((idx, argsJson) in destructiveCalls.withIndex()) {
            val call = ToolCall(id = "git_dest_$idx", name = "execute_command", arguments = argsJson)
            val decision = policy.evaluate(
                call = call,
                permissions = SecurityPermissions(allowDeleteFile = true),
                preset = SecurityPreset.TURBO,
                workspaceRoot = workspaceDir,
            )
            assertEquals(SecurityDecisionKind.REQUIRES_APPROVAL, decision.kind, "Call '$argsJson' must REQUIRE APPROVAL in Turbo")
            assertTrue(decision.reason.contains("Destructive Git operation", ignoreCase = true))
        }
    }

    // --- 5. SHELL LAUNCHERS & BLOCKED COMMANDS ---

    @Test
    fun testShellLauncherArgumentsWithBlockedCommandsAreDeniedEvenWithArbitraryCommands() {
        val launcherCalls = listOf(
            """{"command":"cmd","args":["/c","format","d:"]}""",
            """{"command":"powershell","args":["-Command","shutdown /s"]}""",
            """{"command":"bash","args":["-c","diskpart"]}""",
        )

        for ((idx, argsJson) in launcherCalls.withIndex()) {
            val call = ToolCall(id = "launch_b_$idx", name = "execute_command", arguments = argsJson)
            val decision = policy.evaluate(
                call = call,
                permissions = SecurityPermissions(allowArbitraryCommands = true),
                preset = SecurityPreset.TURBO,
                workspaceRoot = workspaceDir,
            )
            assertEquals(SecurityDecisionKind.DENIED, decision.kind, "Blocked command inside launcher must be DENIED: $argsJson")
            assertTrue(decision.reason.contains("strictly forbidden", ignoreCase = true))
        }
    }

    @Test
    fun testShellLauncherInTurboRequiresApproval() {
        val call = ToolCall(
            id = "sh_turbo",
            name = "execute_command",
            arguments = """{"command":"powershell","args":["-c","Get-Process"]}"""
        )
        val decision = policy.evaluate(
            call = call,
            permissions = SecurityPermissions(allowArbitraryCommands = true),
            preset = SecurityPreset.TURBO,
            workspaceRoot = workspaceDir,
        )
        assertEquals(SecurityDecisionKind.REQUIRES_APPROVAL, decision.kind, "Shell launcher should require approval in Turbo")
    }

    // --- 6. TURBO HARD DENY SCENARIOS ---

    @Test
    fun testTurboAutoApprovesEligibleOperations() {
        // Safe command in allowlist
        val gitCall = ToolCall(id = "13", name = "execute_command", arguments = """{"command":"git","args":["status"]}""")
        val gitDec = policy.evaluate(gitCall, SecurityPermissions(), SecurityPreset.TURBO, workspaceDir)
        assertEquals(SecurityDecisionKind.ALLOWED_IMMEDIATE, gitDec.kind)

        // File write
        val writeCall = ToolCall(id = "14", name = "write_file", arguments = """{"path":"turbo.txt","content":"fast"}""")
        val writeDec = policy.evaluate(writeCall, SecurityPermissions(), SecurityPreset.TURBO, workspaceDir)
        assertEquals(SecurityDecisionKind.ALLOWED_IMMEDIATE, writeDec.kind)
    }

    @Test
    fun testTurboDoesNotBypassDisabledReadFile() {
        val call = ToolCall(id = "15", name = "read_file", arguments = """{"path":"sample.txt"}""")
        val decision = policy.evaluate(
            call = call,
            permissions = SecurityPermissions(allowReadFile = false),
            preset = SecurityPreset.TURBO,
            workspaceRoot = workspaceDir,
        )
        assertEquals(SecurityDecisionKind.DENIED, decision.kind)
        assertTrue(decision.reason.contains("Reading files is disabled", ignoreCase = true))
    }

    @Test
    fun testTurboDoesNotBypassDisabledWriteFile() {
        val call = ToolCall(id = "16", name = "write_file", arguments = """{"path":"turbo.txt","content":"fail"}""")
        val decision = policy.evaluate(
            call = call,
            permissions = SecurityPermissions(allowWriteFile = false),
            preset = SecurityPreset.TURBO,
            workspaceRoot = workspaceDir,
        )
        assertEquals(SecurityDecisionKind.DENIED, decision.kind)
        assertTrue(decision.reason.contains("Writing files is disabled", ignoreCase = true))
    }

    @Test
    fun testTurboDoesNotBypassAllowDeleteFalse() {
        val call = ToolCall(id = "17", name = "delete_file", arguments = """{"path":"sample.txt"}""")
        val decision = policy.evaluate(
            call = call,
            permissions = SecurityPermissions(allowDeleteFile = false),
            preset = SecurityPreset.TURBO,
            workspaceRoot = workspaceDir,
        )
        assertEquals(SecurityDecisionKind.DENIED, decision.kind)
        assertTrue(decision.reason.contains("allowDeleteFile=false", ignoreCase = true))
    }

    @Test
    fun testTurboDoesNotBypassDisabledExecuteCommand() {
        val call = ToolCall(id = "18", name = "execute_command", arguments = """{"command":"git","args":["status"]}""")
        val decision = policy.evaluate(
            call = call,
            permissions = SecurityPermissions(allowExecuteCommand = false),
            preset = SecurityPreset.TURBO,
            workspaceRoot = workspaceDir,
        )
        assertEquals(SecurityDecisionKind.DENIED, decision.kind)
        assertTrue(decision.reason.contains("Executing terminal commands is disabled", ignoreCase = true))
    }

    @Test
    fun testTurboDoesNotBypassTerminalBlockedCommands() {
        val blockedCalls = listOf("format", "shutdown", "reboot", "diskpart")
        for (cmd in blockedCalls) {
            val call = ToolCall(id = "b_$cmd", name = "execute_command", arguments = """{"command":"$cmd"}""")
            val decision = policy.evaluate(
                call = call,
                permissions = SecurityPermissions(allowArbitraryCommands = true), // Even if arbitrary enabled!
                preset = SecurityPreset.TURBO,
                workspaceRoot = workspaceDir,
            )
            assertEquals(SecurityDecisionKind.DENIED, decision.kind, "Command '$cmd' should be strictly denied in Turbo")
            assertTrue(decision.reason.contains("strictly forbidden", ignoreCase = true))
        }
    }

    @Test
    fun testTurboDoesNotBypassTerminalAllowlist() {
        val call = ToolCall(id = "19", name = "execute_command", arguments = """{"command":"curl","args":["https://example.com"]}""")
        val decision = policy.evaluate(
            call = call,
            permissions = SecurityPermissions(allowArbitraryCommands = false),
            preset = SecurityPreset.TURBO,
            workspaceRoot = workspaceDir,
        )
        assertEquals(SecurityDecisionKind.DENIED, decision.kind)
        assertTrue(decision.reason.contains("not in the allowed commands list", ignoreCase = true))
    }

    @Test
    fun testTurboDoesNotBypassWorkspaceEnforcementAndPathTraversal() {
        val traversalCalls = listOf(
            ToolCall(id = "t1", name = "read_file", arguments = """{"path":"../../etc/passwd"}"""),
            ToolCall(id = "t2", name = "write_file", arguments = """{"path":"../secret.txt","content":"hacked"}"""),
            ToolCall(id = "t3", name = "delete_file", arguments = """{"path":"../../system32/cmd.exe"}"""),
        )

        for (call in traversalCalls) {
            val decision = policy.evaluate(
                call = call,
                permissions = SecurityPermissions(allowReadFile = true, allowWriteFile = true, allowDeleteFile = true),
                preset = SecurityPreset.TURBO,
                workspaceRoot = workspaceDir,
            )
            assertEquals(SecurityDecisionKind.DENIED, decision.kind, "Call '${call.name}' with traversal should be denied in Turbo")
            assertTrue(decision.reason.contains("boundary violation", ignoreCase = true) || decision.reason.contains("escapes", ignoreCase = true))
        }
    }

    @Test
    fun testTurboDoesNotAutoApproveUnknownTool() {
        val call = ToolCall(id = "20", name = "launch_nuclear_missile", arguments = "{}")
        val decision = policy.evaluate(
            call = call,
            permissions = SecurityPermissions(),
            preset = SecurityPreset.TURBO,
            workspaceRoot = workspaceDir,
        )
        assertEquals(SecurityDecisionKind.UNKNOWN_TOOL, decision.kind)
        assertTrue(decision.isUnknown)
    }

    // --- 7. POLICY ENFORCING EXECUTOR PIPELINE ---

    @Test
    fun testPolicyEnforcerRejectionPreventsExecution() = runBlocking {
        val mockDelegate = MockToolExecutor()
        var requestedApproval = false

        val executor = PolicyEnforcingToolExecutor(
            delegate = mockDelegate,
            workspacePath = { workspaceDir },
            permissions = { SecurityPermissions() },
            preset = { SecurityPreset.REQUEST_REVIEW },
            commandApprover = { _ ->
                requestedApproval = true
                false // User REJECTS
            },
            policy = policy,
        )

        val call = ToolCall(id = "p1", name = "write_file", arguments = """{"path":"should_not_exist.txt","content":"hello"}""")
        val result = executor.execute(call)

        assertTrue(requestedApproval, "Approval should have been requested")
        assertFalse(result.success, "Tool execution should fail when user rejects")
        assertTrue(result.content.contains("rejected by user", ignoreCase = true))
        assertFalse(mockDelegate.hasFile("should_not_exist.txt"), "File should NOT have been created by delegate")
    }

    @Test
    fun testPolicyEnforcerApprovalAllowsExecution() = runBlocking {
        val mockDelegate = MockToolExecutor()
        var requestedApproval = false

        val executor = PolicyEnforcingToolExecutor(
            delegate = mockDelegate,
            workspacePath = { workspaceDir },
            permissions = { SecurityPermissions() },
            preset = { SecurityPreset.REQUEST_REVIEW },
            commandApprover = { _ ->
                requestedApproval = true
                true // User APPROVES
            },
            policy = policy,
        )

        val call = ToolCall(id = "p2", name = "write_file", arguments = """{"path":"allowed.txt","content":"approved content"}""")
        val result = executor.execute(call)

        assertTrue(requestedApproval, "Approval should have been requested")
        assertTrue(result.success, "Tool execution should succeed after user approval")
        assertTrue(mockDelegate.hasFile("allowed.txt"), "File should have been created by delegate")
    }

    // --- 8. REGRESSION TESTS: GIT OPTIONS, SHELL OBFUSCATION & BASE64 ---

    @Test
    fun testGitWithOptionsCleanDeniedWhenAllowDeleteFileIsFalse() {
        val call = ToolCall(
            id = "git_opt_clean",
            name = "execute_command",
            arguments = """{"command":"git","args":["-C","subpath","clean","-fdx"]}"""
        )
        val decision = policy.evaluate(
            call = call,
            permissions = SecurityPermissions(allowDeleteFile = false, allowArbitraryCommands = true),
            preset = SecurityPreset.TURBO,
            workspaceRoot = workspaceDir,
        )
        assertEquals(SecurityDecisionKind.DENIED, decision.kind)
        assertTrue(decision.reason.contains("allowDeleteFile=false", ignoreCase = true))
    }

    @Test
    fun testGitWithOptionsResetRequiresApprovalInTurbo() {
        val call = ToolCall(
            id = "git_opt_reset",
            name = "execute_command",
            arguments = """{"command":"git","args":["-C","subpath","reset","--hard"]}"""
        )
        val decision = policy.evaluate(
            call = call,
            permissions = SecurityPermissions(allowArbitraryCommands = true),
            preset = SecurityPreset.TURBO,
            workspaceRoot = workspaceDir,
        )
        assertEquals(SecurityDecisionKind.REQUIRES_APPROVAL, decision.kind)
        assertTrue(decision.reason.contains("Destructive Git operation", ignoreCase = true))
    }

    @Test
    fun testGitCheckoutForceWithOptionsRequiresApprovalInTurbo() {
        val call = ToolCall(
            id = "git_opt_checkout",
            name = "execute_command",
            arguments = """{"command":"git","args":["--no-pager","checkout","-f","main"]}"""
        )
        val decision = policy.evaluate(
            call = call,
            permissions = SecurityPermissions(allowArbitraryCommands = true),
            preset = SecurityPreset.TURBO,
            workspaceRoot = workspaceDir,
        )
        assertEquals(SecurityDecisionKind.REQUIRES_APPROVAL, decision.kind)
        assertTrue(decision.reason.contains("Destructive Git operation", ignoreCase = true))
    }

    @Test
    fun testGitBranchDeleteVariantsRequireApprovalInTurbo() {
        val branchCalls = listOf(
            """{"command":"git","args":["branch","-D","feature"]}""",
            """{"command":"git","args":["branch","-d","-f","feature"]}""",
            """{"command":"git","args":["-C","sub","branch","--delete","--force","feature"]}""",
        )
        for (argsJson in branchCalls) {
            val call = ToolCall(id = "git_branch_del", name = "execute_command", arguments = argsJson)
            val decision = policy.evaluate(
                call = call,
                permissions = SecurityPermissions(allowArbitraryCommands = true),
                preset = SecurityPreset.TURBO,
                workspaceRoot = workspaceDir,
            )
            assertEquals(SecurityDecisionKind.REQUIRES_APPROVAL, decision.kind, "Branch delete '$argsJson' should require approval in Turbo")
            assertTrue(decision.reason.contains("Destructive Git operation", ignoreCase = true))
        }
    }

    @Test
    fun testPowerShellBase64EncodedBlockedCommandDenied() {
        // PowerShell encoded command uses UTF-16LE
        val rawCommand = "shutdown /s /t 0"
        val base64Payload = Base64.getEncoder().encodeToString(rawCommand.toByteArray(StandardCharsets.UTF_16LE))
        val call = ToolCall(
            id = "ps_b64",
            name = "execute_command",
            arguments = """{"command":"powershell","args":["-enc","$base64Payload"]}"""
        )
        val decision = policy.evaluate(
            call = call,
            permissions = SecurityPermissions(allowArbitraryCommands = true),
            preset = SecurityPreset.TURBO,
            workspaceRoot = workspaceDir,
        )
        assertEquals(SecurityDecisionKind.DENIED, decision.kind)
        assertTrue(decision.reason.contains("strictly forbidden command", ignoreCase = true))
    }

    @Test
    fun testPowerShellInvalidBase64Denied() {
        val call = ToolCall(
            id = "ps_inv_b64",
            name = "execute_command",
            arguments = """{"command":"powershell","args":["-EncodedCommand","!!NOT_VALID_BASE64!!"]}"""
        )
        val decision = policy.evaluate(
            call = call,
            permissions = SecurityPermissions(allowArbitraryCommands = true),
            preset = SecurityPreset.TURBO,
            workspaceRoot = workspaceDir,
        )
        assertEquals(SecurityDecisionKind.DENIED, decision.kind)
        assertTrue(decision.reason.contains("Undecodable or invalid", ignoreCase = true))
    }

    @Test
    fun testShellQuoteSplittingObfuscationBlocked() {
        val call = ToolCall(
            id = "sh_quote_split",
            name = "execute_command",
            arguments = """{"command":"cmd","args":["/c","f\"o\"r\"m\"a\"t","c:"]}"""
        )
        val decision = policy.evaluate(
            call = call,
            permissions = SecurityPermissions(allowArbitraryCommands = true),
            preset = SecurityPreset.TURBO,
            workspaceRoot = workspaceDir,
        )
        assertEquals(SecurityDecisionKind.DENIED, decision.kind)
        assertTrue(decision.reason.contains("forbidden command: format", ignoreCase = true))
    }

    @Test
    fun testShellLaunchersNeverAutoExecuteInTurbo() {
        val launchers = listOf(
            """{"command":"cmd","args":["/c","dir"]}""",
            """{"command":"bash","args":["-c","ls"]}""",
            """{"command":"powershell","args":["-c","dir"]}""",
            """{"command":"pwsh","args":["-Command","ls"]}""",
        )
        for (argsJson in launchers) {
            val call = ToolCall(id = "sh_launcher", name = "execute_command", arguments = argsJson)
            val decision = policy.evaluate(
                call = call,
                permissions = SecurityPermissions(allowArbitraryCommands = true),
                preset = SecurityPreset.TURBO,
                workspaceRoot = workspaceDir,
            )
            assertEquals(SecurityDecisionKind.REQUIRES_APPROVAL, decision.kind, "Launcher '$argsJson' must require approval in Turbo")
            assertTrue(decision.reason.contains("Shell launcher", ignoreCase = true))
        }
    }

    @Test
    fun testChainedCommandDeniedWhenDeleteForbidden() {
        val call = ToolCall(
            id = "chained_del",
            name = "execute_command",
            arguments = """{"command":"sh","args":["-c","echo hi && rm file.txt"]}"""
        )
        val decision = policy.evaluate(
            call = call,
            permissions = SecurityPermissions(allowDeleteFile = false, allowArbitraryCommands = true),
            preset = SecurityPreset.TURBO,
            workspaceRoot = workspaceDir,
        )
        assertEquals(SecurityDecisionKind.DENIED, decision.kind)
        assertTrue(decision.reason.contains("allowDeleteFile=false", ignoreCase = true))
    }
}
