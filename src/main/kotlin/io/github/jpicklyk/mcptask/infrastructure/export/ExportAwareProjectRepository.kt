package io.github.jpicklyk.mcptask.infrastructure.export

import io.github.jpicklyk.mcptask.domain.model.Project
import io.github.jpicklyk.mcptask.domain.repository.FeatureRepository
import io.github.jpicklyk.mcptask.domain.repository.ProjectRepository
import io.github.jpicklyk.mcptask.domain.repository.Result
import io.github.jpicklyk.mcptask.domain.repository.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Decorator that triggers markdown export after successful project write operations.
 *
 * Delegates all read operations to the wrapped repository unchanged.
 * Overrides create, update, delete to trigger async export after success.
 * Project export cascades to re-export all child features and tasks.
 * On delete, collects child IDs before DB cascade removes them, then deletes all markdown files.
 *
 * @param delegate The underlying ProjectRepository implementation
 * @param exportService The markdown export service
 * @param exportScope Coroutine scope for fire-and-forget export operations
 * @param featureRepository Feature repository for collecting child feature IDs before cascade delete
 * @param taskRepository Task repository for collecting child task IDs before cascade delete
 */
class ExportAwareProjectRepository(
    private val delegate: ProjectRepository,
    private val exportService: MarkdownExportService,
    private val exportScope: CoroutineScope,
    private val featureRepository: FeatureRepository,
    private val taskRepository: TaskRepository
) : ProjectRepository by delegate {

    private val logger = LoggerFactory.getLogger(ExportAwareProjectRepository::class.java)

    override suspend fun create(entity: Project): Result<Project> {
        val result = delegate.create(entity)
        if (result is Result.Success) {
            triggerExport(result.data.id)
        }
        return result
    }

    override suspend fun update(entity: Project): Result<Project> {
        val result = delegate.update(entity)
        if (result is Result.Success) {
            triggerExport(result.data.id)
        }
        return result
    }

    override suspend fun delete(id: UUID): Result<Boolean> {
        // Collect child IDs BEFORE DB delete cascades remove them
        val childFeatureIds = mutableListOf<UUID>()
        val childTaskIds = mutableListOf<UUID>()
        try {
            val featuresResult = featureRepository.findByProject(id, limit = Int.MAX_VALUE)
            if (featuresResult is Result.Success) {
                childFeatureIds.addAll(featuresResult.data.map { it.id })
            }
            // Tasks under features
            for (featureId in childFeatureIds) {
                val tasks = taskRepository.findByFeatureId(featureId)
                childTaskIds.addAll(tasks.map { it.id })
            }
            // Tasks directly under project (no feature)
            val directTasksResult = taskRepository.findByProject(id, limit = Int.MAX_VALUE)
            if (directTasksResult is Result.Success) {
                val featureTaskIds = childTaskIds.toSet()
                childTaskIds.addAll(directTasksResult.data.filter { it.id !in featureTaskIds }.map { it.id })
            }
        } catch (e: Exception) {
            logger.warn("Failed to collect child IDs before project delete {}: {}", id, e.message)
        }

        val result = delegate.delete(id)
        if (result is Result.Success && result.data) {
            exportScope.launch {
                try {
                    // Delete child task files first, then features, then project
                    for (taskId in childTaskIds) {
                        exportService.onEntityDeleted(taskId)
                    }
                    for (featureId in childFeatureIds) {
                        exportService.onEntityDeleted(featureId)
                    }
                    exportService.onEntityDeleted(id)
                } catch (e: Exception) {
                    logger.warn("Failed to handle project deletion export for {}: {}", id, e.message)
                }
            }
        }
        return result
    }

    private fun triggerExport(projectId: UUID) {
        exportScope.launch {
            try {
                exportService.exportProject(projectId)
            } catch (e: Exception) {
                logger.warn("Failed to export project {}: {}", projectId, e.message)
            }
        }
    }
}
