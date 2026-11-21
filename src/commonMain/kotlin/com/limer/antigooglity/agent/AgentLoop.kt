package com.limer.antigooglity.agent

import com.limer.antigooglity.model.ModelAdapter
import com.limer.antigooglity.tools.ToolExecutor
import kotlinx.coroutines.CancellationException

class AgentLoop(
    private val modelAdapter: ModelAdapter,
    private val toolExecutor: ToolExecutor,
    private val config: AgentConfig,
) {
    suspend fun run(
        task: String,
        onEvent: (AgentEvent) -> Unit = {},
    ): String {
        try {
            onEvent(AgentEvent.Started)

            // 1. Создать AgentContext
            val context = AgentContext(task)

            // 2. Добавить системное сообщение
            context.addMessage(
                AgentMessage(
                    role = MessageRole.SYSTEM,
                    content = config.systemPrompt,
                )
            )

            // 3. Добавить сообщение пользователя
            context.addMessage(
                AgentMessage(
                    role = MessageRole.USER,
                    content = task,
                )
            )

            // 4. Запустить цикл с ограничением maxIterations
            for (iteration in 1..config.maxIterations) {
                // 5. Перед каждым обращением к модели отправлять Thinking
                onEvent(AgentEvent.Thinking(iteration))

                // 6. Получить ModelResponse
                val response = modelAdapter.generate(context)

                // 7. Если модель вернула toolCalls
                if (response.toolCalls.isNotEmpty()) {
                    // Добавить сообщение ассистента с вызовами инструментов в историю
                    context.addMessage(
                        AgentMessage(
                            role = MessageRole.ASSISTANT,
                            content = response.text,
                            toolCalls = response.toolCalls,
                        )
                    )

                    // Выполнить каждый инструмент через ToolExecutor последовательно
                    for (call in response.toolCalls) {
                        onEvent(AgentEvent.ToolRequested(call))
                        val result = toolExecutor.execute(call)
                        context.addMessage(
                            AgentMessage(
                                role = MessageRole.TOOL,
                                content = result.content,
                                toolCallId = result.toolCallId,
                                isError = !result.success,
                            )
                        )
                        onEvent(AgentEvent.ToolCompleted(result))
                    }
                } else {
                    // 8. Если инструментов нет: отправить Completed и вернуть финальный текст
                    context.addMessage(
                        AgentMessage(
                            role = MessageRole.ASSISTANT,
                            content = response.text,
                        )
                    )
                    onEvent(AgentEvent.Completed(response.text))
                    return response.text
                }
            }

            // 9. Если достигнут лимит итераций — завершить выполнение с понятной ошибкой
            throw MaxIterationsReachedException(
                "Agent reached maximum iterations limit (${config.maxIterations}) without completing the task"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            onEvent(AgentEvent.Failed(e))
            throw e
        }
    }
}
