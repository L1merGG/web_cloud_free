package com.limer.antigooglity.model

import com.limer.antigooglity.agent.AgentContext

fun interface ModelAdapter {
    suspend fun generate(context: AgentContext): ModelResponse
}
