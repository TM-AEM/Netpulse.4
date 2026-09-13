package com.example.model

/**
 * Minimal model representing safely resolved application identity for a given UID.
 * Fallback values are deterministic without requiring QUERY_ALL_PACKAGES permission.
 */
data class ResolvedAppInfo(
    val uid: Int,
    val packageNames: List<String> = emptyList(),
    val displayName: String
)
