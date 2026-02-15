package io.github.jpicklyk.mcptask.infrastructure.export

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Manages synchronization state for markdown vault exports.
 *
 * Tracks entity UUID → file path mappings in `.sync-state.json` at the vault root.
 * Used for:
 * - Looking up an entity's current file path (for deletion or rename detection)
 * - Recording what was exported (entity ID → file path mapping)
 *
 * State is cached in memory for fast lookups and persisted to disk on changes.
 */
class SyncStateManager(private val vaultPath: Path) {

    private val logger = LoggerFactory.getLogger(SyncStateManager::class.java)
    private val stateFilePath: Path = vaultPath.resolve(".sync-state.json")
    private var state: SyncState = SyncState()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        load()
    }

    /**
     * Get the file path for an entity.
     *
     * @param entityId The UUID of the entity
     * @return The relative file path, or null if not found
     */
    fun getPath(entityId: UUID): String? {
        return state.entities[entityId.toString()]?.path
    }

    /**
     * Record an exported entity.
     *
     * @param entityId The UUID of the entity
     * @param entityType The type of entity (task, feature, project)
     * @param relativePath The relative path to the markdown file
     */
    fun recordExport(entityId: UUID, entityType: String, relativePath: String) {
        val now = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        state.entities[entityId.toString()] = SyncEntry(
            path = relativePath,
            entityType = entityType,
            lastModified = now
        )
        state.lastSync = now
        save()
    }

    /**
     * Remove an entry from the sync state.
     *
     * @param entityId The UUID of the entity to remove
     */
    fun removeEntry(entityId: UUID) {
        state.entities.remove(entityId.toString())
        state.lastSync = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        save()
    }

    /**
     * Check if a sync state file exists on disk.
     *
     * @return true if .sync-state.json exists, false otherwise
     */
    fun hasState(): Boolean {
        return stateFilePath.exists()
    }

    /**
     * Load sync state from disk into memory cache.
     *
     * If the file doesn't exist or is corrupted, starts with empty state.
     */
    fun load() {
        if (!stateFilePath.exists()) {
            logger.debug("Sync state file does not exist, starting with empty state")
            state = SyncState()
            return
        }

        try {
            val content = stateFilePath.readText()
            state = json.decodeFromString<SyncState>(content)
            logger.debug("Loaded sync state with ${state.entities.size} entries")
        } catch (e: Exception) {
            logger.warn("Failed to parse sync state file, starting with empty state", e)
            state = SyncState()
        }
    }

    /**
     * Save the in-memory state to disk.
     *
     * Uses atomic write (temp file + rename) to prevent corruption.
     */
    fun save() {
        try {
            // Ensure vault directory exists
            if (!vaultPath.exists()) {
                Files.createDirectories(vaultPath)
            }

            val tempFile = vaultPath.resolve(".sync-state.json.tmp")
            val jsonContent = json.encodeToString(state)

            // Write to temp file
            tempFile.writeText(jsonContent)

            // Atomic rename
            Files.move(tempFile, stateFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)

            logger.debug("Saved sync state with ${state.entities.size} entries")
        } catch (e: Exception) {
            logger.warn("Failed to save sync state", e)
        }
    }
}

/**
 * Sync state file structure.
 */
@Serializable
data class SyncState(
    val version: String = "1.0",
    var lastSync: String = "",
    val entities: MutableMap<String, SyncEntry> = mutableMapOf()
)

/**
 * Entry for a single entity in the sync state.
 */
@Serializable
data class SyncEntry(
    val path: String,
    val entityType: String,
    val lastModified: String
)
