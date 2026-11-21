package com.limer.antigooglity.tools.fs

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path

class WorkspacePathResolver(
    val rootDirectory: Path,
) {
    val realRoot: Path

    init {
        val root = rootDirectory.toAbsolutePath().normalize()
        if (!Files.exists(root)) {
            Files.createDirectories(root)
        }
        realRoot = root.toRealPath()
    }

    fun resolve(inputPath: String, mustExist: Boolean = false): Path {
        val trimmed = inputPath.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Path must not be empty")
        }

        val rawPath = Path.of(trimmed)
        val candidate = if (rawPath.isAbsolute) {
            rawPath.normalize()
        } else {
            realRoot.resolve(rawPath).normalize()
        }

        // 1. Syntactic boundary check
        if (!candidate.startsWith(realRoot)) {
            throw SecurityException("Access denied: path '$trimmed' resolves outside workspace")
        }

        // 2. Symlink boundary check across existing ancestors
        var ancestor: Path? = candidate
        while (ancestor != null && !Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) {
            ancestor = ancestor.parent
        }

        if (ancestor != null) {
            val realAncestor = try {
                ancestor.toRealPath()
            } catch (e: NoSuchFileException) {
                throw SecurityException("Access denied: broken or invalid link in path '$trimmed'")
            }
            if (!realAncestor.startsWith(realRoot)) {
                throw SecurityException("Access denied: symlink resolves outside workspace for path '$trimmed'")
            }
        }

        if (mustExist) {
            if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                throw NoSuchFileException(toRelativeString(candidate))
            }
            val realCandidate = candidate.toRealPath()
            if (!realCandidate.startsWith(realRoot)) {
                throw SecurityException("Access denied: path '$trimmed' resolves outside workspace via symlink")
            }
        }

        return candidate
    }

    fun toRelativeString(path: Path): String {
        return try {
            val normalized = path.toAbsolutePath().normalize()
            val rel = realRoot.relativize(normalized).toString().replace('\\', '/')
            if (rel.isEmpty()) "." else rel
        } catch (e: Exception) {
            path.fileName?.toString() ?: "."
        }
    }
}
