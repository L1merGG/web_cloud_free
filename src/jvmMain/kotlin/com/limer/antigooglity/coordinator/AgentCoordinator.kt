package com.limer.antigooglity.coordinator

import com.limer.antigooglity.agent.AgentEvent
import com.limer.antigooglity.git.GitOperationResult
import com.limer.antigooglity.git.GitRepositoryInfo
import com.limer.antigooglity.git.GitService
import com.limer.antigooglity.git.JvmGitService
import com.limer.antigooglity.model.ModelAdapter
import com.limer.antigooglity.model.ModelResponse
import com.limer.antigooglity.model.mock.MockModelAdapter
import com.limer.antigooglity.tools.ToolCall
import com.limer.antigooglity.tools.ToolExecutor
import com.limer.antigooglity.tools.mock.MockToolExecutor
import com.limer.antigooglity.tools.terminal.CommandApprover
import com.limer.antigooglity.tools.terminal.TerminalToolExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Coordinates user interactions, workspace state, security permissions,
 * security presets, git repository integration, inline command approvals,
 * and execution of agent tasks.
 */
class AgentCoordinator(
    initialWorkspacePath: Path,
    initialPermissions: SecurityPermissions? = null,
    initialPreset: SecurityPreset? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val preferencesRepository: SecurityPreferencesRepository = FileSecurityPreferencesRepository(),
    private val gitService: GitService = JvmGitService(),
    private val modelAdapterFactory: (prompt: String) -> ModelAdapter = { prompt -> createDefaultMockAdapter(prompt) },
    private val toolExecutorFactory: (workspacePath: Path, permissions: SecurityPermissions, commandApprover: CommandApprover?) -> ToolExecutor = { _, _, approver ->
        MockToolExecutor(
            commandApprover = { cmdArgs ->
                approver?.requestApproval(TerminalToolExecutor.parseCommand(cmdArgs)) ?: true
            }
        )
    },
) {
    val approver = UiCommandApprover { pending ->
        handleApprovalRequested(pending)
    }

    private val savedPrefs = preferencesRepository.load()

    private val _workspacePath = MutableStateFlow(
        try {
            if (Files.exists(initialWorkspacePath)) initialWorkspacePath.toRealPath() else initialWorkspacePath.toAbsolutePath().normalize()
        } catch (_: Exception) {
            initialWorkspacePath.toAbsolutePath().normalize()
        }
    )
    val workspacePath: StateFlow<Path> = _workspacePath.asStateFlow()

    private val _permissions = MutableStateFlow(initialPermissions ?: savedPrefs.permissions)
    val permissions: StateFlow<SecurityPermissions> = _permissions.asStateFlow()

    private val _preset = MutableStateFlow(initialPreset ?: savedPrefs.preset)
    val preset: StateFlow<SecurityPreset> = _preset.asStateFlow()

    private val _gitRepositoryInfo = MutableStateFlow(GitRepositoryInfo(isGitRepository = false))
    val gitRepositoryInfo: StateFlow<GitRepositoryInfo> = _gitRepositoryInfo.asStateFlow()

    private val _recentWorkspaces = MutableStateFlow(
        (listOf(_workspacePath.value.toString()) + savedPrefs.recentWorkspaces).distinct()
    )
    val recentWorkspaces: StateFlow<List<String>> = _recentWorkspaces.asStateFlow()

    private val initialConversation = Conversation()
    private val _conversations = MutableStateFlow(listOf(initialConversation))
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _activeConversationId = MutableStateFlow(initialConversation.id)
    val activeConversationId: StateFlow<String> = _activeConversationId.asStateFlow()

    private val _executionStatus = MutableStateFlow(ExecutionStatus.IDLE)
    val executionStatus: StateFlow<ExecutionStatus> = _executionStatus.asStateFlow()

    private val _statusText = MutableStateFlow("Готов к работе")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    val isRunning: Boolean
        get() = _executionStatus.value == ExecutionStatus.RUNNING

    private var activeJob: Job? = null
    private var currentAssistantMsgId: String? = null
    private val conversationLock = Any()

    init {
        preferencesRepository.save(
            SecurityPreferences(
                preset = _preset.value,
                permissions = _permissions.value,
                recentWorkspaces = _recentWorkspaces.value,
            )
        )
        refreshGitInfo()
    }

    fun newConversation(): String {
        val newConv = Conversation()
        synchronized(conversationLock) {
            _conversations.value = _conversations.value + newConv
        }
        _activeConversationId.value = newConv.id
        _statusText.value = "Готов к работе"
        return newConv.id
    }

    fun selectConversation(id: String) {
        if (_conversations.value.any { it.id == id }) {
            _activeConversationId.value = id
        }
    }

    fun deleteConversation(id: String) {
        synchronized(conversationLock) {
            if (_conversations.value.size <= 1) return
            _conversations.value = _conversations.value.filter { it.id != id }
            if (_activeConversationId.value == id) {
                _activeConversationId.value = _conversations.value.first().id
            }
        }
    }

    fun changeWorkspace(newPath: Path): Boolean {
        if (isRunning) {
            return false
        }
        if (!Files.exists(newPath) || !Files.isDirectory(newPath)) {
            throw IllegalArgumentException("Указанный путь не существует или не является директорией: $newPath")
        }
        val realPath = newPath.toRealPath()
        _workspacePath.value = realPath
        val updatedRecents = (listOf(realPath.toString()) + _recentWorkspaces.value).distinct().take(10)
        _recentWorkspaces.value = updatedRecents
        preferencesRepository.save(
            SecurityPreferences(
                preset = _preset.value,
                permissions = _permissions.value,
                recentWorkspaces = updatedRecents,
            )
        )
        refreshGitInfo()
        return true
    }

    fun updatePermissions(newPermissions: SecurityPermissions): Boolean {
        if (isRunning) {
            return false
        }
        _permissions.value = newPermissions
        preferencesRepository.save(
            SecurityPreferences(
                preset = _preset.value,
                permissions = newPermissions,
                recentWorkspaces = _recentWorkspaces.value,
            )
        )
        return true
    }

    fun updatePreset(newPreset: SecurityPreset): Boolean {
        if (isRunning) {
            return false
        }
        _preset.value = newPreset
        preferencesRepository.save(
            SecurityPreferences(
                preset = newPreset,
                permissions = _permissions.value,
                recentWorkspaces = _recentWorkspaces.value,
            )
        )
        _statusText.value = "Профиль безопасности: ${newPreset.displayName}"
        return true
    }

    fun refreshGitInfo() {
        scope.launch {
            try {
                val info = gitService.getRepositoryInfo(_workspacePath.value.toString())
                _gitRepositoryInfo.value = info
            } catch (e: Exception) {
                _gitRepositoryInfo.value = GitRepositoryInfo(
                    isGitRepository = false,
                    errorMessage = e.message ?: "Failed to read git repository",
                )
            }
        }
    }

    suspend fun switchGitBranch(branchName: String): GitOperationResult {
        val result = gitService.switchBranch(_workspacePath.value.toString(), branchName)
        if (result is GitOperationResult.Success) {
            refreshGitInfo()
            _statusText.value = "Ветка переключена на '$branchName'"
        } else if (result is GitOperationResult.Failure) {
            _statusText.value = "Ошибка переключения: ${result.errorMessage}"
        }
        return result
    }

    fun startTask(prompt: String): Job {
        val trimmed = prompt.trim()
        require(trimmed.isNotEmpty()) { "Текст задачи не может быть пустым" }
        check(!isRunning) { "Агент уже выполняет другую задачу" }

        val convId = _activeConversationId.value
        val userMsg = UiMessage(role = UiMessageRole.USER, text = trimmed)
        val assistantMsgId = UUID.randomUUID().toString()
        currentAssistantMsgId = assistantMsgId
        val assistantPlaceholder = UiMessage(id = assistantMsgId, role = UiMessageRole.ASSISTANT, text = "")

        updateConversation(convId) { conv ->
            val updatedMessages = conv.messages + userMsg + assistantPlaceholder
            val updatedTitle = if (conv.title.startsWith("Новый диалог") || conv.title.startsWith("Диалог ")) {
                trimmed.take(35)
            } else {
                conv.title
            }
            conv.copy(title = updatedTitle, messages = updatedMessages)
        }

        _executionStatus.value = ExecutionStatus.RUNNING
        _statusText.value = "Задача запущена..."

        val modelAdapter = modelAdapterFactory(trimmed)
        val baseToolExecutor = toolExecutorFactory(_workspacePath.value, _permissions.value, approver)
        val toolExecutor = if (baseToolExecutor is PolicyEnforcingToolExecutor) {
            PolicyEnforcingToolExecutor(
                delegate = baseToolExecutor.delegate,
                workspacePath = { _workspacePath.value },
                permissions = { _permissions.value },
                preset = { _preset.value },
                commandApprover = approver,
            )
        } else {
            PolicyEnforcingToolExecutor(
                delegate = baseToolExecutor,
                workspacePath = { _workspacePath.value },
                permissions = { _permissions.value },
                preset = { _preset.value },
                commandApprover = approver,
            )
        }

        val agentInstance = AgentFactory.createAgent(
            workspacePath = _workspacePath.value,
            permissions = _permissions.value,
            commandApprover = approver,
            modelAdapter = modelAdapter,
            toolExecutor = toolExecutor,
        )

        val job = scope.launch {
            try {
                val finalResponse = agentInstance.agent.run(trimmed) { event ->
                    handleAgentEvent(convId, assistantMsgId, event)
                }
                updateConversation(convId) { conv ->
                    val msgs = conv.messages.map { msg ->
                        if (msg.id == assistantMsgId) {
                            msg.copy(text = if (msg.text.isBlank()) finalResponse else msg.text)
                        } else msg
                    }
                    conv.copy(messages = msgs)
                }
                _statusText.value = "Задача успешно выполнена"
                _executionStatus.value = ExecutionStatus.COMPLETED
            } catch (e: CancellationException) {
                cancelPendingApprovals()
                withContext(NonCancellable) {
                    updateConversation(convId) { conv ->
                        val msgs = conv.messages.map { msg ->
                            if (msg.id == assistantMsgId) {
                                val updatedTools = msg.toolExecutions.map { tool ->
                                    if (tool.status == ToolStatus.RUNNING) tool.copy(status = ToolStatus.CANCELLED) else tool
                                }
                                msg.copy(
                                    text = msg.text.ifBlank { "⏹ Задача остановлена пользователем." },
                                    toolExecutions = updatedTools,
                                )
                            } else msg
                        }
                        conv.copy(messages = msgs)
                    }
                    _statusText.value = "Задача отменена"
                    _executionStatus.value = ExecutionStatus.CANCELLED
                }
            } catch (e: Exception) {
                cancelPendingApprovals()
                val errText = e.message ?: e.toString()
                withContext(NonCancellable) {
                    updateConversation(convId) { conv ->
                        val msgs = conv.messages.map { msg ->
                            if (msg.id == assistantMsgId) {
                                msg.copy(text = msg.text.ifBlank { "❌ Ошибка выполнения: $errText" })
                            } else msg
                        }
                        conv.copy(messages = msgs)
                    }
                    _statusText.value = "Ошибка: $errText"
                    _executionStatus.value = ExecutionStatus.FAILED
                }
            } finally {
                activeJob = null
                currentAssistantMsgId = null
                _executionStatus.value = ExecutionStatus.IDLE
            }
        }

        activeJob = job
        return job
    }

    fun cancelTask() {
        cancelPendingApprovals()
        val job = activeJob
        if (job != null && job.isActive) {
            job.cancel()
        }
        _executionStatus.value = ExecutionStatus.IDLE
        _statusText.value = "Задача отменена"
    }

    fun approveCommand(id: String? = null): Boolean {
        val targetId = id ?: approver.currentPending.value?.id ?: return false
        val success = approver.approve(targetId)
        if (success) {
            updateApprovalDecision(targetId, ApprovalDecision.APPROVED)
            _statusText.value = "Команда разрешена пользователем"
        }
        return success
    }

    fun rejectCommand(id: String? = null): Boolean {
        val targetId = id ?: approver.currentPending.value?.id ?: return false
        val success = approver.reject(targetId)
        if (success) {
            updateApprovalDecision(targetId, ApprovalDecision.REJECTED)
            _statusText.value = "Команда отклонена пользователем"
        }
        return success
    }

    private fun handleApprovalRequested(pending: PendingApproval) {
        val convId = _activeConversationId.value
        val msgId = currentAssistantMsgId
        if (msgId != null) {
            updateAssistantMessage(convId, msgId) { msg ->
                val newRequest = UiApprovalRequest(
                    id = pending.id,
                    command = pending.command,
                    decision = ApprovalDecision.PENDING,
                )
                msg.copy(approvalRequests = msg.approvalRequests + newRequest)
            }
        }
        _statusText.value = "Требуется подтверждение: ${pending.command.command}"
    }

    private fun updateApprovalDecision(requestId: String, decision: ApprovalDecision) {
        val convId = _activeConversationId.value
        updateConversation(convId) { conv ->
            val msgs = conv.messages.map { msg ->
                val updatedApprovals = msg.approvalRequests.map { req ->
                    if (req.id == requestId && req.decision == ApprovalDecision.PENDING) {
                        req.copy(decision = decision)
                    } else req
                }
                msg.copy(approvalRequests = updatedApprovals)
            }
            conv.copy(messages = msgs)
        }
    }

    private fun cancelPendingApprovals() {
        val pending = approver.currentPending.value
        approver.cancel()
        if (pending != null) {
            updateApprovalDecision(pending.id, ApprovalDecision.CANCELLED)
        }
    }

    private fun handleAgentEvent(convId: String, assistantMsgId: String, event: AgentEvent) {
        when (event) {
            is AgentEvent.Started -> {
                _statusText.value = "Агент приступил к работе..."
            }
            is AgentEvent.Thinking -> {
                _statusText.value = "Агент думает (итерация ${event.iteration})..."
            }
            is AgentEvent.ToolRequested -> {
                _statusText.value = "Вызов инструмента '${event.call.name}'..."
                updateAssistantMessage(convId, assistantMsgId) { msg ->
                    val newTool = UiToolExecution(
                        toolCallId = event.call.id,
                        toolName = event.call.name,
                        arguments = event.call.arguments,
                        status = ToolStatus.RUNNING,
                    )
                    msg.copy(
                        toolExecutions = msg.toolExecutions + newTool,
                        events = msg.events + event,
                    )
                }
            }
            is AgentEvent.ToolCompleted -> {
                _statusText.value = "Инструмент '${event.result.toolCallId}' завершён."
                updateAssistantMessage(convId, assistantMsgId) { msg ->
                    val updatedTools = msg.toolExecutions.map { tool ->
                        if (tool.toolCallId == event.result.toolCallId) {
                            tool.copy(
                                status = if (event.result.success) ToolStatus.SUCCESS else ToolStatus.FAILED,
                                result = event.result,
                            )
                        } else tool
                    }
                    msg.copy(
                        toolExecutions = updatedTools,
                        events = msg.events + event,
                    )
                }
            }
            is AgentEvent.Completed -> {
                _statusText.value = "Задача завершена."
                updateAssistantMessage(convId, assistantMsgId) { msg ->
                    msg.copy(
                        text = event.response,
                        events = msg.events + event,
                    )
                }
            }
            is AgentEvent.Failed -> {
                _statusText.value = "Ошибка: ${event.message}"
                updateAssistantMessage(convId, assistantMsgId) { msg ->
                    msg.copy(
                        text = "❌ Ошибка: ${event.message}",
                        events = msg.events + event,
                    )
                }
            }
        }
    }

    private fun updateAssistantMessage(
        convId: String,
        assistantMsgId: String,
        transform: (UiMessage) -> UiMessage,
    ) {
        updateConversation(convId) { conv ->
            val msgs = conv.messages.map { msg ->
                if (msg.id == assistantMsgId) transform(msg) else msg
            }
            conv.copy(messages = msgs)
        }
    }

    private fun updateConversation(convId: String, transform: (Conversation) -> Conversation) {
        synchronized(conversationLock) {
            _conversations.value = _conversations.value.map { conv ->
                if (conv.id == convId) transform(conv) else conv
            }
        }
    }

    companion object {
        fun createDefaultMockAdapter(prompt: String): ModelAdapter {
            val lower = prompt.lowercase()
            return when {
                lower.contains("создай") || lower.contains("hello.py") || lower.contains("write") -> {
                    val toolCall = ToolCall(
                        id = "call_write",
                        name = "write_file",
                        arguments = """{"path":"hello.py","content":"print(\"Hello from Antigooglity!\")"}""",
                    )
                    MockModelAdapter(
                        listOf(
                            ModelResponse(text = "Создаю файл hello.py в рабочей директории...", toolCalls = listOf(toolCall)),
                            ModelResponse(text = "Файл hello.py успешно создан в рабочей директории.", toolCalls = emptyList()),
                        )
                    )
                }
                lower.contains("прочитай") || lower.contains("read") -> {
                    val toolCall = ToolCall(
                        id = "call_read",
                        name = "read_file",
                        arguments = """{"path":"hello.py"}""",
                    )
                    MockModelAdapter(
                        listOf(
                            ModelResponse(text = "Читаю файл hello.py...", toolCalls = listOf(toolCall)),
                            ModelResponse(text = "Содержимое файла получено.", toolCalls = emptyList()),
                        )
                    )
                }
                lower.contains("удали") || lower.contains("rm") || lower.contains("опасн") || lower.contains("danger") -> {
                    val toolCall = ToolCall(
                        id = "call_rm",
                        name = "execute_command",
                        arguments = """{"command":"rm","args":["-rf","build_artifacts"]}""",
                    )
                    MockModelAdapter(
                        listOf(
                            ModelResponse(text = "Запрашиваю выполнение опасной команды...", toolCalls = listOf(toolCall)),
                            ModelResponse(text = "Команда успешно выполнена.", toolCalls = emptyList()),
                        )
                    )
                }
                else -> {
                    MockModelAdapter(
                        ModelResponse(
                            text = "Ответ модели на запрос: '$prompt'. Я готов помочь с кодом и файлами.",
                            toolCalls = emptyList(),
                        )
                    )
                }
            }
        }
    }
}
