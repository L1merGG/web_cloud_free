package com.limer.antigooglity.tools.fs

import java.nio.file.Path

data class WorkspaceConfig(
    val rootDirectory: Path,
    val allowRead: Boolean = true,
    val allowWrite: Boolean = true,
    val allowDelete: Boolean = false,
    val maxReadSizeBytes: Long = 10 * 1024 * 1024L,
    val maxWriteSizeBytes: Long = 10 * 1024 * 1024L,
    val createParentDirectories: Boolean = true,
) {
    init {
        require(maxReadSizeBytes > 0) { "maxReadSizeBytes must be positive, but was $maxReadSizeBytes" }
        require(maxWriteSizeBytes > 0) { "maxWriteSizeBytes must be positive, but was $maxWriteSizeBytes" }
    }
}
