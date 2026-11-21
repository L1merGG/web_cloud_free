package com.limer.antigooglity.tools.terminal

import com.limer.antigooglity.tools.ToolCall
import com.limer.antigooglity.tools.ToolExecutor
import com.limer.antigooglity.tools.ToolResult
import kotlinx.coroutines.CancellationException
import java.nio.file.NoSuchFileException
import java.nio.file.Path

class TerminalToolExecutor(
    val config: TerminalConfig,
    private val terminalExecutor: TerminalExecutor = TerminalExecutor(config),
) : ToolExecutor {

    constructor(workspaceRoot: Path) : this(TerminalConfig(rootDirectory = workspaceRoot))

    override suspend fun execute(call: ToolCall): ToolResult {
        if (call.name != "execute_command") {
            return ToolResult(
                toolCallId = call.id,
                content = "Unknown tool: '${call.name}'",
                success = false,
            )
        }

        return try {
            val cmd = parseCommand(call.arguments)
            val result = terminalExecutor.execute(cmd)
            ToolResult(
                toolCallId = call.id,
                content = result.toSummary(cmd.workingDirectory),
                success = result.success,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            val msg = e.message ?: "Security violation"
            val content = if (msg.contains("Command requires user approval")) {
                msg
            } else {
                "Security violation: $msg"
            }
            ToolResult(
                toolCallId = call.id,
                content = content,
                success = false,
            )
        } catch (e: NoSuchFileException) {
            ToolResult(
                toolCallId = call.id,
                content = "Directory or file not found: ${e.file ?: e.message}",
                success = false,
            )
        } catch (e: IllegalArgumentException) {
            ToolResult(
                toolCallId = call.id,
                content = "Invalid arguments: ${e.message}",
                success = false,
            )
        } catch (e: ProcessStartException) {
            ToolResult(
                toolCallId = call.id,
                content = "Process start failed: ${e.message}",
                success = false,
            )
        } catch (e: Exception) {
            ToolResult(
                toolCallId = call.id,
                content = "Execution error: ${e.message ?: e::class.simpleName}",
                success = false,
            )
        }
    }

    companion object {
        fun parseCommand(jsonStr: String): TerminalCommand {
            val trimmed = jsonStr.trim()
            if (trimmed.isEmpty()) {
                throw IllegalArgumentException("Arguments string must not be empty")
            }
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
                throw IllegalArgumentException("Expected JSON object, got: ${trimmed.take(30)}")
            }

            var command: String? = null
            var args: List<String> = emptyList()
            var workingDirectory: String = "."

            var i = 1
            val len = trimmed.length

            fun skipWhitespace() {
                while (i < len && trimmed[i].isWhitespace()) i++
            }

            fun parseString(): String {
                skipWhitespace()
                if (i >= len || trimmed[i] != '"') {
                    throw IllegalArgumentException("Expected '\"' at position $i")
                }
                i++
                val sb = StringBuilder()
                while (i < len) {
                    val c = trimmed[i]
                    if (c == '\\') {
                        i++
                        if (i >= len) throw IllegalArgumentException("Unterminated escape sequence")
                        when (val esc = trimmed[i]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000c')
                            'u' -> {
                                if (i + 4 < len) {
                                    val hex = trimmed.substring(i + 1, i + 5)
                                    val code = hex.toIntOrNull(16)
                                        ?: throw IllegalArgumentException("Invalid unicode escape: \\u$hex")
                                    sb.append(code.toChar())
                                    i += 4
                                } else {
                                    throw IllegalArgumentException("Incomplete unicode escape")
                                }
                            }
                            else -> sb.append(esc)
                        }
                    } else if (c == '"') {
                        i++
                        return sb.toString()
                    } else {
                        sb.append(c)
                    }
                    i++
                }
                throw IllegalArgumentException("Unclosed string quote in JSON")
            }

            fun parseArray(): List<String> {
                skipWhitespace()
                if (i >= len || trimmed[i] != '[') {
                    throw IllegalArgumentException("Expected '[' at position $i")
                }
                i++
                val list = mutableListOf<String>()
                skipWhitespace()
                if (i < len && trimmed[i] == ']') {
                    i++
                    return list
                }
                while (i < len) {
                    skipWhitespace()
                    if (i >= len) throw IllegalArgumentException("Unexpected end of array")
                    val element = parseString()
                    list.add(element)
                    skipWhitespace()
                    if (i < len && trimmed[i] == ',') {
                        i++
                        skipWhitespace()
                        if (i < len && trimmed[i] == ']') {
                            throw IllegalArgumentException("Trailing comma in array is not valid JSON")
                        }
                    } else if (i < len && trimmed[i] == ']') {
                        i++
                        break
                    } else {
                        throw IllegalArgumentException("Expected ',' or ']' at position $i")
                    }
                }
                return list
            }

            skipWhitespace()
            if (i < len && trimmed[i] == '}') {
                throw IllegalArgumentException("Missing required argument 'command'")
            }

            while (i < len) {
                skipWhitespace()
                if (i >= len || trimmed[i] == '}') {
                    if (i < len && trimmed[i] == '}') i++
                    break
                }

                val key = parseString()
                skipWhitespace()
                if (i >= len || trimmed[i] != ':') {
                    throw IllegalArgumentException("Expected ':' after key '$key'")
                }
                i++
                skipWhitespace()

                when (key) {
                    "command" -> {
                        command = parseString()
                    }
                    "args" -> {
                        args = parseArray()
                    }
                    "workingDirectory", "working_directory", "cwd" -> {
                        workingDirectory = parseString()
                    }
                    else -> {
                        if (trimmed[i] == '"') {
                            parseString()
                        } else if (trimmed[i] == '[') {
                            parseArray()
                        } else {
                            while (i < len && trimmed[i] != ',' && trimmed[i] != '}') i++
                        }
                    }
                }

                skipWhitespace()
                if (i < len && trimmed[i] == ',') {
                    i++
                    skipWhitespace()
                    if (i < len && trimmed[i] == '}') {
                        throw IllegalArgumentException("Trailing comma before '}' is not valid JSON")
                    }
                } else if (i < len && trimmed[i] == '}') {
                    i++
                    break
                } else {
                    throw IllegalArgumentException("Expected ',' or '}' at position $i")
                }
            }

            skipWhitespace()
            if (i < len) {
                throw IllegalArgumentException("Unexpected trailing characters after JSON object")
            }

            if (command.isNullOrBlank()) {
                throw IllegalArgumentException("Missing required argument 'command'")
            }

            return TerminalCommand(
                command = command,
                args = args,
                workingDirectory = workingDirectory,
            )
        }
    }
}
