package io.github.jpicklyk.mcptask.infrastructure.export

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilePathResolverTest {

    private val resolver = FilePathResolver()

    // ========== Project Path Tests ==========

    @Test
    fun `resolveProjectPath returns correct path for normal project name`() {
        val path = resolver.resolveProjectPath("My Project")
        assertEquals("My Project/_project.md", path)
    }

    @Test
    fun `resolveProjectPath sanitizes project name`() {
        val path = resolver.resolveProjectPath("Project: Test/Invalid")
        assertEquals("Project TestInvalid/_project.md", path)
    }

    @Test
    fun `resolveProjectPath handles empty project name`() {
        val path = resolver.resolveProjectPath("")
        assertEquals("_unnamed/_project.md", path)
    }

    @Test
    fun `resolveProjectPath handles blank project name`() {
        val path = resolver.resolveProjectPath("   ")
        assertEquals("_unnamed/_project.md", path)
    }

    // ========== Feature Path Tests ==========

    @Test
    fun `resolveFeaturePath returns correct path with project`() {
        val path = resolver.resolveFeaturePath("My Feature", "My Project")
        assertEquals("My Project/My Feature/_feature.md", path)
    }

    @Test
    fun `resolveFeaturePath returns root-level path without project`() {
        val path = resolver.resolveFeaturePath("My Feature", null)
        assertEquals("My Feature/_feature.md", path)
    }

    @Test
    fun `resolveFeaturePath returns root-level path with blank project`() {
        val path = resolver.resolveFeaturePath("My Feature", "   ")
        assertEquals("My Feature/_feature.md", path)
    }

    @Test
    fun `resolveFeaturePath sanitizes feature and project names`() {
        val path = resolver.resolveFeaturePath("Feature: Test", "Project: Test")
        assertEquals("Project Test/Feature Test/_feature.md", path)
    }

    // ========== Task Path Tests ==========

    @Test
    fun `resolveTaskPath returns correct path with project and feature`() {
        val path = resolver.resolveTaskPath("My Task", "My Feature", "My Project")
        assertEquals("My Project/My Feature/My Task.md", path)
    }

    @Test
    fun `resolveTaskPath returns correct path with project only`() {
        val path = resolver.resolveTaskPath("My Task", null, "My Project")
        assertEquals("My Project/My Task.md", path)
    }

    @Test
    fun `resolveTaskPath returns correct path with project and blank feature`() {
        val path = resolver.resolveTaskPath("My Task", "   ", "My Project")
        assertEquals("My Project/My Task.md", path)
    }

    @Test
    fun `resolveTaskPath returns feature-level path with feature only`() {
        val path = resolver.resolveTaskPath("My Task", "My Feature", null)
        assertEquals("My Feature/My Task.md", path)
    }

    @Test
    fun `resolveTaskPath returns feature-level path with feature and blank project`() {
        val path = resolver.resolveTaskPath("My Task", "My Feature", "   ")
        assertEquals("My Feature/My Task.md", path)
    }

    @Test
    fun `resolveTaskPath returns root-level path for orphaned task`() {
        val path = resolver.resolveTaskPath("My Task", null, null)
        assertEquals("My Task.md", path)
    }

    @Test
    fun `resolveTaskPath returns root-level path for task with blank parent names`() {
        val path = resolver.resolveTaskPath("My Task", "   ", "   ")
        assertEquals("My Task.md", path)
    }

    @Test
    fun `resolveTaskPath sanitizes all names`() {
        val path = resolver.resolveTaskPath("Task: Test", "Feature: Test", "Project: Test")
        assertEquals("Project Test/Feature Test/Task Test.md", path)
    }

    // ========== Filename Sanitization Tests ==========

    @Test
    fun `sanitizeFileName removes slash characters`() {
        val sanitized = FilePathResolver.sanitizeFileName("Test/Name")
        assertEquals("TestName", sanitized)
    }

    @Test
    fun `sanitizeFileName removes backslash characters`() {
        val sanitized = FilePathResolver.sanitizeFileName("Test\\Name")
        assertEquals("TestName", sanitized)
    }

    @Test
    fun `sanitizeFileName removes colon characters`() {
        val sanitized = FilePathResolver.sanitizeFileName("Test:Name")
        assertEquals("TestName", sanitized)
    }

    @Test
    fun `sanitizeFileName removes asterisk characters`() {
        val sanitized = FilePathResolver.sanitizeFileName("Test*Name")
        assertEquals("TestName", sanitized)
    }

    @Test
    fun `sanitizeFileName removes question mark characters`() {
        val sanitized = FilePathResolver.sanitizeFileName("Test?Name")
        assertEquals("TestName", sanitized)
    }

    @Test
    fun `sanitizeFileName removes quote characters`() {
        val sanitized = FilePathResolver.sanitizeFileName("Test\"Name")
        assertEquals("TestName", sanitized)
    }

    @Test
    fun `sanitizeFileName removes angle bracket characters`() {
        val sanitized = FilePathResolver.sanitizeFileName("Test<>Name")
        assertEquals("TestName", sanitized)
    }

    @Test
    fun `sanitizeFileName removes pipe characters`() {
        val sanitized = FilePathResolver.sanitizeFileName("Test|Name")
        assertEquals("TestName", sanitized)
    }

    @Test
    fun `sanitizeFileName removes all invalid characters`() {
        val sanitized = FilePathResolver.sanitizeFileName("Test/\\:*?\"<>|Name")
        assertEquals("TestName", sanitized)
    }

    @Test
    fun `sanitizeFileName trims leading dots`() {
        val sanitized = FilePathResolver.sanitizeFileName("...Test")
        assertEquals("Test", sanitized)
    }

    @Test
    fun `sanitizeFileName trims trailing dots`() {
        val sanitized = FilePathResolver.sanitizeFileName("Test...")
        assertEquals("Test", sanitized)
    }

    @Test
    fun `sanitizeFileName trims leading spaces`() {
        val sanitized = FilePathResolver.sanitizeFileName("   Test")
        assertEquals("Test", sanitized)
    }

    @Test
    fun `sanitizeFileName trims trailing spaces`() {
        val sanitized = FilePathResolver.sanitizeFileName("Test   ")
        assertEquals("Test", sanitized)
    }

    @Test
    fun `sanitizeFileName trims leading and trailing dots and spaces`() {
        val sanitized = FilePathResolver.sanitizeFileName(" . .Test. . ")
        assertEquals("Test", sanitized)
    }

    @Test
    fun `sanitizeFileName truncates long names to 200 characters`() {
        val longName = "a".repeat(250)
        val sanitized = FilePathResolver.sanitizeFileName(longName)
        assertEquals(200, sanitized.length)
        assertEquals("a".repeat(200), sanitized)
    }

    @Test
    fun `sanitizeFileName handles exactly 200 characters`() {
        val exactName = "a".repeat(200)
        val sanitized = FilePathResolver.sanitizeFileName(exactName)
        assertEquals(200, sanitized.length)
        assertEquals(exactName, sanitized)
    }

    @Test
    fun `sanitizeFileName handles names shorter than 200 characters`() {
        val shortName = "Test Name"
        val sanitized = FilePathResolver.sanitizeFileName(shortName)
        assertEquals("Test Name", sanitized)
    }

    @Test
    fun `sanitizeFileName returns _unnamed for empty string`() {
        val sanitized = FilePathResolver.sanitizeFileName("")
        assertEquals("_unnamed", sanitized)
    }

    @Test
    fun `sanitizeFileName returns _unnamed for blank string`() {
        val sanitized = FilePathResolver.sanitizeFileName("   ")
        assertEquals("_unnamed", sanitized)
    }

    @Test
    fun `sanitizeFileName returns _unnamed for only dots`() {
        val sanitized = FilePathResolver.sanitizeFileName("...")
        assertEquals("_unnamed", sanitized)
    }

    @Test
    fun `sanitizeFileName returns _unnamed for only invalid characters`() {
        val sanitized = FilePathResolver.sanitizeFileName("/\\:*?\"<>|")
        assertEquals("_unnamed", sanitized)
    }

    @Test
    fun `sanitizeFileName returns _unnamed for whitespace and invalid characters`() {
        val sanitized = FilePathResolver.sanitizeFileName("  /\\:*?  ")
        assertEquals("_unnamed", sanitized)
    }

    // ========== Windows Reserved Names Tests ==========

    @Test
    fun `sanitizeFileName handles CON reserved name`() {
        val sanitized = FilePathResolver.sanitizeFileName("CON")
        assertEquals("_CON", sanitized)
    }

    @Test
    fun `sanitizeFileName handles PRN reserved name`() {
        val sanitized = FilePathResolver.sanitizeFileName("PRN")
        assertEquals("_PRN", sanitized)
    }

    @Test
    fun `sanitizeFileName handles AUX reserved name`() {
        val sanitized = FilePathResolver.sanitizeFileName("AUX")
        assertEquals("_AUX", sanitized)
    }

    @Test
    fun `sanitizeFileName handles NUL reserved name`() {
        val sanitized = FilePathResolver.sanitizeFileName("NUL")
        assertEquals("_NUL", sanitized)
    }

    @Test
    fun `sanitizeFileName handles COM1 reserved name`() {
        val sanitized = FilePathResolver.sanitizeFileName("COM1")
        assertEquals("_COM1", sanitized)
    }

    @Test
    fun `sanitizeFileName handles COM9 reserved name`() {
        val sanitized = FilePathResolver.sanitizeFileName("COM9")
        assertEquals("_COM9", sanitized)
    }

    @Test
    fun `sanitizeFileName handles LPT1 reserved name`() {
        val sanitized = FilePathResolver.sanitizeFileName("LPT1")
        assertEquals("_LPT1", sanitized)
    }

    @Test
    fun `sanitizeFileName handles LPT9 reserved name`() {
        val sanitized = FilePathResolver.sanitizeFileName("LPT9")
        assertEquals("_LPT9", sanitized)
    }

    @Test
    fun `sanitizeFileName handles lowercase reserved names`() {
        val sanitized = FilePathResolver.sanitizeFileName("con")
        assertEquals("_con", sanitized)
    }

    @Test
    fun `sanitizeFileName handles mixed case reserved names`() {
        val sanitized = FilePathResolver.sanitizeFileName("CoN")
        assertEquals("_CoN", sanitized)
    }

    @Test
    fun `sanitizeFileName handles reserved names with extensions`() {
        val sanitized = FilePathResolver.sanitizeFileName("CON.txt")
        assertEquals("_CON.txt", sanitized)
    }

    @Test
    fun `sanitizeFileName handles reserved names with multiple dots`() {
        val sanitized = FilePathResolver.sanitizeFileName("AUX.test.md")
        assertEquals("_AUX.test.md", sanitized)
    }

    @Test
    fun `sanitizeFileName does not modify valid names containing reserved words`() {
        val sanitized = FilePathResolver.sanitizeFileName("CONSOLE")
        assertEquals("CONSOLE", sanitized)
    }

    @Test
    fun `sanitizeFileName does not modify valid names starting with reserved words`() {
        val sanitized = FilePathResolver.sanitizeFileName("CON_TEST")
        assertEquals("CON_TEST", sanitized)
    }

    // ========== Complex Sanitization Tests ==========

    @Test
    fun `sanitizeFileName handles complex name with multiple issues`() {
        val sanitized = FilePathResolver.sanitizeFileName("  ...Test/Name: Invalid*Chars?...  ")
        assertEquals("TestName InvalidChars", sanitized)
    }

    @Test
    fun `sanitizeFileName handles name with unicode characters`() {
        val sanitized = FilePathResolver.sanitizeFileName("Test ñame with émojis 😊")
        assertEquals("Test ñame with émojis 😊", sanitized)
    }

    @Test
    fun `sanitizeFileName preserves internal spaces`() {
        val sanitized = FilePathResolver.sanitizeFileName("Test Name With Spaces")
        assertEquals("Test Name With Spaces", sanitized)
    }

    @Test
    fun `sanitizeFileName preserves hyphens and underscores`() {
        val sanitized = FilePathResolver.sanitizeFileName("Test-Name_With-Special")
        assertEquals("Test-Name_With-Special", sanitized)
    }

    @Test
    fun `sanitizeFileName preserves parentheses and brackets`() {
        val sanitized = FilePathResolver.sanitizeFileName("Test (Name) [Special]")
        assertEquals("Test (Name) [Special]", sanitized)
    }

    @Test
    fun `sanitizeFileName truncates after removing invalid characters`() {
        val longName = "Test" + "/".repeat(250)
        val sanitized = FilePathResolver.sanitizeFileName(longName)
        assertTrue(sanitized.length <= 200)
        assertEquals("Test", sanitized)
    }

    // ========== Edge Cases ==========

    @Test
    fun `path resolution handles all empty inputs for task`() {
        val path = resolver.resolveTaskPath("", "", "")
        assertEquals("_unnamed.md", path)
    }

    @Test
    fun `path resolution handles special characters in all components`() {
        val path = resolver.resolveTaskPath("Task: 1", "Feature: 2", "Project: 3")
        assertEquals("Project 3/Feature 2/Task 1.md", path)
    }

    @Test
    fun `path resolution handles very long names in hierarchy`() {
        val longName = "Very Long Name ".repeat(20)
        val path = resolver.resolveTaskPath(longName, longName, longName)
        // Each component should be truncated to 200 chars
        val parts = path.split("/")
        assertTrue(parts.all { it.length <= 200 + 3 }) // +3 for .md extension
    }

    @Test
    fun `path resolution handles reserved names in hierarchy`() {
        val path = resolver.resolveTaskPath("CON", "PRN", "AUX")
        assertEquals("_AUX/_PRN/_CON.md", path)
    }
}
