package com.limer.antigooglity.agent

class AgentContext(
    val task: String,
) {
    private val _messages: MutableList<AgentMessage> = mutableListOf()

    val messages: List<AgentMessage>
        get() = _messages.toList()

    fun addMessage(message: AgentMessage) {
        _messages.add(message)
    }

    fun addMessages(messages: Iterable<AgentMessage>) {
        _messages.addAll(messages)
    }

    fun getHistory(): List<AgentMessage> = messages
}
