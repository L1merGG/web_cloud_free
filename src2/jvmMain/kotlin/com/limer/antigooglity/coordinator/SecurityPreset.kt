package com.limer.antigooglity.coordinator

enum class SecurityPreset(
    val id: String,
    val displayName: String,
    val description: String,
) {
    REQUEST_REVIEW(
        id = "request_review",
        displayName = "Request Review",
        description = "Every tool call requires user approval.",
    ),
    DEFAULT(
        id = "default",
        displayName = "Default",
        description = "Ask before sensitive operations.",
    ),
    AUTO(
        id = "auto",
        displayName = "Auto",
        description = "Automatically decide if commands run.",
    ),
    TURBO(
        id = "turbo",
        displayName = "Turbo",
        description = "Automatically approve eligible actions; security boundaries remain enforced.",
    );

    companion object {
        fun fromId(id: String?): SecurityPreset =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
    }
}
