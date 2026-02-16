package io.github.jpicklyk.mcptask.infrastructure.export

import io.github.jpicklyk.mcptask.domain.model.EntityType
import io.github.jpicklyk.mcptask.domain.rendering.MarkdownRenderer
import io.github.jpicklyk.mcptask.domain.repository.Result
import io.github.jpicklyk.mcptask.infrastructure.repository.RepositoryProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * Implementation of [MarkdownExportService] for exporting entities to a markdown vault.
 *
 * **Architecture:**
 * - Uses [RepositoryProvider] to load entities and sections
 * - Uses [MarkdownRenderer] to convert entities to markdown
 * - Uses [FilePathResolver] to determine file paths
 * - Uses [SyncStateManager] to track exported files
 *
 * **Cascade Exports:**
 * - Exporting a project re-exports all child features and tasks
 * - Exporting a feature re-exports all child tasks
 * - This ensures parent/child relationships stay synchronized
 *
 * **Rename Detection:**
 * - Compares new path to sync state path
 * - If different, deletes old file before writing new file
 * - Cleans up empty parent directories
 *
 * **Error Handling:**
 * - All public methods wrap implementation in try/catch
 * - Export failures are logged as warnings, never thrown
 * - Database operations never fail due to export issues
 *
 * @param repositoryProvider Provider for accessing repositories
 * @param markdownRenderer Renderer for converting entities to markdown
 * @param vaultPath Root path of the markdown vault
 */
