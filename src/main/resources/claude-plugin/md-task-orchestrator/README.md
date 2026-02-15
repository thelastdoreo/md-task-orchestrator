# MD Task Orchestrator Plugin

Claude Code plugin for MD Task Orchestrator — task management with automatic markdown export.

## What It Does

Provides skills, agents, and communication style for orchestrating work through the md-task-orchestrator MCP server:

- **12 Skills** — feature orchestration, task orchestration, dependency analysis, status progression, and more
- **4 Specialist Agents** — feature architect, planning specialist, senior engineer, implementation specialist
- **Communication Style** — professional coordinator voice with status indicators

## Installation

From GitHub:
```
/plugin install thelastdoreo/md-task-orchestrator
```

After installation, run `setup_project` via the MCP server to initialize configuration for your project.

## Usage

Skills auto-activate from natural language:
- "Create a feature for X" → Feature Orchestration Skill
- "What's next?" → Task Orchestration Skill
- "What's blocking?" → Dependency Analysis Skill

## Token Cost

The session hook adds ~600 tokens of communication style guidelines.

## Version

**Version**: 2.0.0

## Links

- [MD Task Orchestrator](https://github.com/thelastdoreo/md-task-orchestrator)
- Forked from [MCP Task Orchestrator](https://github.com/jpicklyk/task-orchestrator)
