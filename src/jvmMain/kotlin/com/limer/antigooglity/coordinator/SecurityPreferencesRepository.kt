package com.limer.antigooglity.coordinator

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

data class SecurityPreferences(
    val preset: SecurityPreset = SecurityPreset.DEFAULT,
    val permissions: SecurityPermissions = SecurityPermissions(),
    val recentWorkspaces: List<String> = emptyList(),
)

interface SecurityPreferencesRepository {
    fun load(): SecurityPreferences
    fun save(preferences: SecurityPreferences)
}

class FileSecurityPreferencesRepository(
    private val configFile: Path = defaultConfigFile(),
) : SecurityPreferencesRepository {

    private val lock = Any()

    override fun load(): SecurityPreferences {
        synchronized(lock) {
            if (!Files.exists(configFile)) {
                return SecurityPreferences()
            }
            return try {
                val json = Files.readString(configFile).trim()
                parsePreferences(json)
            } catch (_: Exception) {
                SecurityPreferences()
            }
        }
    }

    override fun save(preferences: SecurityPreferences) {
        synchronized(lock) {
            try {
                val parent = configFile.parent
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent)
                }
                val json = serializePreferences(preferences)
                val tempFile = Files.createTempFile(parent ?: Path.of("."), "sec_pref_", ".tmp")
                Files.writeString(tempFile, json)
                Files.move(tempFile, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                // If atomic move fails on some file systems, attempt direct write
                try {
                    Files.writeString(configFile, serializePreferences(preferences))
                } catch (_: Exception) {
                    // Ignore write failures gracefully
                }
            }
        }
    }

    private fun serializePreferences(pref: SecurityPreferences): String {
        val recentJson = pref.recentWorkspaces.joinToString(",") { "\"${escapeJson(it)}\"" }
        return """
        {
          "preset": "${pref.preset.id}",
          "allowReadFile": ${pref.permissions.allowReadFile},
          "allowWriteFile": ${pref.permissions.allowWriteFile},
          "allowDeleteFile": ${pref.permissions.allowDeleteFile},
          "allowExecuteCommand": ${pref.permissions.allowExecuteCommand},
          "allowArbitraryCommands": ${pref.permissions.allowArbitraryCommands},
          "requireApprovalForDangerous": ${pref.permissions.requireApprovalForDangerous},
          "recentWorkspaces": [$recentJson]
        }
        """.trimIndent()
    }

    private fun parsePreferences(json: String): SecurityPreferences {
        val presetId = extractString(json, "preset")
        val preset = SecurityPreset.fromId(presetId)

        val allowRead = extractBoolean(json, "allowReadFile", default = true)
        val allowWrite = extractBoolean(json, "allowWriteFile", default = true)
        val allowDelete = extractBoolean(json, "allowDeleteFile", default = false)
        val allowExecute = extractBoolean(json, "allowExecuteCommand", default = true)
        val allowArbitrary = extractBoolean(json, "allowArbitraryCommands", default = false)
        val requireApproval = extractBoolean(json, "requireApprovalForDangerous", default = true)

        val recentList = extractStringList(json, "recentWorkspaces")

        return SecurityPreferences(
            preset = preset,
            permissions = SecurityPermissions(
                allowReadFile = allowRead,
                allowWriteFile = allowWrite,
                allowDeleteFile = allowDelete,
                allowExecuteCommand = allowExecute,
                allowArbitraryCommands = allowArbitrary,
                requireApprovalForDangerous = requireApproval,
            ),
            recentWorkspaces = recentList,
        )
    }

    private fun extractString(json: String, key: String): String? {
        val pattern = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
        return pattern.find(json)?.groupValues?.get(1)
    }

    private fun extractBoolean(json: String, key: String, default: Boolean): Boolean {
        val pattern = Regex("\"$key\"\\s*:\\s*(true|false)", RegexOption.IGNORE_CASE)
        val match = pattern.find(json)?.groupValues?.get(1) ?: return default
        return match.equals("true", ignoreCase = true)
    }

    private fun extractStringList(json: String, key: String): List<String> {
        val pattern = Regex("\"$key\"\\s*:\\s*\\[([^\\]]*)\\]")
        val match = pattern.find(json)?.groupValues?.get(1) ?: return emptyList()
        val itemPattern = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
        return itemPattern.findAll(match)
            .map { unescapeJson(it.groupValues[1]) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(10)
            .toList()
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private fun unescapeJson(str: String): String {
        return str.replace("\\\"", "\"").replace("\\\\", "\\")
    }

    companion object {
        fun defaultConfigFile(): Path {
            val userHome = System.getProperty("user.home") ?: "."
            return Path.of(userHome, ".antigooglity", "security_settings.json")
        }
    }
}
