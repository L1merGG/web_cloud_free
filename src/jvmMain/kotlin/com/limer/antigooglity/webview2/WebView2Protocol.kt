package com.limer.antigooglity.webview2

sealed class SimpleJsonValue {
    data class JsonObject(val properties: Map<String, SimpleJsonValue>) : SimpleJsonValue() {
        fun getString(key: String): String? = (properties[key] as? JsonString)?.value
        fun getBoolean(key: String): Boolean? = (properties[key] as? JsonBoolean)?.value
        fun getInt(key: String): Int? = (properties[key] as? JsonNumber)?.value?.toInt()
        fun getLong(key: String): Long? = (properties[key] as? JsonNumber)?.value?.toLong()
        fun getDouble(key: String): Double? = (properties[key] as? JsonNumber)?.value
        fun getArray(key: String): List<SimpleJsonValue>? = (properties[key] as? JsonArray)?.elements
        fun getObject(key: String): JsonObject? = properties[key] as? JsonObject

        override fun toJson(): String {
            val sb = StringBuilder("{")
            var first = true
            for ((k, v) in properties) {
                if (!first) sb.append(",")
                first = false
                sb.append(WebView2Command.quote(k)).append(":").append(v.toJson())
            }
            sb.append("}")
            return sb.toString()
        }
    }

    data class JsonArray(val elements: List<SimpleJsonValue>) : SimpleJsonValue() {
        override fun toJson(): String {
            val sb = StringBuilder("[")
            var first = true
            for (el in elements) {
                if (!first) sb.append(",")
                first = false
                sb.append(el.toJson())
            }
            sb.append("]")
            return sb.toString()
        }
    }

    data class JsonString(val value: String) : SimpleJsonValue() {
        override fun toJson(): String = WebView2Command.quote(value)
    }

    data class JsonNumber(val value: Double) : SimpleJsonValue() {
        override fun toJson(): String {
            return if (value == value.toLong().toDouble()) {
                value.toLong().toString()
            } else {
                value.toString()
            }
        }
    }

    data class JsonBoolean(val value: Boolean) : SimpleJsonValue() {
        override fun toJson(): String = if (value) "true" else "false"
    }

    object JsonNull : SimpleJsonValue() {
        override fun toString(): String = "null"
        override fun toJson(): String = "null"
    }

    abstract fun toJson(): String

    companion object {
        fun parse(json: String): SimpleJsonValue {
            val parser = SimpleJsonParser(json)
            val value = parser.parseValue()
            parser.skipWhitespace()
            if (!parser.isEof()) {
                throw IllegalArgumentException("Extraneous characters after JSON at index ${parser.currentIndex}")
            }
            return value
        }
    }
}

internal class SimpleJsonParser(private val src: String) {
    private var idx = 0
    private val len = src.length

    val currentIndex: Int
        get() = idx

    fun isEof(): Boolean = idx >= len

    fun skipWhitespace() {
        while (idx < len && (src[idx] == ' ' || src[idx] == '\t' || src[idx] == '\n' || src[idx] == '\r')) {
            idx++
        }
    }

    fun parseValue(): SimpleJsonValue {
        skipWhitespace()
        if (idx >= len) {
            throw IllegalArgumentException("Unexpected end of JSON input at index $idx")
        }
        return when (val c = src[idx]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't', 'f' -> parseBoolean()
            'n' -> parseNull()
            '-', in '0'..'9' -> parseNumber()
            else -> throw IllegalArgumentException("Unexpected character '$c' at offset $idx in JSON")
        }
    }

    private fun parseObject(): SimpleJsonValue.JsonObject {
        idx++ // skip '{'
        val map = LinkedHashMap<String, SimpleJsonValue>()
        skipWhitespace()
        if (idx < len && src[idx] == '}') {
            idx++
            return SimpleJsonValue.JsonObject(map)
        }

        while (idx < len) {
            skipWhitespace()
            if (idx >= len || src[idx] != '"') {
                throw IllegalArgumentException("Expected string key in object at offset $idx")
            }
            val key = parseString().value
            skipWhitespace()
            if (idx >= len || src[idx] != ':') {
                throw IllegalArgumentException("Expected ':' after key '$key' at offset $idx")
            }
            idx++ // skip ':'
            val value = parseValue()
            map[key] = value

            skipWhitespace()
            if (idx < len && src[idx] == ',') {
                idx++
                continue
            }
            if (idx < len && src[idx] == '}') {
                idx++
                return SimpleJsonValue.JsonObject(map)
            }
            throw IllegalArgumentException("Expected ',' or '}' in object at offset $idx")
        }
        throw IllegalArgumentException("Unclosed object at offset $idx")
    }

