package com.limer.antigooglity.coordinator

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecurityPreferencesRepositoryTest {

    private lateinit var tempDir: Path
    private lateinit var configFile: Path
    private lateinit var repository: FileSecurityPreferencesRepository

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("antigooglity-pref-test-").toRealPath()
        configFile = tempDir.resolve("security_settings.json")
        repository = FileSecurityPreferencesRepository(configFile)
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun testDefaultPreferencesWhenFileMissing() {
        assertFalse(Files.exists(configFile))
        val prefs = repository.load()

        assertEquals(SecurityPreset.DEFAULT, prefs.preset)
        assertTrue(prefs.permissions.allowReadFile)
        assertTrue(prefs.permissions.allowWriteFile)
        assertFalse(prefs.permissions.allowDeleteFile)
        assertTrue(prefs.permissions.allowExecuteCommand)
        assertFalse(prefs.permissions.allowArbitraryCommands)
        assertTrue(prefs.permissions.requireApprovalForDangerous)
        assertTrue(prefs.recentWorkspaces.isEmpty())
    }

    @Test
    fun testSaveAndLoadRoundtrip() {
        val custom = SecurityPreferences(
            preset = SecurityPreset.TURBO,
            permissions = SecurityPermissions(
                allowReadFile = true,
                allowWriteFile = false,
                allowDeleteFile = false,
                allowExecuteCommand = true,
                allowArbitraryCommands = true,
                requireApprovalForDangerous = false,
            ),
            recentWorkspaces = listOf("D:/Projects/ProjectA", "D:/Projects/ProjectB"),
        )

        repository.save(custom)
        assertTrue(Files.exists(configFile))

        val loaded = repository.load()
        assertEquals(SecurityPreset.TURBO, loaded.preset)
        assertTrue(loaded.permissions.allowReadFile)
        assertFalse(loaded.permissions.allowWriteFile)
        assertFalse(loaded.permissions.allowDeleteFile)
        assertTrue(loaded.permissions.allowExecuteCommand)
        assertTrue(loaded.permissions.allowArbitraryCommands)
        assertFalse(loaded.permissions.requireApprovalForDangerous)
        assertEquals(listOf("D:/Projects/ProjectA", "D:/Projects/ProjectB"), loaded.recentWorkspaces)
    }

    @Test
    fun testUpdatingPresetPreservesPermissions() {
        val initial = SecurityPreferences(
            preset = SecurityPreset.DEFAULT,
            permissions = SecurityPermissions(allowDeleteFile = true, allowArbitraryCommands = true),
        )
        repository.save(initial)

        val loaded1 = repository.load()
        assertEquals(SecurityPreset.DEFAULT, loaded1.preset)
        assertTrue(loaded1.permissions.allowDeleteFile)

        // Update preset to REQUEST_REVIEW while keeping permissions
        val updated = loaded1.copy(preset = SecurityPreset.REQUEST_REVIEW)
        repository.save(updated)

        val loaded2 = repository.load()
        assertEquals(SecurityPreset.REQUEST_REVIEW, loaded2.preset)
        assertTrue(loaded2.permissions.allowDeleteFile, "Permissions must not be reset on preset change")
        assertTrue(loaded2.permissions.allowArbitraryCommands)
    }

    @Test
    fun testUpdatingPermissionsPreservesPreset() {
        val initial = SecurityPreferences(
            preset = SecurityPreset.AUTO,
            permissions = SecurityPermissions(),
        )
        repository.save(initial)

        val loaded1 = repository.load()
        assertEquals(SecurityPreset.AUTO, loaded1.preset)

        // Update permissions
        val updated = loaded1.copy(permissions = loaded1.permissions.copy(allowReadFile = false))
        repository.save(updated)

        val loaded2 = repository.load()
        assertEquals(SecurityPreset.AUTO, loaded2.preset, "Preset must not be reset on permissions change")
        assertFalse(loaded2.permissions.allowReadFile)
    }

    @Test
    fun testCorruptFileFallbackToDefault() {
        Files.writeString(configFile, "{ corrupt invalid json !!!")
        val fallback = repository.load()
        assertEquals(SecurityPreset.DEFAULT, fallback.preset)
        assertTrue(fallback.permissions.allowReadFile)
    }
}
