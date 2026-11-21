package com.limer.antigooglity.webview2.gemini

import com.limer.antigooglity.webview2.ScriptExecutor
import com.limer.antigooglity.webview2.SimpleJsonValue
import com.limer.antigooglity.webview2.WebView2Response
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FakeScriptExecutor : ScriptExecutor {
    val executedScripts = mutableListOf<String>()
    var scriptHandler: (String) -> WebView2Response = {
        WebView2Response(
            requestId = "test",
            command = "EXECUTE_SCRIPT",
            success = true,
            data = SimpleJsonValue.JsonObject(emptyMap()),
        )
    }

    override suspend fun executeScript(script: String, timeoutMs: Long): WebView2Response {
        executedScripts.add(script)
        return scriptHandler(script)
    }
}

class GeminiDomControllerTest {

    private fun createScriptResultResponse(dataMap: Map<String, SimpleJsonValue>): WebView2Response {
        val resultObj = SimpleJsonValue.JsonObject(dataMap)
        val outerData = SimpleJsonValue.JsonObject(mapOf("result" to resultObj))
        return WebView2Response(
            requestId = "fake-req",
            command = "EXECUTE_SCRIPT",
            success = true,
            data = outerData,
            rawData = null,
        )
    }

    private fun createDefaultPageState(
        isDocumentLoaded: Boolean = true,
        isGeminiUrl: Boolean = true,
        isChatReady: Boolean = true,
        hasEditor: Boolean = true,
        isEditorEmpty: Boolean = true,
        isEditorAccessible: Boolean = true,
        hasSendButton: Boolean = true,
        isSendButtonDisabled: Boolean = false,
        isGenerating: Boolean = false,
    ): Map<String, SimpleJsonValue> = mapOf(
        "isDocumentLoaded" to SimpleJsonValue.JsonBoolean(isDocumentLoaded),
        "isGeminiUrl" to SimpleJsonValue.JsonBoolean(isGeminiUrl),
        "isChatReady" to SimpleJsonValue.JsonBoolean(isChatReady),
        "hasEditor" to SimpleJsonValue.JsonBoolean(hasEditor),
        "isEditorEmpty" to SimpleJsonValue.JsonBoolean(isEditorEmpty),
        "isEditorAccessible" to SimpleJsonValue.JsonBoolean(isEditorAccessible),
        "hasSendButton" to SimpleJsonValue.JsonBoolean(hasSendButton),
        "isSendButtonDisabled" to SimpleJsonValue.JsonBoolean(isSendButtonDisabled),
        "isGenerating" to SimpleJsonValue.JsonBoolean(isGenerating),
    )

    private fun createMessageItem(id: String, text: String): SimpleJsonValue.JsonObject =
        SimpleJsonValue.JsonObject(mapOf(
            "id" to SimpleJsonValue.JsonString(id),
            "text" to SimpleJsonValue.JsonString(text),
        ))

    // 1. Nested DOM elements do not inflate count of unique responses
    @Test
    fun testNestedElementsDoNotInflateResponses(): Unit = runBlocking {
        val fakeExecutor = FakeScriptExecutor()
        val controller = GeminiDomController(fakeExecutor)

        fakeExecutor.scriptHandler = { script ->
            when {
                script.contains("modelNodes") -> {
                    // Script returns unique top-level containers only
                    val responses = listOf(
                        createMessageItem("model-1", "Only top level response"),
                    )
                    createScriptResultResponse(mapOf("responses" to SimpleJsonValue.JsonArray(responses)))
                }
                else -> createScriptResultResponse(emptyMap())
            }
        }

        val responses = controller.getAllModelResponses()
        assertEquals(1, responses.size)
        assertEquals("model-1", responses[0].id)
        assertEquals("Only top level response", responses[0].text)
    }

