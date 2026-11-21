package com.limer.antigooglity.tools.fs

import com.limer.antigooglity.agent.Agent
import com.limer.antigooglity.model.mock.MockModelAdapter
import com.limer.antigooglity.tools.ToolCall
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileSystemToolExecutorTest {

    private lateinit var tempDir: Path
    private lateinit var executor: FileSystemToolExecutor

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("antigooglity-fs-test-").toRealPath()
        executor = FileSystemToolExecutor(tempDir)
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    // 1. Создание файла
    @Test
    fun testCreateFile() = runBlocking {
        val call = ToolCall(
            id = "c1",
            name = "write_file",
            arguments = """{"path": "hello.txt", "content": "Hello, Antigooglity!"}""",
        )
        val result = executor.execute(call)
        assertTrue(result.success, "write_file should succeed: ${result.content}")

        val target = tempDir.resolve("hello.txt")
        assertTrue(Files.exists(target), "File should exist on disk")
        assertEquals("Hello, Antigooglity!", Files.readString(target))
    }

    // 2. Перезапись существующего файла
    @Test
    fun testOverwriteFile() = runBlocking {
        val target = tempDir.resolve("overwrite.txt")
        Files.writeString(target, "Old content")

        val call = ToolCall(
            id = "c2",
            name = "write_file",
            arguments = """{"path": "overwrite.txt", "content": "New content"}""",
        )
        val result = executor.execute(call)
        assertTrue(result.success)
        assertEquals("New content", Files.readString(target))
    }

    // 3. Чтение созданного файла
    @Test
    fun testReadFile() = runBlocking {
        val target = tempDir.resolve("read_test.py")
        Files.writeString(target, "print('testing read')")

        val call = ToolCall(
            id = "c3",
            name = "read_file",
            arguments = """{"path": "read_test.py"}""",
        )
        val result = executor.execute(call)
        assertTrue(result.success)
        assertTrue(result.content.contains("print('testing read')"))
        assertTrue(result.content.contains("read_test.py"))
    }

    // 4. Просмотр директории
    @Test
    fun testListDirectory() = runBlocking {
        Files.writeString(tempDir.resolve("file_a.txt"), "A")
        Files.createDirectories(tempDir.resolve("sub_dir"))

        val call = ToolCall(
            id = "c4",
            name = "list_directory",
            arguments = """{"path": "."}""",
        )
        val result = executor.execute(call)
        assertTrue(result.success)
        assertTrue(result.content.contains("file_a.txt (file)"))
        assertTrue(result.content.contains("sub_dir (directory)"))
        assertFalse(result.content.contains(tempDir.toString()), "Should not disclose absolute path")

        // Проверка пустой поддиректории
        val emptyCall = ToolCall(
            id = "c4_empty",
            name = "list_directory",
            arguments = """{"path": "sub_dir"}""",
        )
        val emptyResult = executor.execute(emptyCall)
        assertTrue(emptyResult.success)
        assertTrue(emptyResult.content.contains("is empty"))
    }

    // 5. Создание вложенных директорий
    @Test
    fun testCreateNestedDirectories() = runBlocking {
        val call = ToolCall(
            id = "c5",
            name = "create_directory",
            arguments = """{"path": "nested/inner/folder"}""",
        )
        val result = executor.execute(call)
        assertTrue(result.success)

        val target = tempDir.resolve("nested/inner/folder")
        assertTrue(Files.exists(target) && Files.isDirectory(target))
    }

    // 6. Удаление файла при разрешённом удалении
    @Test
    fun testDeleteFileWhenAllowed() = runBlocking {
        val deleteExecutor = FileSystemToolExecutor(
            WorkspaceConfig(rootDirectory = tempDir, allowDelete = true)
        )
        val target = tempDir.resolve("to_delete.txt")
        Files.writeString(target, "Delete me")

        val call = ToolCall(
            id = "c6",
            name = "delete_file",
            arguments = """{"path": "to_delete.txt"}""",
        )
        val result = deleteExecutor.execute(call)
        assertTrue(result.success)
        assertFalse(Files.exists(target))
    }

    // 7. Запрет удаления при отключённом разрешении (по умолчанию)
    @Test
    fun testDeleteFileProhibitedByDefault() = runBlocking {
        val target = tempDir.resolve("protected.txt")
        Files.writeString(target, "Stay safe")

        val call = ToolCall(
            id = "c7",
            name = "delete_file",
            arguments = """{"path": "protected.txt"}""",
        )
        val result = executor.execute(call)
        assertFalse(result.success)
        assertTrue(result.content.contains("Permission denied"))
        assertTrue(Files.exists(target))
    }

    // 8. Запрет чтения за пределами workspace
    @Test
    fun testReadOutsideWorkspaceForbidden() = runBlocking {
        val outsideFile = Files.createTempFile("outside-", ".txt")
        try {
            val call = ToolCall(
                id = "c8",
                name = "read_file",
                arguments = """{"path": "${outsideFile.toAbsolutePath().toString().replace('\\', '/')}"}""",
            )
            val result = executor.execute(call)
            assertFalse(result.success)
            assertTrue(result.content.contains("Security violation") || result.content.contains("Access denied"))
        } finally {
            Files.deleteIfExists(outsideFile)
        }
    }

    // 9. Запрет записи за пределами workspace
    @Test
    fun testWriteOutsideWorkspaceForbidden() = runBlocking {
        val outsidePath = tempDir.parent.resolve("evil_file.txt").toAbsolutePath().toString().replace('\\', '/')
        val call = ToolCall(
            id = "c9",
            name = "write_file",
            arguments = """{"path": "$outsidePath", "content": "malicious"}""",
        )
        val result = executor.execute(call)
        assertFalse(result.success)
        assertTrue(result.content.contains("Security violation") || result.content.contains("Access denied"))
    }

    // 10. Запрет обхода workspace через ../
    @Test
    fun testPathTraversalWithDotDotForbidden() = runBlocking {
        val call = ToolCall(
            id = "c10",
            name = "write_file",
            arguments = """{"path": "../escape.txt", "content": "escape"}""",
        )
        val result = executor.execute(call)
        assertFalse(result.success)
        assertTrue(result.content.contains("Security violation") || result.content.contains("Access denied"))
    }

    // 11. Обработка неизвестного инструмента
    @Test
    fun testUnknownToolReturnsError() = runBlocking {
        val call = ToolCall(
            id = "c11",
            name = "execute_bash",
            arguments = """{"cmd": "rm -rf /"}""",
        )
        val result = executor.execute(call)
        assertFalse(result.success)
        assertTrue(result.content.contains("Unknown tool: 'execute_bash'"))
    }

    // 12. Обработка некорректного JSON
    @Test
    fun testMalformedJsonHandling() = runBlocking {
        val call = ToolCall(
            id = "c12",
            name = "write_file",
            arguments = "{path: 'broken'",
        )
        val result = executor.execute(call)
        assertFalse(result.success)
        assertTrue(result.content.contains("Invalid arguments"))
    }

    // 13. Обработка отсутствующего файла
    @Test
    fun testMissingFileHandling() = runBlocking {
        val call = ToolCall(
            id = "c13",
            name = "read_file",
            arguments = """{"path": "non_existent.txt"}""",
        )
        val result = executor.execute(call)
        assertFalse(result.success)
        assertTrue(result.content.contains("not found"))
    }

    // 14. Ограничения размера файлов
    @Test
    fun testFileSizeLimits() = runBlocking {
        val limitedExecutor = FileSystemToolExecutor(
            WorkspaceConfig(
                rootDirectory = tempDir,
                maxWriteSizeBytes = 10L,
                maxReadSizeBytes = 10L,
            )
        )

        // Превышение размера при записи
        val writeCall = ToolCall(
            id = "c14_write",
            name = "write_file",
            arguments = """{"path": "big.txt", "content": "This content is longer than 10 bytes"}""",
        )
        val writeResult = limitedExecutor.execute(writeCall)
        assertFalse(writeResult.success)
        assertTrue(writeResult.content.contains("exceeds maximum write size"))

        // Превышение размера при чтении
        val normalTarget = tempDir.resolve("big_to_read.txt")
        Files.writeString(normalTarget, "123456789012345") // 15 bytes
        val readCall = ToolCall(
            id = "c14_read",
            name = "read_file",
            arguments = """{"path": "big_to_read.txt"}""",
        )
        val readResult = limitedExecutor.execute(readCall)
        assertFalse(readResult.success)
        assertTrue(readResult.content.contains("exceeds maximum read size"))
    }

    // 15. Защита от выхода через символическую ссылку (если поддерживается ОС)
    @Test
    fun testSymlinkEscapeForbiddenIfSupported() = runBlocking {
        val externalDir = Files.createTempDirectory("antigooglity-external-").toRealPath()
        try {
            val linkPath = tempDir.resolve("symlink_to_external")
            val symlinkCreated = try {
                Files.createSymbolicLink(linkPath, externalDir)
                true
            } catch (e: Exception) {
                // На некоторых платформах (например, Windows без прав администратора/Developer Mode)
                // создание символических ссылок может быть запрещено ОС
                false
            }

            if (symlinkCreated) {
                val call = ToolCall(
                    id = "c15",
                    name = "write_file",
                    arguments = """{"path": "symlink_to_external/file.txt", "content": "traversal"}""",
                )
                val result = executor.execute(call)
                assertFalse(result.success)
                assertTrue(result.content.contains("Security violation") || result.content.contains("Access denied"))
            }
        } finally {
            externalDir.toFile().deleteRecursively()
        }
    }

    // 8. Интеграционный тест сквозного сценария с Agent
    @Test
    fun testIntegrationScenarioWithAgent() = runBlocking {
        val model = MockModelAdapter.createDemoAdapter(
            fileName = "hello.py",
            fileContent = "print(\"Hello from Antigooglity!\")",
            finalResponseText = "Файл hello.py успешно создан.",
        )
        val agent = Agent(
            modelAdapter = model,
            toolExecutor = executor,
        )

        val finalText = agent.run("Создай файл hello.py с содержимым print(\"Hello from Antigooglity!\")")

        assertEquals("Файл hello.py успешно создан.", finalText)

        val createdFile = tempDir.resolve("hello.py")
        assertTrue(Files.exists(createdFile), "hello.py must exist in real workspace")
        assertEquals("print(\"Hello from Antigooglity!\")", Files.readString(createdFile))
    }
}
