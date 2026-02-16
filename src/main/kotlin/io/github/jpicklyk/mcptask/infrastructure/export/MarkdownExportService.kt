package io.github.jpicklyk.mcptask.infrastructure.export

import java.util.UUID

/**
 * Service for exporting task orchestrator entities to a markdown vault.
 *
 * Maintains a synchronized mirror of the database in markdown format,
 * organized hierarchically by project/feature/task relationships.
 * Each feature file includes a child task status table, and each
 * project file includes a child feature status table.
 *
 * **Key Responsibilities:**
 * - Export entities (projects, features, tasks) to markdown files
 * - Include child status tables in feature and project files
 * - Track file paths in sync state for rename detection
 * - Delete markdown files when entities are deleted
 * - Clean up empty directories after deletion
 * - Re-export child entities when parent names change (path affected)
 *
 * **Error Handling:**
 * All public methods are fail-safe - export failures are logged but never thrown.
 * This ensures database operations never fail due to export issues.
 */
interface MarkdownExportService {
    /**
     * Export a task to its markdown file.
     *
     * @param taskId The UUID of the task to export
     */
    suspend fun exportTask(taskId: UUID)

    /**
     * Export a feature to its markdown file, including child task status table.
     * Only cascades to re-export child tasks if the feature name changed (affects paths).
     *
     * @param featureId The UUID of the feature to export
     */
    suspend fun exportFeature(featureId: UUID)

    /**
     * Export a project to its markdown file, including child feature status table.
     * Only cascades to re-export child features if the project name changed (affects paths).
     *
     * @param projectId The UUID of the project to export
     */
    suspend fun exportProject(projectId: UUID)

    /**
     * Handle entity deletion by removing markdown file and sync state.
     *
     * @param entityId The UUID of the deleted entity
     */
    suspend fun onEntityDeleted(entityId: UUID)

    /**
     * Re-export parent feature and project files when a child entity changes.
     * This updates the status tables embedded in the parent files.
     * Resolves parent chain upward — if projectId is null but featureId is set,
     * looks up the feature's projectId.
     */
    suspend fun notifyParentExports(featureId: UUID?, projectId: UUID?)

    /**
     * Export all entities to markdown vault. Idempotent.
     */
    suspend fun fullExport()
}
