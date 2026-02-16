package io.github.jpicklyk.mcptask.infrastructure.export

import io.github.jpicklyk.mcptask.domain.model.Feature
import io.github.jpicklyk.mcptask.domain.repository.FeatureRepository
import io.github.jpicklyk.mcptask.domain.repository.Result
import io.github.jpicklyk.mcptask.domain.repository.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Decorator that triggers markdown export after successful feature write operations.
 *
 * Delegates all read operations to the wrapped repository unchanged.
 * Overrides create, update, delete to trigger async export after success.
 * Feature export cascades to re-export all child tasks.
 * On delete, collects child task IDs before DB cascade removes them, then deletes all markdown files.
 *
 * @param delegate The underlying FeatureRepository implementation
 * @param exportService The markdown export service
 * @param exportScope Coroutine scope for fire-and-forget export operations
 * @param taskRepository Task repository for collecting child task IDs before cascade delete
 */
class ExportAwareFeatureRepository(
    private val delegate: FeatureRepository,
    private val exportService: MarkdownExportService,
    private val exportScope: CoroutineScope,
    private val taskRepository: TaskRepository
) : FeatureRepository by delegate {

    private val logger = LoggerFactory.getLogger(ExportAwareFeatureRepository::class.java)

    override suspend fun create(entity: Feature): Result<Feature> {
        val result = delegate.create(entity)
        if (result is Result.Success) {
            triggerExport(result.data)
        }
        return result
    }

    override suspend fun update(entity: Feature): Result<Feature> {
        val result = delegate.update(entity)
        if (result is Result.Success) {
            triggerExport(result.data)
        }
        return result
    }

    override suspend fun delete(id: UUID): Result<Boolean> {
        // Load feature before delete for parent project ID
        val featureResult = delegate.getById(id)
        val feature = (featureResult as? Result.Success)?.data

        // Collect child task IDs BEFORE DB delete cascades remove them
        val childTaskIds = mutableListOf<UUID>()
        try {
            val tasks = taskRepository.findByFeatureId(id)
            childTaskIds.addAll(tasks.map { it.id })
        } catch (e: Exception) {
            logger.warn("Failed to collect child task IDs before feature delete {}: {}", id, e.message)
        }

        val result = delegate.delete(id)
        if (result is Result.Success && result.data) {
            exportScope.launch {
                try {
                    // Delete child task files, then feature file
                    for (taskId in childTaskIds) {
                        exportService.onEntityDeleted(taskId)
                    }
                    exportService.onEntityDeleted(id)
                    // Re-export parent project (updates embedded status table)
                    if (feature?.projectId != null) {
                        exportService.exportProject(feature.projectId)
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to handle feature deletion export for {}: {}", id, e.message)
                }
            }
        }
        return result
    }

    private fun triggerExport(feature: Feature) {
        exportScope.launch {
            try {
                exportService.exportFeature(feature.id)
                // Re-export parent project (updates embedded status table)
                if (feature.projectId != null) {
                    exportService.exportProject(feature.projectId)
                }
            } catch (e: Exception) {
                logger.warn("Failed to export feature {}: {}", feature.id, e.message)
            }
        }
    }
}
