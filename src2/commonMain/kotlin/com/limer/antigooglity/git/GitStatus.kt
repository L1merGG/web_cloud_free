package com.limer.antigooglity.git

data class GitStatus(
    val isClean: Boolean,
    val uncommittedChangesCount: Int = 0,
    val branch: String? = null,
    val summary: String = if (isClean) "Clean" else "$uncommittedChangesCount uncommitted changes",
)
