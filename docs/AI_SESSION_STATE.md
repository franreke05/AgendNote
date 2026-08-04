# AI Session State

## Current task
Executing the phased plan in `docs/agendnote/IMPLEMENTATION_PLAN.md` (all phases, per user
direction). Fases 2, 3, and 4 are complete and verified (by compilation + full Android
builds, not by device/emulator testing - none was launched this session). Next up per the
plan: Fase 5 (recurrencia robusta), Fase 6 (captura rápida NLP es-ES + listas inteligentes),
Fase 7 (funcionalidades "después", evaluar una por una), Fase 8 (regresión final).

## Files touched this session
27 commits on `agent/finish-audit-notifications`. Summary by phase:
- Docs: `docs/agendnote/*.md` (discovery, audits, implementation plan/log, Fase 4 proposal).
- Fase 3: undo-on-complete (`GlassSnackbar`, `PendingUndo`), reduce motion/transparency
  (`AccessibilityPreferences` expect/actual, `ReduceMotion.kt`), `GlassEmptyState`.
- Fase 2: sanitized error responses (Kotlin `resolveServerError`, all 6 Edge Functions'
  `internalErrorResponse`).
- Fase 4: SQL migration (`deadline_at`, `task_reminders`, `task_subtasks`), `api-tasks`
  Edge Function extended, Kotlin DTOs/domain (`Subtask`, `TaskItem`/`TaskDraft` extended),
  `NewTaskSheet` UI (deadline/reminders/subtasks), `TaskDetailsOverlay` read-only display,
  notification scheduler wired to `earliestReminderInstant`, `TaskCard` subtask progress chip.
- Tests: 52/52 passing (debug + release), up from 39 at session start.

## Decisions made
- See `docs/AI_DECISION_LOG.md` for durable architectural decisions (security model
  correction, Supabase MCP scope, TDD-where-testable discipline).
- Reminders modeled as explicit stored instants (not offsets) per the user's delegation of
  that product decision back to my own recommendation in `FASE4_PROPUESTA.md`.
- Deliberately did NOT rewrite the working `AndroidReminderScheduler`/`AndroidReminderStore`
  to support N simultaneous alarms per task - too risky to do unverified. Only the earliest
  reminder is actually scheduled today; this is documented as explicit deferred work.
- Preserved existing behavior: a task with a time still gets a default reminder even if the
  user never opens the reminders section (LaunchedEffect defaulting to "en el momento").

## Pending work
- Visual/on-device QA for everything built this session - no emulator was launched.
- iOS: nothing compiled this session (no macOS/Xcode); every iOS file touched is flagged
  unverified in its commit.
- SQL migration and Edge Function changes: not applied/tested against a live Supabase project
  (user chose repo-only auditing).
- Known gap (not this session's to fix): no way to edit an already-created task anywhere in
  the app (only create/toggle-done/delete) - limits deadline/reminders/subtasks to
  creation-time only for now.
- Fases 5-8 of the plan not started.

## Commands run
Repeated `:composeApp:testDebugUnitTest`/`:testReleaseUnitTest` (after every RED and GREEN
step) and `:androidApp:assembleDebug` (after every UI-touching change) - all green throughout.
No `deno`, no live Supabase MCP calls against AgendNote's real project (none available/opted
out), no Android emulator launched.

## Failures / blockers
- Same as before: iOS compilation and Supabase live verification are unavailable in this
  environment. Both are called out explicitly in every commit message and doc that touches
  them - never silently assumed correct.
