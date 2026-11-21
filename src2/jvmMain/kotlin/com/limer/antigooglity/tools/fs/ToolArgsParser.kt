package com.limer.antigooglity.tools.fs

internal object ToolArgsParser {

    fun parseObject(jsonStr: String): Map<String, String> {
        val trimmed = jsonStr.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Arguments string must not be empty")
        }
        if (!trimmed.startsWith("{")) {
            throw IllegalArgumentException("Expected JSON object starting with '{', but got: ${trimmed.take(20)}")
        }

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
            i++ // skip opening quote
            val sb = StringBuilder()
            while (i < len) {
                val c = trimmed[i]
                if (c == '\\') {
                    i++
                    if (i >= len) throw IllegalArgumentException("Unterminated escape sequence at end of string")
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
                                throw IllegalArgumentException("Incomplete unicode escape at position $i")
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
            throw IllegalArgumentException("Unclosed string quote in JSON")
        }

        val result = mutableMapOf<String, String>()

        skipWhitespace()
        if (i < len && trimmed[i] == '}') {
            i++
            skipWhitespace()
            if (i < len) throw IllegalArgumentException("Unexpected trailing characters after JSON object at position $i")
            return result
        }

        while (i < len) {
            skipWhitespace()
            if (i >= len) throw IllegalArgumentException("Unexpected end of JSON, expected key or '}'")
            if (trimmed[i] == '}') {
                i++
                break
            }

            // Parse key
            val key = parseString()

            // Colon
            skipWhitespace()
            if (i >= len || trimmed[i] != ':') {
                throw IllegalArgumentException("Expected ':' after key '$key' at position $i")
            }
            i++ // skip ':'

            // Parse value
            skipWhitespace()
            if (i >= len) throw IllegalArgumentException("Expected value after ':' at position $i")

            val value: String = if (trimmed[i] == '"') {
                parseString()
            } else if (trimmed[i] == '{' || trimmed[i] == '[') {
                val openChar = trimmed[i]
                val closeChar = if (openChar == '{') '}' else ']'
                val start = i
                var depth = 0
                var inStr = false
                while (i < len) {
                    val ch = trimmed[i]
                    if (ch == '\\' && inStr) {
                        i += 2
                        continue
                    }
                    if (ch == '"') {
                        inStr = !inStr
                    } else if (!inStr) {
                        if (ch == openChar) depth++
                        else if (ch == closeChar) {
                            depth--
                            if (depth == 0) {
                                i++
                                break
                            }
                        }
                    }
                    i++
                }
                trimmed.substring(start, i)
            } else {
                val start = i
                while (i < len && trimmed[i] != ',' && trimmed[i] != '}' && !trimmed[i].isWhitespace()) {
                    i++
                }
                val raw = trimmed.substring(start, i)
                if (raw.isEmpty()) throw IllegalArgumentException("Expected value at position $start")
                raw
            }

            result[key] = value

            skipWhitespace()
            if (i < len && trimmed[i] == ',') {
                i++
                skipWhitespace()
                if (i < len && trimmed[i] == '}') {
                    throw IllegalArgumentException("Trailing comma before '}' is not valid JSON at position $i")
                }
            } else if (i < len && trimmed[i] == '}') {
                i++
                break
            } else {
                throw IllegalArgumentException("Expected ',' or '}' at position $i, but got '${if (i < len) trimmed[i] else "EOF"}'")
            }
        }

        skipWhitespace()
        if (i < len) {
            throw IllegalArgumentException("Unexpected trailing characters after JSON object: '${trimmed.substring(i)}'")
        }

        return result
    }
}
