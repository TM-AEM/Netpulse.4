package com.example.data.resolver

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.example.model.ResolvedAppInfo

/**
 * Abstraction for safely resolving Android UID to package information
 * without requiring broad permissions such as QUERY_ALL_PACKAGES.
 */
interface AppInfoResolver {
    fun resolveAppInfo(uid: Int): ResolvedAppInfo
}

/**
 * Deterministic fallback resolver that formats unknown or unresolvable UIDs safely.
 */
class FallbackAppInfoResolver : AppInfoResolver {
    override fun resolveAppInfo(uid: Int): ResolvedAppInfo {
        val name = when (uid) {
            0 -> "Root (Kernel)"
            Process.SYSTEM_UID -> "Android OS"
            AppInfoResolverImpl.UID_REMOVED -> "Removed Apps"
            AppInfoResolverImpl.UID_TETHERING -> "Tethering"
            else -> "UID $uid"
        }
        return ResolvedAppInfo(
            uid = uid,
            packageNames = emptyList(),
            displayName = name
        )
    }
}

/**
 * Production implementation using standard PackageManager.getPackagesForUid(uid).
 * Never crashes when a package cannot be found or is filtered by package visibility.
 */
class AppInfoResolverImpl(
    private val context: Context,
    private val packageManager: PackageManager? = null
) : AppInfoResolver {

    private val pm: PackageManager? = packageManager ?: try {
        context.packageManager
    } catch (e: Exception) {
        null
    }

    companion object {
        const val UID_REMOVED = -4
        const val UID_TETHERING = -5
    }

    override fun resolveAppInfo(uid: Int): ResolvedAppInfo {
        // Special Android kernel and system UIDs
        when (uid) {
            0 -> return ResolvedAppInfo(
                uid = uid,
                packageNames = emptyList(),
                displayName = "Root (Kernel)"
            )
            UID_REMOVED -> return ResolvedAppInfo(
                uid = uid,
                packageNames = emptyList(),
                displayName = "Removed Apps"
            )
            UID_TETHERING -> return ResolvedAppInfo(
                uid = uid,
                packageNames = emptyList(),
                displayName = "Tethering"
            )
        }

        val manager = pm ?: return fallbackForUid(uid)

        val packages = try {
            manager.getPackagesForUid(uid)
        } catch (e: Exception) {
            null
        }

        if (packages.isNullOrEmpty()) {
            return when (uid) {
                Process.SYSTEM_UID -> ResolvedAppInfo(
                    uid = uid,
                    packageNames = listOf("android"),
                    displayName = "Android OS"
                )
                else -> fallbackForUid(uid)
            }
        }

        val sortedPackages = packages.toList().sorted()
        val primaryPackage = sortedPackages.first()

        val label = try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.getApplicationInfo(primaryPackage, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                manager.getApplicationInfo(primaryPackage, 0)
            }
            manager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            primaryPackage
        }

        val displayName = if (label.isNotBlank()) label else primaryPackage

        return ResolvedAppInfo(
            uid = uid,
            packageNames = sortedPackages,
            displayName = displayName
        )
    }

    private fun fallbackForUid(uid: Int): ResolvedAppInfo {
        val name = when (uid) {
            Process.SYSTEM_UID -> "Android OS"
            else -> "UID $uid"
        }
        return ResolvedAppInfo(
            uid = uid,
            packageNames = emptyList(),
            displayName = name
        )
    }
}
