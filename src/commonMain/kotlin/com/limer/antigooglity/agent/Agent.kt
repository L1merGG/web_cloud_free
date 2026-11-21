package com.limer.antigooglity.agent

import com.limer.antigooglity.model.ModelAdapter
import com.limer.antigooglity.tools.ToolExecutor

class Agent(
    val modelAdapter: ModelAdapter,
    val toolExecutor: ToolExecutor,
    val config: AgentConfig = AgentConfig(),
) {
    private val loop = AgentLoop(modelAdapter, toolExecutor, config)

    suspend fun run(
        task: String,
        onEvent: (AgentEvent) -> Unit = {},
    ): String {
        return loop.run(task, onEvent)
    }
}
