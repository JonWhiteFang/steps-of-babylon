package com.whitefang.stepsofbabylon.data.diagnostics

/**
 * One persisted crash record (#190 REL-1). Local-only diagnostic — never uploaded.
 * `timestampMillis` is supplied by the caller (the store reads no clock, so it is
 * trivially testable); `stackPreview` is truncated to [CrashBreadcrumbStore.MAX_STACK_CHARS].
 */
data class CrashBreadcrumb(
    val timestampMillis: Long,
    val threadName: String,
    val exceptionClass: String,
    val message: String?,
    val stackPreview: String,
)
