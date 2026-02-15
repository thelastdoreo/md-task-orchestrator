package io.github.jpicklyk.mcptask.infrastructure.export

import io.github.jpicklyk.mcptask.domain.model.EntityType
import io.github.jpicklyk.mcptask.domain.model.Section
import io.github.jpicklyk.mcptask.domain.repository.Result
import io.github.jpicklyk.mcptask.domain.repository.SectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Decorator that triggers parent entity markdown export after successful section write operations.
 *
 * When a section is added, updated, or deleted, re-exports the parent entity
 * (project, feature, or task) so the markdown file includes the latest sections.
 *
 * @param delegate The underlying SectionRepository implementation
 * @param exportService The markdown export service
 * @param exportScope Coroutine scope for fire-and-forget export operations
 */
class ExportAwareSectionRepository(
    private val delegate: SectionRepository,
    private val exportService: MarkdownExportService,
    private val exportScope: CoroutineScope
) : SectionRepository by delegate {

    private val logger = LoggerFactory.getLogger(ExportAwareSectionRepository::class.java)

    override suspend fun addSection(entityType: EntityType, entityId: UUID, section: Section): Result<Section> {
        val result = delegate.addSection(entityType, entityId, section)
        if (result is Result.Success) {
            triggerParentExport(entityType, entityId)
        }
        return result
    }

    override suspend fun updateSection(section: Section): Result<Section> {
        val result = delegate.updateSection(section)
        if (result is Result.Success) {
            triggerParentExport(result.data.entityType, result.data.entityId)
        }
        return result
    }

    override suspend fun deleteSection(id: UUID): Result<Boolean> {
        // Load section before deleting to get parent info
        val sectionResult = delegate.getSection(id)
        val result = delegate.deleteSection(id)
        if (result is Result.Success && result.data && sectionResult is Result.Success) {
            triggerParentExport(sectionResult.data.entityType, sectionResult.data.entityId)
        }
        return result
    }

    override suspend fun reorderSections(entityType: EntityType, entityId: UUID, sectionIds: List<UUID>): Result<Boolean> {
        val result = delegate.reorderSections(entityType, entityId, sectionIds)
        if (result is Result.Success && result.data) {
            triggerParentExport(entityType, entityId)
        }
        return result
    }

    private fun triggerParentExport(entityType: EntityType, entityId: UUID) {
        exportScope.launch {
            try {
                when (entityType) {
                    EntityType.TASK -> exportService.exportTask(entityId)
                    EntityType.FEATURE -> exportService.exportFeature(entityId)
                    EntityType.PROJECT -> exportService.exportProject(entityId)
                    else -> logger.debug("Skipping export for entity type {}", entityType)
                }
            } catch (e: Exception) {
                logger.warn("Failed to export {} {}: {}", entityType, entityId, e.message)
            }
        }
    }
}
