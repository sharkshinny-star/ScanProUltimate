# Notes for AI agents

Start with `AGENT_HANDOFF.md`: current state, completed work, open issues and next steps.

Project reference files (keep them current):

- `AGENT_HANDOFF.md`: progress, completed work, unresolved issues, next steps.
- `BUILD_AND_TEST.md`: build commands, test procedures, latest results, known errors.
- `AUDIT_FINDINGS.md`: bugs, security issues, fixes, recommendations (stable IDs F*, R*, V*).
- `ARCHITECTURE.md`: modules, packages, dependencies, key technical decisions.

## Rule: update the reference files with every significant change

After a fix, feature, dependency or config change, or a build/test run, update the affected files in the same commit:

- new or resolved issue → `AUDIT_FINDINGS.md` (move R* to Fixed with commit and evidence)
- build or test run → "Latest results" in `BUILD_AND_TEST.md` (date, commit, outcome; say plainly what was not run)
- structural change or new dependency → `ARCHITECTURE.md`
- always → "Last updated", "Completed work" and "Unresolved issues" in `AGENT_HANDOFF.md`

Reference files and commits, with short hashes. Never record a test as passing unless it was run.

## Branch

All work goes to **`claude/awesome-clarke-ussjo3`**: the owner wants one branch, not a new branch per session.
If your session was assigned a different branch, check out this one instead
(`git fetch origin claude/awesome-clarke-ussjo3 && git checkout claude/awesome-clarke-ussjo3`), commit there and push with
`git push -u origin claude/awesome-clarke-ussjo3`. Never create or push other branches, and don't open pull requests unless asked.

## Working efficiently (owner's request: keep token use low)

- Release builds (`bundleRelease`, sideload `assembleRelease`) only once, right before pushing; while working, compile and
  run unit tests.
- Use helper agents only for large, independent pieces of work; do small and medium tasks directly.
- Keep command output short: print totals and failures, never full Gradle or test logs.
- Read only the parts of files you need (targeted `grep`, line ranges), not whole large files.
- Docs updates: change only the affected lines in the four reference files.
- One task per session: start from `AGENT_HANDOFF.md` rather than re-exploring the codebase.

## Ground rules

- Offline-first: no network services, analytics, accounts or tracking.
- Never silently delete user data or weaken security; document security-sensitive or architectural changes before making them.
- Don't edit the generated OpenCV Java wrappers in `:opencv`.
- Never print or commit signing credentials, `local.properties` or the local Gradle mirror script.