    private fun parseArray(): SimpleJsonValue.JsonArray {
        idx++ // skip '['
        val list = ArrayList<SimpleJsonValue>()
        skipWhitespace()
        if (idx < len && src[idx] == ']') {
            idx++
            return SimpleJsonValue.JsonArray(list)
        }

        while (idx < len) {
            val value = parseValue()
            list.add(value)
            skipWhitespace()
            if (idx < len && src[idx] == ',') {
                idx++
                continue
            }
            if (idx < len && src[idx] == ']') {
                idx++
                return SimpleJsonValue.JsonArray(list)
            }
            throw IllegalArgumentException("Expected ',' or ']' in array at offset $idx")
        }
        throw IllegalArgumentException("Unclosed array at offset $idx")
    }

    fun parseString(): SimpleJsonValue.JsonString {
        if (idx >= len || src[idx] != '"') {
            throw IllegalArgumentException("Expected '\"' at offset $idx")
        }
        idx++ // skip opening '"'
        val sb = StringBuilder()
        while (idx < len) {
            val c = src[idx]
            if (c.code in 0..0x1F) {
                throw IllegalArgumentException("Unescaped control character 0x${Integer.toHexString(c.code)} in string at offset $idx")
            }
            if (c == '\\') {
                idx++
                if (idx >= len) {
                    throw IllegalArgumentException("Unterminated escape sequence in string at offset $idx")
                }
                when (val esc = src[idx]) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        if (idx + 4 >= len) {
                            throw IllegalArgumentException("Incomplete unicode escape at offset $idx")
                        }
                        val hex = src.substring(idx + 1, idx + 5)
                        if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                            throw IllegalArgumentException("Invalid hex in unicode escape: '\\u$hex' at offset $idx")
                        }
                        val codePoint = hex.toInt(16)
                        sb.append(codePoint.toChar())
                        idx += 4
                    }
                    else -> throw IllegalArgumentException("Invalid escape character '\\$esc' at offset $idx")
                }
            } else if (c == '"') {
                idx++
                return SimpleJsonValue.JsonString(sb.toString())
            } else {
                sb.append(c)
            }
            idx++
        }
        throw IllegalArgumentException("Unterminated string literal at offset $idx")
    }

    private fun parseNumber(): SimpleJsonValue.JsonNumber {
        val start = idx
        // RFC 8259: number = [ minus ] int [ frac ] [ exp ]
        if (idx < len && src[idx] == '-') {
            idx++
        }
        if (idx >= len) {
            throw IllegalArgumentException("Expected digits after '-' at offset $idx")
        }
        if (src[idx] == '0') {
            idx++
            // Leading zeroes are not allowed in RFC 8259 (e.g. 01 is illegal)
            if (idx < len && src[idx].isDigit()) {
                throw IllegalArgumentException("Leading zeros are not allowed in numbers at offset $idx")
            }
        } else if (src[idx] in '1'..'9') {
            while (idx < len && src[idx].isDigit()) {
                idx++
            }
        } else {
            throw IllegalArgumentException("Expected digit in number at offset $idx")
        }

        // Fraction part
        if (idx < len && src[idx] == '.') {
            idx++
            if (idx >= len || !src[idx].isDigit()) {
                throw IllegalArgumentException("Expected digit after decimal point at offset $idx")
            }
            while (idx < len && src[idx].isDigit()) {
                idx++
            }
        }

        // Exponent part
        if (idx < len && (src[idx] == 'e' || src[idx] == 'E')) {
            idx++
            if (idx < len && (src[idx] == '+' || src[idx] == '-')) {
                idx++
            }
            if (idx >= len || !src[idx].isDigit()) {
                throw IllegalArgumentException("Expected digit in exponent at offset $idx")
            }
            while (idx < len && src[idx].isDigit()) {
                idx++
            }
        }

        val numStr = src.substring(start, idx)
        val d = numStr.toDoubleOrNull()
            ?: throw IllegalArgumentException("Invalid number format '$numStr' at offset $start")
        return SimpleJsonValue.JsonNumber(d)
    }

    private fun parseBoolean(): SimpleJsonValue.JsonBoolean {
        if (src.startsWith("true", idx)) {
            val nextIdx = idx + 4
            if (nextIdx < len && (src[nextIdx].isLetterOrDigit() || src[nextIdx] == '_')) {
                throw IllegalArgumentException("Invalid identifier starting with 'true' at offset $idx")
            }
            idx = nextIdx
            return SimpleJsonValue.JsonBoolean(true)
        } else if (src.startsWith("false", idx)) {
            val nextIdx = idx + 5
            if (nextIdx < len && (src[nextIdx].isLetterOrDigit() || src[nextIdx] == '_')) {
                throw IllegalArgumentException("Invalid identifier starting with 'false' at offset $idx")
            }
            idx = nextIdx
            return SimpleJsonValue.JsonBoolean(false)
        } else {
            throw IllegalArgumentException("Expected boolean at offset $idx")
        }
    }

    private fun parseNull(): SimpleJsonValue.JsonNull {
        if (src.startsWith("null", idx)) {
            val nextIdx = idx + 4
            if (nextIdx < len && (src[nextIdx].isLetterOrDigit() || src[nextIdx] == '_')) {
                throw IllegalArgumentException("Invalid identifier starting with 'null' at offset $idx")
            }
            idx = nextIdx
            return SimpleJsonValue.JsonNull
        }
        throw IllegalArgumentException("Expected null at offset $idx")
    }
}

