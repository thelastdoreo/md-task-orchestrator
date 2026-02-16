package io.github.jpicklyk.mcptask.application.tools.export

import io.github.jpicklyk.mcptask.application.tools.ToolCategory
import io.github.jpicklyk.mcptask.application.tools.ToolExecutionContext
import io.github.jpicklyk.mcptask.application.tools.base.BaseToolDefinition
import io.github.jpicklyk.mcptask.infrastructure.export.MarkdownExportService
import io.github.jpicklyk.mcptask.infrastructure.util.ErrorCodes
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * MCP tool to trigger a full re-export of all entities to the markdown vault.
 *
 * Use this to rebuild the vault after wiping it, or to bring the vault back
 * in sync with the database. The operation is idempotent — running it multiple
 * times is safe and will overwrite existing files with current data.
 *
 * @param exportService Optional export service; null when markdown export is disabled
 */
class RebuildVaultTool(
    private val exportService: MarkdownExportService?
) : BaseToolDefinition() {

    override val category: ToolCategory = ToolCategory.SYSTEM
    override val name: String = "rebuild_vault"
    override val title: String = "Rebuild Markdown Vault"

    override val description: String = """Re-export all projects, features, and tasks to the markdown vault.

Use when:
- Vault was wiped or corrupted
- Vault is out of sync with the database
- First-time setup after enabling markdown export

This is idempotent — safe to run multiple times. Overwrites existing files with current data.

No parameters required.

Related: manage_container, query_container
"""

    override val parameterSchema: Tool.Input = Tool.Input(
        properties = JsonObject(emptyMap()),
        required = emptyList()
    )

    override suspend fun execute(params: JsonElement, context: ToolExecutionContext): JsonElement {
        if (exportService == null) {
            return errorResponse(
                "Markdown export is not enabled. Set MD_VAULT_PATH environment variable to enable.",
                ErrorCodes.VALIDATION_ERROR
            )
        }

        return try {
            exportService.fullExport()
            successResponse("Vault rebuild completed. All projects, features, and tasks have been exported.")
        } catch (e: Exception) {
            errorResponse(
                "Vault rebuild failed",
                ErrorCodes.INTERNAL_ERROR,
                e.message ?: "Unknown error"
            )
        }
    }
}