    // 2. Old response is not returned
    @Test
    fun testOldResponseNotReturnedAsNew(): Unit = runBlocking {
        val fakeExecutor = FakeScriptExecutor()
        val controller = GeminiDomController(fakeExecutor)

        val preSnapshot = GeminiConversationSnapshot(
            userQueryCount = 1,
            modelResponseCount = 1,
            lastUserQuery = "First query",
            lastModelResponse = "Old response from turn 1",
            modelMessageIds = listOf("model-1"),
            isGenerating = false,
        )

        var pollCount = 0
        fakeExecutor.scriptHandler = { script ->
            when {
                script.contains("isDocumentLoaded") ->
                    createScriptResultResponse(createDefaultPageState())
                script.contains("modelNodes") -> {
                    pollCount++
                    val list = if (pollCount <= 2) {
                        listOf(createMessageItem("model-1", "Old response from turn 1"))
                    } else {
                        listOf(
                            createMessageItem("model-1", "Old response from turn 1"),
                            createMessageItem("model-2", "Brand new response from turn 2"),
                        )
                    }
                    createScriptResultResponse(mapOf("responses" to SimpleJsonValue.JsonArray(list)))
                }
                else -> createScriptResultResponse(emptyMap())
            }
        }

        val result = controller.waitForResponse(
            preSnapshot = preSnapshot,
            timeoutMs = 5000L,
            pollIntervalMs = 50L,
        )

        assertTrue(result.found, "New response should be found")
        assertEquals("Brand new response from turn 2", result.text)
    }

    // 3. Two consecutive responses with identical text are correctly distinguished by ID/index
    @Test
    fun testTwoConsecutiveIdenticalResponsesDistinguished(): Unit = runBlocking {
        val fakeExecutor = FakeScriptExecutor()
        val controller = GeminiDomController(fakeExecutor)

        val identicalText = "Identical answer text from both turns"
        val preSnapshot = GeminiConversationSnapshot(
            userQueryCount = 1,
            modelResponseCount = 1,
            lastUserQuery = "First query",
            lastModelResponse = identicalText,
            modelMessageIds = listOf("model-1"),
            isGenerating = false,
        )

        var pollCount = 0
        fakeExecutor.scriptHandler = { script ->
            when {
                script.contains("isDocumentLoaded") ->
                    createScriptResultResponse(createDefaultPageState(isGenerating = false))
                script.contains("modelNodes") -> {
                    pollCount++
                    val list = if (pollCount <= 2) {
                        listOf(createMessageItem("model-1", identicalText))
                    } else {
                        listOf(
                            createMessageItem("model-1", identicalText),
                            createMessageItem("model-2", identicalText),
                        )
                    }
                    createScriptResultResponse(mapOf("responses" to SimpleJsonValue.JsonArray(list)))
                }
                else -> createScriptResultResponse(emptyMap())
            }
        }

        val result = controller.waitForResponse(
            preSnapshot = preSnapshot,
            timeoutMs = 5000L,
            pollIntervalMs = 50L,
        )

        assertTrue(result.found, "New identical response should be recognized as a distinct turn")
        assertEquals(identicalText, result.text)
    }

    // 4. Two consecutive responses with different text not mixed up
    @Test
    fun testTwoConsecutiveResponsesDifferentText(): Unit = runBlocking {
        val fakeExecutor = FakeScriptExecutor()
        val controller = GeminiDomController(fakeExecutor)

        val preSnapshot = GeminiConversationSnapshot(
            modelResponseCount = 1,
            lastModelResponse = "Answer Alpha",
            modelMessageIds = listOf("model-1"),
        )

        var pollCount = 0
        fakeExecutor.scriptHandler = { script ->
            when {
                script.contains("isDocumentLoaded") ->
                    createScriptResultResponse(createDefaultPageState())
                script.contains("modelNodes") -> {
                    pollCount++
                    val list = if (pollCount <= 2) {
                        listOf(createMessageItem("model-1", "Answer Alpha"))
                    } else {
                        listOf(
                            createMessageItem("model-1", "Answer Alpha"),
                            createMessageItem("model-2", "Answer Beta"),
                        )
                    }
                    createScriptResultResponse(mapOf("responses" to SimpleJsonValue.JsonArray(list)))
                }
                else -> createScriptResultResponse(emptyMap())
            }
        }

        val result = controller.waitForResponse(preSnapshot = preSnapshot, timeoutMs = 5000L, pollIntervalMs = 50L)
        assertTrue(result.found)
        assertEquals("Answer Beta", result.text)
    }

