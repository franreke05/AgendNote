# AI Decision Log

## 2026-07-27 — Preserve Glass while tightening hierarchy
Retain the existing glassmorphism system and coral accent; improve density, contrast, spacing and navigation safety instead of redesigning the brand.

## 2026-07-27 — Bottom navigation owns its space
Lay out bottom navigation in the app shell so every destination receives a safe content viewport and no screen needs overlay-specific padding.

## 2026-07-27 — Make modals opaque and scrollable
Use a dedicated modal fill and full-width platform `Dialog` for creation, picker, detail and confirmation flows. Background context remains visible through the scrim but never competes with form text.

## 2026-07-27 — Prefer discoverable option grids
Show finite choices such as recurrence modes and the 16 label colors in complete grids. Reserve horizontal scrolling for genuinely unbounded content such as user-created labels.

## 2026-07-27 — Permissions follow user intent
Do not request notifications at startup. Start the notification and exact-alarm permission sequence only after the user presses `Configurar recordatorios`.

## 2026-07-27 — Serialize reminder mutations
Use one common FIFO channel to process schedule/cancel commands off the UI thread. This preserves mutation order, avoids stale alarms and keeps common JVM tests independent of a Main dispatcher.

## 2026-08-04 — AgendNote's security model is single-tenant by design, not RLS-per-user
`tasks`/`notes`/`labels`/etc. have no `user_id` column and no Supabase Auth. RLS is enabled with zero policies (deny-all direct client access); Edge Functions running under `service_role` gate access with one static shared secret (`x-app-secret`, timing-safe compared). Full detail in `docs/agendnote/SECURITY_AUDIT.md`. Do not "fix" this by adding `auth.uid()` policies without a deliberate multi-user redesign decision.
