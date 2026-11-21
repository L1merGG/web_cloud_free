package com.limer.antigooglity.git

data class GitRepositoryInfo(
    val isGitRepository: Boolean,
    val rootPath: String? = null,
    val currentBranch: String? = null,
    val branches: List<GitBranch> = emptyList(),
    val status: GitStatus? = null,
    val errorMessage: String? = null,
)
