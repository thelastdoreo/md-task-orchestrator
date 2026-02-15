package io.github.jpicklyk.mcptask.infrastructure.export

import io.github.jpicklyk.mcptask.domain.model.Feature
import io.github.jpicklyk.mcptask.domain.repository.FeatureRepository
import io.github.jpicklyk.mcptask.domain.repository.Result
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
 *
 * @param delegate The underlying FeatureRepository implementation
 * @param exportService The markdown export service
 * @param exportScope Coroutine scope for fire-and-forget export operations
 */
class ExportAwareFeatureRepository(
    private val delegate: FeatureRepository,
    private val exportService: MarkdownExportService,
    private val exportScope: CoroutineScope
) : FeatureRepository by delegate {

    private val logger = LoggerFactory.getLogger(ExportAwareFeatureRepository::class.java)

    override suspend fun create(entity: Feature): Result<Feature> {
        val result = delegate.create(entity)
        if (result is Result.Success) {
            triggerExport(result.data.id)
        }
        return result
    }

    override suspend fun update(entity: Feature): Result<Feature> {
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
                    logger.warn("Failed to handle feature deletion export for {}: {}", id, e.message)
                }
            }
        }
        return result
    }

    private fun triggerExport(featureId: UUID) {
        exportScope.launch {
            try {
                exportService.exportFeature(featureId)
            } catch (e: Exception) {
                logger.warn("Failed to export feature {}: {}", featureId, e.message)
            }
        }
    }
}
