package io.github.jpicklyk.mcptask.infrastructure.export

import io.github.jpicklyk.mcptask.domain.model.*
import io.github.jpicklyk.mcptask.domain.repository.*
import io.github.jpicklyk.mcptask.infrastructure.repository.RepositoryProvider
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExportAwareRepositoryTest {

    private lateinit var exportService: MarkdownExportService
    private lateinit var testScope: TestScope

    @BeforeEach
    fun setup() {
        exportService = mockk(relaxed = true)
        testScope = TestScope()
    }

    @Nested
    inner class TaskRepositoryDecoratorTest {

        private lateinit var delegate: TaskRepository
        private lateinit var decorator: ExportAwareTaskRepository

        @BeforeEach
        fun setup() {
            delegate = mockk(relaxed = true)
            decorator = ExportAwareTaskRepository(delegate, exportService, testScope)
        }

        @Test
        fun `create triggers export on success`() = testScope.runTest {
            val task = createTestTask()
            coEvery { delegate.create(task) } returns Result.Success(task)

            val result = decorator.create(task)

            assertTrue(result is Result.Success)
            advanceUntilIdle()
            coVerify { exportService.exportTask(task.id) }
        }

        @Test
        fun `create does not trigger export on failure`() = testScope.runTest {
            val task = createTestTask()
            coEvery { delegate.create(task) } returns Result.Error(RepositoryError.DatabaseError("fail"))

            decorator.create(task)

            advanceUntilIdle()
            coVerify(exactly = 0) { exportService.exportTask(any()) }
        }

        @Test
        fun `update triggers export on success`() = testScope.runTest {
            val task = createTestTask()
            coEvery { delegate.update(task) } returns Result.Success(task)

            decorator.update(task)

            advanceUntilIdle()
            coVerify { exportService.exportTask(task.id) }
        }

        @Test
        fun `delete triggers onEntityDeleted on success`() = testScope.runTest {
            val id = UUID.randomUUID()
            coEvery { delegate.delete(id) } returns Result.Success(true)

            decorator.delete(id)

            advanceUntilIdle()
            coVerify { exportService.onEntityDeleted(id) }
        }

        @Test
        fun `delete does not trigger export when result is false`() = testScope.runTest {
            val id = UUID.randomUUID()
            coEvery { delegate.delete(id) } returns Result.Success(false)

            decorator.delete(id)

            advanceUntilIdle()
            coVerify(exactly = 0) { exportService.onEntityDeleted(any()) }
        }

        @Test
        fun `read operations delegate unchanged`() = testScope.runTest {
            val id = UUID.randomUUID()
            val task = createTestTask(id)
            coEvery { delegate.getById(id) } returns Result.Success(task)

            val result = decorator.getById(id)

            assertTrue(result is Result.Success)
            assertEquals(task, (result as Result.Success).data)
            coVerify(exactly = 0) { exportService.exportTask(any()) }
        }
    }

    @Nested
    inner class FeatureRepositoryDecoratorTest {

        private lateinit var delegate: FeatureRepository
        private lateinit var taskRepository: TaskRepository
        private lateinit var decorator: ExportAwareFeatureRepository

        @BeforeEach
        fun setup() {
            delegate = mockk(relaxed = true)
            taskRepository = mockk(relaxed = true)
            decorator = ExportAwareFeatureRepository(delegate, exportService, testScope, taskRepository)
        }

        @Test
        fun `create triggers export on success`() = testScope.runTest {
            val feature = createTestFeature()
            coEvery { delegate.create(feature) } returns Result.Success(feature)

            decorator.create(feature)

            advanceUntilIdle()
            coVerify { exportService.exportFeature(feature.id) }
        }

        @Test
        fun `update triggers export on success`() = testScope.runTest {
            val feature = createTestFeature()
            coEvery { delegate.update(feature) } returns Result.Success(feature)

            decorator.update(feature)

            advanceUntilIdle()
            coVerify { exportService.exportFeature(feature.id) }
        }

        @Test
        fun `delete triggers onEntityDeleted on success`() = testScope.runTest {
            val id = UUID.randomUUID()
            coEvery { delegate.delete(id) } returns Result.Success(true)
            coEvery { taskRepository.findByFeatureId(id) } returns emptyList()

            decorator.delete(id)

            advanceUntilIdle()
            coVerify { exportService.onEntityDeleted(id) }
        }

        @Test
        fun `delete cascades to child task markdown files`() = testScope.runTest {
            val featureId = UUID.randomUUID()
            val taskId1 = UUID.randomUUID()
            val taskId2 = UUID.randomUUID()
            val task1 = createTestTask(taskId1)
            val task2 = createTestTask(taskId2)

            coEvery { taskRepository.findByFeatureId(featureId) } returns listOf(task1, task2)
            coEvery { delegate.delete(featureId) } returns Result.Success(true)

            decorator.delete(featureId)

            advanceUntilIdle()
            coVerify { exportService.onEntityDeleted(taskId1) }
            coVerify { exportService.onEntityDeleted(taskId2) }
            coVerify { exportService.onEntityDeleted(featureId) }
        }
    }

    @Nested
    inner class ProjectRepositoryDecoratorTest {

        private lateinit var delegate: ProjectRepository
        private lateinit var featureRepository: FeatureRepository
        private lateinit var taskRepository: TaskRepository
        private lateinit var decorator: ExportAwareProjectRepository

        @BeforeEach
        fun setup() {
            delegate = mockk(relaxed = true)
            featureRepository = mockk(relaxed = true)
            taskRepository = mockk(relaxed = true)
            decorator = ExportAwareProjectRepository(delegate, exportService, testScope, featureRepository, taskRepository)
        }

        @Test
        fun `create triggers export on success`() = testScope.runTest {
            val project = createTestProject()
            coEvery { delegate.create(project) } returns Result.Success(project)

            decorator.create(project)

            advanceUntilIdle()
            coVerify { exportService.exportProject(project.id) }
        }

        @Test
        fun `update triggers export on success`() = testScope.runTest {
            val project = createTestProject()
            coEvery { delegate.update(project) } returns Result.Success(project)

            decorator.update(project)

            advanceUntilIdle()
            coVerify { exportService.exportProject(project.id) }
        }

        @Test
        fun `delete triggers onEntityDeleted on success`() = testScope.runTest {
            val id = UUID.randomUUID()
            coEvery { featureRepository.findByProject(id, limit = Int.MAX_VALUE) } returns Result.Success(emptyList())
            coEvery { taskRepository.findByProject(id, limit = Int.MAX_VALUE) } returns Result.Success(emptyList())
            coEvery { delegate.delete(id) } returns Result.Success(true)

            decorator.delete(id)

            advanceUntilIdle()
            coVerify { exportService.onEntityDeleted(id) }
        }

        @Test
        fun `delete cascades to child feature and task markdown files`() = testScope.runTest {
            val projectId = UUID.randomUUID()
            val featureId = UUID.randomUUID()
            val taskId1 = UUID.randomUUID()
            val taskId2 = UUID.randomUUID()
            val feature = createTestFeature(featureId)
            val task1 = createTestTask(taskId1)
            val task2 = createTestTask(taskId2)

            coEvery { featureRepository.findByProject(projectId, limit = Int.MAX_VALUE) } returns Result.Success(listOf(feature))
            coEvery { taskRepository.findByFeatureId(featureId) } returns listOf(task1)
            coEvery { taskRepository.findByProject(projectId, limit = Int.MAX_VALUE) } returns Result.Success(listOf(task2))
            coEvery { delegate.delete(projectId) } returns Result.Success(true)

            decorator.delete(projectId)

            advanceUntilIdle()
            coVerify { exportService.onEntityDeleted(taskId1) }
            coVerify { exportService.onEntityDeleted(taskId2) }
            coVerify { exportService.onEntityDeleted(featureId) }
            coVerify { exportService.onEntityDeleted(projectId) }
        }
    }

    @Nested
    inner class SectionRepositoryDecoratorTest {

        private lateinit var delegate: SectionRepository
        private lateinit var decorator: ExportAwareSectionRepository

        @BeforeEach
        fun setup() {
            delegate = mockk(relaxed = true)
            decorator = ExportAwareSectionRepository(delegate, exportService, testScope)
        }

        @Test
        fun `addSection triggers parent task export`() = testScope.runTest {
            val entityId = UUID.randomUUID()
            val section = createTestSection(EntityType.TASK, entityId)
            coEvery { delegate.addSection(EntityType.TASK, entityId, section) } returns Result.Success(section)

            decorator.addSection(EntityType.TASK, entityId, section)

            advanceUntilIdle()
            coVerify { exportService.exportTask(entityId) }
        }

        @Test
        fun `addSection triggers parent feature export`() = testScope.runTest {
            val entityId = UUID.randomUUID()
            val section = createTestSection(EntityType.FEATURE, entityId)
            coEvery { delegate.addSection(EntityType.FEATURE, entityId, section) } returns Result.Success(section)

            decorator.addSection(EntityType.FEATURE, entityId, section)

            advanceUntilIdle()
            coVerify { exportService.exportFeature(entityId) }
        }

        @Test
        fun `addSection triggers parent project export`() = testScope.runTest {
            val entityId = UUID.randomUUID()
            val section = createTestSection(EntityType.PROJECT, entityId)
            coEvery { delegate.addSection(EntityType.PROJECT, entityId, section) } returns Result.Success(section)

            decorator.addSection(EntityType.PROJECT, entityId, section)

            advanceUntilIdle()
            coVerify { exportService.exportProject(entityId) }
        }

        @Test
        fun `updateSection triggers parent export`() = testScope.runTest {
            val entityId = UUID.randomUUID()
            val section = createTestSection(EntityType.TASK, entityId)
            coEvery { delegate.updateSection(section) } returns Result.Success(section)

            decorator.updateSection(section)

            advanceUntilIdle()
            coVerify { exportService.exportTask(entityId) }
        }

        @Test
        fun `deleteSection triggers parent export`() = testScope.runTest {
            val entityId = UUID.randomUUID()
            val sectionId = UUID.randomUUID()
            val section = createTestSection(EntityType.FEATURE, entityId, sectionId)
            coEvery { delegate.getSection(sectionId) } returns Result.Success(section)
            coEvery { delegate.deleteSection(sectionId) } returns Result.Success(true)

            decorator.deleteSection(sectionId)

            advanceUntilIdle()
            coVerify { exportService.exportFeature(entityId) }
        }

        @Test
        fun `reorderSections triggers parent export`() = testScope.runTest {
            val entityId = UUID.randomUUID()
            val sectionIds = listOf(UUID.randomUUID(), UUID.randomUUID())
            coEvery { delegate.reorderSections(EntityType.TASK, entityId, sectionIds) } returns Result.Success(true)

            decorator.reorderSections(EntityType.TASK, entityId, sectionIds)

            advanceUntilIdle()
            coVerify { exportService.exportTask(entityId) }
        }
    }

    @Nested
    inner class ExportAwareRepositoryProviderTest {

        @Test
        fun `wraps repositories with export-aware decorators`() {
            val baseProvider = mockk<RepositoryProvider>(relaxed = true)
            val provider = ExportAwareRepositoryProvider(baseProvider, exportService, testScope)

            assertTrue(provider.taskRepository() is ExportAwareTaskRepository)
            assertTrue(provider.featureRepository() is ExportAwareFeatureRepository)
            assertTrue(provider.projectRepository() is ExportAwareProjectRepository)
            assertTrue(provider.sectionRepository() is ExportAwareSectionRepository)
        }

        @Test
        fun `passes through template and dependency repositories unchanged`() {
            val baseProvider = mockk<RepositoryProvider>(relaxed = true)
            val templateRepo = mockk<TemplateRepository>()
            val dependencyRepo = mockk<DependencyRepository>()
            every { baseProvider.templateRepository() } returns templateRepo
            every { baseProvider.dependencyRepository() } returns dependencyRepo

            val provider = ExportAwareRepositoryProvider(baseProvider, exportService, testScope)

            assertEquals(templateRepo, provider.templateRepository())
            assertEquals(dependencyRepo, provider.dependencyRepository())
        }
    }

    // Test helpers

    private fun createTestTask(id: UUID = UUID.randomUUID()) = Task(
        id = id,
        title = "Test Task",
        summary = "A test task for unit testing purposes with enough characters",
        status = TaskStatus.PENDING,
        priority = Priority.MEDIUM,
        complexity = 5,
        createdAt = Instant.now(),
        modifiedAt = Instant.now()
    )

    private fun createTestFeature(id: UUID = UUID.randomUUID()) = Feature(
        id = id,
        name = "Test Feature",
        summary = "A test feature for unit testing purposes with enough characters",
        status = FeatureStatus.PLANNING,
        priority = Priority.MEDIUM,
        createdAt = Instant.now(),
        modifiedAt = Instant.now()
    )

    private fun createTestProject(id: UUID = UUID.randomUUID()) = Project(
        id = id,
        name = "Test Project",
        summary = "A test project for unit testing purposes with enough characters",
        status = ProjectStatus.PLANNING,
        createdAt = Instant.now(),
        modifiedAt = Instant.now()
    )

    private fun createTestSection(
        entityType: EntityType,
        entityId: UUID,
        id: UUID = UUID.randomUUID()
    ) = Section(
        id = id,
        entityType = entityType,
        entityId = entityId,
        title = "Test Section",
        usageDescription = "For testing",
        content = "Test content",
        ordinal = 0,
        createdAt = Instant.now(),
        modifiedAt = Instant.now()
    )
}
