package io.github.jpicklyk.mcptask.infrastructure.export

import io.github.jpicklyk.mcptask.domain.model.Project
import io.github.jpicklyk.mcptask.domain.repository.ProjectRepository
import io.github.jpicklyk.mcptask.domain.repository.Result
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
 *
 * @param delegate The underlying ProjectRepository implementation
 * @param exportService The markdown export service
 * @param exportScope Coroutine scope for fire-and-forget export operations
 */
class ExportAwareProjectRepository(
    private val delegate: ProjectRepository,
    private val exportService: MarkdownExportService,
    private val exportScope: CoroutineScope
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
        val result = delegate.delete(id)
        if (result is Result.Success && result.data) {
            exportScope.launch {
                try {
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
