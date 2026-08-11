package com.franciscor.agendnote.core.platform

/**
 * True only in debug builds - gates the "test notification in +10s" development affordance
 * (Operación Aniversario, "Sprint Final" directive item 13: "NO dejes un botón absurdo de debug
 * visible en la release final").
 */
expect val isDebugBuild: Boolean
