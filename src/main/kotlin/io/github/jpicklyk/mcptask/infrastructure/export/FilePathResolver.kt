package io.github.jpicklyk.mcptask.infrastructure.export

/**
 * Resolves hierarchical file paths for entities within a markdown vault.
 *
 * This class determines the correct file path for projects, features, and tasks
 * based on their hierarchical relationships, ensuring proper organization within
 * the vault directory structure.
 *
 * **Path Resolution Logic:**
 * - Project → `"Project Name/_project.md"`
 * - Feature with project → `"Project Name/Feature Name/_feature.md"`
 * - Feature without project → `"Feature Name/_feature.md"`
 * - Task with project + feature → `"Project Name/Feature Name/Task Title.md"`
 * - Task with project only → `"Project Name/Task Title.md"`
 * - Task with feature only → `"Feature Name/Task Title.md"`
 * - Task with no parent → `"Task Title.md"`
 *
 * **Filename Sanitization:**
 * - Removes invalid characters: `/ \ : * ? " < > |`
 * - Trims leading/trailing dots and spaces
 * - Truncates to 200 characters
 * - Replaces empty results with `_unnamed`
 * - Handles Windows reserved names (CON, PRN, AUX, NUL, COM1-9, LPT1-9) by prefixing with `_`
 */
class FilePathResolver {

    /**
     * Resolves the file path for a project.
     *
     * @param projectName The name of the project
     * @return Relative path within vault (e.g., "Project Name/_project.md")
     */
    fun resolveProjectPath(projectName: String): String {
        val sanitized = sanitizeFileName(projectName)
        return "$sanitized/_project.md"
    }

    /**
     * Resolves the file path for a feature.
     *
     * @param featureName The name of the feature
     * @param projectName The name of the parent project, or null if unassigned
     * @return Relative path within vault
     */
    fun resolveFeaturePath(featureName: String, projectName: String?): String {
        val sanitizedFeature = sanitizeFileName(featureName)

        return if (projectName.isNullOrBlank()) {
            "$sanitizedFeature/_feature.md"
        } else {
            val sanitizedProject = sanitizeFileName(projectName)
            "$sanitizedProject/$sanitizedFeature/_feature.md"
        }
    }

    /**
     * Resolves the file path for a task.
     *
     * @param taskTitle The title of the task
     * @param featureName The name of the parent feature, or null if unassigned
     * @param projectName The name of the parent project, or null if unassigned
     * @return Relative path within vault
     */
    fun resolveTaskPath(taskTitle: String, featureName: String?, projectName: String?): String {
        val sanitizedTask = sanitizeFileName(taskTitle)

        return when {
            // Task with both project and feature
            !projectName.isNullOrBlank() && !featureName.isNullOrBlank() -> {
                val sanitizedProject = sanitizeFileName(projectName)
                val sanitizedFeature = sanitizeFileName(featureName)
                "$sanitizedProject/$sanitizedFeature/$sanitizedTask.md"
            }
            // Task with project only
            !projectName.isNullOrBlank() -> {
                val sanitizedProject = sanitizeFileName(projectName)
                "$sanitizedProject/$sanitizedTask.md"
            }
            // Task with feature only
            !featureName.isNullOrBlank() -> {
                val sanitizedFeature = sanitizeFileName(featureName)
                "$sanitizedFeature/$sanitizedTask.md"
            }
            // Task with no parent
            else -> {
                "$sanitizedTask.md"
            }
        }
    }

    companion object {
        /**
         * Windows reserved filenames that need special handling.
         */
        private val WINDOWS_RESERVED_NAMES = setOf(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
        )

        /**
         * Invalid characters for filenames across platforms.
         */
        private val INVALID_CHARS = Regex("[/\\\\:*?\"<>|]")

        /**
         * Maximum filename length to prevent filesystem issues.
         */
        private const val MAX_FILENAME_LENGTH = 200

        /**
         * Sanitizes a filename by removing invalid characters and handling edge cases.
         *
         * **Sanitization Rules:**
         * - Removes characters: `/ \ : * ? " < > |`
         * - Trims leading/trailing dots and spaces
         * - Truncates to 200 characters
         * - Replaces empty result with `_unnamed`
         * - Handles Windows reserved names (CON, PRN, AUX, NUL, COM1-9, LPT1-9) by prefixing with `_`
         *
         * @param name The filename to sanitize
         * @return Sanitized filename safe for all platforms
         */
        fun sanitizeFileName(name: String): String {
            // Replace invalid characters with empty string
            var sanitized = name.replace(INVALID_CHARS, "")

            // Trim leading/trailing dots and spaces
            sanitized = sanitized.trim('.', ' ')

            // Truncate to max length
            if (sanitized.length > MAX_FILENAME_LENGTH) {
                sanitized = sanitized.substring(0, MAX_FILENAME_LENGTH)
            }

            // Handle empty result
            if (sanitized.isBlank()) {
                return "_unnamed"
            }

            // Handle Windows reserved names (case-insensitive check)
            val upperName = sanitized.uppercase()
            if (WINDOWS_RESERVED_NAMES.contains(upperName)) {
                return "_$sanitized"
            }

            // Check for reserved names with extensions (e.g., "CON.txt")
            val nameWithoutExtension = sanitized.substringBeforeLast('.')
            if (WINDOWS_RESERVED_NAMES.contains(nameWithoutExtension.uppercase())) {
                return "_$sanitized"
            }

            return sanitized
        }
    }
}
