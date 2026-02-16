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
 * **Terminal Status Subfolders:**
 * Entities with terminal statuses are placed in dedicated subfolders within
 * their parent directory, keeping active items at the root level:
 * - COMPLETED → `Completed/`
 * - CANCELLED → `Cancelled/`
 * - DEFERRED → `Deferred/`
 * - ARCHIVED → `Archived/`
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
     * @param status The project status (used to determine terminal subfolder)
     * @return Relative path within vault (e.g., "Project Name/_project.md" or "Completed/Project Name/_project.md")
     */
    fun resolveProjectPath(projectName: String, status: String? = null): String {
        val sanitized = sanitizeFileName(projectName)
        val subfolder = terminalSubfolder(status)
        return if (subfolder != null) {
            "$subfolder/$sanitized/_project.md"
        } else {
            "$sanitized/_project.md"
        }
    }

    /**
     * Resolves the file path for a feature.
     *
     * @param featureName The name of the feature
     * @param projectName The name of the parent project, or null if unassigned
     * @param status The feature status (used to determine terminal subfolder)
     * @return Relative path within vault
     */
    fun resolveFeaturePath(featureName: String, projectName: String?, status: String? = null): String {
        val sanitizedFeature = sanitizeFileName(featureName)
        val subfolder = terminalSubfolder(status)

        return if (projectName.isNullOrBlank()) {
            if (subfolder != null) {
                "$subfolder/$sanitizedFeature/_feature.md"
            } else {
                "$sanitizedFeature/_feature.md"
            }
        } else {
            val sanitizedProject = sanitizeFileName(projectName)
            if (subfolder != null) {
                "$sanitizedProject/$subfolder/$sanitizedFeature/_feature.md"
            } else {
                "$sanitizedProject/$sanitizedFeature/_feature.md"
            }
        }
    }

    /**
     * Resolves the file path for a task.
     *
     * @param taskTitle The title of the task
     * @param featureName The name of the parent feature, or null if unassigned
     * @param projectName The name of the parent project, or null if unassigned
     * @param status The task status (used to determine terminal subfolder)
     * @return Relative path within vault
     */
    fun resolveTaskPath(taskTitle: String, featureName: String?, projectName: String?, status: String? = null): String {
        val sanitizedTask = sanitizeFileName(taskTitle)
        val subfolder = terminalSubfolder(status)

        return when {
            // Task with both project and feature
            !projectName.isNullOrBlank() && !featureName.isNullOrBlank() -> {
                val sanitizedProject = sanitizeFileName(projectName)
                val sanitizedFeature = sanitizeFileName(featureName)
                if (subfolder != null) {
                    "$sanitizedProject/$sanitizedFeature/$subfolder/$sanitizedTask.md"
                } else {
                    "$sanitizedProject/$sanitizedFeature/$sanitizedTask.md"
                }
            }
            // Task with project only
            !projectName.isNullOrBlank() -> {
                val sanitizedProject = sanitizeFileName(projectName)
                if (subfolder != null) {
                    "$sanitizedProject/$subfolder/$sanitizedTask.md"
                } else {
                    "$sanitizedProject/$sanitizedTask.md"
                }
            }
            // Task with feature only
            !featureName.isNullOrBlank() -> {
                val sanitizedFeature = sanitizeFileName(featureName)
                if (subfolder != null) {
                    "$sanitizedFeature/$subfolder/$sanitizedTask.md"
                } else {
                    "$sanitizedFeature/$sanitizedTask.md"
                }
            }
            // Task with no parent
            else -> {
                if (subfolder != null) {
                    "$subfolder/$sanitizedTask.md"
                } else {
                    "$sanitizedTask.md"
                }
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
         * Returns the terminal status subfolder name, or null if the status is active.
         * Each terminal status gets its own dedicated subfolder.
         */
        fun terminalSubfolder(status: String?): String? {
            return when (status?.uppercase()) {
                "COMPLETED" -> "Completed"
                "CANCELLED" -> "Cancelled"
                "DEFERRED" -> "Deferred"
                "ARCHIVED" -> "Archived"
                else -> null
            }
        }

        /**
         * Sanitizes a filename by removing invalid characters and handling edge cases.
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
