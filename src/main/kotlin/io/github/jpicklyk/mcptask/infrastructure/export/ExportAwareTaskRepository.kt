package io.github.jpicklyk.mcptask.infrastructure.export

import io.github.jpicklyk.mcptask.domain.model.Task
import io.github.jpicklyk.mcptask.domain.repository.Result
import io.github.jpicklyk.mcptask.domain.repository.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Decorator that triggers markdown export after successful task write operations.
 *
 * Delegates all read operations to the wrapped repository unchanged.
 * Overrides create, update, delete to trigger async export after success.
 * Also notifies parent feature/project status docs on any task change.
 *
 * @param delegate The underlying TaskRepository implementation
 * @param exportService The markdown export service
 * @param exportScope Coroutine scope for fire-and-forget export operations
 */
class ExportAwareTaskRepository(
    private val delegate: TaskRepository,
    private val exportService: MarkdownExportService,
    private val exportScope: CoroutineScope
) : TaskRepository by delegate {

    private val logger = LoggerFactory.getLogger(ExportAwareTaskRepository::class.java)

    override suspend fun create(entity: Task): Result<Task> {
        val result = delegate.create(entity)
        if (result is Result.Success) {
            triggerExport(result.data)
        }
        return result
    }

    override suspend fun update(entity: Task): Result<Task> {
        val result = delegate.update(entity)
        if (result is Result.Success) {
            triggerExport(result.data)
        }
        return result
    }

    override suspend fun delete(id: UUID): Result<Boolean> {
        // Load task before delete to get parent IDs for status doc notification
        val taskResult = delegate.getById(id)
        val task = (taskResult as? Result.Success)?.data

        val result = delegate.delete(id)
        if (result is Result.Success && result.data) {
            exportScope.launch {
                try {
                    exportService.onEntityDeleted(id)
                    // Notify parent status docs that a child was removed
                    if (task != null) {
                        exportService.notifyParentStatusDocs(task.featureId, task.projectId)
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to handle task deletion export for {}: {}", id, e.message)
                }
            }
        }
        return result
    }

    private fun triggerExport(task: Task) {
        exportScope.launch {
            try {
                exportService.exportTask(task.id)
                exportService.notifyParentStatusDocs(task.featureId, task.projectId)
            } catch (e: Exception) {
                logger.warn("Failed to export task {}: {}", task.id, e.message)
            }
        }
    }
}