    // 5. No response -> timeout
    @Test
    fun testNoResponseTimeout(): Unit = runBlocking {
        val fakeExecutor = FakeScriptExecutor()
        val controller = GeminiDomController(fakeExecutor)

        fakeExecutor.scriptHandler = { script ->
            when {
                script.contains("isDocumentLoaded") ->
                    createScriptResultResponse(createDefaultPageState(isGenerating = false))
                script.contains("modelNodes") ->
                    createScriptResultResponse(mapOf("responses" to SimpleJsonValue.JsonArray(emptyList())))
                else -> createScriptResultResponse(emptyMap())
            }
        }

        val result = controller.waitForResponse(timeoutMs = 200L, pollIntervalMs = 50L)
        assertFalse(result.found)
        assertEquals("TIMEOUT_WAITING_FOR_RESPONSE", result.error)
        assertEquals("", result.text)
    }

    // 6. Partial response while generating -> timeout returns found=false and populates partialText
    @Test
    fun testPartialResponseWhileGeneratingReturnsFoundFalse(): Unit = runBlocking {
        val fakeExecutor = FakeScriptExecutor()
        val controller = GeminiDomController(fakeExecutor)

        val preSnapshot = GeminiConversationSnapshot(modelResponseCount = 0)

        fakeExecutor.scriptHandler = { script ->
            when {
                script.contains("isDocumentLoaded") ->
                    // isGenerating stays TRUE constantly
                    createScriptResultResponse(createDefaultPageState(isGenerating = true))
                script.contains("modelNodes") -> {
                    val list = listOf(createMessageItem("model-1", "Streaming chunk 1 2 3..."))
                    createScriptResultResponse(mapOf("responses" to SimpleJsonValue.JsonArray(list)))
                }
                else -> createScriptResultResponse(emptyMap())
            }
        }

        val result = controller.waitForResponse(preSnapshot = preSnapshot, timeoutMs = 300L, pollIntervalMs = 50L)
        assertFalse(result.found, "Must NOT return found=true if generation is still active when timeout occurs")
        assertEquals("", result.text)
        assertEquals("Streaming chunk 1 2 3...", result.partialText)
        assertEquals("TIMEOUT_WAITING_FOR_RESPONSE", result.error)
    }

    // 7. Empty prompt rejection
    @Test
    fun testEmptyOrWhitespacePromptRejected(): Unit = runBlocking {
        val fakeExecutor = FakeScriptExecutor()
        val controller = GeminiDomController(fakeExecutor)

        val emptyRes = controller.sendPrompt("")
        assertFalse(emptyRes.success)
        assertEquals("EMPTY_PROMPT", emptyRes.error)

        val whitespaceRes = controller.sendPrompt("   \t\n  ")
        assertFalse(whitespaceRes.success)
        assertEquals("EMPTY_PROMPT", whitespaceRes.error)
    }

    // 8. Editor not empty rejection
    @Test
    fun testEditorNotEmptyReturnsError(): Unit = runBlocking {
        val fakeExecutor = FakeScriptExecutor()
        val controller = GeminiDomController(fakeExecutor)

        fakeExecutor.scriptHandler = { script ->
            when {
                script.contains("isDocumentLoaded") ->
                    createScriptResultResponse(createDefaultPageState(isEditorEmpty = false))
                else -> createScriptResultResponse(emptyMap())
            }
        }

        val result = controller.sendPrompt("Hello world")
        assertFalse(result.success)
        assertEquals("EDITOR_NOT_EMPTY", result.error)
    }

    // 9. Inserted text mismatch
    @Test
    fun testInsertedTextMismatch(): Unit = runBlocking {
        val fakeExecutor = FakeScriptExecutor()
        val controller = GeminiDomController(fakeExecutor)

        fakeExecutor.scriptHandler = { script ->
            when {
                script.contains("isDocumentLoaded") ->
                    createScriptResultResponse(createDefaultPageState(isEditorEmpty = true))
                script.contains("userNodes") ->
                    createScriptResultResponse(mapOf("userQueryCount" to SimpleJsonValue.JsonNumber(0.0)))
                script.contains("insertText") ->
                    createScriptResultResponse(mapOf(
                        "success" to SimpleJsonValue.JsonBoolean(true),
                        "editorText" to SimpleJsonValue.JsonString("Something else entirely"),
                    ))
                else -> createScriptResultResponse(emptyMap())
            }
        }

        val result = controller.sendPrompt("Expected prompt")
        assertFalse(result.success)
        assertEquals("INSERTED_TEXT_MISMATCH", result.error)
    }

