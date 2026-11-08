# Notes for AI agents (any tool)

This project's rules and context live in files every agent should read before working:

1. `AGENT_HANDOFF.md`: current state, completed work, open issues, next steps. Start here.
2. `CLAUDE.md`: project rules (branch `claude/awesome-clarke-ussjo3` only, British English, offline-first, keep the four reference files current, never commit signing credentials or `local.properties`). They apply to every agent, not only Claude.
3. `AUDIT_FINDINGS.md`, `BUILD_AND_TEST.md`, `ARCHITECTURE.md`: findings with stable IDs, build and test commands and results, structure.

Gradle needs `JAVA_HOME` set to Android Studio's bundled JDK:
`/Applications/Android Studio.app/Contents/jbr/Contents/Home`.
