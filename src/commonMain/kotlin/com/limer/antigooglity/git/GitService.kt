package com.limer.antigooglity.git

interface GitService {
    suspend fun isGitAvailable(): Boolean
    suspend fun getRepositoryInfo(workspacePath: String): GitRepositoryInfo
    suspend fun switchBranch(workspacePath: String, branchName: String): GitOperationResult
}
