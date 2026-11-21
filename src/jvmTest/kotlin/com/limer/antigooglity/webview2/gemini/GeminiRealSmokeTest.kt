package com.limer.antigooglity.webview2.gemini

import com.limer.antigooglity.webview2.WebView2HostClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import java.io.File
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Live Smoke Test against real Gemini web app using persisted profile.
 * Opt-in only: skipped via Assume.assumeTrue unless system property 'antigooglity.gemini.real.smoke=true' is passed.
 * Tests two consecutive conversation turns in the same chat session.
 *
 * NOTE: This test interacts with the real Gemini account and adds 2 messages to Gemini conversation history!
 */
class GeminiRealSmokeTest {

    private var client: WebView2HostClient? = null
    private var controller: GeminiDomController? = null

    @BeforeTest
    fun setUp() {
        val isOptIn = System.getProperty("antigooglity.gemini.real.smoke", "false").toBoolean() ||
            System.getenv("ANTIGOOGLITY_GEMINI_REAL_SMOKE")?.toBoolean() == true

        // Correct test framework skipping mechanism
        Assume.assumeTrue(
            "Skipping GeminiRealSmokeTest: opt-in required via -Dantigooglity.gemini.real.smoke=true or ':shared:geminiRealSmokeTest'",
            isOptIn,
        )

        val c = WebView2HostClient()
        client = c
        controller = GeminiDomController(c)
    }

    @AfterTest
    fun tearDown() {
        try {
            client?.close()
        } catch (_: Exception) {}
    }

    @Test
    fun testTwoConsecutiveGeminiTurns(): Unit = runBlocking {
        val c = client ?: error("Client not initialized")
        val ctrl = controller ?: error("Controller not initialized")

        c.start()

        // 1. Navigate to Gemini App
        val nav = c.navigate("https://gemini.google.com/app", timeoutMs = 45000L)
        assertTrue(nav.success, "Navigation failed: ${nav.error}")

        // 2. Wait for editor to be ready and accessible
        val editorReadyResult = ctrl.waitForEditorReady(timeoutMs = 20000L)
        assertTrue(editorReadyResult.ready, "Gemini editor was not ready/accessible within timeout: ${editorReadyResult.failureReason}")

        val state = ctrl.getPageState()
        assertTrue(state.hasEditor, "Gemini editor (.ql-editor) should be present in DOM")
        assertTrue(state.isGeminiUrl, "Current URL must be https://gemini.google.com")

        // === TURN 1: ALPHA ===
        val markerAlpha = "ANTIGOOGLITY_SMOKE_ALPHA_" + UUID.randomUUID().toString().replace("-", "").take(8)
        val queryAlpha = "Ответь только этим маркером и больше ничем: $markerAlpha"

        val sendRes1 = ctrl.sendPrompt(queryAlpha)
        assertTrue(sendRes1.success, "Turn 1 sendPrompt failed: ${sendRes1.error}")

        val response1 = ctrl.waitForResponse(preSnapshot = sendRes1.snapshot, timeoutMs = 60000L)
        assertTrue(response1.found, "Turn 1 response not found or not completed: ${response1.error}")
        assertTrue(response1.text.contains(markerAlpha), "Turn 1 response '${response1.text}' should contain marker '$markerAlpha'")

        // Brief delay between turns for DOM to settle
        delay(1000L)
        val readyBeforeTurn2 = ctrl.waitForEditorReady(timeoutMs = 15000L)
        assertTrue(readyBeforeTurn2.ready, "Gemini editor was not ready before Turn 2: ${readyBeforeTurn2.failureReason}")

        // === TURN 2: BETA ===
        val markerBeta = "ANTIGOOGLITY_SMOKE_BETA_" + UUID.randomUUID().toString().replace("-", "").take(8)
        val queryBeta = "Ответь только этим маркером и больше ничем: $markerBeta"

        val sendRes2 = ctrl.sendPrompt(queryBeta)
        assertTrue(sendRes2.success, "Turn 2 sendPrompt failed: ${sendRes2.error}")

        val response2 = ctrl.waitForResponse(preSnapshot = sendRes2.snapshot, timeoutMs = 60000L)
        assertTrue(response2.found, "Turn 2 response not found or not completed: ${response2.error}")
        assertTrue(response2.text.contains(markerBeta), "Turn 2 response '${response2.text}' should contain marker '$markerBeta'")

        // === VERIFY CORRELATION ===
        assertNotEquals(response1.text, response2.text, "Turn 2 response must not equal Turn 1 response")
        assertTrue(!response2.text.contains(markerAlpha), "Turn 2 response must not return Turn 1 marker")

        // Save last response artifact
        val outFile = File("build/diagnostics/gemini-last-response.txt")
        outFile.parentFile?.mkdirs()
        outFile.writeText("Turn 1: ${response1.text}\nTurn 2: ${response2.text}\n", Charsets.UTF_8)

        println("=== Gemini Real Consecutive Smoke Test Completed Successfully ===")
        println("Turn 1 ($markerAlpha): '${response1.text}'")
        println("Turn 2 ($markerBeta): '${response2.text}'")
        println("================================================================")

        c.closeHost()
    }
}
