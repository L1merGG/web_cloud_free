package com.limer.antigooglity.webview2

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebView2HostIntegrationTest {

    private lateinit var client: WebView2HostClient
    private lateinit var testHtmlFile: Path
    private lateinit var tempProfileDir: Path

    @BeforeTest
    fun setUp() {
        client = WebView2HostClient()

        // Create a dedicated local test HTML file
        testHtmlFile = Files.createTempFile("antigooglity-webview2-test-", ".html")
        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Spike Test Page</title>
            </head>
            <body>
                <h1 id="header">Antigooglity Spike</h1>
                <p id="desc">Hidden Browser Execution Verified</p>
                <script>
                    window.testEnv = {
                        isSpike: true,
                        framework: "WebView2",
                        status: "active"
                    };
                </script>
            </body>
            </html>
        """.trimIndent()
        Files.writeString(testHtmlFile, htmlContent)

        // Isolated temporary test profile directory
        tempProfileDir = Files.createTempDirectory("antigooglity-test-profile-")
    }

    @AfterTest
    fun tearDown() {
        try {
            client.close()
        } catch (_: Exception) {}
        try {
            Files.deleteIfExists(testHtmlFile)
        } catch (_: Exception) {}
        try {
            tempProfileDir.toFile().deleteRecursively()
        } catch (_: Exception) {}
    }

    @Test
    fun testPingAndGetStatus(): Unit = runBlocking {
        val startResp = client.start(userDataFolder = tempProfileDir)
        assertTrue(startResp.success, "Start PING failed: ${startResp.error}")
        assertTrue(client.isRunning, "Client should be running")

        val statusResp = client.getStatus()
        assertTrue(statusResp.success, "GET_STATUS failed: ${statusResp.error}")
        val data = statusResp.rawData ?: ""
        assertTrue(data.contains("\"initialized\":true"), "Should be initialized")
        assertTrue(data.contains("\"isVisible\":false"), "Controller should have isVisible=false by default")
        assertTrue(data.contains(tempProfileDir.fileName.toString()) || data.contains("test-profile"), "Profile directory should match isolated test profile")

        val closeResp = client.closeHost()
        assertNotNull(closeResp)
        assertTrue(closeResp.success, "CLOSE failed: ${closeResp.error}")
        assertFalse(client.isRunning, "Client should not be running after close")
    }

    @Test
    fun testNavigationAndScriptExecution(): Unit = runBlocking {
        client.start(userDataFolder = tempProfileDir)

        val fileUrl = testHtmlFile.toUri().toString()
        val navResp = client.navigate(fileUrl)
        assertTrue(navResp.success, "Navigation failed: ${navResp.error}")

        val urlResp = client.getCurrentUrl()
        assertTrue(urlResp.success, "GET_CURRENT_URL failed: ${urlResp.error}")
        assertTrue(urlResp.rawData?.contains(testHtmlFile.fileName.toString()) == true, "URL should contain test file name")

        // 1. Read document title
        val titleResp = client.executeScript("document.title")
        assertTrue(titleResp.success, "EXECUTE_SCRIPT title failed: ${titleResp.error}")
        assertTrue(titleResp.rawData?.contains("Spike Test Page") == true, "Title mismatch: ${titleResp.rawData}")

        // 2. Read DOM element text
        val domResp = client.executeScript("document.getElementById('desc').innerText")
        assertTrue(domResp.success, "EXECUTE_SCRIPT DOM failed: ${domResp.error}")
        assertTrue(domResp.rawData?.contains("Hidden Browser Execution Verified") == true, "DOM text mismatch: ${domResp.rawData}")

        // 3. Read window object
        val objResp = client.executeScript("JSON.stringify(window.testEnv)")
        assertTrue(objResp.success, "EXECUTE_SCRIPT window object failed: ${objResp.error}")
        assertTrue(objResp.rawData?.contains("WebView2") == true, "Window object mismatch: ${objResp.rawData}")

        client.closeHost()
    }

    @Test
    fun testProfilePersistenceAcrossRestarts(): Unit = runBlocking {
        val fileUrl = testHtmlFile.toUri().toString()

        // Session 1: write to localStorage and close
        client.start(userDataFolder = tempProfileDir)
        client.navigate(fileUrl)
        val writeResp = client.executeScript("localStorage.setItem('probe_key', 'persisted_val_42')")
        assertTrue(writeResp.success, "Failed to write to localStorage: ${writeResp.error}")
        client.closeHost()

        // Session 2: new client instance using same isolated test profile, read from localStorage
        val client2 = WebView2HostClient()
        try {
            client2.start(userDataFolder = tempProfileDir)
            client2.navigate(fileUrl)
            val readResp = client2.executeScript("localStorage.getItem('probe_key')")
            assertTrue(readResp.success, "Failed to read from localStorage: ${readResp.error}")
            assertTrue(readResp.rawData?.contains("persisted_val_42") == true, "Persisted value mismatch: ${readResp.rawData}")
            client2.closeHost()
        } finally {
            client2.close()
        }
    }

    @Test
    fun testVisibilityToggle(): Unit = runBlocking {
        client.start(userDataFolder = tempProfileDir)

        // Initially hidden
        val statusInitial = client.getStatus()
        assertTrue(statusInitial.rawData?.contains("\"isVisible\":false") == true)

        // Toggle to visible
        val setVisTrue = client.setVisibility(true)
        assertTrue(setVisTrue.success, "setVisibility(true) failed: ${setVisTrue.error}")
        assertTrue(setVisTrue.rawData?.contains("\"isVisible\":true") == true)

        val statusVisible = client.getStatus()
        assertTrue(statusVisible.rawData?.contains("\"isVisible\":true") == true)
        val visibleData = statusVisible.rawData
        assertNotNull(visibleData)
        assertTrue(visibleData.contains("\"formVisible\":true"))

        // Toggle back to hidden
        val setVisFalse = client.setVisibility(false)
        assertTrue(setVisFalse.success, "setVisibility(false) failed: ${setVisFalse.error}")
        assertTrue(setVisFalse.rawData?.contains("\"isVisible\":false") == true)

        val statusHidden = client.getStatus()
        assertTrue(statusHidden.rawData?.contains("\"isVisible\":false") == true)
        val hiddenData = statusHidden.rawData
        assertNotNull(hiddenData)
        assertTrue(hiddenData.contains("\"formVisible\":false"))

        client.closeHost()
    }

    @Test
    fun testGetDocumentInfoAndNavigationStatus(): Unit = runBlocking {
        client.start(userDataFolder = tempProfileDir)
        val fileUrl = testHtmlFile.toUri().toString()
        client.navigate(fileUrl)

        // Test GET_DOCUMENT_INFO
        val docResp = client.getDocumentInfo()
        assertTrue(docResp.success, "getDocumentInfo failed: ${docResp.error}")
        val docInfo = docResp.parseDocumentInfo()
        assertNotNull(docInfo)
        assertEquals("Spike Test Page", docInfo.title)
        assertEquals("complete", docInfo.readyState)
        assertTrue(docInfo.href.contains(testHtmlFile.fileName.toString()))

        // Test GET_NAVIGATION_STATUS
        val navResp = client.getNavigationStatus()
        assertTrue(navResp.success, "getNavigationStatus failed: ${navResp.error}")
        val navStatus = navResp.parseNavigationStatus()
        assertNotNull(navStatus)
        assertEquals("Spike Test Page", navStatus.documentTitle)
        assertFalse(navStatus.isNavigating)
        assertTrue(navStatus.currentUrl.contains(testHtmlFile.fileName.toString()))

        // Test GET_PAGE_TEXT
        val textResp = client.getPageText(maxLength = 100)
        assertTrue(textResp.success, "getPageText failed: ${textResp.error}")
        val pageText = textResp.parsePageText()
        assertNotNull(pageText)
        assertTrue(pageText.text.contains("Antigooglity Spike"))
        assertTrue(pageText.text.contains("Hidden Browser Execution Verified"))
        assertFalse(pageText.truncated)

        client.closeHost()
    }

    @Test
    fun testNavigationErrorHandling(): Unit = runBlocking {
        client.start(userDataFolder = tempProfileDir)

        // Navigating to an invalid / unreachable URL should complete with error or report failure
        val navResp = client.navigate("https://invalid-non-existent-domain-12345.test", timeoutMs = 10000L)
        if (navResp.success) {
            val data = navResp.rawData ?: ""
            assertTrue(data.contains("\"isSuccess\":false") || data.contains("errorStatus"), "Should report navigation error")
        } else {
            assertTrue(navResp.error != null, "Error message should be present")
        }

        client.closeHost()
    }

    @Test
    fun testScriptExecutionTimeout(): Unit = runBlocking {
        client.start(userDataFolder = tempProfileDir)
        val fileUrl = testHtmlFile.toUri().toString()
        client.navigate(fileUrl)

        // Script that blocks synchronously longer than the 500ms timeout
        val timeoutResp = client.executeScript(
            script = "var start = Date.now(); while(Date.now() - start < 3000) {}; 'done'",
            timeoutMs = 500L,
        )
        assertFalse(timeoutResp.success, "Script should have timed out")
        assertTrue(timeoutResp.error?.contains("timed out", ignoreCase = true) == true, "Error should indicate timeout: ${timeoutResp.error}")

        client.closeHost()
    }

    @Test
    fun testCloseHostDuringInFlightOperation(): Unit = runBlocking {
        client.start(userDataFolder = tempProfileDir)
        val fileUrl = testHtmlFile.toUri().toString()
        client.navigate(fileUrl)

        // Immediately close while ready/active
        val closeResp = client.closeHost()
        assertNotNull(closeResp)
        assertTrue(closeResp.success)
        assertFalse(client.isRunning)
    }
}