data class WebView2Command(
    val requestId: String,
    val command: String,
    val url: String? = null,
    val script: String? = null,
    val timeoutMs: Int? = null,
    val visible: Boolean? = null,
    val maxLength: Int? = null,
) {
    fun toJson(): String {
        val sb = StringBuilder("{")
        sb.append("\"requestId\":").append(quote(requestId))
        sb.append(",\"command\":").append(quote(command))
        if (url != null) {
            sb.append(",\"url\":").append(quote(url))
        }
        if (script != null) {
            sb.append(",\"script\":").append(quote(script))
        }
        if (timeoutMs != null) {
            sb.append(",\"timeoutMs\":").append(timeoutMs)
        }
        if (visible != null) {
            sb.append(",\"visible\":").append(visible)
        }
        if (maxLength != null) {
            sb.append(",\"maxLength\":").append(maxLength)
        }
        sb.append("}")
        return sb.toString()
    }

    companion object {
        fun quote(value: String): String {
            val sb = StringBuilder("\"")
            for (c in value) {
                when (c) {
                    '\\' -> sb.append("\\\\")
                    '"' -> sb.append("\\\"")
                    '\b' -> sb.append("\\b")
                    '\u000C' -> sb.append("\\f")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> {
                        if (c.code in 0..0x1f) {
                            sb.append(String.format("\\u%04x", c.code))
                        } else {
                            sb.append(c)
                        }
                    }
                }
            }
            sb.append("\"")
            return sb.toString()
        }
    }
}

data class WebView2DocumentInfo(
    val href: String,
    val title: String,
    val readyState: String,
    val visibilityState: String,
    val hasFocus: Boolean,
)

data class WebView2NavigationStatus(
    val currentUrl: String,
    val isNavigating: Boolean,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val documentTitle: String,
)

data class WebView2PageText(
    val text: String,
    val length: Int,
    val truncated: Boolean,
)

data class WebView2Response(
    val requestId: String,
    val command: String,
    val success: Boolean,
    val data: SimpleJsonValue? = null,
    val rawData: String? = null,
    val error: String? = null,
) {
    fun parseDocumentInfo(): WebView2DocumentInfo? {
        val obj = (data as? SimpleJsonValue.JsonObject) ?: return null
        return WebView2DocumentInfo(
            href = obj.getString("href") ?: "",
            title = obj.getString("title") ?: "",
            readyState = obj.getString("readyState") ?: "",
            visibilityState = obj.getString("visibilityState") ?: "",
            hasFocus = obj.getBoolean("hasFocus") ?: false,
        )
    }

    fun parseNavigationStatus(): WebView2NavigationStatus? {
        val obj = (data as? SimpleJsonValue.JsonObject) ?: return null
        return WebView2NavigationStatus(
            currentUrl = obj.getString("currentUrl") ?: "",
            isNavigating = obj.getBoolean("isNavigating") ?: false,
            canGoBack = obj.getBoolean("canGoBack") ?: false,
            canGoForward = obj.getBoolean("canGoForward") ?: false,
            documentTitle = obj.getString("documentTitle") ?: "",
        )
    }

    fun parsePageText(): WebView2PageText? {
        val obj = (data as? SimpleJsonValue.JsonObject) ?: return null
        return WebView2PageText(
            text = obj.getString("text") ?: "",
            length = obj.getInt("length") ?: 0,
            truncated = obj.getBoolean("truncated") ?: false,
        )
    }

    /**
     * Retrieves the 'result' field from data (returned by EXECUTE_SCRIPT).
     */
    fun getResultJson(): SimpleJsonValue? {
        val obj = data as? SimpleJsonValue.JsonObject ?: return null
        return obj.properties["result"]
    }

    fun getResultString(): String? {
        val res = getResultJson() ?: return null
        return when (res) {
            is SimpleJsonValue.JsonString -> res.value
            else -> null
        }
    }

    companion object {
        fun parse(jsonLine: String): WebView2Response {
            val root = SimpleJsonValue.parse(jsonLine.trim()) as? SimpleJsonValue.JsonObject
                ?: throw IllegalArgumentException("Response is not a JSON object: $jsonLine")

            val requestId = root.getString("requestId") ?: ""
            val command = root.getString("command") ?: ""
            val success = root.getBoolean("success") ?: false
            val error = root.getString("error")
            val dataValue = root.properties["data"]

            val rawDataString = when (dataValue) {
                null, is SimpleJsonValue.JsonNull -> null
                is SimpleJsonValue.JsonString -> dataValue.value
                else -> dataValue.toJson()
            }

            return WebView2Response(
                requestId = requestId,
                command = command,
                success = success,
                data = if (dataValue is SimpleJsonValue.JsonNull) null else dataValue,
                rawData = rawDataString,
                error = error,
            )
        }
    }
}
