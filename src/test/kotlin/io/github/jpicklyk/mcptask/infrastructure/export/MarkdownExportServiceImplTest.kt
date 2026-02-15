package io.github.jpicklyk.mcptask.infrastructure.export

import io.github.jpicklyk.mcptask.domain.model.*
import io.github.jpicklyk.mcptask.domain.rendering.MarkdownRenderer
import io.github.jpicklyk.mcptask.domain.repository.*
import io.github.jpicklyk.mcptask.infrastructure.repository.RepositoryProvider
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [MarkdownExportServiceImpl].
 *
 * Uses MockK for repository mocking and JUnit TempDir for vault filesystem testing.
 */
class MarkdownExportServiceImplTest {

    @TempDir
    lateinit var vaultPath: Path

    private lateinit var mockRepoProvider: RepositoryProvider
    private lateinit var mockTaskRepo: TaskRepository
    private lateinit var mockFeatureRepo: FeatureRepository
    private lateinit var mockProjectRepo: ProjectRepository
    private lateinit var mockSectionRepo: SectionRepository
    private lateinit var mockRenderer: MarkdownRenderer

    private lateinit var service: MarkdownExportServiceImpl

    @BeforeEach
    fun setUp() {
        // Create mocks
        mockRepoProvider = mockk()
        mockTaskRepo = mockk()
        mockFeatureRepo = mockk()
        mockProjectRepo = mockk()
        mockSectionRepo = mockk()
        mockRenderer = mockk()

        // Wire repository provider
        every { mockRepoProvider.taskRepository() } returns mockTaskRepo
        every { mockRepoProvider.featureRepository() } returns mockFeatureRepo
        every { mockRepoProvider.projectRepository() } returns mockProjectRepo
        every { mockRepoProvider.sectionRepository() } returns mockSectionRepo

        // Create service
        service = MarkdownExportServiceImpl(
            repositoryProvider = mockRepoProvider,
            markdownRenderer = mockRenderer,
            vaultPath = vaultPath
        )
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    // ========== exportTask Tests ==========

    @Test
    fun `exportTask writes file at correct path with rendered content`() = runBlocking {
        val taskId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val featureId = UUID.randomUUID()

        val task = Task(
            id = taskId,
            projectId = projectId,
            featureId = featureId,
            title = "Test Task",
            summary = "A test task",
            createdAt = Instant.now(),
            modifiedAt = Instant.now()
        )

        val sections = listOf(
            Section(
                id = UUID.randomUUID(),
                entityType = EntityType.TASK,
                entityId = taskId,
                title = "Details",
                usageDescription = "Task details",
                content = "Task content",
                ordinal = 1
            )
        )

        val project = Project(
            id = projectId,
            name = "Test Project",
            summary = "A test project"
        )

        val feature = Feature(
            id = featureId,
            projectId = projectId,
            name = "Test Feature",
            summary = "A test feature"
        )

        val renderedMarkdown = "# Test Task\n\nA test task\n\n## Details\n\nTask content"

        // Mock repository calls
        coEvery { mockTaskRepo.getById(taskId) } returns Result.Success(task)
        coEvery { mockSectionRepo.getSectionsForEntity(EntityType.TASK, taskId) } returns Result.Success(sections)
        coEvery { mockFeatureRepo.getById(featureId) } returns Result.Success(feature)
        coEvery { mockProjectRepo.getById(projectId) } returns Result.Success(project)

        // Mock renderer
        every { mockRenderer.renderTask(task, sections) } returns renderedMarkdown

        // Execute
        service.exportTask(taskId)

        // Verify file was written
        val expectedPath = vaultPath.resolve("Test Project/Test Feature/Test Task.md")
        assertTrue(expectedPath.exists(), "Task markdown file should exist")
        assertEquals(renderedMarkdown, expectedPath.readText(), "File content should match rendered markdown")

        // Verify all mocks were called
        coVerify { mockTaskRepo.getById(taskId) }
        coVerify { mockSectionRepo.getSectionsForEntity(EntityType.TASK, taskId) }
        coVerify { mockFeatureRepo.getById(featureId) }
        coVerify { mockProjectRepo.getById(projectId) }
        verify { mockRenderer.renderTask(task, sections) }
    }

    @Test
    fun `exportTask handles orphaned task (no project, no feature)`() = runBlocking {
        val taskId = UUID.randomUUID()
        val task = Task(
            id = taskId,
            projectId = null,
            featureId = null,
            title = "Orphaned Task",
            summary = "A task without parents"
        )

        coEvery { mockTaskRepo.getById(taskId) } returns Result.Success(task)
        coEvery { mockSectionRepo.getSectionsForEntity(EntityType.TASK, taskId) } returns Result.Success(emptyList())
        every { mockRenderer.renderTask(task, emptyList()) } returns "# Orphaned Task"

        service.exportTask(taskId)

        val expectedPath = vaultPath.resolve("_unassigned/Orphaned Task.md")
        assertTrue(expectedPath.exists(), "Orphaned task should be in _unassigned")
    }

    @Test
    fun `exportTask handles missing task (repository error)`() = runBlocking {
        val taskId = UUID.randomUUID()

        coEvery { mockTaskRepo.getById(taskId) } returns Result.Error(
            RepositoryError.NotFound(taskId, EntityType.TASK, "Task not found")
        )

        // Should not throw
        service.exportTask(taskId)

        // Verify no file was created (vault should be empty or only have sync state)
        val fileCount = Files.walk(vaultPath)
            .filter { Files.isRegularFile(it) && !it.fileName.toString().startsWith(".sync-state") }
            .count()
        assertEquals(0, fileCount, "No markdown files should be created for missing task")
    }

    @Test
    fun `exportTask detects rename and deletes old file`() = runBlocking {
        val taskId = UUID.randomUUID()
        val task = Task(
            id = taskId,
            title = "Renamed Task",
            summary = "Task was renamed"
        )

        // First export with old title
        val oldTask = task.copy(title = "Old Task Name")
        coEvery { mockTaskRepo.getById(taskId) } returns Result.Success(oldTask)
        coEvery { mockSectionRepo.getSectionsForEntity(EntityType.TASK, taskId) } returns Result.Success(emptyList())
        every { mockRenderer.renderTask(oldTask, emptyList()) } returns "# Old Task Name"

        service.exportTask(taskId)

        val oldPath = vaultPath.resolve("_unassigned/Old Task Name.md")
        assertTrue(oldPath.exists(), "Old file should exist after first export")

        // Second export with new title
        coEvery { mockTaskRepo.getById(taskId) } returns Result.Success(task)
        every { mockRenderer.renderTask(task, emptyList()) } returns "# Renamed Task"

        service.exportTask(taskId)

        val newPath = vaultPath.resolve("_unassigned/Renamed Task.md")
        assertTrue(newPath.exists(), "New file should exist after rename")
        assertFalse(oldPath.exists(), "Old file should be deleted after rename")
    }

    // ========== exportFeature Tests ==========

    @Test
    fun `exportFeature writes file and re-exports child tasks`() = runBlocking {
        val featureId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val taskId1 = UUID.randomUUID()
        val taskId2 = UUID.randomUUID()

        val feature = Feature(
            id = featureId,
            projectId = projectId,
            name = "Test Feature",
            summary = "A test feature"
        )

        val project = Project(
            id = projectId,
            name = "Test Project",
            summary = "A test project"
        )

        val task1 = Task(id = taskId1, featureId = featureId, title = "Task 1", summary = "First task")
        val task2 = Task(id = taskId2, featureId = featureId, title = "Task 2", summary = "Second task")

        // Mock feature export
        coEvery { mockFeatureRepo.getById(featureId) } returns Result.Success(feature)
        coEvery { mockSectionRepo.getSectionsForEntity(EntityType.FEATURE, featureId) } returns Result.Success(emptyList())
        coEvery { mockProjectRepo.getById(projectId) } returns Result.Success(project)
        every { mockRenderer.renderFeature(feature, emptyList()) } returns "# Test Feature"

        // Mock child tasks
        every { mockTaskRepo.findByFeatureId(featureId) } returns listOf(task1, task2)
        coEvery { mockTaskRepo.getById(taskId1) } returns Result.Success(task1)
        coEvery { mockTaskRepo.getById(taskId2) } returns Result.Success(task2)
        coEvery { mockSectionRepo.getSectionsForEntity(EntityType.TASK, taskId1) } returns Result.Success(emptyList())
        coEvery { mockSectionRepo.getSectionsForEntity(EntityType.TASK, taskId2) } returns Result.Success(emptyList())
        every { mockRenderer.renderTask(task1, emptyList()) } returns "# Task 1"
        every { mockRenderer.renderTask(task2, emptyList()) } returns "# Task 2"

        service.exportFeature(featureId)

        // Verify feature file
        val featurePath = vaultPath.resolve("Test Project/Test Feature/_feature.md")
        assertTrue(featurePath.exists(), "Feature file should exist")

        // Verify child task files
        val task1Path = vaultPath.resolve("Test Project/Test Feature/Task 1.md")
        val task2Path = vaultPath.resolve("Test Project/Test Feature/Task 2.md")
        assertTrue(task1Path.exists(), "Task 1 file should exist")
        assertTrue(task2Path.exists(), "Task 2 file should exist")
    }

    // ========== exportProject Tests ==========

    @Test
    fun `exportProject writes file and re-exports child features and tasks`() = runBlocking {
        val projectId = UUID.randomUUID()
        val featureId = UUID.randomUUID()

        val project = Project(
            id = projectId,
            name = "Test Project",
            summary = "A test project"
        )

        val feature = Feature(
            id = featureId,
            projectId = projectId,
            name = "Test Feature",
            summary = "A test feature"
        )

        // Mock project export
        coEvery { mockProjectRepo.getById(projectId) } returns Result.Success(project)
        coEvery { mockSectionRepo.getSectionsForEntity(EntityType.PROJECT, projectId) } returns Result.Success(emptyList())
        every { mockRenderer.renderProject(project, emptyList()) } returns "# Test Project"

        // Mock child features
        coEvery { mockFeatureRepo.findByProject(projectId, limit = Int.MAX_VALUE) } returns Result.Success(listOf(feature))
        coEvery { mockFeatureRepo.getById(featureId) } returns Result.Success(feature)
        coEvery { mockSectionRepo.getSectionsForEntity(EntityType.FEATURE, featureId) } returns Result.Success(emptyList())
        every { mockRenderer.renderFeature(feature, emptyList()) } returns "# Test Feature"
        every { mockTaskRepo.findByFeatureId(featureId) } returns emptyList()

        service.exportProject(projectId)

        // Verify project file
        val projectPath = vaultPath.resolve("Test Project/_project.md")
        assertTrue(projectPath.exists(), "Project file should exist")

        // Verify feature file
        val featurePath = vaultPath.resolve("Test Project/Test Feature/_feature.md")
        assertTrue(featurePath.exists(), "Feature file should exist")
    }

    // ========== onEntityDeleted Tests ==========

    @Test
    fun `onEntityDeleted removes file and cleans empty directories`() = runBlocking {
        val taskId = UUID.randomUUID()
        val task = Task(
            id = taskId,
            title = "Task to Delete",
            summary = "Will be deleted"
        )

        // First export the task
        coEvery { mockTaskRepo.getById(taskId) } returns Result.Success(task)
        coEvery { mockSectionRepo.getSectionsForEntity(EntityType.TASK, taskId) } returns Result.Success(emptyList())
        every { mockRenderer.renderTask(task, emptyList()) } returns "# Task to Delete"

        service.exportTask(taskId)

        val taskPath = vaultPath.resolve("_unassigned/Task to Delete.md")
        assertTrue(taskPath.exists(), "Task file should exist before deletion")

        // Delete the task
        service.onEntityDeleted(taskId)

        assertFalse(taskPath.exists(), "Task file should be deleted")
        assertFalse(vaultPath.resolve("_unassigned").exists(), "Empty _unassigned directory should be cleaned up")
    }

    @Test
    fun `onEntityDeleted handles missing entity gracefully`() = runBlocking {
        val taskId = UUID.randomUUID()

        // Should not throw when entity doesn't exist in sync state
        service.onEntityDeleted(taskId)
    }

    // ========== fullExport Tests ==========

    @Test
    fun `fullExport exports all entities`() = runBlocking {
        val projectId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        val taskId = UUID.randomUUID()

        val project = Project(id = projectId, name = "Project", summary = "Project summary")
        val feature = Feature(id = featureId, projectId = projectId, name = "Feature", summary = "Feature summary")
        val task = Task(id = taskId, featureId = featureId, title = "Task", summary = "Task summary")

        // Mock findAll for all repositories
        coEvery { mockProjectRepo.findAll(limit = Int.MAX_VALUE) } returns Result.Success(listOf(project))
        coEvery { mockFeatureRepo.findAll(limit = Int.MAX_VALUE) } returns Result.Success(listOf(feature))
        coEvery { mockTaskRepo.findAll(limit = Int.MAX_VALUE) } returns Result.Success(listOf(task))

        // Mock individual entity loads
        coEvery { mockProjectRepo.getById(projectId) } returns Result.Success(project)
        coEvery { mockFeatureRepo.getById(featureId) } returns Result.Success(feature)
        coEvery { mockTaskRepo.getById(taskId) } returns Result.Success(task)

        // Mock sections
        coEvery { mockSectionRepo.getSectionsForEntity(EntityType.PROJECT, projectId) } returns Result.Success(emptyList())
        coEvery { mockSectionRepo.getSectionsForEntity(EntityType.FEATURE, featureId) } returns Result.Success(emptyList())
        coEvery { mockSectionRepo.getSectionsForEntity(EntityType.TASK, taskId) } returns Result.Success(emptyList())

        // Mock renderers
        every { mockRenderer.renderProject(project, emptyList()) } returns "# Project"
        every { mockRenderer.renderFeature(feature, emptyList()) } returns "# Feature"
        every { mockRenderer.renderTask(task, emptyList()) } returns "# Task"

        // Mock child queries
        coEvery { mockFeatureRepo.findByProject(projectId, limit = Int.MAX_VALUE) } returns Result.Success(listOf(feature))
        every { mockTaskRepo.findByFeatureId(featureId) } returns listOf(task)

        service.fullExport()

        // Verify all files were created
        assertTrue(vaultPath.resolve("Project/_project.md").exists(), "Project file should exist")
        assertTrue(vaultPath.resolve("Project/Feature/_feature.md").exists(), "Feature file should exist")
        assertTrue(vaultPath.resolve("Project/Feature/Task.md").exists(), "Task file should exist")
    }

    @Test
    fun `fullExport handles repository errors gracefully`() = runBlocking {
        // Mock failures
        coEvery { mockProjectRepo.findAll(limit = Int.MAX_VALUE) } returns Result.Error(
            RepositoryError.DatabaseError("Database error")
        )
        coEvery { mockFeatureRepo.findAll(limit = Int.MAX_VALUE) } returns Result.Error(
            RepositoryError.DatabaseError("Database error")
        )
        coEvery { mockTaskRepo.findAll(limit = Int.MAX_VALUE) } returns Result.Error(
            RepositoryError.DatabaseError("Database error")
        )

        // Should not throw
        service.fullExport()
    }

    @Test
    fun `exportTask handles sections loading error gracefully`() = runBlocking {
        val taskId = UUID.randomUUID()
        val task = Task(
            id = taskId,
            title = "Test Task",
            summary = "Task with section error"
        )

        coEvery { mockTaskRepo.getById(taskId) } returns Result.Success(task)
        coEvery { mockSectionRepo.getSectionsForEntity(EntityType.TASK, taskId) } returns Result.Error(
            RepositoryError.DatabaseError("Sections error")
        )
        every { mockRenderer.renderTask(task, emptyList()) } returns "# Test Task"

        // Should not throw, should render with empty sections
        service.exportTask(taskId)

        val taskPath = vaultPath.resolve("_unassigned/Test Task.md")
        assertTrue(taskPath.exists(), "Task file should exist even when sections fail to load")
    }

    @Test
    fun `exportFeature handles unassigned feature (no project)`() = runBlocking {
        val featureId = UUID.randomUUID()
        val feature = Feature(
            id = featureId,
            projectId = null,
            name = "Unassigned Feature",
            summary = "Feature without project"
        )

        coEvery { mockFeatureRepo.getById(featureId) } returns Result.Success(feature)
        coEvery { mockSectionRepo.getSectionsForEntity(EntityType.FEATURE, featureId) } returns Result.Success(emptyList())
        every { mockRenderer.renderFeature(feature, emptyList()) } returns "# Unassigned Feature"
        every { mockTaskRepo.findByFeatureId(featureId) } returns emptyList()

        service.exportFeature(featureId)

        val expectedPath = vaultPath.resolve("_unassigned/Unassigned Feature/_feature.md")
        assertTrue(expectedPath.exists(), "Unassigned feature should be in _unassigned")
    }
}
