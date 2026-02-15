package io.github.jpicklyk.mcptask.infrastructure.export

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Configuration for automatic markdown vault export.
 *
 * Vault path defaults to a `md/` directory sibling to the SQLite database file,
 * derived from the `DATABASE_PATH` environment variable. This can be overridden
 * with `MD_VAULT_PATH` for custom locations.
 *
 * Export can be disabled entirely by setting `MD_AUTO_EXPORT=false`.
 *
 * **Environment Variables:**
 * - `MD_VAULT_PATH` — Override vault directory path (optional)
 * - `MD_AUTO_EXPORT` — Enable/disable auto-export (default: `true`)
 * - `DATABASE_PATH` — Used to derive default vault path (default: `data/tasks.db`)
 */
object MarkdownExportConfig {

    /**
     * The vault directory path for markdown export.
     *
     * Resolution order:
     * 1. `MD_VAULT_PATH` environment variable (explicit override)
     * 2. Derived from `DATABASE_PATH`: replaces the db filename with `md/`
     *    - `data/tasks.db` → `data/md/`
     *    - `/app/data/tasks.db` → `/app/data/md/`
     */
    val vaultPath: Path
        get() {
            val override = System.getenv("MD_VAULT_PATH")
            if (!override.isNullOrBlank()) {
                return Paths.get(override)
            }
            val databasePath = System.getenv("DATABASE_PATH") ?: "data/tasks.db"
            return Paths.get(databasePath).parent?.resolve("md") ?: Paths.get("data/md")
        }

    /**
     * Whether automatic markdown export is enabled.
     * Defaults to `true`. Set `MD_AUTO_EXPORT=false` to disable.
     */
    val autoExport: Boolean
        get() = System.getenv("MD_AUTO_EXPORT")?.toBoolean() ?: true

    /**
     * Whether markdown export is enabled.
     * Requires [autoExport] to be true.
     */
    fun isEnabled(): Boolean = autoExport
}
