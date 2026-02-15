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
            triggerExport(result.data.id)
        }
        return result
    }

    override suspend fun update(entity: Task): Result<Task> {
        val result = delegate.update(entity)
        if (result is Result.Success) {
            triggerExport(result.data.id)
        }
        return result
    }

    override suspend fun delete(id: UUID): Result<Boolean> {
        val result = delegate.delete(id)
        if (result is Result.Success && result.data) {
            exportScope.launch {
                try {
                    exportService.onEntityDeleted(id)
                } catch (e: Exception) {
                    logger.warn("Failed to handle task deletion export for {}: {}", id, e.message)
                }
            }
        }
        return result
    }

    private fun triggerExport(taskId: UUID) {
        exportScope.launch {
            try {
                exportService.exportTask(taskId)
            } catch (e: Exception) {
                logger.warn("Failed to export task {}: {}", taskId, e.message)
            }
        }
    }
}
