package io.github.jpicklyk.mcptask.infrastructure.export

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class SyncStateManagerTest {

    @Test
    fun `recordExport should create entry and save to disk`(@TempDir tempDir: Path) {
        val manager = SyncStateManager(tempDir)
        val entityId = UUID.randomUUID()
        val relativePath = "Project Name/Feature Name/Task Title.md"

        manager.recordExport(entityId, "task", relativePath)

        // Verify in-memory retrieval
        assertEquals(relativePath, manager.getPath(entityId))

        // Verify persisted to disk
        val stateFile = tempDir.resolve(".sync-state.json")
        assertTrue(stateFile.exists())

        val content = stateFile.readText()
        assertTrue(content.contains(entityId.toString()))
        assertTrue(content.contains(relativePath))
        assertTrue(content.contains("task"))
    }

    @Test
    fun `getPath should return null for non-existent entity`(@TempDir tempDir: Path) {
        val manager = SyncStateManager(tempDir)
        val nonExistentId = UUID.randomUUID()

        assertNull(manager.getPath(nonExistentId))
    }

    @Test
    fun `removeEntry should delete entry and update disk`(@TempDir tempDir: Path) {
        val manager = SyncStateManager(tempDir)
        val entityId = UUID.randomUUID()
        val relativePath = "Project/Feature/Task.md"

        // Record then remove
        manager.recordExport(entityId, "task", relativePath)
        assertEquals(relativePath, manager.getPath(entityId))

        manager.removeEntry(entityId)

        // Verify removed from memory
        assertNull(manager.getPath(entityId))

        // Verify removed from disk
        val content = tempDir.resolve(".sync-state.json").readText()
        assertFalse(content.contains(entityId.toString()))
    }

    @Test
    fun `load should read existing state file`(@TempDir tempDir: Path) {
        val entityId = UUID.randomUUID()
        val relativePath = "Project/Feature/Task.md"

        // Create state file manually
        val stateFile = tempDir.resolve(".sync-state.json")
        stateFile.writeText("""
            {
              "version": "1.0",
              "lastSync": "2026-02-15T10:30:00Z",
              "entities": {
                "$entityId": {
                  "path": "$relativePath",
                  "entityType": "task",
                  "lastModified": "2026-02-15T10:25:00Z"
                }
              }
            }
        """.trimIndent())

        // Load and verify
        val manager = SyncStateManager(tempDir)
        assertEquals(relativePath, manager.getPath(entityId))
    }

    @Test
    fun `hasState should return false when no file exists`(@TempDir tempDir: Path) {
        val manager = SyncStateManager(tempDir)
        assertFalse(manager.hasState())
    }

    @Test
    fun `hasState should return true after save`(@TempDir tempDir: Path) {
        val manager = SyncStateManager(tempDir)
        assertFalse(manager.hasState())

        manager.recordExport(UUID.randomUUID(), "task", "path/to/file.md")

        assertTrue(manager.hasState())
    }

    @Test
    fun `handle missing file gracefully`(@TempDir tempDir: Path) {
        // Should not throw exception
        val manager = SyncStateManager(tempDir)

        assertNull(manager.getPath(UUID.randomUUID()))
        assertFalse(manager.hasState())
    }

    @Test
    fun `handle corrupted JSON gracefully`(@TempDir tempDir: Path) {
        val stateFile = tempDir.resolve(".sync-state.json")
        stateFile.writeText("{ this is not valid json }")

        // Should not throw, should start with empty state
        val manager = SyncStateManager(tempDir)

        assertNull(manager.getPath(UUID.randomUUID()))
        assertTrue(manager.hasState()) // File exists even if corrupted
    }

    @Test
    fun `multiple entries should be tracked correctly`(@TempDir tempDir: Path) {
        val manager = SyncStateManager(tempDir)

        val entity1 = UUID.randomUUID()
        val entity2 = UUID.randomUUID()
        val entity3 = UUID.randomUUID()

        manager.recordExport(entity1, "task", "path/to/task1.md")
        manager.recordExport(entity2, "feature", "path/to/feature.md")
        manager.recordExport(entity3, "project", "path/to/project.md")

        assertEquals("path/to/task1.md", manager.getPath(entity1))
        assertEquals("path/to/feature.md", manager.getPath(entity2))
        assertEquals("path/to/project.md", manager.getPath(entity3))

        // Verify all persisted
        val content = tempDir.resolve(".sync-state.json").readText()
        assertTrue(content.contains(entity1.toString()))
        assertTrue(content.contains(entity2.toString()))
        assertTrue(content.contains(entity3.toString()))
    }

    @Test
    fun `recordExport should update existing entry`(@TempDir tempDir: Path) {
        val manager = SyncStateManager(tempDir)
        val entityId = UUID.randomUUID()

        manager.recordExport(entityId, "task", "old/path.md")
        assertEquals("old/path.md", manager.getPath(entityId))

        manager.recordExport(entityId, "task", "new/path.md")
        assertEquals("new/path.md", manager.getPath(entityId))

        // Verify only one entry exists on disk
        val content = tempDir.resolve(".sync-state.json").readText()
        assertTrue(content.contains("new/path.md"))
        assertFalse(content.contains("old/path.md"))
    }

    @Test
    fun `save should create vault directory if missing`(@TempDir tempDir: Path) {
        val vaultPath = tempDir.resolve("nonexistent/vault")
        assertFalse(vaultPath.exists())

        val manager = SyncStateManager(vaultPath)
        manager.recordExport(UUID.randomUUID(), "task", "path/to/task.md")

        // Verify directory was created
        assertTrue(vaultPath.exists())
        assertTrue(vaultPath.resolve(".sync-state.json").exists())
    }

    @Test
    fun `load should preserve unknown JSON fields`(@TempDir tempDir: Path) {
        val stateFile = tempDir.resolve(".sync-state.json")
        stateFile.writeText("""
            {
              "version": "1.0",
              "lastSync": "2026-02-15T10:30:00Z",
              "futureField": "should be ignored",
              "entities": {}
            }
        """.trimIndent())

        // Should not throw due to unknown field
        val manager = SyncStateManager(tempDir)
        assertTrue(manager.hasState())
    }

    @Test
    fun `state should persist across manager instances`(@TempDir tempDir: Path) {
        val entityId = UUID.randomUUID()
        val relativePath = "persistent/path.md"

        // First manager instance
        val manager1 = SyncStateManager(tempDir)
        manager1.recordExport(entityId, "task", relativePath)

        // Second manager instance (simulates restart)
        val manager2 = SyncStateManager(tempDir)
        assertEquals(relativePath, manager2.getPath(entityId))
    }

    @Test
    fun `removeEntry on non-existent entity should not throw`(@TempDir tempDir: Path) {
        val manager = SyncStateManager(tempDir)
        val nonExistentId = UUID.randomUUID()

        // Should not throw exception
        manager.removeEntry(nonExistentId)

        assertNull(manager.getPath(nonExistentId))
    }

    @Test
    fun `JSON should be formatted for readability`(@TempDir tempDir: Path) {
        val manager = SyncStateManager(tempDir)
        manager.recordExport(UUID.randomUUID(), "task", "path/to/task.md")

        val content = tempDir.resolve(".sync-state.json").readText()

        // Verify pretty printing (multiple lines)
        assertTrue(content.lines().size > 1)
        assertTrue(content.contains("  ")) // Indentation
    }
}