    // 10. Editor cleared but user message did not appear -> SEND_NOT_CONFIRMED
    @Test
    fun testEditorClearedButMessageDidNotAppear(): Unit = runBlocking {
        val fakeExecutor = FakeScriptExecutor()
        val controller = GeminiDomController(fakeExecutor)

        fakeExecutor.scriptHandler = { script ->
            when {
                script.contains("isDocumentLoaded") ->
                    createScriptResultResponse(createDefaultPageState(isEditorEmpty = true))
                script.contains("userNodes") ->
                    // User message count never increases and text never appears
                    createScriptResultResponse(mapOf(
                        "userQueryCount" to SimpleJsonValue.JsonNumber(0.0),
                        "lastUserQuery" to SimpleJsonValue.JsonString(""),
                    ))
                script.contains("insertText") ->
                    createScriptResultResponse(mapOf(
                        "success" to SimpleJsonValue.JsonBoolean(true),
                        "editorText" to SimpleJsonValue.JsonString("My Prompt"),
                    ))
                script.contains("sendBtn") ->
                    createScriptResultResponse(mapOf("triggered" to SimpleJsonValue.JsonBoolean(true)))
                else -> createScriptResultResponse(emptyMap())
            }
        }

        val result = controller.sendPrompt("My Prompt")
        assertFalse(result.success)
        assertEquals("SEND_NOT_CONFIRMED", result.error)
    }

    // 11. Send button exists but disabled -> no Enter dispatch
    @Test
    fun testSendButtonDisabledDoesNotDispatchEnter(): Unit = runBlocking {
        val fakeExecutor = FakeScriptExecutor()
        val controller = GeminiDomController(fakeExecutor)

        fakeExecutor.scriptHandler = { script ->
            when {
                script.contains("isDocumentLoaded") ->
                    createScriptResultResponse(createDefaultPageState(isEditorEmpty = true))
                script.contains("userNodes") ->
                    createScriptResultResponse(mapOf("userQueryCount" to SimpleJsonValue.JsonNumber(0.0)))
                script.contains("insertText") ->
                    createScriptResultResponse(mapOf(
                        "success" to SimpleJsonValue.JsonBoolean(true),
                        "editorText" to SimpleJsonValue.JsonString("Hello"),
                    ))
                script.contains("sendBtn") ->
                    // sendBtn is disabled
                    createScriptResultResponse(mapOf(
                        "triggered" to SimpleJsonValue.JsonBoolean(false),
                        "error" to SimpleJsonValue.JsonString("SEND_BUTTON_DISABLED"),
                    ))
                else -> createScriptResultResponse(emptyMap())
            }
        }

        val result = controller.sendPrompt("Hello")
        assertFalse(result.success)
        assertEquals("SEND_BUTTON_DISABLED", result.error)
    }

    // 12. No duplicate submission
    @Test
    fun testNoDuplicateSubmissionOnFailure(): Unit = runBlocking {
        val fakeExecutor = FakeScriptExecutor()
        val controller = GeminiDomController(fakeExecutor)

        fakeExecutor.scriptHandler = { script ->
            when {
                script.contains("isDocumentLoaded") ->
                    createScriptResultResponse(createDefaultPageState(isEditorEmpty = true))
                script.contains("userNodes") ->
                    createScriptResultResponse(mapOf("userQueryCount" to SimpleJsonValue.JsonNumber(0.0)))
                script.contains("insertText") ->
                    createScriptResultResponse(mapOf("success" to SimpleJsonValue.JsonBoolean(false), "error" to SimpleJsonValue.JsonString("INSERT_FAILED")))
                else -> createScriptResultResponse(emptyMap())
            }
        }

        val result = controller.sendPrompt("Prompt")
        assertFalse(result.success)
        val triggerCalls = fakeExecutor.executedScripts.filter { it.contains("sendBtn.click") }
        assertEquals(0, triggerCalls.size)
    }

