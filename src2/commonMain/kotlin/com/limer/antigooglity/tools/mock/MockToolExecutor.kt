package com.limer.antigooglity.tools.mock

import com.limer.antigooglity.tools.ToolCall
import com.limer.antigooglity.tools.ToolExecutor
import com.limer.antigooglity.tools.ToolResult

class MockToolExecutor(
    private val commandApprover: (suspend (String) -> Boolean)? = null,
) : ToolExecutor {

    private val virtualStorage: MutableMap<String, String> = mutableMapOf()
    private val _executedCalls: MutableList<ToolCall> = mutableListOf()

    val executedCalls: List<ToolCall>
        get() = _executedCalls.toList()

    fun getFile(path: String): String? = virtualStorage[normalizePath(path)]

    fun hasFile(path: String): Boolean = virtualStorage.containsKey(normalizePath(path))

    fun getAllFiles(): Map<String, String> = virtualStorage.toMap()

    fun clear() {
        virtualStorage.clear()
        _executedCalls.clear()
    }

    override suspend fun execute(call: ToolCall): ToolResult {
        _executedCalls.add(call)

        return when (call.name) {
            "write_file" -> handleWriteFile(call)
            "read_file" -> handleReadFile(call)
            "execute_command" -> handleExecuteCommand(call)
            else -> ToolResult(
                toolCallId = call.id,
                content = "Unknown tool: '${call.name}'",
                success = false,
            )
        }
    }

    private fun handleWriteFile(call: ToolCall): ToolResult {
        val args = parseSimpleJson(call.arguments)
        val fileName = args["path"] ?: args["filename"] ?: args["file"] ?: args["name"]
        val content = args["content"] ?: args["text"]

        if (fileName.isNullOrBlank()) {
            return ToolResult(
                toolCallId = call.id,
                content = "Missing required argument 'path' or 'filename' in arguments: ${call.arguments}",
                success = false,
            )
        }

        if (content == null) {
            return ToolResult(
                toolCallId = call.id,
                content = "Missing required argument 'content' in arguments: ${call.arguments}",
                success = false,
            )
        }

        virtualStorage[normalizePath(fileName)] = content

        return ToolResult(
            toolCallId = call.id,
            content = "File '$fileName' created successfully (${content.length} characters)",
            success = true,
        )
    }

    private fun handleReadFile(call: ToolCall): ToolResult {
        val args = parseSimpleJson(call.arguments)
        val fileName = args["path"] ?: args["filename"] ?: args["file"] ?: args["name"]
        if (fileName.isNullOrBlank()) {
            return ToolResult(
                toolCallId = call.id,
                content = "Missing required argument 'path' or 'filename' in arguments: ${call.arguments}",
                success = false,
            )
        }

        val normalized = normalizePath(fileName)
        val content = virtualStorage[normalized]
            ?: if (normalized == "hello.py") "print(\"Hello from Antigooglity!\")\n" else null

        return if (content != null) {
            ToolResult(
                toolCallId = call.id,
                content = "File: $fileName\n\n$content",
                success = true,
            )
        } else {
            ToolResult(
                toolCallId = call.id,
                content = "File not found: '$fileName'",
                success = false,
            )
        }
    }

    private suspend fun handleExecuteCommand(call: ToolCall): ToolResult {
        val args = parseSimpleJson(call.arguments)
        val command = args["command"] ?: "command"

        val isDangerous = command == "rm" || command.contains("delete") || command.contains("danger")
        if (isDangerous && commandApprover != null) {
            val approved = commandApprover.invoke(call.arguments)
            if (!approved) {
                return ToolResult(
                    toolCallId = call.id,
                    content = "Execution rejected by user for command: '$command'",
                    success = false,
                )
            }
        }

        return ToolResult(
            toolCallId = call.id,
            content = "Command '$command' executed successfully (mock)",
            success = true,
        )
    }

    private fun normalizePath(path: String): String = path.trim().replace('\\', '/')

    companion object {
        fun parseSimpleJson(input: String): Map<String, String> {
            val result = mutableMapOf<String, String>()
            val trimmed = input.trim()
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
                return result
            }

            var i = 1 // skip '{'
            val len = trimmed.length

            fun skipWhitespace() {
                while (i < len && trimmed[i].isWhitespace()) i++
            }

            fun parseString(): String? {
                skipWhitespace()
                if (i >= len || trimmed[i] != '"') return null
                i++ // skip opening quote
                val sb = StringBuilder()
                while (i < len) {
                    val c = trimmed[i]
                    if (c == '\\') {
                        i++
                        if (i >= len) break
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
                                    hex.toIntOrNull(16)?.let { sb.append(it.toChar()) }
                                    i += 4
                                }
                            }
                            else -> sb.append(esc)
                        }
                    } else if (c == '"') {
                        i++ // skip closing quote
                        return sb.toString()
                    } else {
                        sb.append(c)
                    }
                    i++
                }
                return sb.toString()
            }

            while (i < len - 1) {
                skipWhitespace()
                if (i >= len - 1 || trimmed[i] == '}') break
                if (trimmed[i] == ',') {
                    i++
                    continue
                }
                val key = parseString() ?: break
                skipWhitespace()
                if (i < len && trimmed[i] == ':') {
                    i++
                }
                skipWhitespace()
                val value = parseString()
                if (value != null) {
                    result[key] = value
                } else {
                    val start = i
                    while (i < len && trimmed[i] != ',' && trimmed[i] != '}') i++
                    val raw = trimmed.substring(start, i).trim().removeSurrounding("\"")
                    result[key] = raw
                }
            }

            return result
        }
    }
}
