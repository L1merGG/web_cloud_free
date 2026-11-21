package com.limer.antigooglity.tools

import com.limer.antigooglity.tools.fs.FileSystemToolExecutor
import com.limer.antigooglity.tools.fs.WorkspaceConfig
import com.limer.antigooglity.tools.terminal.TerminalConfig
import com.limer.antigooglity.tools.terminal.TerminalToolExecutor
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompositeToolExecutorTest {

    private lateinit var tempDir: Path
    private lateinit var compositeExecutor: CompositeToolExecutor

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("antigooglity-composite-test-").toRealPath()
        val fsExecutor = FileSystemToolExecutor(WorkspaceConfig(rootDirectory = tempDir))
        val termExecutor = TerminalToolExecutor(TerminalConfig(rootDirectory = tempDir, allowedCommands = setOf("java")))
        compositeExecutor = CompositeToolExecutor(fsExecutor, termExecutor)
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun testDelegatesFileSystemToolCall() = runBlocking {
        val call = ToolCall(
            id = "c_fs",
            name = "write_file",
            arguments = """{"path": "sample.txt", "content": "Hello Composite"}""",
        )
        val result = compositeExecutor.execute(call)
        assertTrue(result.success, "File system call should succeed: ${result.content}")
        assertTrue(Files.exists(tempDir.resolve("sample.txt")))
    }

    @Test
    fun testDelegatesTerminalToolCall() = runBlocking {
        val call = ToolCall(
            id = "c_term",
            name = "execute_command",
            arguments = """{"command": "java", "args": ["--help"]}""",
        )
        val result = compositeExecutor.execute(call)
        assertTrue(result.success, "Terminal call should succeed: ${result.content}")
        assertTrue(result.content.contains("Exit code: 0"))
    }

    @Test
    fun testUnknownToolReturnsFailure() = runBlocking {
        val call = ToolCall(
            id = "c_unknown",
            name = "unknown_tool_xyz",
            arguments = "{}",
        )
        val result = compositeExecutor.execute(call)
        assertFalse(result.success)
        assertTrue(result.content.contains("Unknown tool") || result.content.contains("No executor available"))
    }
}
