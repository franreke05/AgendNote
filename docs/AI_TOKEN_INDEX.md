# AI Token Index

| Path | Contains | Read when |
|------|----------|-----------|
| `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/app/App.kt` | Shared root composition | Changing startup/theme behavior |
| `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/app/navigation/AppNavHost.kt` | App shell and destination wiring | Changing navigation or global layout |
| `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/app/navigation/NavigationComponents.kt` | Headers, bottom bar and remote banner | Polishing global UI |
| `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/core/ui/` | Glass components, theme and responsive metrics | Changing design-system behavior |
| `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/` | Agenda, calendar, recurrence and task state | Any task/calendar work |
| `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/labels/` | Label CRUD and UI | Label work |
| `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/settings/` | Theme, background and destructive actions | Settings work |
| `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/core/network/` | Ktor client, DTOs and configuration | Backend/API failures |
| `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/core/notifications/` | Common notification contract/provider | Reminder behavior shared by platforms |
| `composeApp/src/androidMain/kotlin/com/franciscor/agendnote/core/notifications/` | Alarm scheduling, persistence, boot restore and receiver | Android reminders or permissions |
| `composeApp/src/iosMain/kotlin/com/franciscor/agendnote/core/notifications/` | UserNotifications implementation | iOS reminder behavior |
| `composeApp/src/commonTest/` | Shared ViewModel and recurrence tests | Regression testing |
| `docs/superpowers/` | Accepted product specs/plans | Validating intended behavior |
| `docs/FINAL_UI_QA_REPORT.md` | Screen-by-screen before/change/reason and QA evidence | Reviewing or handing off the 2026-07-27 audit |
| `supabase/` | Backend contract and deployment files | Data-model or Edge Function work |
