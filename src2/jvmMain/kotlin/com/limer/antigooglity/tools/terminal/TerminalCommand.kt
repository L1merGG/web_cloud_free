package com.limer.antigooglity.tools.terminal

data class TerminalCommand(
    val command: String,
    val args: List<String> = emptyList(),
    val workingDirectory: String = ".",
)
