package com.limer.antigooglity.agent

import com.limer.antigooglity.model.ModelResponse
import com.limer.antigooglity.model.mock.MockModelAdapter
import com.limer.antigooglity.tools.ToolCall
import com.limer.antigooglity.tools.mock.MockToolExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentCoreTest {

    // 1. MockModelAdapter возвращает ответы в заданном порядке
    @Test
    fun testMockModelAdapterReturnsResponsesInGivenOrder() = runBlocking {
        val r1 = ModelResponse(text = "Response 1")
        val r2 = ModelResponse(text = "Response 2")
        val adapter = MockModelAdapter(r1, r2)
        val context = AgentContext("test task")

        assertEquals("Response 1", adapter.generate(context).text)
        assertEquals("Response 2", adapter.generate(context).text)
        assertEquals(2, adapter.callCount)

        val exception = assertFailsWith<IllegalStateException> {
            adapter.generate(context)
        }
        assertTrue(exception.message?.contains("no more responses") == true)
    }

    // 2. Agent корректно обрабатывает ответ с одним ToolCall
    // 3. Agent получает результат инструмента и передаёт обновлённую историю модели при следующем вызове
    // 4. MockToolExecutor сохраняет файл с правильным именем и содержимым
    // 5. Agent возвращает финальный ответ модели
    // 6. История содержит USER, ASSISTANT с ToolCall, TOOL и финальный ASSISTANT
    @Test
    fun testStandardDemoScenarioWithSingleToolCall() = runBlocking {
        val model = MockModelAdapter.createDemoAdapter(
            fileName = "hello.py",
            fileContent = "print(\"Hello from Antigooglity!\")",
            toolCallId = "call_001",
            finalResponseText = "Файл hello.py успешно создан.",
        )
        val tools = MockToolExecutor()
        val agent = Agent(model, tools, AgentConfig(systemPrompt = "System instructions"))

        val events = mutableListOf<AgentEvent>()
        val result = agent.run(MockModelAdapter.DEFAULT_DEMO_TASK) { event ->
            events.add(event)
        }

        // 4. MockToolExecutor сохраняет файл с правильным именем и содержимым
        assertTrue(tools.hasFile("hello.py"))
        assertEquals("print(\"Hello from Antigooglity!\")", tools.getFile("hello.py"))

        // 5. Agent возвращает финальный ответ модели
        assertEquals("Файл hello.py успешно создан.", result)

        // 2 & 3. Проверка вызовов модели и переданной истории
        assertEquals(2, model.callCount)
        val finalContext = model.lastContext!!
        val history = finalContext.getHistory()

        // 6. История содержит SYSTEM, USER, ASSISTANT с ToolCall, TOOL и финальный ASSISTANT
        assertEquals(5, history.size)
        assertEquals(MessageRole.SYSTEM, history[0].role)
        assertEquals("System instructions", history[0].content)

        assertEquals(MessageRole.USER, history[1].role)
        assertEquals(MockModelAdapter.DEFAULT_DEMO_TASK, history[1].content)

        assertEquals(MessageRole.ASSISTANT, history[2].role)
        assertEquals(1, history[2].toolCalls.size)
        assertEquals("call_001", history[2].toolCalls[0].id)
        assertEquals("write_file", history[2].toolCalls[0].name)

        assertEquals(MessageRole.TOOL, history[3].role)
        assertEquals("call_001", history[3].toolCallId)
        assertEquals(false, history[3].isError)
        assertTrue(history[3].content.contains("created successfully"))

        assertEquals(MessageRole.ASSISTANT, history[4].role)
        assertEquals("Файл hello.py успешно создан.", history[4].content)

        // Проверка последовательности событий
        assertTrue(events[0] is AgentEvent.Started)
        assertTrue(events.any { it is AgentEvent.Thinking && it.iteration == 1 })
        assertTrue(events.any { it is AgentEvent.ToolRequested && it.call.id == "call_001" })
        assertTrue(events.any { it is AgentEvent.ToolCompleted && it.result.success })
        assertTrue(events.any { it is AgentEvent.Thinking && it.iteration == 2 })
        assertTrue(events.last() is AgentEvent.Completed)
        assertEquals("Файл hello.py успешно создан.", (events.last() as AgentEvent.Completed).response)
    }

    // 7. Несколько вызовов инструментов обрабатываются корректно
    @Test
    fun testMultipleToolCallsInSingleTurn() = runBlocking {
        val toolCall1 = ToolCall("c1", "write_file", """{"path": "file1.txt", "content": "Hello 1"}""")
        val toolCall2 = ToolCall("c2", "write_file", """{"path": "file2.txt", "content": "Hello 2"}""")
        val response1 = ModelResponse(text = "", toolCalls = listOf(toolCall1, toolCall2))
        val response2 = ModelResponse(text = "Both files created.")

        val model = MockModelAdapter(response1, response2)
        val tools = MockToolExecutor()
        val agent = Agent(model, tools)

        val result = agent.run("Create 2 files")

        assertEquals("Both files created.", result)
        assertEquals("Hello 1", tools.getFile("file1.txt"))
        assertEquals("Hello 2", tools.getFile("file2.txt"))
        assertEquals(2, tools.executedCalls.size)

        // Проверяем историю
        val finalContext = model.lastContext!!
        val history = finalContext.getHistory()
        // SYSTEM, USER, ASSISTANT (2 tool calls), TOOL (c1), TOOL (c2), ASSISTANT (final)
        val assistantMsg = history.first { it.role == MessageRole.ASSISTANT && it.toolCalls.isNotEmpty() }
        assertEquals(2, assistantMsg.toolCalls.size)

        val toolMessages = history.filter { it.role == MessageRole.TOOL }
        assertEquals(2, toolMessages.size)
        assertEquals("c1", toolMessages[0].toolCallId)
        assertEquals("c2", toolMessages[1].toolCallId)
    }

    // 8. Неизвестный инструмент обрабатывается предсказуемо
    @Test
    fun testUnknownToolReturnsFailureResult() = runBlocking {
        val badCall = ToolCall("err_1", "unsupported_command", """{"arg": 123}""")
        val response1 = ModelResponse(text = "Trying command", toolCalls = listOf(badCall))
        val response2 = ModelResponse(text = "Handled error.")

        val model = MockModelAdapter(response1, response2)
        val tools = MockToolExecutor()
        val agent = Agent(model, tools)

        val events = mutableListOf<AgentEvent>()
        val result = agent.run("Do command") { events.add(it) }

        assertEquals("Handled error.", result)
        val toolCompleted = events.filterIsInstance<AgentEvent.ToolCompleted>().firstOrNull()
        assertNotNull(toolCompleted)
        assertFalse(toolCompleted.result.success)
        assertTrue(toolCompleted.result.content.contains("Unknown tool"))

        // Проверяем, что в истории результат помечен как isError = true
        val toolMsg = model.lastContext!!.getHistory().first { it.role == MessageRole.TOOL }
        assertEquals(true, toolMsg.isError)
        assertEquals("err_1", toolMsg.toolCallId)
    }

    // 9. Превышение maxIterations приводит к ожидаемому исключению
    @Test
    fun testMaxIterationsExceededThrowsException() = runBlocking {
        // Модель бесконечно запрашивает инструменты
        val repeatedToolCall = ToolCall("loop_call", "write_file", """{"path": "f.txt", "content": "text"}""")
        val infiniteResponses = List(10) {
            ModelResponse(text = "Looping...", toolCalls = listOf(repeatedToolCall))
        }
        val model = MockModelAdapter(infiniteResponses)
        val tools = MockToolExecutor()
        val config = AgentConfig(maxIterations = 3)
        val agent = Agent(model, tools, config)

        val events = mutableListOf<AgentEvent>()
        val exception = assertFailsWith<MaxIterationsReachedException> {
            agent.run("Infinite loop task") { events.add(it) }
        }

        assertTrue(exception.message?.contains("maximum iterations limit (3)") == true)
        val failedEvent = events.lastOrNull() as? AgentEvent.Failed
        assertNotNull(failedEvent)
        assertTrue(failedEvent.error is MaxIterationsReachedException)
    }

    // Проверка CancellationException: не должна глушиться и не должна отправлять Failed
    @Test
    fun testCancellationExceptionIsRethrownWithoutFailedEvent() = runBlocking {
        val cancellingModel = object : com.limer.antigooglity.model.ModelAdapter {
            override suspend fun generate(context: AgentContext): ModelResponse {
                throw CancellationException("Coroutine cancelled")
            }
        }
        val tools = MockToolExecutor()
        val agent = Agent(cancellingModel, tools)

        val events = mutableListOf<AgentEvent>()
        assertFailsWith<CancellationException> {
            agent.run("Cancel me") { events.add(it) }
        }

        assertTrue(events.none { it is AgentEvent.Failed })
    }

    // Проверка парсера JSON в MockToolExecutor
    @Test
    fun testMockToolExecutorJsonParserEscapesAndWhitespace() {
        val json = """
            {
               "path" : "dir/test_file.py",
               "content" : "line 1\n\"escaped quotes\"\nline 2"
            }
        """.trimIndent()
        val parsed = MockToolExecutor.parseSimpleJson(json)
        assertEquals("dir/test_file.py", parsed["path"])
        assertEquals("line 1\n\"escaped quotes\"\nline 2", parsed["content"])
    }
}
