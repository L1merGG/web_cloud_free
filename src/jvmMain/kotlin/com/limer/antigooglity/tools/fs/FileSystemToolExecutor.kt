package com.limer.antigooglity.tools.fs

import com.limer.antigooglity.tools.ToolCall
import com.limer.antigooglity.tools.ToolExecutor
import com.limer.antigooglity.tools.ToolResult
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class FileSystemToolExecutor(
    val config: WorkspaceConfig,
) : ToolExecutor {

    constructor(workspaceRoot: Path) : this(WorkspaceConfig(rootDirectory = workspaceRoot))

    private val pathResolver = WorkspacePathResolver(config.rootDirectory)
    val realRoot: Path get() = pathResolver.realRoot

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "write_file" -> handleWriteFile(call)
                "read_file" -> handleReadFile(call)
                "list_directory" -> handleListDirectory(call)
                "create_directory" -> handleCreateDirectory(call)
                "delete_file" -> handleDeleteFile(call)
                else -> ToolResult(
                    toolCallId = call.id,
                    content = "Unknown tool: '${call.name}'",
                    success = false,
                )
            }
        } catch (e: SecurityException) {
            ToolResult(
                toolCallId = call.id,
                content = "Security violation: ${e.message}",
                success = false,
            )
        } catch (e: IllegalArgumentException) {
            ToolResult(
                toolCallId = call.id,
                content = "Invalid arguments: ${e.message}",
                success = false,
            )
        } catch (e: AccessDeniedException) {
            ToolResult(
                toolCallId = call.id,
                content = "Access denied: ${e.message}",
                success = false,
            )
        } catch (e: NoSuchFileException) {
            ToolResult(
                toolCallId = call.id,
                content = "File or directory not found: ${e.file ?: e.message}",
                success = false,
            )
        } catch (e: IOException) {
            ToolResult(
                toolCallId = call.id,
                content = "I/O error: ${e.message ?: e::class.simpleName}",
                success = false,
            )
        }
    }

    private fun handleWriteFile(call: ToolCall): ToolResult {
        if (!config.allowWrite) {
            return ToolResult(
                toolCallId = call.id,
                content = "Permission denied: write operation is disabled in workspace configuration",
                success = false,
            )
        }

        val args = ToolArgsParser.parseObject(call.arguments)
        val pathStr = args["path"] ?: args["filename"] ?: args["file"]
            ?: throw IllegalArgumentException("Missing required argument 'path'")
        val content = args["content"] ?: args["text"]
            ?: throw IllegalArgumentException("Missing required argument 'content'")

        val target = resolvePath(pathStr)
        val relPath = toRelativeString(target)

        if (target == realRoot) {
            return ToolResult(
                toolCallId = call.id,
                content = "Cannot write to workspace root directory as a file",
                success = false,
            )
        }

        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && Files.isDirectory(target)) {
            return ToolResult(
                toolCallId = call.id,
                content = "Cannot write file: '$relPath' is an existing directory",
                success = false,
            )
        }

        val bytes = content.toByteArray(Charsets.UTF_8)
        if (bytes.size > config.maxWriteSizeBytes) {
            return ToolResult(
                toolCallId = call.id,
                content = "File size (${bytes.size} bytes) exceeds maximum write size (${config.maxWriteSizeBytes} bytes)",
                success = false,
            )
        }

        val parent = target.parent
        if (parent != null && !Files.exists(parent)) {
            if (config.createParentDirectories) {
                Files.createDirectories(parent)
            } else {
                return ToolResult(
                    toolCallId = call.id,
                    content = "Parent directory does not exist for '$relPath'",
                    success = false,
                )
            }
        }

        Files.write(
            target,
            bytes,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )

        return ToolResult(
            toolCallId = call.id,
            content = "File '$relPath' written successfully (${bytes.size} bytes)",
            success = true,
        )
    }

    private fun handleReadFile(call: ToolCall): ToolResult {
        if (!config.allowRead) {
            return ToolResult(
                toolCallId = call.id,
                content = "Permission denied: read operation is disabled in workspace configuration",
                success = false,
            )
        }

        val args = ToolArgsParser.parseObject(call.arguments)
        val pathStr = args["path"] ?: args["filename"] ?: args["file"]
            ?: throw IllegalArgumentException("Missing required argument 'path'")

        val target = resolvePath(pathStr, mustExist = true)
        val relPath = toRelativeString(target)

        if (Files.isDirectory(target)) {
            return ToolResult(
                toolCallId = call.id,
                content = "Cannot read '$relPath': target is a directory",
                success = false,
            )
        }

        val size = Files.size(target)
        if (size > config.maxReadSizeBytes) {
            return ToolResult(
                toolCallId = call.id,
                content = "File '$relPath' size ($size bytes) exceeds maximum read size (${config.maxReadSizeBytes} bytes)",
                success = false,
            )
        }

        val content = Files.readString(target, Charsets.UTF_8)
        return ToolResult(
            toolCallId = call.id,
            content = "File: $relPath\n\n$content",
            success = true,
        )
    }

    private fun handleListDirectory(call: ToolCall): ToolResult {
        if (!config.allowRead) {
            return ToolResult(
                toolCallId = call.id,
                content = "Permission denied: read operation is disabled in workspace configuration",
                success = false,
            )
        }

        val args = ToolArgsParser.parseObject(call.arguments)
        val pathStr = args["path"] ?: "."

        val target = if (pathStr.trim() == "." || pathStr.trim().isEmpty()) {
            realRoot
        } else {
            resolvePath(pathStr, mustExist = true)
        }

        val relPath = toRelativeString(target)

        if (!Files.isDirectory(target)) {
            return ToolResult(
                toolCallId = call.id,
                content = "Cannot list directory: '$relPath' is not a directory",
                success = false,
            )
        }

        val entries = mutableListOf<String>()
        Files.newDirectoryStream(target).use { stream ->
            for (entry in stream) {
                val name = entry.fileName.toString()
                val type = if (Files.isDirectory(entry)) "directory" else "file"
                entries.add("$name ($type)")
            }
        }
        entries.sort()

        val content = if (entries.isEmpty()) {
            "Directory '$relPath' is empty"
        } else {
            "Directory '$relPath':\n" + entries.joinToString("\n") { "- $it" }
        }

        return ToolResult(
            toolCallId = call.id,
            content = content,
            success = true,
        )
    }

    private fun handleCreateDirectory(call: ToolCall): ToolResult {
        if (!config.allowWrite) {
            return ToolResult(
                toolCallId = call.id,
                content = "Permission denied: write operation is disabled in workspace configuration",
                success = false,
            )
        }

        val args = ToolArgsParser.parseObject(call.arguments)
        val pathStr = args["path"] ?: args["directory"]
            ?: throw IllegalArgumentException("Missing required argument 'path'")

        val target = resolvePath(pathStr)
        val relPath = toRelativeString(target)

        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isDirectory(target)) {
                return ToolResult(
                    toolCallId = call.id,
                    content = "Directory '$relPath' already exists",
                    success = true,
                )
            } else {
                return ToolResult(
                    toolCallId = call.id,
                    content = "Cannot create directory '$relPath': a file already exists at this path",
                    success = false,
                )
            }
        }

        Files.createDirectories(target)

        return ToolResult(
            toolCallId = call.id,
            content = "Directory '$relPath' created successfully",
            success = true,
        )
    }

    private fun handleDeleteFile(call: ToolCall): ToolResult {
        if (!config.allowDelete) {
            return ToolResult(
                toolCallId = call.id,
                content = "Permission denied: delete operation is disabled in workspace configuration",
                success = false,
            )
        }

        val args = ToolArgsParser.parseObject(call.arguments)
        val pathStr = args["path"] ?: args["filename"] ?: args["file"]
            ?: throw IllegalArgumentException("Missing required argument 'path'")

        val target = resolvePath(pathStr, mustExist = true)
        val relPath = toRelativeString(target)

        if (target == realRoot) {
            return ToolResult(
                toolCallId = call.id,
                content = "Cannot delete workspace root directory",
                success = false,
            )
        }

        if (Files.isDirectory(target)) {
            return ToolResult(
                toolCallId = call.id,
                content = "Cannot delete '$relPath': target is a directory (recursive deletion is disabled)",
                success = false,
            )
        }

        Files.delete(target)

        return ToolResult(
            toolCallId = call.id,
            content = "File '$relPath' deleted successfully",
            success = true,
        )
    }

    private fun resolvePath(inputPath: String, mustExist: Boolean = false): Path =
        pathResolver.resolve(inputPath, mustExist)

    fun toRelativeString(path: Path): String =
        pathResolver.toRelativeString(path)
}
