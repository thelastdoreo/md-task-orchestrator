package io.github.jpicklyk.mcptask.infrastructure.export

import java.util.UUID

/**
 * Service for exporting task orchestrator entities to a markdown vault.
 *
 * Maintains a synchronized mirror of the database in markdown format,
 * organized hierarchically by project/feature/task relationships.
 *
 * **Key Responsibilities:**
 * - Export entities (projects, features, tasks) to markdown files
 * - Track file paths in sync state for rename detection
 * - Delete markdown files when entities are deleted
 * - Clean up empty directories after deletion
 * - Re-export child entities when parents change
 *
 * **Error Handling:**
 * All public methods are fail-safe - export failures are logged but never thrown.
 * This ensures database operations never fail due to export issues.
 */
interface MarkdownExportService {
    /**
     * Export a task to its markdown file.
     *
     * - Loads task and sections from repository
     * - Resolves parent names (feature, project) for path resolution
     * - Detects renames and deletes old file if path changed
     * - Renders markdown and writes to vault
     * - Updates sync state
     *
     * @param taskId The UUID of the task to export
     */
    suspend fun exportTask(taskId: UUID)

    /**
     * Export a feature to its markdown file.
     *
     * - Loads feature and sections from repository
     * - Resolves parent name (project) for path resolution
     * - Detects renames and deletes old file if path changed
     * - Renders markdown and writes to vault
     * - Updates sync state
     * - Re-exports all child tasks (cascade export)
     *
     * @param featureId The UUID of the feature to export
     */
    suspend fun exportFeature(featureId: UUID)

    /**
     * Export a project to its markdown file.
     *
     * - Loads project and sections from repository
     * - Detects renames and deletes old file if path changed
     * - Renders markdown and writes to vault
     * - Updates sync state
     * - Re-exports all child features (cascade export)
     * - Re-exports all child tasks through features (cascade export)
     *
     * @param projectId The UUID of the project to export
     */
    suspend fun exportProject(projectId: UUID)

    /**
     * Handle entity deletion by removing markdown file and sync state.
     *
     * - Looks up file path in sync state
     * - Deletes markdown file
     * - Cleans up empty parent directories
     * - Removes entry from sync state
     *
     * @param entityId The UUID of the deleted entity
     */
    suspend fun onEntityDeleted(entityId: UUID)

    /**
     * Export all entities to markdown vault.
     *
     * - Exports all projects
     * - Exports all features
     * - Exports all tasks
     *
     * This operation is idempotent - re-exporting entities that were
     * already exported as children of parents is safe.
     */
    suspend fun fullExport()
}
