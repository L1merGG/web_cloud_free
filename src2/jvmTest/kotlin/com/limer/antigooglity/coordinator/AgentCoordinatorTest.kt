package com.limer.antigooglity.coordinator

import com.limer.antigooglity.git.GitOperationResult
import com.limer.antigooglity.git.GitService
import com.limer.antigooglity.git.JvmGitService
import com.limer.antigooglity.model.ModelResponse
import com.limer.antigooglity.model.mock.MockModelAdapter
import com.limer.antigooglity.tools.ToolCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

class AgentCoordinatorTest {

    private lateinit var tempDir: Path
    private lateinit var otherDir: Path

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("antigooglity-coord-test-").toRealPath()
        otherDir = Files.createTempDirectory("antigooglity-coord-other-").toRealPath()
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
        otherDir.toFile().deleteRecursively()
    }

    private fun createCoordinatorWithRealTools(
        workspacePath: Path,
        permissions: SecurityPermissions = SecurityPermissions(),
        modelAdapter: MockModelAdapter,
        preset: SecurityPreset = SecurityPreset.DEFAULT,
    ): AgentCoordinator {
        return AgentCoordinator(
            initialWorkspacePath = workspacePath,
            initialPermissions = permissions,
            initialPreset = preset,
            scope = CoroutineScope(Dispatchers.Default),
            modelAdapterFactory = { modelAdapter },
            toolExecutorFactory = { ws, perm, app -> AgentFactory.createPolicyEnforcingToolExecutor(ws, perm, preset, app) },
        )
    }

    // 1. Управление состоянием выполнения
    @Test
    fun testExecutionStateTransitionsAndCompletion() = runBlocking {
        val mockAdapter = MockModelAdapter(
            ModelResponse(text = "Привет от модели!", toolCalls = emptyList())
        )
        val coordinator = AgentCoordinator(
            initialWorkspacePath = tempDir,
            scope = CoroutineScope(Dispatchers.Default),
            modelAdapterFactory = { mockAdapter },
        )

        assertEquals(ExecutionStatus.IDLE, coordinator.executionStatus.value)

        val job = coordinator.startTask("Скажи привет")
        assertTrue(coordinator.isRunning)

        job.join()

        assertEquals(ExecutionStatus.IDLE, coordinator.executionStatus.value)
        val activeConv = coordinator.conversations.value.first { it.id == coordinator.activeConversationId.value }
        assertEquals(2, activeConv.messages.size)
        assertEquals(UiMessageRole.USER, activeConv.messages[0].role)
        assertEquals("Скажи привет", activeConv.messages[0].text)
        assertEquals(UiMessageRole.ASSISTANT, activeConv.messages[1].role)
        assertEquals("Привет от модели!", activeConv.messages[1].text)
    }

    // 2. Обработка событий AgentEvent
    @Test
    fun testAgentEventHandling() = runBlocking {
        val toolCall = ToolCall(
            id = "c_write_file",
            name = "write_file",
            arguments = """{"path": "coord_test.txt", "content": "Coordinator Works"}""",
        )
        val mockAdapter = MockModelAdapter(
            ModelResponse(text = "Создаю файл...", toolCalls = listOf(toolCall)),
            ModelResponse(text = "Готово!", toolCalls = emptyList()),
        )

        val coordinator = createCoordinatorWithRealTools(
            workspacePath = tempDir,
            modelAdapter = mockAdapter,
        )

        val job = coordinator.startTask("Создай файл")
        job.join()

        val activeConv = coordinator.conversations.value.first { it.id == coordinator.activeConversationId.value }
        val assistantMsg = activeConv.messages.first { it.role == UiMessageRole.ASSISTANT }
        assertEquals("Готово!", assistantMsg.text)
        assertEquals(1, assistantMsg.toolExecutions.size)
        val toolExec = assistantMsg.toolExecutions.first()
        assertEquals("c_write_file", toolExec.toolCallId)
        assertEquals("write_file", toolExec.toolName)
        assertEquals(ToolStatus.SUCCESS, toolExec.status)
        val result = toolExec.result
        assertNotNull(result)
        assertTrue(result.success)
        assertTrue(Files.exists(tempDir.resolve("coord_test.txt")))
    }

    // 3. Отмена задачи
    @Test
    fun testTaskCancellation() = runBlocking {
        val sleepSource = """
            public class SleepAppCoord {
                public static void main(String[] args) throws Exception {
                    Thread.sleep(10000);
                }
            }
        """.trimIndent()
        Files.writeString(tempDir.resolve("SleepAppCoord.java"), sleepSource)

        val toolCall = ToolCall(
            id = "c_sleep",
            name = "execute_command",
            arguments = """{"command": "java", "args": ["SleepAppCoord.java"]}""",
        )
        val mockAdapter = MockModelAdapter(
            ModelResponse(text = "Запускаю долгий процесс...", toolCalls = listOf(toolCall)),
            ModelResponse(text = "Процесс завершён", toolCalls = emptyList()),
        )

        val coordinator = createCoordinatorWithRealTools(
            workspacePath = tempDir,
            modelAdapter = mockAdapter,
        )

        val job = coordinator.startTask("Запусти долгий процесс")
        for (i in 0 until 50) {
            if (coordinator.isRunning) break
            delay(50)
        }
        assertTrue(coordinator.isRunning)

        coordinator.cancelTask()
        job.join()

        assertFalse(coordinator.isRunning)
        val activeConv = coordinator.conversations.value.first { it.id == coordinator.activeConversationId.value }
        val assistantMsg = activeConv.messages.first { it.role == UiMessageRole.ASSISTANT }
        assertTrue(assistantMsg.text.contains("остановлена") || assistantMsg.text.contains("Задача"))
    }

    // 4. Подтверждение и отказ для опасных команд (Inline Approval)
    @Test
    fun testCommandApprovalAndRejection() = runBlocking {
        // Сценарий А: Отказ в выполнении опасной команды
        val toolCallReject = ToolCall(
            id = "c_rm_reject",
            name = "execute_command",
            arguments = """{"command": "rm", "args": ["-rf", "foo"]}""",
        )
        val mockAdapterReject = MockModelAdapter(
            ModelResponse(text = "Удаляю...", toolCalls = listOf(toolCallReject)),
            ModelResponse(text = "Завершено", toolCalls = emptyList()),
        )

        val coordinatorReject = createCoordinatorWithRealTools(
            workspacePath = tempDir,
            permissions = SecurityPermissions(
                allowExecuteCommand = true,
                allowDeleteFile = true, // allowDeleteFile=true allows dangerous prompt instead of hard deny
                requireApprovalForDangerous = true,
            ),
            modelAdapter = mockAdapterReject,
        )

        val jobReject = coordinatorReject.startTask("Опасная команда")

        // Ожидаем появления запроса подтверждения
        for (i in 0 until 50) {
            if (coordinatorReject.approver.currentPending.value != null) break
            delay(100)
        }
        val pending = coordinatorReject.approver.currentPending.value
        assertNotNull(pending, "Approval request should be pending")
        assertEquals("rm", pending.command.command)

        // Отклоняем команду
        val rejectedFirst = coordinatorReject.rejectCommand(pending.id)
        assertTrue(rejectedFirst, "Initial rejectCommand should return true")

        // Повторное решение не должно приниматься
        val rejectedSecond = coordinatorReject.rejectCommand(pending.id)
        assertFalse(rejectedSecond, "Second rejectCommand should return false")
        val approvedAfterReject = coordinatorReject.approveCommand(pending.id)
        assertFalse(approvedAfterReject, "approveCommand after reject should return false")

        jobReject.join()

        val convReject = coordinatorReject.conversations.value.first { it.id == coordinatorReject.activeConversationId.value }
        val assistantMsgReject = convReject.messages.first { it.role == UiMessageRole.ASSISTANT }
        val toolExecReject = assistantMsgReject.toolExecutions.firstOrNull()
        assertNotNull(toolExecReject)
        assertEquals(ToolStatus.FAILED, toolExecReject.status)
        val rejectResult = toolExecReject.result
        assertNotNull(rejectResult)
        assertTrue(rejectResult.content.contains("rejected by user") || rejectResult.content.contains("Security violation"))

        // Проверяем карточку в сообщении
        assertEquals(1, assistantMsgReject.approvalRequests.size)
        val cardReject = assistantMsgReject.approvalRequests.first()
        assertEquals(pending.id, cardReject.id)
        assertEquals(ApprovalDecision.REJECTED, cardReject.decision)

        // Сценарий Б: Одобрение выполнения команды
        val safeJavaToolCall = ToolCall(
            id = "c_rm_approve",
            name = "execute_command",
            arguments = """{"command": "rm", "args": ["--help"]}""",
        )
        val mockAdapterApprove = MockModelAdapter(
            ModelResponse(text = "Выполняю rm...", toolCalls = listOf(safeJavaToolCall)),
            ModelResponse(text = "Выполнено", toolCalls = emptyList()),
        )
        val coordinatorApprove = createCoordinatorWithRealTools(
            workspacePath = tempDir,
            permissions = SecurityPermissions(
                allowExecuteCommand = true,
                allowDeleteFile = true,
                requireApprovalForDangerous = true,
            ),
            modelAdapter = mockAdapterApprove,
        )

        val jobApprove = coordinatorApprove.startTask("Опасная команда одобрение")
        for (i in 0 until 50) {
            if (coordinatorApprove.approver.currentPending.value != null) break
            delay(100)
        }
        val pendingApprove = coordinatorApprove.approver.currentPending.value
        assertNotNull(pendingApprove)

        val approvedFirst = coordinatorApprove.approveCommand(pendingApprove.id)
        assertTrue(approvedFirst, "Initial approveCommand should return true")

        // Повторное решение не должно приниматься
        val approvedSecond = coordinatorApprove.approveCommand(pendingApprove.id)
        assertFalse(approvedSecond, "Second approveCommand should return false")
        val rejectedAfterApprove = coordinatorApprove.rejectCommand(pendingApprove.id)
        assertFalse(rejectedAfterApprove, "rejectCommand after approve should return false")

        jobApprove.join()

        val convApprove = coordinatorApprove.conversations.value.first { it.id == coordinatorApprove.activeConversationId.value }
        val assistantMsgApprove = convApprove.messages.first { it.role == UiMessageRole.ASSISTANT }
        val toolExecApprove = assistantMsgApprove.toolExecutions.firstOrNull()
        assertNotNull(toolExecApprove)
        val approveResult = toolExecApprove.result
        assertNotNull(approveResult)
        assertFalse(approveResult.content.contains("rejected by user"))

        // Проверяем сохранность карточки подтверждения со статусом APPROVED
        assertEquals(1, assistantMsgApprove.approvalRequests.size)
        val cardApprove = assistantMsgApprove.approvalRequests.first()
        assertEquals(pendingApprove.id, cardApprove.id)
        assertEquals(ApprovalDecision.APPROVED, cardApprove.decision)
    }

    // 5. Отмена задачи во время ожидания подтверждения (Inline Approval -> CANCELLED)
    @Test
    fun testInlineApprovalCancelledOnTaskCancel() = runBlocking {
        val toolCall = ToolCall(
            id = "c_rm_cancel",
            name = "execute_command",
            arguments = """{"command": "rm", "args": ["-rf", "bar"]}""",
        )
        val mockAdapter = MockModelAdapter(
            ModelResponse(text = "Запрос удаления...", toolCalls = listOf(toolCall)),
            ModelResponse(text = "Конец", toolCalls = emptyList()),
        )

        val coordinator = createCoordinatorWithRealTools(
            workspacePath = tempDir,
            permissions = SecurityPermissions(
                allowExecuteCommand = true,
                allowDeleteFile = true,
                requireApprovalForDangerous = true,
            ),
            modelAdapter = mockAdapter,
        )

        val job = coordinator.startTask("Удали bar")

        for (i in 0 until 50) {
            val conv = coordinator.conversations.value.first { it.id == coordinator.activeConversationId.value }
            val asst = conv.messages.firstOrNull { it.role == UiMessageRole.ASSISTANT }
            if (asst?.approvalRequests?.any { it.decision == ApprovalDecision.PENDING } == true) {
                break
            }
            delay(100)
        }

        // Проверяем, что карточка создана в PENDING статусе
        val activeConv = coordinator.conversations.value.first { it.id == coordinator.activeConversationId.value }
        val assistantMsg = activeConv.messages.first { it.role == UiMessageRole.ASSISTANT }
        assertEquals(1, assistantMsg.approvalRequests.size)
        val pendingCard = assistantMsg.approvalRequests.first()
        assertEquals(ApprovalDecision.PENDING, pendingCard.decision)

        // Отменяем задачу
        coordinator.cancelTask()
        job.join()

        // Проверяем, что карточка перешла в CANCELLED и осталась в истории
        val convAfterCancel = coordinator.conversations.value.first { it.id == coordinator.activeConversationId.value }
        val assistantMsgAfterCancel = convAfterCancel.messages.first { it.role == UiMessageRole.ASSISTANT }
        assertEquals(1, assistantMsgAfterCancel.approvalRequests.size)
        val cancelledCard = assistantMsgAfterCancel.approvalRequests.first()
        assertEquals(ApprovalDecision.CANCELLED, cancelledCard.decision)
        assertEquals(pendingCard.id, cancelledCard.id)
    }

    // 6. Изменение Workspace при активной задаче запрещено
    @Test
    fun testWorkspaceChangeRejectedWhileRunning() = runBlocking {
        val sleepSource = """
            public class SleepAppWs {
                public static void main(String[] args) throws Exception {
                    Thread.sleep(5000);
                }
            }
        """.trimIndent()
        Files.writeString(tempDir.resolve("SleepAppWs.java"), sleepSource)

        val toolCall = ToolCall(
            id = "c_sleep_ws",
            name = "execute_command",
            arguments = """{"command": "java", "args": ["SleepAppWs.java"]}""",
        )
        val mockAdapter = MockModelAdapter(
            ModelResponse(text = "Запускаю...", toolCalls = listOf(toolCall)),
            ModelResponse(text = "Готово", toolCalls = emptyList()),
        )

        val coordinator = createCoordinatorWithRealTools(
            workspacePath = tempDir,
            modelAdapter = mockAdapter,
        )

        val job = coordinator.startTask("Долгая задача")
        for (i in 0 until 50) {
            if (coordinator.isRunning) break
            delay(50)
        }
        assertTrue(coordinator.isRunning)

        // Попытка сменить workspace во время выполнения должна быть отклонена
        val changedWhileRunning = coordinator.changeWorkspace(otherDir)
        assertFalse(changedWhileRunning, "Workspace change must be rejected while task is running")
        assertEquals(tempDir, coordinator.workspacePath.value)

        // Отменяем задачу
        coordinator.cancelTask()
        job.join()
        assertFalse(coordinator.isRunning)

        // Теперь смена workspace должна успешно пройти
        val changedAfterStop = coordinator.changeWorkspace(otherDir)
        assertTrue(changedAfterStop, "Workspace change should succeed when idle")
        assertEquals(otherDir, coordinator.workspacePath.value)
    }

    // 7. Security Preset Switching & State Flow
    @Test
    fun testSecurityPresetSwitchingAndPersistence() {
        val repo = FileSecurityPreferencesRepository(tempDir.resolve("test_settings.json"))
        val coordinator = AgentCoordinator(
            initialWorkspacePath = tempDir,
            preferencesRepository = repo,
        )

        assertEquals(SecurityPreset.DEFAULT, coordinator.preset.value)

        assertTrue(coordinator.updatePreset(SecurityPreset.TURBO))
        assertEquals(SecurityPreset.TURBO, coordinator.preset.value)

        // New coordinator instance using same repo restores saved preset
        val coordinator2 = AgentCoordinator(
            initialWorkspacePath = tempDir,
            preferencesRepository = repo,
        )
        assertEquals(SecurityPreset.TURBO, coordinator2.preset.value)
    }

    // 8. Request Review preset in AgentCoordinator requires approval for write
    @Test
    fun testCoordinatorRequestReviewRequiresApproval() = runBlocking {
        val toolCall = ToolCall(
            id = "rr_write",
            name = "write_file",
            arguments = """{"path":"rr_test.txt","content":"review me"}""",
        )
        val mockAdapter = MockModelAdapter(
            ModelResponse(text = "Пишу файл...", toolCalls = listOf(toolCall)),
            ModelResponse(text = "Записано!", toolCalls = emptyList()),
        )

        val coordinator = createCoordinatorWithRealTools(
            workspacePath = tempDir,
            preset = SecurityPreset.REQUEST_REVIEW,
            modelAdapter = mockAdapter,
        )

        val job = coordinator.startTask("Создай файл в Request Review")

        // Wait for approval request
        for (i in 0 until 50) {
            if (coordinator.approver.currentPending.value != null) break
            delay(100)
        }

        val pending = coordinator.approver.currentPending.value
        assertNotNull(pending, "Request Review preset must require approval for write_file")
        assertEquals("write_file", pending.command.command)

        // Approve it
        assertTrue(coordinator.approveCommand(pending.id))
        job.join()

        assertTrue(Files.exists(tempDir.resolve("rr_test.txt")))
    }

    // 9. Turbo preset auto-approves eligible operations without approval
    @Test
    fun testCoordinatorTurboAutoApproves() = runBlocking {
        val toolCall = ToolCall(
            id = "tb_write",
            name = "write_file",
            arguments = """{"path":"tb_test.txt","content":"turbo speed"}""",
        )
        val mockAdapter = MockModelAdapter(
            ModelResponse(text = "Создаю турбо файл...", toolCalls = listOf(toolCall)),
            ModelResponse(text = "Готово!", toolCalls = emptyList()),
        )

        val coordinator = createCoordinatorWithRealTools(
            workspacePath = tempDir,
            preset = SecurityPreset.TURBO,
            modelAdapter = mockAdapter,
        )

        val job = coordinator.startTask("Создай турбо файл")
        job.join()

        // Verify no approval was ever requested
        assertFalse(coordinator.isRunning)
        assertTrue(Files.exists(tempDir.resolve("tb_test.txt")))
        val activeConv = coordinator.conversations.value.first { it.id == coordinator.activeConversationId.value }
        val assistantMsg = activeConv.messages.first { it.role == UiMessageRole.ASSISTANT }
        assertTrue(assistantMsg.approvalRequests.isEmpty(), "Turbo mode should not produce approval requests for eligible actions")
    }

    // 10. Git integration via AgentCoordinator
    @Test
    fun testCoordinatorGitInfoAndBranchSwitching() = runBlocking {
        val gitService = JvmGitService()
        if (!gitService.isGitAvailable()) {
            println("Skipping coordinator git test: git CLI not available.")
            return@runBlocking
        }

        // Init local repo
        val pb1 = ProcessBuilder("git", "init").directory(tempDir.toFile())
        pb1.start().waitFor()
        ProcessBuilder("git", "config", "user.name", "Test").directory(tempDir.toFile()).start().waitFor()
        ProcessBuilder("git", "config", "user.email", "test@coord.local").directory(tempDir.toFile()).start().waitFor()
        Files.writeString(tempDir.resolve("f.txt"), "hello")
        ProcessBuilder("git", "add", "f.txt").directory(tempDir.toFile()).start().waitFor()
        ProcessBuilder("git", "commit", "-m", "init").directory(tempDir.toFile()).start().waitFor()
        ProcessBuilder("git", "branch", "feature-coord").directory(tempDir.toFile()).start().waitFor()

        val coordinator = AgentCoordinator(
            initialWorkspacePath = tempDir,
            gitService = gitService,
        )

        // Wait for git info to populate
        for (i in 0 until 50) {
            if (coordinator.gitRepositoryInfo.value.isGitRepository) break
            delay(100)
        }

        assertTrue(coordinator.gitRepositoryInfo.value.isGitRepository)
        assertTrue(coordinator.gitRepositoryInfo.value.branches.any { it.name == "feature-coord" })

        // Switch branch
        val switchRes = coordinator.switchGitBranch("feature-coord")
        assertTrue(switchRes is GitOperationResult.Success)

        for (i in 0 until 50) {
            if (coordinator.gitRepositoryInfo.value.currentBranch == "feature-coord") break
            delay(100)
        }

        assertEquals("feature-coord", coordinator.gitRepositoryInfo.value.currentBranch)
    }

    // 11. Dynamic Preset change applies immediately to subsequent tool calls
    @Test
    fun testDynamicPresetChangeAppliesImmediatelyToSubsequentTasks() = runBlocking {
        val writeCall1 = ToolCall(id = "dyn_1", name = "write_file", arguments = """{"path":"dyn1.txt","content":"file 1"}""")
        val writeCall2 = ToolCall(id = "dyn_2", name = "write_file", arguments = """{"path":"dyn2.txt","content":"file 2"}""")

        val mockAdapter1 = MockModelAdapter(
            ModelResponse(text = "Writing file 1...", toolCalls = listOf(writeCall1)),
            ModelResponse(text = "Done 1", toolCalls = emptyList()),
        )
        val mockAdapter2 = MockModelAdapter(
            ModelResponse(text = "Writing file 2...", toolCalls = listOf(writeCall2)),
            ModelResponse(text = "Done 2", toolCalls = emptyList()),
        )

        var currentAdapter: MockModelAdapter = mockAdapter1

        val coordinator = AgentCoordinator(
            initialWorkspacePath = tempDir,
            initialPreset = SecurityPreset.DEFAULT,
            scope = CoroutineScope(Dispatchers.Default),
            modelAdapterFactory = { currentAdapter },
            toolExecutorFactory = { ws, perm, app -> AgentFactory.createPolicyEnforcingToolExecutor(ws, perm, SecurityPreset.DEFAULT, app) },
        )

        // Run 1: In DEFAULT preset, writing file does not require approval
        val job1 = coordinator.startTask("Write 1")
        job1.join()
        assertTrue(Files.exists(tempDir.resolve("dyn1.txt")))

        // Dynamically update preset to REQUEST_REVIEW
        assertTrue(coordinator.updatePreset(SecurityPreset.REQUEST_REVIEW))
        assertEquals(SecurityPreset.REQUEST_REVIEW, coordinator.preset.value)

        // Run 2: In REQUEST_REVIEW, writing file MUST require approval
        currentAdapter = mockAdapter2
        val job2 = coordinator.startTask("Write 2")

        for (i in 0 until 50) {
            if (coordinator.approver.currentPending.value != null) break
            delay(100)
        }
        val pending = coordinator.approver.currentPending.value
        assertNotNull(pending, "In REQUEST_REVIEW preset, writing file must require user approval")
        assertEquals("write_file", pending.command.command)

        assertTrue(coordinator.approveCommand(pending.id))
        job2.join()
        assertTrue(Files.exists(tempDir.resolve("dyn2.txt")))
    }

    // 12. Approval binding prevents argument tampering and ID reuse
    @Test
    fun testApprovalBindingPreventsIdReuseAndMismatchedApproval() = runBlocking {
        val toolCall = ToolCall(
            id = "bind_call",
            name = "execute_command",
            arguments = """{"command":"rm","args":["--help"]}""",
        )
        val mockAdapter = MockModelAdapter(
            ModelResponse(text = "Executing...", toolCalls = listOf(toolCall)),
            ModelResponse(text = "Done", toolCalls = emptyList()),
        )

        val coordinator = createCoordinatorWithRealTools(
            workspacePath = tempDir,
            permissions = SecurityPermissions(allowExecuteCommand = true, allowDeleteFile = true, requireApprovalForDangerous = true),
            modelAdapter = mockAdapter,
        )

        val job = coordinator.startTask("Run dangerous command")
        for (i in 0 until 50) {
            if (coordinator.approver.currentPending.value != null) break
            delay(100)
        }
        val pending = coordinator.approver.currentPending.value
        assertNotNull(pending)
        assertEquals("rm", pending.command.command)
        assertEquals(listOf("--help"), pending.command.args)

        // Attempting to approve with incorrect ID fails
        val wrongApproved = coordinator.approveCommand("fake-uuid-not-matching")
        assertFalse(wrongApproved, "Approving with mismatched ID must fail")
        assertNotNull(coordinator.approver.currentPending.value, "Pending approval must remain pending after invalid ID attempt")

        // Approving with correct ID succeeds
        val correctApproved = coordinator.approveCommand(pending.id)
        assertTrue(correctApproved, "Approving with matching ID must succeed")

        // Reusing the same ID fails
        val reusedApproved = coordinator.approveCommand(pending.id)
        assertFalse(reusedApproved, "Reusing approval ID must fail")

        job.join()
    }
}
