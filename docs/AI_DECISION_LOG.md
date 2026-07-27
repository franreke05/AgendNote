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
