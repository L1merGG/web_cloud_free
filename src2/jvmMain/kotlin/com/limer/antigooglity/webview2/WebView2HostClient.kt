package com.limer.antigooglity.webview2

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

interface ScriptExecutor {
    suspend fun executeScript(script: String, timeoutMs: Long = 15000L): WebView2Response
}

class WebView2HostClient(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : ScriptExecutor, AutoCloseable {

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private val writeMutex = Mutex()

    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<WebView2Response>>()
    private val stderrLogsQueue = ConcurrentLinkedQueue<String>()
    private val isClosed = AtomicBoolean(false)

    val isRunning: Boolean
        get() = process?.isAlive == true && !isClosed.get()

    val diagnosticLogs: List<String>
        get() = stderrLogsQueue.toList()

    suspend fun start(
        customHostExePath: Path? = null,
        userDataFolder: Path? = null,
        timeoutMs: Long = 15000L,
    ): WebView2Response = withContext(Dispatchers.IO) {
        val exePath = customHostExePath ?: resolveDefaultHostExe()
        require(Files.exists(exePath)) { "WebView2 Host executable not found at: $exePath" }

        val cmdArgs = mutableListOf(exePath.toAbsolutePath().toString())
        if (userDataFolder != null) {
            cmdArgs.add("--user-data-dir")
            cmdArgs.add(userDataFolder.toAbsolutePath().toString())
        }

        val pb = ProcessBuilder(cmdArgs)
        pb.redirectInput(ProcessBuilder.Redirect.PIPE)
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE)
        pb.redirectError(ProcessBuilder.Redirect.PIPE)

        // Ensure DOTNET_ROOT is populated for child process if in custom location
        val dotnetRoot = System.getenv("DOTNET_ROOT")
            ?: "${System.getProperty("user.home")}\\AppData\\Local\\Microsoft\\dotnet"
        if (File(dotnetRoot).isDirectory) {
            pb.environment()["DOTNET_ROOT"] = dotnetRoot
            val currentPath = pb.environment()["PATH"] ?: ""
            if (!currentPath.contains(dotnetRoot)) {
                pb.environment()["PATH"] = "$dotnetRoot;$currentPath"
            }
        }

        val proc = pb.start()
        process = proc
        writer = BufferedWriter(OutputStreamWriter(proc.outputStream, StandardCharsets.UTF_8))

        // Start stderr reader
        scope.launch(Dispatchers.IO) {
            val errReader = BufferedReader(InputStreamReader(proc.errorStream, StandardCharsets.UTF_8))
            try {
                while (isActive) {
                    val line = errReader.readLine() ?: break
                    stderrLogsQueue.add(line)
                    if (stderrLogsQueue.size > 200) {
                        stderrLogsQueue.poll()
                    }
                }
            } catch (_: Exception) {
            }
        }

        // Start stdout reader (JSON Lines)
        scope.launch(Dispatchers.IO) {
            val outReader = BufferedReader(InputStreamReader(proc.inputStream, StandardCharsets.UTF_8))
            try {
                while (isActive) {
                    val line = outReader.readLine() ?: break
                    try {
                        val response = WebView2Response.parse(line)
                        val deferred = pendingRequests.remove(response.requestId)
                        deferred?.complete(response)
                    } catch (e: Exception) {
                        stderrLogsQueue.add("Failed to parse host response line: '$line' - ${e.message}")
                    }
                }
            } catch (_: Exception) {
            } finally {
                // Cancel any pending requests if stream terminates
                val errResponse = WebView2Response(
                    requestId = "eof",
                    command = "EOF",
                    success = false,
                    error = "Host process stdout closed",
                )
                pendingRequests.values.forEach { it.complete(errResponse) }
                pendingRequests.clear()
            }
        }

        // Verify connection with PING, closing child process on failure
        val pingResp = try {
            ping(timeoutMs)
        } catch (e: Throwable) {
            cleanupProcess()
            if (e is CancellationException) throw e
            throw IllegalStateException("Failed to initialize WebView2 Host: ${e.message}", e)
        }

        if (!pingResp.success) {
            cleanupProcess()
            error("Failed to initialize WebView2 Host: ${pingResp.error}")
        }

        pingResp
    }

    suspend fun sendCommand(
        commandName: String,
        url: String? = null,
        script: String? = null,
        visible: Boolean? = null,
        maxLength: Int? = null,
        timeoutMs: Long = 10000L,
    ): WebView2Response = withContext(Dispatchers.IO) {
        check(isRunning) { "WebView2 Host is not running" }

        val reqId = "req-" + UUID.randomUUID().toString().take(8)
        val deferred = CompletableDeferred<WebView2Response>()
        pendingRequests[reqId] = deferred

        val cmd = WebView2Command(
            requestId = reqId,
            command = commandName,
            url = url,
            script = script,
            visible = visible,
            maxLength = maxLength,
            timeoutMs = timeoutMs.toInt(),
        )

        val json = cmd.toJson()

        try {
            writeMutex.withLock {
                val w = writer ?: throw IllegalStateException("Writer is null")
                w.write(json)
                w.newLine()
                w.flush()
            }
        } catch (e: Throwable) {
            pendingRequests.remove(reqId)
            if (e is CancellationException) throw e
            return@withContext WebView2Response(
                requestId = reqId,
                command = commandName,
                success = false,
                error = "Failed to write command '$commandName' to Host: ${e.message}",
            )
        }

        try {
            withTimeout(timeoutMs) {
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            pendingRequests.remove(reqId)
            WebView2Response(
                requestId = reqId,
                command = commandName,
                success = false,
                error = "Command '$commandName' timed out after ${timeoutMs}ms",
            )
        } catch (e: CancellationException) {
            pendingRequests.remove(reqId)
            throw e
        } catch (e: Throwable) {
            pendingRequests.remove(reqId)
            WebView2Response(
                requestId = reqId,
                command = commandName,
                success = false,
                error = "Command '$commandName' failed: ${e.message}",
            )
        }
    }

    suspend fun ping(timeoutMs: Long = 5000L): WebView2Response =
        sendCommand("PING", timeoutMs = timeoutMs)

    suspend fun getStatus(timeoutMs: Long = 5000L): WebView2Response =
        sendCommand("GET_STATUS", timeoutMs = timeoutMs)

    suspend fun getCurrentUrl(timeoutMs: Long = 5000L): WebView2Response =
        sendCommand("GET_CURRENT_URL", timeoutMs = timeoutMs)

    suspend fun getNavigationStatus(timeoutMs: Long = 5000L): WebView2Response =
        sendCommand("GET_NAVIGATION_STATUS", timeoutMs = timeoutMs)

    suspend fun setVisibility(visible: Boolean, timeoutMs: Long = 5000L): WebView2Response =
        sendCommand("SET_VISIBILITY", visible = visible, timeoutMs = timeoutMs)

    suspend fun getDocumentInfo(timeoutMs: Long = 10000L): WebView2Response =
        sendCommand("GET_DOCUMENT_INFO", timeoutMs = timeoutMs)

    suspend fun getPageText(maxLength: Int = 4000, timeoutMs: Long = 10000L): WebView2Response =
        sendCommand("GET_PAGE_TEXT", maxLength = maxLength, timeoutMs = timeoutMs)

    suspend fun navigate(url: String, timeoutMs: Long = 30000L): WebView2Response =
        sendCommand("NAVIGATE", url = url, timeoutMs = timeoutMs)

    override suspend fun executeScript(script: String, timeoutMs: Long): WebView2Response =
        sendCommand("EXECUTE_SCRIPT", script = script, timeoutMs = timeoutMs)

    suspend fun closeHost(timeoutMs: Long = 5000L): WebView2Response? {
        if (!isClosed.compareAndSet(false, true)) {
            return null
        }

        return try {
            val proc = process
            if (proc != null && proc.isAlive) {
                val reqId = "req-" + UUID.randomUUID().toString().take(8)
                val deferred = CompletableDeferred<WebView2Response>()
                pendingRequests[reqId] = deferred

                val cmd = WebView2Command(
                    requestId = reqId,
                    command = "CLOSE",
                    timeoutMs = timeoutMs.toInt(),
                )

                try {
                    writeMutex.withLock {
                        val w = writer
                        if (w != null) {
                            w.write(cmd.toJson())
                            w.newLine()
                            w.flush()
                        }
                    }
                } catch (_: Exception) {
                    pendingRequests.remove(reqId)
                    return null
                }

                try {
                    withTimeout(timeoutMs) {
                        deferred.await()
                    }
                } catch (_: Exception) {
                    null
                }
            } else null
        } catch (_: Exception) {
            null
        } finally {
            cleanupProcess()
        }
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            cleanupProcess()
        }
    }

    private fun cleanupProcess() {
        try {
            writer?.close()
        } catch (_: Exception) {}

        val proc = process
        if (proc != null && proc.isAlive) {
            try {
                proc.destroy()
                if (!proc.waitFor(1000, TimeUnit.MILLISECONDS)) {
                    proc.destroyForcibly()
                }
            } catch (_: Exception) {}
        }

        scope.cancel()
    }

    companion object {
        fun resolveDefaultHostExe(): Path {
            val candidatePaths = listOf(
                Path.of("tools/webview2-host/bin/Release/net8.0-windows/AntigooglityWebView2Host.exe"),
                Path.of("../tools/webview2-host/bin/Release/net8.0-windows/AntigooglityWebView2Host.exe"),
                Path.of("../../tools/webview2-host/bin/Release/net8.0-windows/AntigooglityWebView2Host.exe"),
                Path.of(System.getProperty("user.dir"), "tools/webview2-host/bin/Release/net8.0-windows/AntigooglityWebView2Host.exe"),
                Path.of("D:/Programs/IntelliJ IDEA 2026.2.3/Antigooglity/tools/webview2-host/bin/Release/net8.0-windows/AntigooglityWebView2Host.exe"),
            )

            for (p in candidatePaths) {
                if (Files.exists(p)) {
                    return p.toRealPath()
                }
            }

            return candidatePaths.first()
        }
    }
}
