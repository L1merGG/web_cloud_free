package com.limer.antigooglity.tools.terminal

import com.limer.antigooglity.agent.Agent
import com.limer.antigooglity.agent.AgentConfig
import com.limer.antigooglity.model.ModelResponse
import com.limer.antigooglity.model.mock.MockModelAdapter
import com.limer.antigooglity.tools.ToolCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TerminalToolExecutorTest {

    private lateinit var tempDir: Path
    private lateinit var executor: TerminalToolExecutor
    private val javaCmd = "java"

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("antigooglity-terminal-test-").toRealPath()
        executor = TerminalToolExecutor(
            TerminalConfig(
                rootDirectory = tempDir,
                allowedCommands = setOf(javaCmd),
                timeout = 15.seconds,
            )
        )
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    // 1. Успешное выполнение разрешённой команды
    // 2. Получение stdout
    // 4. Правильное получение exitCode
    @Test
    fun testSuccessfulExecutionAndStdout() = runBlocking {
        val call = ToolCall(
            id = "c1",
            name = "execute_command",
            arguments = """{"command": "$javaCmd", "args": ["--help"]}""",
        )
        val result = executor.execute(call)
        assertTrue(result.success, "Command should succeed: ${result.content}")
        assertTrue(result.content.contains("Exit code: 0"))
        assertTrue(result.content.contains("Usage: java") || result.content.contains("options"))
    }

    // 3. Получение stderr
    // 5. Обработка ненулевого exitCode
    @Test
    fun testStderrAndNonZeroExitCode() = runBlocking {
        val call = ToolCall(
            id = "c2",
            name = "execute_command",
            arguments = """{"command": "$javaCmd", "args": ["-invalid_option_xyz"]}""",
        )
        val result = executor.execute(call)
        assertFalse(result.success, "Command should fail with non-zero exit code")
        assertTrue(result.content.contains("Exit code: 1") || result.content.contains("Exit code: 2"))
        assertTrue(result.content.contains("Unrecognized option") || result.content.contains("Error"))
    }

    // 6. Запрет неизвестной команды (строго заблокированной)
    @Test
    fun testBlockedCommandForbidden() = runBlocking {
        val call = ToolCall(
            id = "c3",
            name = "execute_command",
            arguments = """{"command": "format", "args": ["C:"]}""",
        )
        val result = executor.execute(call)
        assertFalse(result.success)
        assertTrue(result.content.contains("strictly forbidden"))
    }

    // 7. Запрет произвольных команд при отключённом разрешении
    @Test
    fun testArbitraryCommandForbidden() = runBlocking {
        val call = ToolCall(
            id = "c4",
            name = "execute_command",
            arguments = """{"command": "unlisted_tool_abc", "args": []}""",
        )
        val result = executor.execute(call)
        assertFalse(result.success)
        assertTrue(result.content.contains("not in the allowed commands list"))
    }

    // 8. Запрет выхода рабочей директории за пределы workspace
    @Test
    fun testWorkingDirectoryOutsideWorkspaceForbidden() = runBlocking {
        val call = ToolCall(
            id = "c5",
            name = "execute_command",
            arguments = """{"command": "$javaCmd", "args": ["--help"], "workingDirectory": "../../"}""",
        )
        val result = executor.execute(call)
        assertFalse(result.success)
        assertTrue(result.content.contains("Security violation") || result.content.contains("Access denied"))
    }

    // 9. Запрет запуска из несуществующей директории
    @Test
    fun testNonExistentWorkingDirectoryForbidden() = runBlocking {
        val call = ToolCall(
            id = "c6",
            name = "execute_command",
            arguments = """{"command": "$javaCmd", "args": ["--help"], "workingDirectory": "no_such_folder"}""",
        )
        val result = executor.execute(call)
        assertFalse(result.success)
        assertTrue(
            result.content.contains("not found") ||
            result.content.contains("Invalid arguments") ||
            result.content.contains("is not a directory")
        )
    }

    // 10. Таймаут длительного процесса
    @Test
    fun testProcessTimeout() = runBlocking {
        val sleepSource = """
            public class SleepApp {
                public static void main(String[] args) throws Exception {
                    Thread.sleep(10000);
                }
            }
        """.trimIndent()
        Files.writeString(tempDir.resolve("SleepApp.java"), sleepSource)

        val timeoutExecutor = TerminalToolExecutor(
            TerminalConfig(
                rootDirectory = tempDir,
                allowedCommands = setOf(javaCmd),
                timeout = 500.milliseconds,
            )
        )

        val call = ToolCall(
            id = "c7",
            name = "execute_command",
            arguments = """{"command": "$javaCmd", "args": ["SleepApp.java"]}""",
        )
        val result = timeoutExecutor.execute(call)
        assertFalse(result.success)
        assertTrue(result.content.contains("TIMED OUT"))
    }

    // 11. Ограничение stdout
    @Test
    fun testStdoutTruncation() = runBlocking {
        val limitedExecutor = TerminalToolExecutor(
            TerminalConfig(
                rootDirectory = tempDir,
                allowedCommands = setOf(javaCmd),
                maxStdoutSizeBytes = 50L,
            )
        )
        val call = ToolCall(
            id = "c8",
            name = "execute_command",
            arguments = """{"command": "$javaCmd", "args": ["--help"]}""",
        )
        val result = limitedExecutor.execute(call)
        assertTrue(result.content.contains("Warning: Output was truncated"))
    }

    // 12. Ограничение stderr
    @Test
    fun testStderrTruncation() = runBlocking {
        val limitedExecutor = TerminalToolExecutor(
            TerminalConfig(
                rootDirectory = tempDir,
                allowedCommands = setOf(javaCmd),
                maxStderrSizeBytes = 10L,
            )
        )
        val call = ToolCall(
            id = "c9",
            name = "execute_command",
            arguments = """{"command": "$javaCmd", "args": ["-invalid_option_xyz"]}""",
        )
        val result = limitedExecutor.execute(call)
        assertTrue(result.content.contains("Warning: Output was truncated"))
    }

    // 13. Обработка неизвестного инструмента
    @Test
    fun testUnknownToolReturnsError() = runBlocking {
        val call = ToolCall(
            id = "c10",
            name = "unknown_terminal_tool",
            arguments = """{}""",
        )
        val result = executor.execute(call)
        assertFalse(result.success)
        assertTrue(result.content.contains("Unknown tool: 'unknown_terminal_tool'"))
    }

    // 14. Корректная обработка CancellationException
    @Test
    fun testCancellationExceptionHandling() = runBlocking {
        val sleepSource = """
            public class SleepApp2 {
                public static void main(String[] args) throws Exception {
                    Thread.sleep(10000);
                }
            }
        """.trimIndent()
        Files.writeString(tempDir.resolve("SleepApp2.java"), sleepSource)

        val terminalExecutor = TerminalExecutor(
            TerminalConfig(
                rootDirectory = tempDir,
                allowedCommands = setOf(javaCmd),
                timeout = 10.seconds,
            )
        )

        val job = launch {
            terminalExecutor.execute(
                TerminalCommand(command = javaCmd, args = listOf("SleepApp2.java"))
            )
        }

        delay(300)
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    // 15. Обработка отсутствующего исполняемого файла
    @Test
    fun testMissingExecutableHandling() = runBlocking {
        val allowArbitraryExecutor = TerminalToolExecutor(
            TerminalConfig(
                rootDirectory = tempDir,
                allowArbitraryCommands = true,
            )
        )
        val call = ToolCall(
            id = "c12",
            name = "execute_command",
            arguments = """{"command": "non_existent_exe_99999"}""",
        )
        val result = allowArbitraryExecutor.execute(call)
        assertFalse(result.success)
        assertTrue(result.content.contains("Process start failed") || result.content.contains("not found"))
    }

    // Проверка опасных команд и подтверждения
    @Test
    fun testDangerousCommandRequiresApproval() = runBlocking {
        val dangerousExecutor = TerminalToolExecutor(
            TerminalConfig(
                rootDirectory = tempDir,
                commandApprover = null,
            )
        )
        val call = ToolCall(
            id = "c13",
            name = "execute_command",
            arguments = """{"command": "rm", "args": ["-rf", "foo"]}""",
        )
        val result = dangerousExecutor.execute(call)
        assertFalse(result.success)
        assertTrue(result.content.contains("Command requires user approval"))
    }

    // АУДИТ 1: Попытка запуска исполняемого файла через посторонний абсолютный путь
    @Test
    fun testForeignAbsolutePathForbiddenEvenIfBasenameMatches() = runBlocking {
        val outsideFile = Files.createTempFile("fake_java_", ".exe")
        try {
            val call = ToolCall(
                id = "audit_1",
                name = "execute_command",
                arguments = """{"command": "${outsideFile.toAbsolutePath().toString().replace('\\', '/')}"}""",
            )
            val result = executor.execute(call)
            assertFalse(result.success)
            assertTrue(
                result.content.contains("Absolute executable path") ||
                result.content.contains("forbidden") ||
                result.content.contains("Security violation")
            )
        } finally {
            Files.deleteIfExists(outsideFile)
        }
    }

    // АУДИТ 2: Попытка обхода через относительный путь в workspace с разрешённым именем файла
    @Test
    fun testWorkspaceRelativeExecutableForbiddenIfNotExplicitlyAllowed() = runBlocking {
        val subDir = tempDir.resolve("evil_sub")
        Files.createDirectories(subDir)
        val fakeBin = subDir.resolve("java.exe")
        Files.writeString(fakeBin, "echo malicious")

        val call = ToolCall(
            id = "audit_2",
            name = "execute_command",
            arguments = """{"command": "evil_sub/java.exe"}""",
        )
        val result = executor.execute(call)
        assertFalse(result.success)
        assertTrue(result.content.contains("is not in the allowed commands list") || result.content.contains("Security violation"))
    }

    // АУДИТ 3: Поведение с .bat / .cmd на Windows (запрет по умолчанию)
    @Test
    fun testWindowsBatchScriptForbiddenByDefault() = runBlocking {
        val isWindows = System.getProperty("os.name")?.lowercase(Locale.ROOT)?.contains("win") == true
        if (!isWindows) {
            return@runBlocking
        }

        val batFile = tempDir.resolve("script.bat")
        Files.writeString(batFile, "@echo off\r\necho hello\r\n")

        val call = ToolCall(
            id = "audit_3",
            name = "execute_command",
            arguments = """{"command": "script.bat"}""",
        )
        val result = executor.execute(call)
        assertFalse(result.success)
        assertTrue(result.content.contains("Windows batch scripts (.bat, .cmd) is disabled by security policy"))
    }

    // АУДИТ 4: Ограничение памяти при большом выводе (потоковая фильтрация)
    @Test
    fun testLargeOutputMemoryBounded() = runBlocking {
        val largeOutputSource = """
            public class LargeOutputApp {
                public static void main(String[] args) {
                    for (int i = 0; i < 2000; i++) {
                        System.out.println("Line number " + i + " with some repeated text padding data 1234567890");
                    }
                }
            }
        """.trimIndent()
        Files.writeString(tempDir.resolve("LargeOutputApp.java"), largeOutputSource)

        val boundedExecutor = TerminalToolExecutor(
            TerminalConfig(
                rootDirectory = tempDir,
                allowedCommands = setOf(javaCmd),
                maxStdoutSizeBytes = 100L,
            )
        )

        val call = ToolCall(
            id = "audit_4",
            name = "execute_command",
            arguments = """{"command": "$javaCmd", "args": ["LargeOutputApp.java"]}""",
        )
        val result = boundedExecutor.execute(call)
        assertTrue(result.success)
        assertTrue(result.content.contains("Warning: Output was truncated"))
        val stdoutPart = result.content.substringAfter("--- stdout ---").substringBefore("--- stderr ---").trim()
        assertTrue(stdoutPart.length <= 150, "Stored stdout must be bounded by limit")
    }

    // АУДИТ 5: Корректное усечение UTF-8 без повреждения многобайтовых символов
    @Test
    fun testUtf8TruncationPreservesValidCharsWithoutReplacement() = runBlocking {
        // Записываем явные байты UTF-8:
        // 'П' = 0xD0, 0x9F (2 байта)
        // 'р' = 0xD1, 0x80 (2 байта)
        // 'и' = 0xD0, 0xB8 (2 байта)
        // 'в' = 0xD0, 0xB2 (2 байта)
        val utf8Source = """
            public class Utf8App {
                public static void main(String[] args) throws Exception {
                    byte[] bytes = new byte[] {
                        (byte) 0xD0, (byte) 0x9F,
                        (byte) 0xD1, (byte) 0x80,
                        (byte) 0xD0, (byte) 0xB8,
                        (byte) 0xD0, (byte) 0xB2
                    };
                    System.out.write(bytes);
                    System.out.flush();
                }
            }
        """.trimIndent()
        Files.writeString(tempDir.resolve("Utf8App.java"), utf8Source)

        // 5 байт обрезает прямо посередине третьего символа 'и' (байт 0xD0)
        val utf8Executor = TerminalToolExecutor(
            TerminalConfig(
                rootDirectory = tempDir,
                allowedCommands = setOf(javaCmd),
                maxStdoutSizeBytes = 5L,
            )
        )

        val call = ToolCall(
            id = "audit_5",
            name = "execute_command",
            arguments = """{"command": "$javaCmd", "args": ["Utf8App.java"]}""",
        )
        val result = utf8Executor.execute(call)
        assertTrue(result.success)
        assertFalse(result.content.contains('\uFFFD'), "Truncated UTF-8 must not contain replacement character \\uFFFD")
        assertTrue(result.content.contains("Пр"), "Must preserve complete preceding characters 'Пр'")
    }

    // 5.2.1: Сохранение легитимного символа U+FFFD при декодировании
    @Test
    fun testRealUfffdCharacterPreserved() = runBlocking {
        val ufffdSource = """
            public class UfffdApp {
                public static void main(String[] args) throws Exception {
                    byte[] bytes = new byte[] {
                        'P', 'r', 'e', 'f', 'i', 'x', '_',
                        (byte) 0xEF, (byte) 0xBF, (byte) 0xBD,
                        '_', 'S', 'u', 'f', 'f', 'i', 'x'
                    };
                    System.out.write(bytes);
                    System.out.flush();
                }
            }
        """.trimIndent()
        Files.writeString(tempDir.resolve("UfffdApp.java"), ufffdSource)

        val call = ToolCall(
            id = "test_real_ufffd",
            name = "execute_command",
            arguments = """{"command": "$javaCmd", "args": ["UfffdApp.java"]}""",
        )
        val result = executor.execute(call)
        assertTrue(result.success)
        assertTrue(result.content.contains('\uFFFD'), "Legitimate \\uFFFD character must be preserved in output")
        assertTrue(result.content.contains("Prefix_\uFFFD_Suffix"))
    }

    // 5.2.2: Переносимое завершение дерева дочерних процессов при таймауте
    @Test
    fun testChildProcessTreeTerminationOnTimeout() = runBlocking {
        val childSource = """
            public class ChildApp {
                public static void main(String[] args) throws Exception {
                    Thread.sleep(60000);
                }
            }
        """.trimIndent()
        Files.writeString(tempDir.resolve("ChildApp.java"), childSource)

        val parentSource = """
            import java.io.File;
            import java.nio.file.Files;
            import java.nio.file.Path;

            public class ParentApp {
                public static void main(String[] args) throws Exception {
                    String javaBin = ProcessHandle.current().info().command().orElse("java");
                    Process child = new ProcessBuilder(javaBin, "ChildApp.java")
                        .directory(new File("."))
                        .start();

                    long parentPid = ProcessHandle.current().pid();
                    long childPid = child.pid();

                    Files.writeString(Path.of("pids_timeout.txt"), parentPid + "," + childPid);

                    child.waitFor();
                }
            }
        """.trimIndent()
        Files.writeString(tempDir.resolve("ParentApp.java"), parentSource)

        val timeoutExecutor = TerminalToolExecutor(
            TerminalConfig(
                rootDirectory = tempDir,
                allowedCommands = setOf(javaCmd),
                timeout = 2.seconds,
            )
        )

        val call = ToolCall(
            id = "tree_timeout_call",
            name = "execute_command",
            arguments = """{"command": "$javaCmd", "args": ["ParentApp.java"]}""",
        )
        val result = timeoutExecutor.execute(call)
        assertFalse(result.success)
        assertTrue(result.content.contains("TIMED OUT"))

        val pidsFile = tempDir.resolve("pids_timeout.txt")
        assertTrue(Files.exists(pidsFile), "pids_timeout.txt must be created by ParentApp")
        val (parentPidStr, childPidStr) = Files.readString(pidsFile).trim().split(",")
        val parentPid = parentPidStr.toLong()
        val childPid = childPidStr.toLong()

        var parentAlive = true
        var childAlive = true
        for (i in 0 until 50) {
            parentAlive = ProcessHandle.of(parentPid).map { it.isAlive }.orElse(false)
            childAlive = ProcessHandle.of(childPid).map { it.isAlive }.orElse(false)
            if (!parentAlive && !childAlive) break
            delay(100)
        }
        assertFalse(parentAlive, "Parent process (PID $parentPid) must be terminated")
        assertFalse(childAlive, "Child process (PID $childPid) must be terminated")
    }

    // 5.2.3: Переносимое завершение дерева дочерних процессов при отмене корутины
    @Test
    fun testChildProcessTreeTerminationOnCancellation() = runBlocking {
        val childSource = """
            public class ChildCancelApp {
                public static void main(String[] args) throws Exception {
                    Thread.sleep(60000);
                }
            }
        """.trimIndent()
        Files.writeString(tempDir.resolve("ChildCancelApp.java"), childSource)

        val parentSource = """
            import java.io.File;
            import java.nio.file.Files;
            import java.nio.file.Path;

            public class ParentCancelApp {
                public static void main(String[] args) throws Exception {
                    String javaBin = ProcessHandle.current().info().command().orElse("java");
                    Process child = new ProcessBuilder(javaBin, "ChildCancelApp.java")
                        .directory(new File("."))
                        .start();

                    long parentPid = ProcessHandle.current().pid();
                    long childPid = child.pid();

                    Files.writeString(Path.of("pids_cancel.txt"), parentPid + "," + childPid);

                    child.waitFor();
                }
            }
        """.trimIndent()
        Files.writeString(tempDir.resolve("ParentCancelApp.java"), parentSource)

        val terminalExecutor = TerminalExecutor(
            TerminalConfig(
                rootDirectory = tempDir,
                allowedCommands = setOf(javaCmd),
                timeout = 30.seconds,
            )
        )

        val job = launch {
            terminalExecutor.execute(
                TerminalCommand(command = javaCmd, args = listOf("ParentCancelApp.java"))
            )
        }

        val pidsFile = tempDir.resolve("pids_cancel.txt")
        // Ждём, пока ParentCancelApp запустится и создаст pids_cancel.txt
        for (i in 0 until 50) {
            if (Files.exists(pidsFile)) break
            delay(100)
        }
        assertTrue(Files.exists(pidsFile), "pids_cancel.txt must exist before cancellation")

        val (parentPidStr, childPidStr) = Files.readString(pidsFile).trim().split(",")
        val parentPid = parentPidStr.toLong()
        val childPid = childPidStr.toLong()

        job.cancelAndJoin()
        assertTrue(job.isCancelled)

        var parentAlive = true
        var childAlive = true
        for (i in 0 until 50) {
            parentAlive = ProcessHandle.of(parentPid).map { it.isAlive }.orElse(false)
            childAlive = ProcessHandle.of(childPid).map { it.isAlive }.orElse(false)
            if (!parentAlive && !childAlive) break
            delay(100)
        }
        assertFalse(parentAlive, "Parent process (PID $parentPid) must be terminated after cancellation")
        assertFalse(childAlive, "Child process (PID $childPid) must be terminated after cancellation")
    }

    // 5.2.4: Защита от binary planting внутри workspace
    @Test
    fun testBinaryPlantingInWorkspacePrevented() = runBlocking {
        // Создаём фейковый исполняемый файл с именем java (или java.exe) прямо в корне workspace
        val fakeJava = tempDir.resolve("java.exe")
        Files.writeString(fakeJava, "malicious script or invalid binary")

        // Вызов команды "java" должен разрешиться в системный JDK java, а не в локальный файл из workspace
        val call = ToolCall(
            id = "test_planting",
            name = "execute_command",
            arguments = """{"command": "$javaCmd", "args": ["--help"]}""",
        )
        val result = executor.execute(call)
        assertTrue(result.success, "Should execute real system java instead of workspace binary: ${result.content}")
        assertTrue(result.content.contains("Exit code: 0"))
        assertTrue(result.content.contains("Usage: java") || result.content.contains("options"))
    }

    // 5.2.5: Поддержка доверенных путей (trustedCommandPaths)
    @Test
    fun testTrustedCommandPathsUsage() = runBlocking {
        val javaPath = ProcessHandle.current().info().command().orElse(null)
        if (javaPath != null) {
            val customExecutor = TerminalToolExecutor(
                TerminalConfig(
                    rootDirectory = tempDir,
                    allowedCommands = setOf("myjava"),
                    trustedCommandPaths = mapOf("myjava" to Path.of(javaPath)),
                )
            )
            val call = ToolCall(
                id = "c_trusted",
                name = "execute_command",
                arguments = """{"command": "myjava", "args": ["--help"]}""",
            )
            val result = customExecutor.execute(call)
            assertTrue(result.success, "Command with trusted path must succeed: ${result.content}")
            assertTrue(result.content.contains("Exit code: 0"))
        }
    }

    // 11. Интеграционный тест: Agent + MockModelAdapter + TerminalToolExecutor
    @Test
    fun testIntegrationScenarioWithAgent() = runBlocking {
        val firstResponse = ModelResponse(
            text = "Executing version check...",
            toolCalls = listOf(
                ToolCall(
                    id = "term_call_1",
                    name = "execute_command",
                    arguments = """{"command": "$javaCmd", "args": ["--help"]}""",
                )
            ),
        )
        val secondResponse = ModelResponse(
            text = "Command executed successfully.",
            toolCalls = emptyList(),
        )
        val model = MockModelAdapter(firstResponse, secondResponse)

        val agent = Agent(
            modelAdapter = model,
            toolExecutor = executor,
            config = AgentConfig(systemPrompt = "You are a terminal assistant"),
        )

        val events = mutableListOf<com.limer.antigooglity.agent.AgentEvent>()
        val finalText = agent.run("Check java version") { events.add(it) }

        assertEquals("Command executed successfully.", finalText)

        val toolCompleted = events.filterIsInstance<com.limer.antigooglity.agent.AgentEvent.ToolCompleted>().firstOrNull()
        assertNotNull(toolCompleted)
        assertTrue(toolCompleted.result.success)
        assertTrue(toolCompleted.result.content.contains("Exit code: 0"))

        val finalContext = model.lastContext!!
        val toolMsg = finalContext.getHistory().first { it.role == com.limer.antigooglity.agent.MessageRole.TOOL }
        assertEquals("term_call_1", toolMsg.toolCallId)
        assertEquals(false, toolMsg.isError)
    }
}