class MarkdownExportServiceImpl(
    private val repositoryProvider: RepositoryProvider,
    private val markdownRenderer: MarkdownRenderer,
    private val vaultPath: Path
) : MarkdownExportService {

    private val logger = LoggerFactory.getLogger(MarkdownExportServiceImpl::class.java)
    private val filePathResolver = FilePathResolver()
    private val syncStateManager = SyncStateManager(vaultPath)

    override suspend fun exportTask(taskId: UUID) {
        try {
            logger.debug("Exporting task {}", taskId)

            // Load task
            val taskResult = repositoryProvider.taskRepository().getById(taskId)
            if (taskResult !is Result.Success) {
                logger.warn("Failed to load task {}: {}", taskId, (taskResult as? Result.Error)?.error?.message)
                return
            }
            val task = taskResult.data

            // Load sections
            val sectionsResult = repositoryProvider.sectionRepository().getSectionsForEntity(EntityType.TASK, taskId)
            val sections = when (sectionsResult) {
                is Result.Success -> sectionsResult.data
                is Result.Error -> {
                    logger.warn("Failed to load sections for task {}: {}", taskId, sectionsResult.error.message)
                    emptyList()
                }
            }

            // Resolve parent names
            var featureName: String? = null
            var projectName: String? = null
            var resolvedProjectId = task.projectId

            if (task.featureId != null) {
                val featureResult = repositoryProvider.featureRepository().getById(task.featureId)
                if (featureResult is Result.Success) {
                    featureName = featureResult.data.name
                    // Inherit project from feature if task doesn't have one set directly
                    if (resolvedProjectId == null && featureResult.data.projectId != null) {
                        resolvedProjectId = featureResult.data.projectId
                    }
                }
            }

            if (resolvedProjectId != null) {
                val projectResult = repositoryProvider.projectRepository().getById(resolvedProjectId)
                if (projectResult is Result.Success) {
                    projectName = projectResult.data.name
                }
            }

            // Resolve file path
            val relativePath = filePathResolver.resolveTaskPath(task.title, featureName, projectName, task.status.name)

            // Check for rename
            deleteOldFileIfMoved(taskId, relativePath)

            // Render markdown
            val markdown = markdownRenderer.renderTask(task, sections)

            // Write file
            writeMarkdownFile(relativePath, markdown)

            // Update sync state
            syncStateManager.recordExport(taskId, "task", relativePath)

            logger.debug("Successfully exported task {} to {}", taskId, relativePath)

        } catch (e: Exception) {
            logger.warn("Failed to export task $taskId: ${e.message}", e)
        }
    }

    override suspend fun exportFeature(featureId: UUID) {
        try {
            logger.debug("Exporting feature {}", featureId)

            // Load feature
            val featureResult = repositoryProvider.featureRepository().getById(featureId)
            if (featureResult !is Result.Success) {
                logger.warn("Failed to load feature {}: {}", featureId, (featureResult as? Result.Error)?.error?.message)
                return
            }
            val feature = featureResult.data

            // Load sections
            val sectionsResult = repositoryProvider.sectionRepository().getSectionsForEntity(EntityType.FEATURE, featureId)
            val sections = when (sectionsResult) {
                is Result.Success -> sectionsResult.data
                is Result.Error -> {
                    logger.warn("Failed to load sections for feature {}: {}", featureId, sectionsResult.error.message)
                    emptyList()
                }
            }

            // Resolve parent name
            var projectName: String? = null
            if (feature.projectId != null) {
                val projectResult = repositoryProvider.projectRepository().getById(feature.projectId)
                if (projectResult is Result.Success) {
                    projectName = projectResult.data.name
                }
            }

            // Load child tasks for status table
            val childTasks = repositoryProvider.taskRepository().findByFeatureId(featureId)

            // Resolve file path
            val relativePath = filePathResolver.resolveFeaturePath(feature.name, projectName, feature.status.name)

            // Detect path change via sync state comparison (name or status change)
            val oldPath = syncStateManager.getPath(featureId)
            val pathChanged = oldPath != null && oldPath != relativePath
            val isFirstExport = oldPath == null

            // Check for rename/move
            deleteOldFileIfMoved(featureId, relativePath)

            // Render markdown (includes child task status table)
            val markdown = markdownRenderer.renderFeature(feature, sections, childTasks)

            // Write file
            writeMarkdownFile(relativePath, markdown)

            // Update sync state
            syncStateManager.recordExport(featureId, "feature", relativePath)

            logger.debug("Successfully exported feature {} to {}", featureId, relativePath)

            // Re-export child tasks if path changed (name or status change) or first export
            if (pathChanged || isFirstExport) {
                logger.debug("Re-exporting child tasks for feature {} (pathChanged={}, firstExport={})", featureId, pathChanged, isFirstExport)
                for (task in childTasks) {
                    exportTask(task.id)
                }
            }

        } catch (e: Exception) {
            logger.warn("Failed to export feature $featureId: ${e.message}", e)
        }
    }

    override suspend fun exportProject(projectId: UUID) {
        try {
            logger.debug("Exporting project {}", projectId)

            // Load project
            val projectResult = repositoryProvider.projectRepository().getById(projectId)
            if (projectResult !is Result.Success) {
                logger.warn("Failed to load project {}: {}", projectId, (projectResult as? Result.Error)?.error?.message)
                return
            }
            val project = projectResult.data

            // Load sections
            val sectionsResult = repositoryProvider.sectionRepository().getSectionsForEntity(EntityType.PROJECT, projectId)
            val sections = when (sectionsResult) {
                is Result.Success -> sectionsResult.data
                is Result.Error -> {
                    logger.warn("Failed to load sections for project {}: {}", projectId, sectionsResult.error.message)
                    emptyList()
                }
            }

            // Load child features for status table
            val featuresResult = repositoryProvider.featureRepository().findByProject(projectId, limit = Int.MAX_VALUE)
            val childFeatures = when (featuresResult) {
                is Result.Success -> featuresResult.data
                is Result.Error -> emptyList()
            }

            // Resolve file path
            val relativePath = filePathResolver.resolveProjectPath(project.name, project.status.name)

            // Detect path change via sync state comparison (name or status change)
            val oldPath = syncStateManager.getPath(projectId)
            val pathChanged = oldPath != null && oldPath != relativePath
            val isFirstExport = oldPath == null

            // Check for rename/move
            deleteOldFileIfMoved(projectId, relativePath)

            // Render markdown (includes child feature status table)
            val markdown = markdownRenderer.renderProject(project, sections, childFeatures)

            // Write file
            writeMarkdownFile(relativePath, markdown)

            // Update sync state
            syncStateManager.recordExport(projectId, "project", relativePath)

            logger.debug("Successfully exported project {} to {}", projectId, relativePath)

            // Re-export child features if path changed (name or status change) or first export
            if (pathChanged || isFirstExport) {
                logger.debug("Re-exporting child features for project {} (pathChanged={}, firstExport={})", projectId, pathChanged, isFirstExport)
                for (feature in childFeatures) {
                    exportFeature(feature.id)
                }
            }

        } catch (e: Exception) {
            logger.warn("Failed to export project $projectId: ${e.message}", e)
        }
    }

    override suspend fun notifyParentExports(featureId: UUID?, projectId: UUID?) {
        try {
            // Re-export parent feature (includes updated child task status table)
            if (featureId != null) {
                exportFeature(featureId)
            }

            // Resolve project ID (may need to look up via feature)
            var resolvedProjectId = projectId
            if (resolvedProjectId == null && featureId != null) {
                val featureResult = repositoryProvider.featureRepository().getById(featureId)
                if (featureResult is Result.Success) {
                    resolvedProjectId = featureResult.data.projectId
                }
            }

            // Re-export parent project (includes updated child feature status table)
            if (resolvedProjectId != null) {
                exportProject(resolvedProjectId)
            }
        } catch (e: Exception) {
            logger.warn("Failed to notify parent exports: ${e.message}", e)
        }
    }

    override suspend fun onEntityDeleted(entityId: UUID) {
        try {
            logger.debug("Handling deletion for entity {}", entityId)

            // Look up path in sync state
            val path = syncStateManager.getPath(entityId)
            if (path == null) {
                logger.debug("Entity {} not found in sync state, nothing to delete", entityId)
                return
            }

            // Delete file
            val filePath = vaultPath.resolve(path)
            deleteFile(filePath)

            // Clean up empty parent directories
            cleanEmptyParentDirs(filePath.parent)

            // Remove from sync state
            syncStateManager.removeEntry(entityId)

            logger.debug("Successfully deleted file for entity {} at {}", entityId, path)

        } catch (e: Exception) {
            logger.warn("Failed to handle deletion for entity $entityId: ${e.message}", e)
        }
    }

    override suspend fun fullExport() {
        try {
            logger.info("Starting full export of all entities to markdown vault")

            // Export all projects
            val projectsResult = repositoryProvider.projectRepository().findAll(limit = Int.MAX_VALUE)
            if (projectsResult is Result.Success) {
                logger.info("Exporting {} projects", projectsResult.data.size)
                for (project in projectsResult.data) {
                    exportProject(project.id)
                }
            } else {
                logger.warn("Failed to load projects for full export")
            }

            // Export all features (some may already be exported as children of projects)
            val featuresResult = repositoryProvider.featureRepository().findAll(limit = Int.MAX_VALUE)
            if (featuresResult is Result.Success) {
                logger.info("Exporting {} features", featuresResult.data.size)
                for (feature in featuresResult.data) {
                    exportFeature(feature.id)
                }
            } else {
                logger.warn("Failed to load features for full export")
            }

            // Export all tasks (some may already be exported as children of features)
            val tasksResult = repositoryProvider.taskRepository().findAll(limit = Int.MAX_VALUE)
            if (tasksResult is Result.Success) {
                logger.info("Exporting {} tasks", tasksResult.data.size)
                for (task in tasksResult.data) {
                    exportTask(task.id)
                }
            } else {
                logger.warn("Failed to load tasks for full export")
            }

            logger.info("Full export completed successfully")

        } catch (e: Exception) {
            logger.warn("Full export failed: ${e.message}", e)
        }
    }

    /**
     * Write markdown content to a file at the given relative path.
     *
     * Creates parent directories if needed.
     * Uses IO dispatcher for file operations.
     *
     * @param relativePath Relative path within vault
     * @param content Markdown content to write
     */
    private suspend fun writeMarkdownFile(relativePath: String, content: String) = withContext(Dispatchers.IO) {
        val filePath = vaultPath.resolve(relativePath)
        Files.createDirectories(filePath.parent)
        filePath.writeText(content)
    }

    /**
     * Delete old file if entity was renamed (path changed).
     *
     * Compares new relative path to sync state path.
     * If different, deletes old file and cleans up empty directories.
     *
     * @param entityId UUID of the entity
     * @param newRelativePath New relative path for the entity
     */
    private suspend fun deleteOldFileIfMoved(entityId: UUID, newRelativePath: String) {
        val oldPath = syncStateManager.getPath(entityId)
        if (oldPath != null && oldPath != newRelativePath) {
            logger.debug("Entity {} moved from {} to {}, deleting old file", entityId, oldPath, newRelativePath)
            val oldFile = vaultPath.resolve(oldPath)
            deleteFile(oldFile)
            cleanEmptyParentDirs(oldFile.parent)
        }
    }

    /**
     * Delete a file if it exists.
     *
     * Uses IO dispatcher for file operations.
     *
     * @param filePath Path to file to delete
     */
    private suspend fun deleteFile(filePath: Path) = withContext(Dispatchers.IO) {
        if (filePath.exists()) {
            Files.delete(filePath)
        }
    }

    /**
     * Clean up empty parent directories after file deletion.
     *
     * Walks up the directory tree from the given directory,
     * deleting directories that are empty.
     * Stops at vault root.
     *
     * @param dir Directory to start cleaning from
     */
    private suspend fun cleanEmptyParentDirs(dir: Path) = withContext(Dispatchers.IO) {
        var current = dir
        while (current != vaultPath && current.startsWith(vaultPath)) {
            if (Files.isDirectory(current) && Files.list(current).use { it.count() } == 0L) {
                Files.delete(current)
                current = current.parent
            } else {
                break
            }
        }
    }
}