    // 13. Invalid hostname rejected
    @Test
    fun testInvalidHostnameRejected(): Unit = runBlocking {
        val fakeExecutor = FakeScriptExecutor()
        val controller = GeminiDomController(fakeExecutor)

        fakeExecutor.scriptHandler = { script ->
            when {
                script.contains("isDocumentLoaded") ->
                    createScriptResultResponse(createDefaultPageState(isGeminiUrl = false))
                else -> createScriptResultResponse(emptyMap())
            }
        }

        val result = controller.sendPrompt("Prompt")
        assertFalse(result.success)
        assertEquals("NOT_GEMINI_PAGE", result.error)
    }

    // 14. Cancellation test verifying actual cancellation propagation
    @Test
    fun testCancellationPropagatesThroughWaitForResponse(): Unit = runBlocking {
        val fakeExecutor = FakeScriptExecutor()
        val controller = GeminiDomController(fakeExecutor)

        fakeExecutor.scriptHandler = {
            createScriptResultResponse(createDefaultPageState(isGenerating = true))
        }

        var exceptionCaught: Throwable? = null
        val job = launch {
            try {
                controller.waitForResponse(timeoutMs = 60000L, pollIntervalMs = 50L)
            } catch (e: Throwable) {
                exceptionCaught = e
                throw e
            }
        }

        delay(100L)
        job.cancel()
        job.join()

        assertTrue(job.isCancelled, "Job should be cancelled")
        assertTrue(exceptionCaught is CancellationException, "CancellationException should have been thrown during operation")
    }

    // 15. Strict JSON Parser conformance tests
    @Test
    fun testJsonParserStrictValidation() {
        // Empty input
        assertFailsWith<IllegalArgumentException> { SimpleJsonValue.parse("") }
        assertFailsWith<IllegalArgumentException> { SimpleJsonValue.parse("   ") }

        // Unclosed object / array
        assertFailsWith<IllegalArgumentException> { SimpleJsonValue.parse("{\"key\": 1") }
        assertFailsWith<IllegalArgumentException> { SimpleJsonValue.parse("[1, 2") }

        // Extraneous characters after JSON
        assertFailsWith<IllegalArgumentException> { SimpleJsonValue.parse("{\"a\":1} trailing") }
        assertFailsWith<IllegalArgumentException> { SimpleJsonValue.parse("123 456") }

        // Unclosed string
        assertFailsWith<IllegalArgumentException> { SimpleJsonValue.parse("\"hello") }

        // Unknown escape
        assertFailsWith<IllegalArgumentException> { SimpleJsonValue.parse("\"\\x\"") }

        // Invalid unicode escape
        assertFailsWith<IllegalArgumentException> { SimpleJsonValue.parse("\"\\u12\"") }
        assertFailsWith<IllegalArgumentException> { SimpleJsonValue.parse("\"\\uZZZZ\"") }

        // Unescaped control character
        assertFailsWith<IllegalArgumentException> { SimpleJsonValue.parse("\"\n\"") }
        assertFailsWith<IllegalArgumentException> { SimpleJsonValue.parse("\"\t\"") }

        // Invalid numbers
        assertFailsWith<IllegalArgumentException> { SimpleJsonValue.parse("01") }
        assertFailsWith<IllegalArgumentException> { SimpleJsonValue.parse("1.") }
        assertFailsWith<IllegalArgumentException> { SimpleJsonValue.parse("1e") }
        assertFailsWith<IllegalArgumentException> { SimpleJsonValue.parse("-") }

        // Valid numbers with exponents
        val num1 = SimpleJsonValue.parse("1.25e2") as SimpleJsonValue.JsonNumber
        assertEquals(125.0, num1.value)
        val num2 = SimpleJsonValue.parse("-3.5E-1") as SimpleJsonValue.JsonNumber
        assertEquals(-0.35, num2.value)

        // Cyrillic & Unicode surrogates
        val cyr = SimpleJsonValue.parse("\"Привет, \uD83D\uDE00 мир!\"") as SimpleJsonValue.JsonString
        assertTrue(cyr.value.contains("Привет"))

        // Markdown text roundtrip
        val md = """
            {
                "content": "# Title\n\n```kotlin\nval x = 1\n```\n- item 1\n- item 2"
            }
        """.trimIndent()
        val parsedMd = SimpleJsonValue.parse(md) as SimpleJsonValue.JsonObject
        assertTrue(parsedMd.getString("content")!!.contains("```kotlin"))
    }
}
