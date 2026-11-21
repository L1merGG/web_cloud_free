package com.limer.antigooglity.git

sealed interface GitOperationResult {
    data class Success(val message: String) : GitOperationResult
    data class Failure(val errorMessage: String, val hasUncommittedChanges: Boolean = false) : GitOperationResult
}
