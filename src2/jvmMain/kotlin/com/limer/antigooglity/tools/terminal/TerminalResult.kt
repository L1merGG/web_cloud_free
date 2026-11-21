package com.limer.antigooglity.tools.terminal

data class TerminalResult(
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
    val outputTruncated: Boolean = false,
    val success: Boolean = (exitCode == 0 && !timedOut),
) {
    fun toSummary(relativeWorkingDirectory: String = "."): String {
        val sb = StringBuilder()
        sb.appendLine("Command: $command")
        sb.appendLine("Working directory: $relativeWorkingDirectory")
        sb.appendLine("Exit code: $exitCode")
        sb.appendLine("Success: $success")
        if (timedOut) {
            sb.appendLine("Status: TIMED OUT")
        }
        if (outputTruncated) {
            sb.appendLine("Warning: Output was truncated due to size limits")
        }
        if (stdout.isNotBlank()) {
            sb.appendLine("--- stdout ---")
            sb.appendLine(stdout)
        }
        if (stderr.isNotBlank()) {
            sb.appendLine("--- stderr ---")
            sb.appendLine(stderr)
        }
        return sb.toString().trimEnd()
    }
}
