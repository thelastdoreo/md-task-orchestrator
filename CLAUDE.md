# CLAUDE.md

## Project Overview

Kotlin-based MCP server for hierarchical task management (Projects → Features → Tasks) with dependency tracking, templates, and workflow automation.

**Stack:** Kotlin 2.2.0, Exposed ORM, MCP SDK 0.7.2, Flyway, SQLite, Gradle, Docker

## Build Commands

```bash
./gradlew build                    # Build (fat JAR)
./gradlew test                     # All tests
./gradlew test --tests "ClassName" # Specific test
./gradlew clean build              # Clean build
```

**Docker:**
```bash
docker build -t mcp-task-orchestrator:dev .
docker run --rm -i -v mcp-task-data:/app/data -v /project:/project -e AGENT_CONFIG_DIR=/project mcp-task-orchestrator:dev
```

## Architecture (Clean Architecture)

- **Domain** (`domain/`) — Models, repository interfaces. No framework deps.
- **Application** (`application/tools/`, `application/service/`) — MCP tools, services, templates
- **Infrastructure** (`infrastructure/`) — SQLite repos, Exposed schema, Flyway migrations
- **Interface** (`interfaces/mcp/`) — McpServer, McpToolAdapter, resources, prompts

**Entry:** `src/main/kotlin/Main.kt`
**Base package:** `io.github.jpicklyk.mcptask`

## AGENT_CONFIG_DIR (Docker Critical)

Services reading `.taskorchestrator/` config MUST use:
```kotlin
val root = Paths.get(System.getenv("AGENT_CONFIG_DIR") ?: System.getProperty("user.dir"))
```
In Docker, set `-e AGENT_CONFIG_DIR=/project` to point to mounted project directory.

## Adding Components

- **MCP Tool:** Extend `BaseToolDefinition`, implement `validateParams()`/`execute()`, register in `McpServer.createTools()`
- **Migration:** Create `src/main/resources/db/migration/V{N}__{Description}.sql`
- **Template:** Create in `application/service/templates/`, register in `TemplateInitializerImpl.kt`
- **Repository method:** Interface in `domain/repository/`, implement in `infrastructure/database/repository/`

## Environment Variables

- `DATABASE_PATH` — SQLite path (default: `data/tasks.db`)
- `USE_FLYWAY` — Enable migrations (default: `true` in Docker)
- `MCP_DEBUG` — Debug logging
- `AGENT_CONFIG_DIR` — Directory containing `.taskorchestrator/` (default: cwd)

## Source vs Installed Files

**ALWAYS modify SOURCE files** in `src/main/resources/`, NEVER `.claude/` (overwritten by `setup_claude_orchestration`):
- Agents: `src/main/resources/agents/claude/task-orchestrator/*.md`
- Skills: `src/main/resources/skills/*/SKILL.md`
- Plugin: `src/main/resources/claude-plugin/task-orchestrator/`
- Config: `src/main/resources/orchestration/default-config.yaml`

## v2 Consolidated Tools (18 tools)

All tools use `operation` + `containerType` parameters:
- `manage_container` — create, update, delete, setStatus, bulkUpdate
- `query_container` — get, search, export, overview
- `manage_sections` / `query_sections` — Section CRUD
- `manage_template` / `query_templates` / `apply_template` — Template ops
- `manage_dependency` / `query_dependencies` — Dependency ops
- `list_tags` / `get_tag_usage` / `rename_tag` — Tag ops
- `get_next_status` / `query_workflow_state` — Workflow (read-only)
- `recommend_agent` / `get_agent_definition` / `setup_project` / `rebuild_vault` — System

**Key patterns:**
- Use `overview` operation for hierarchical views without section content (93-95% token savings vs get+sections)
- Use `get` with `includeSections=true` only when user explicitly wants section content
- Always `list_templates` before creating tasks/features
- Use `recommend_agent(taskId)` to route to specialist subagents

## Git Workflow

Main branch: `main`. Conventional commits. Run tests before committing.

## Version

Format: `{major}.{minor}.{patch}.{git-commit-count}-{qualifier}` in `build.gradle.kts`
