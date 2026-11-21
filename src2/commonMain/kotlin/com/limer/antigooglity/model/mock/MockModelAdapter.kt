package com.limer.antigooglity.model.mock

import com.limer.antigooglity.agent.AgentContext
import com.limer.antigooglity.model.ModelAdapter
import com.limer.antigooglity.model.ModelResponse
import com.limer.antigooglity.tools.ToolCall

class MockModelAdapter(
    initialResponses: List<ModelResponse> = emptyList(),
) : ModelAdapter {

    constructor(vararg responses: ModelResponse) : this(responses.toList())

    private val responseQueue: ArrayDeque<ModelResponse> = ArrayDeque(initialResponses)
    private val _capturedContexts: MutableList<AgentContext> = mutableListOf()

    val capturedContexts: List<AgentContext>
        get() = _capturedContexts.toList()

    val callCount: Int
        get() = _capturedContexts.size

    val lastContext: AgentContext?
        get() = _capturedContexts.lastOrNull()

    fun enqueueResponse(response: ModelResponse) {
        responseQueue.add(response)
    }

    fun enqueueResponses(responses: Iterable<ModelResponse>) {
        responseQueue.addAll(responses)
    }

    override suspend fun generate(context: AgentContext): ModelResponse {
        _capturedContexts.add(context)
        if (responseQueue.isEmpty()) {
            throw IllegalStateException(
                "MockModelAdapter: no more responses configured (invoked $callCount time(s)). " +
                    "Ensure enough responses are enqueued for the agent loop."
            )
        }
        return responseQueue.removeFirst()
    }

    companion object {
        const val DEFAULT_DEMO_TASK = "Создай файл hello.py с содержимым print(\"Hello from Antigooglity!\")"
        const val DEFAULT_FILE_NAME = "hello.py"
        const val DEFAULT_FILE_CONTENT = "print(\"Hello from Antigooglity!\")"
        const val DEFAULT_TOOL_CALL_ID = "call_001"
        const val DEFAULT_FINAL_RESPONSE = "Файл hello.py успешно создан."

        fun escapeJson(value: String): String {
            return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
        }

        fun createDemoAdapter(
            fileName: String = DEFAULT_FILE_NAME,
            fileContent: String = DEFAULT_FILE_CONTENT,
            toolCallId: String = DEFAULT_TOOL_CALL_ID,
            finalResponseText: String = DEFAULT_FINAL_RESPONSE,
        ): MockModelAdapter {
            val argumentsJson = """{"path":"${escapeJson(fileName)}","content":"${escapeJson(fileContent)}"}"""
            val firstResponse = ModelResponse(
                text = "Создаю файл $fileName...",
                toolCalls = listOf(
                    ToolCall(
                        id = toolCallId,
                        name = "write_file",
                        arguments = argumentsJson,
                    )
                ),
            )
            val secondResponse = ModelResponse(
                text = finalResponseText,
                toolCalls = emptyList(),
            )
            return MockModelAdapter(listOf(firstResponse, secondResponse))
        }
    }
}
