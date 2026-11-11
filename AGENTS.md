# Notes for AI agents (any tool)

This project's rules and context live in files every agent should read before working:

1. `AGENT_HANDOFF.md`: current state, completed work, open issues, next steps. Start here.
2. `CLAUDE.md`: project rules (branch `claude/awesome-clarke-ussjo3` only, British English, offline-first, keep the four reference files current, never commit signing credentials or `local.properties`). They apply to every agent, not only Claude.
3. `AUDIT_FINDINGS.md`, `BUILD_AND_TEST.md`, `ARCHITECTURE.md`: findings with stable IDs, build and test commands and results, structure.
4. `AGENT_DEBUGGING_LESSONS.md`: how hard bugs were actually found and fixed (read it when a bug shows no error, or before spending more than one attempt on a guess).

Gradle needs `JAVA_HOME` set to Android Studio's bundled JDK:
`/Applications/Android Studio.app/Contents/jbr/Contents/Home`, or `/Applications/Android Studio Preview.app/...` when only the Preview is installed (the case since 2026-11-12).

# GitHub Actions budget rules

Copy this file into the root of EVERY private repo (as `AGENTS.md`, or paste it into `CLAUDE.md`).
These rules are mandatory for any coding agent working in this repo.

## Why

The account is on GitHub Free. ALL private repos share one monthly allowance:

- 2,000 Actions minutes per month (Linux runners count 1x; Windows 2x; macOS 10x)
- 0.5 GB Actions storage (artifacts + caches, all private repos together)

Going over blocks CI for every private repo until the next month. The goal is to never
come close: keep total usage under about 1,000 minutes and 0.3 GB.

## 1. Pushing (most important)

- Commit locally as often as you like, but PUSH in batches: once when a task is done,
  not after every commit.
- Never push just to see whether CI passes. Run the build, lint, typecheck and tests
  locally first.
- If CI fails, reproduce and fix it locally, then push once. Never push repeated
  "try again" commits.
- Commits that only touch docs, notes, comments or markdown: add `[skip ci]` to the
  commit message.
- If any run fails with a quota, billing, "spending limit" or "storage" message:
  stop pushing and tell the owner. Do not retry.

## 2. Configure this repo's workflows by repo type

Before creating or editing anything in `.github/workflows/`, check what the repo contains
and use ONLY the matching templates from the budget kit:

| The repo has...                                   | Use                     |
|---------------------------------------------------|-------------------------|
| `package.json` (web / Node / Vite / Next, etc.)   | `ci-web.yml`            |
| Android / Gradle (`gradlew`, `build.gradle*`)     | `android.yml`           |
| A `supabase/` folder (migrations or functions)    | `supabase-deploy.yml`   |
| No `supabase/` folder                             | NO Supabase workflow. Delete any Supabase workflow that exists. |
| Only docs / no build step                         | No workflow at all      |

A repo can need more than one (for example web + Supabase).
Do not add any other workflow (scheduled jobs, matrix builds, extra OS) without asking the owner.

## 3. Rules every workflow must follow

1. Triggers: `push` to `main` only, `pull_request` to `main`, and `workflow_dispatch`.
   Never `push` on all branches. Never `schedule:` (cron) unless the owner asks.
2. `paths` or `paths-ignore` so docs-only changes don't run anything:
   `paths-ignore: ['**.md', 'docs/**', '.github/ISSUE_TEMPLATE/**']`
3. Concurrency on every build/test workflow so a new push cancels the old run:
   ```yaml
   concurrency:
     group: ${{ github.workflow }}-${{ github.ref }}
     cancel-in-progress: true
   ```
   EXCEPTION: deploy workflows (Supabase) use `cancel-in-progress: false`.
   Cancelling a deploy halfway can leave a database migration half-applied.
4. `runs-on: ubuntu-latest` only. Never `macos-*` or `windows-*`.
5. `timeout-minutes` on every job: 10 for web, 20 for Android, 10 for deploys.
6. No matrix builds (several Node or Java versions). Test one version.
7. Skip work that isn't needed: no Android/web build when only Supabase files changed,
   and the reverse (use `paths:` filters).

## 4. Storage rules (artifacts and caches)

- `actions/upload-artifact`: always `retention-days: 1`. Upload only the single file
  needed (for example the APK), never `node_modules`, `build/` or `.gradle/`.
  Prefer not uploading artifacts at all unless the owner needs them.
- Gradle: `gradle/actions/setup-gradle` with
  `cache-read-only: ${{ github.ref != 'refs/heads/main' }}` (only `main` writes the cache).
- Node: `actions/setup-node` with `cache: npm` (or pnpm/yarn) is fine. Do not add a
  separate `actions/cache` step for `node_modules`.
- Never cache build output folders.

## 5. When you change a workflow

Tell the owner, before pushing:
- which workflow files changed and why
- roughly how many minutes one run takes and when it will run

## 6. Rough budget check

Web CI ~3 min, Android build ~12 min, Supabase deploy ~1 min.
Example: 5 private repos, each with ~15 runs on main/PRs a month, is about 15 x 5 x 5 min
= 375 minutes, which leaves plenty of headroom. If you notice a repo running far more
often than this, tell the owner.
