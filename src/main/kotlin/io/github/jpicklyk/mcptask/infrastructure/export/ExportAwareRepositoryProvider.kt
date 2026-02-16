package io.github.jpicklyk.mcptask.infrastructure.export

import io.github.jpicklyk.mcptask.domain.repository.*
import io.github.jpicklyk.mcptask.infrastructure.repository.RepositoryProvider
import kotlinx.coroutines.CoroutineScope

/**
 * Repository provider that wraps repositories with export-aware decorators.
 *
 * When markdown auto-export is enabled, this provider wraps the task, feature,
 * project, and section repositories so that every successful write operation
 * triggers an async markdown file export. Template and dependency repositories
 * are passed through unchanged since they don't have markdown representations.
 *
 * The [MarkdownExportServiceImpl] receives the unwrapped [baseProvider] to avoid
 * circular triggers — export reads entities from the base repositories directly.
 *
 * @param baseProvider The underlying repository provider (unwrapped)
 * @param exportService The markdown export service (constructed with baseProvider)
 * @param exportScope Coroutine scope for fire-and-forget export operations
 */
class ExportAwareRepositoryProvider(
    private val baseProvider: RepositoryProvider,
    exportService: MarkdownExportService,
    exportScope: CoroutineScope
) : RepositoryProvider {

    private val taskRepo = ExportAwareTaskRepository(
        baseProvider.taskRepository(), exportService, exportScope
    )
    private val featureRepo = ExportAwareFeatureRepository(
        baseProvider.featureRepository(), exportService, exportScope,
        baseProvider.taskRepository()
    )
    private val projectRepo = ExportAwareProjectRepository(
        baseProvider.projectRepository(), exportService, exportScope,
        baseProvider.featureRepository(), baseProvider.taskRepository()
    )
    private val sectionRepo = ExportAwareSectionRepository(
        baseProvider.sectionRepository(), exportService, exportScope
    )

    override fun taskRepository(): TaskRepository = taskRepo
    override fun featureRepository(): FeatureRepository = featureRepo
    override fun projectRepository(): ProjectRepository = projectRepo
    override fun sectionRepository(): SectionRepository = sectionRepo

    // Pass through unchanged — no markdown representation
    override fun templateRepository(): TemplateRepository = baseProvider.templateRepository()
    override fun dependencyRepository(): DependencyRepository = baseProvider.dependencyRepository()
}
