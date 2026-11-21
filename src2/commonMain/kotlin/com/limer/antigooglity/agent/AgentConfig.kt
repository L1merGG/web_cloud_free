package com.limer.antigooglity.agent

data class AgentConfig(
    val systemPrompt: String = "",
    val maxIterations: Int = 20,
) {
    init {
        require(maxIterations > 0) { "maxIterations must be greater than 0, but was $maxIterations" }
    }
}
