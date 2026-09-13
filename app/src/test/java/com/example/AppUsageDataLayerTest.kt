package com.example

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.NetworkStatsRepositoryImpl
import com.example.data.resolver.AppInfoResolver
import com.example.data.resolver.AppInfoResolverImpl
import com.example.data.resolver.FallbackAppInfoResolver
import com.example.data.source.NetworkStatsDataSource
import com.example.model.DateRange
import com.example.model.NetworkStatsDebugInfo
import com.example.model.NetworkType
import com.example.model.NetworkUsage
import com.example.model.RawUidUsageBucket
import com.example.model.ResolvedAppInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPackageManager
import org.robolectric.Shadows
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUsageDataLayerTest {

    private lateinit var context: Context
    private lateinit var packageManager: PackageManager
    private lateinit var shadowPackageManager: ShadowPackageManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        packageManager = context.packageManager
        shadowPackageManager = Shadows.shadowOf(packageManager)
    }

    private class FakeNetworkStatsDataSource(
        var hasPermission: Boolean = true,
        val wifiBuckets: MutableList<RawUidUsageBucket> = mutableListOf(),
        val mobileBuckets: MutableList<RawUidUsageBucket> = mutableListOf()
    ) : NetworkStatsDataSource {
        val queriedTypes = mutableListOf<NetworkType>()

        override fun hasUsageStatsPermission(): Boolean = hasPermission

        override suspend fun getUsageForNetwork(
            networkType: NetworkType,
            startTimeMs: Long,
            endTimeMs: Long
        ): NetworkUsage {
            if (!hasPermission) throw SecurityException("PACKAGE_USAGE_STATS permission is not granted")
            return NetworkUsage.zero(networkType, startTimeMs, endTimeMs)
        }

        override suspend fun getUidUsage(
            networkType: NetworkType,
            startTimeMs: Long,
            endTimeMs: Long
        ): List<RawUidUsageBucket> {
            if (!hasPermission) throw SecurityException("PACKAGE_USAGE_STATS permission is not granted")
            queriedTypes.add(networkType)
            return when (networkType) {
                NetworkType.WIFI -> wifiBuckets
                NetworkType.MOBILE -> mobileBuckets
                NetworkType.TOTAL -> throw IllegalArgumentException("Query individual types to aggregate TOTAL")
            }
        }

        override suspend fun getDebugInfo(
            networkType: NetworkType,
            startTimeMs: Long,
            endTimeMs: Long
        ): NetworkStatsDebugInfo {
            throw UnsupportedOperationException("Not needed in this test")
        }
    }

    private class StubAppInfoResolver(
        private val mapping: Map<Int, ResolvedAppInfo> = emptyMap()
    ) : AppInfoResolver {
        override fun resolveAppInfo(uid: Int): ResolvedAppInfo {
            return mapping[uid] ?: ResolvedAppInfo(uid, emptyList(), "UID $uid")
        }
    }

    // A. Multiple buckets for the same UID are aggregated correctly.
    @Test
    fun `A - multiple buckets for the same UID are aggregated correctly`() = runBlocking {
        val dataSource = FakeNetworkStatsDataSource(
            wifiBuckets = mutableListOf(
                RawUidUsageBucket(uid = 10100, rxBytes = 1000L, txBytes = 500L, rxPackets = 10L, txPackets = 5L),
                RawUidUsageBucket(uid = 10100, rxBytes = 2000L, txBytes = 300L, rxPackets = 20L, txPackets = 3L),
                RawUidUsageBucket(uid = 10100, rxBytes = 500L, txBytes = 200L, rxPackets = 5L, txPackets = 2L)
            )
        )
        val repo = NetworkStatsRepositoryImpl(dataSource)
        val dateRange = DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1))

        val result = repo.getAppUsageForRange(dateRange, NetworkType.WIFI)
        assertTrue(result.isSuccess)
        val list = result.getOrThrow()
        assertEquals(1, list.size)

        val appUsage = list[0]
        assertEquals(10100, appUsage.uid)
        assertEquals(3500L, appUsage.downloadBytes)
        assertEquals(1000L, appUsage.uploadBytes)
        assertEquals(4500L, appUsage.totalBytes)
        assertEquals(35L, appUsage.rxPackets)
        assertEquals(10L, appUsage.txPackets)
    }

    // B. WIFI and MOBILE remain isolated.
    @Test
    fun `B - WIFI and MOBILE remain isolated`() = runBlocking {
        val dataSource = FakeNetworkStatsDataSource(
            wifiBuckets = mutableListOf(
                RawUidUsageBucket(uid = 10001, rxBytes = 5000L, txBytes = 1000L)
            ),
            mobileBuckets = mutableListOf(
                RawUidUsageBucket(uid = 10002, rxBytes = 2000L, txBytes = 400L)
            )
        )
        val repo = NetworkStatsRepositoryImpl(dataSource)
        val dateRange = DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1))

        // Query WIFI only
        val wifiResult = repo.getAppUsageForRange(dateRange, NetworkType.WIFI)
        assertTrue(wifiResult.isSuccess)
        val wifiList = wifiResult.getOrThrow()
        assertEquals(1, wifiList.size)
        assertEquals(10001, wifiList[0].uid)
        assertEquals(5000L, wifiList[0].wifiUsage.downloadBytes)
        assertEquals(0L, wifiList[0].mobileUsage.totalBytes)
        assertEquals(listOf(NetworkType.WIFI), dataSource.queriedTypes)

        dataSource.queriedTypes.clear()

        // Query MOBILE only
        val mobileResult = repo.getAppUsageForRange(dateRange, NetworkType.MOBILE)
        assertTrue(mobileResult.isSuccess)
        val mobileList = mobileResult.getOrThrow()
        assertEquals(1, mobileList.size)
        assertEquals(10002, mobileList[0].uid)
        assertEquals(2000L, mobileList[0].mobileUsage.downloadBytes)
        assertEquals(0L, mobileList[0].wifiUsage.totalBytes)
        assertEquals(listOf(NetworkType.MOBILE), dataSource.queriedTypes)
    }

    // C. TOTAL correctly equals WIFI + MOBILE per UID.
    @Test
    fun `C - TOTAL correctly equals WIFI + MOBILE per UID`() = runBlocking {
        val dataSource = FakeNetworkStatsDataSource(
            wifiBuckets = mutableListOf(
                RawUidUsageBucket(uid = 10050, rxBytes = 4000L, txBytes = 1000L, rxPackets = 40L, txPackets = 10L)
            ),
            mobileBuckets = mutableListOf(
                RawUidUsageBucket(uid = 10050, rxBytes = 6000L, txBytes = 2000L, rxPackets = 60L, txPackets = 20L)
            )
        )
        val repo = NetworkStatsRepositoryImpl(dataSource)
        val dateRange = DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1))

        val result = repo.getAppUsageForRange(dateRange, NetworkType.TOTAL)
        assertTrue(result.isSuccess)
        val list = result.getOrThrow()
        assertEquals(1, list.size)

        val app = list[0]
        assertEquals(10050, app.uid)
        assertEquals(4000L, app.wifiUsage.downloadBytes)
        assertEquals(1000L, app.wifiUsage.uploadBytes)
        assertEquals(6000L, app.mobileUsage.downloadBytes)
        assertEquals(2000L, app.mobileUsage.uploadBytes)

        assertEquals(10000L, app.totalUsage.downloadBytes)
        assertEquals(3000L, app.totalUsage.uploadBytes)
        assertEquals(13000L, app.totalUsage.totalBytes)
        assertEquals(app.wifiUsage.totalBytes + app.mobileUsage.totalBytes, app.totalUsage.totalBytes)
        assertEquals(100L, app.rxPackets)
        assertEquals(30L, app.txPackets)
    }

    // D. RX maps to Download.
    @Test
    fun `D - RX maps to Download`() = runBlocking {
        val dataSource = FakeNetworkStatsDataSource(
            wifiBuckets = mutableListOf(
                RawUidUsageBucket(uid = 10200, rxBytes = 7777L, txBytes = 0L)
            )
        )
        val repo = NetworkStatsRepositoryImpl(dataSource)
        val dateRange = DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1))

        val result = repo.getAppUsageForRange(dateRange, NetworkType.WIFI).getOrThrow()
        assertEquals(7777L, result[0].downloadBytes)
        assertEquals(7777L, result[0].totalUsage.downloadBytes)
        assertEquals(0L, result[0].uploadBytes)
    }

    // E. TX maps to Upload.
    @Test
    fun `E - TX maps to Upload`() = runBlocking {
        val dataSource = FakeNetworkStatsDataSource(
            wifiBuckets = mutableListOf(
                RawUidUsageBucket(uid = 10200, rxBytes = 0L, txBytes = 8888L)
            )
        )
        val repo = NetworkStatsRepositoryImpl(dataSource)
        val dateRange = DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1))

        val result = repo.getAppUsageForRange(dateRange, NetworkType.WIFI).getOrThrow()
        assertEquals(0L, result[0].downloadBytes)
        assertEquals(8888L, result[0].uploadBytes)
        assertEquals(8888L, result[0].totalUsage.uploadBytes)
    }

    // F. Packet counters preserve actual values.
    @Test
    fun `F - Packet counters preserve actual values`() = runBlocking {
        val dataSource = FakeNetworkStatsDataSource(
            wifiBuckets = mutableListOf(
                RawUidUsageBucket(uid = 10300, rxBytes = 100L, txBytes = 100L, rxPackets = 12L, txPackets = 8L)
            )
        )
        val repo = NetworkStatsRepositoryImpl(dataSource)
        val dateRange = DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1))

        val result = repo.getAppUsageForRange(dateRange, NetworkType.WIFI).getOrThrow()
        assertEquals(12L, result[0].rxPackets)
        assertEquals(8L, result[0].txPackets)
    }

    // G. Packet counters remain unavailable when source reports unavailable.
    @Test
    fun `G - Packet counters remain unavailable when source reports unavailable`() = runBlocking {
        val dataSource = FakeNetworkStatsDataSource(
            wifiBuckets = mutableListOf(
                RawUidUsageBucket(uid = 10400, rxBytes = 500L, txBytes = 200L, rxPackets = -1L, txPackets = -1L),
                RawUidUsageBucket(uid = 10400, rxBytes = 300L, txBytes = 100L, rxPackets = -1L, txPackets = -1L)
            )
        )
        val repo = NetworkStatsRepositoryImpl(dataSource)
        val dateRange = DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1))

        val result = repo.getAppUsageForRange(dateRange, NetworkType.WIFI).getOrThrow()
        assertEquals(-1L, result[0].rxPackets)
        assertEquals(-1L, result[0].txPackets)
    }

    // H. Unknown UID does not crash.
    @Test
    fun `H - Unknown UID does not crash`() {
        val resolver = AppInfoResolverImpl(context)
        val info = resolver.resolveAppInfo(99999)
        assertEquals(99999, info.uid)
        assertTrue(info.packageNames.isEmpty())
        assertEquals("UID 99999", info.displayName)

        val fallbackResolver = FallbackAppInfoResolver()
        val fbInfo = fallbackResolver.resolveAppInfo(88888)
        assertEquals(88888, fbInfo.uid)
        assertEquals("UID 88888", fbInfo.displayName)
    }

    // I. Multiple packages sharing one UID are handled deterministically.
    @Test
    fun `I - Multiple packages sharing one UID are handled deterministically`() {
        val uid = 10555
        val appInfo = ApplicationInfo().apply {
            packageName = "com.shared.app.a"
            nonLocalizedLabel = "Shared App A"
        }
        shadowPackageManager.addPackage(appInfo.packageName)
        shadowPackageManager.setPackagesForUid(uid, "com.shared.app.b", "com.shared.app.a")

        val resolver = AppInfoResolverImpl(context)
        val resolved = resolver.resolveAppInfo(uid)

        assertEquals(uid, resolved.uid)
        // Must be sorted deterministically
        assertEquals(listOf("com.shared.app.a", "com.shared.app.b"), resolved.packageNames)
        assertNotNull(resolved.displayName)
        assertTrue(resolved.displayName.isNotEmpty())
    }

    // J. Zero-byte UID behavior is deterministic.
    @Test
    fun `J - Zero-byte UID behavior is deterministic`() = runBlocking {
        val dataSource = FakeNetworkStatsDataSource(
            wifiBuckets = mutableListOf(
                // 1. Zero bytes with no packets -> must be excluded
                RawUidUsageBucket(uid = 10001, rxBytes = 0L, txBytes = 0L, rxPackets = -1L, txPackets = -1L),
                // 2. Zero bytes with zero packets -> must be excluded
                RawUidUsageBucket(uid = 10002, rxBytes = 0L, txBytes = 0L, rxPackets = 0L, txPackets = 0L),
                // 3. Zero bytes but WITH packets -> meaningful packet activity, must be retained
                RawUidUsageBucket(uid = 10003, rxBytes = 0L, txBytes = 0L, rxPackets = 10L, txPackets = 0L),
                // 4. Positive bytes -> retained
                RawUidUsageBucket(uid = 10004, rxBytes = 100L, txBytes = 0L, rxPackets = -1L, txPackets = -1L)
            )
        )
        val repo = NetworkStatsRepositoryImpl(dataSource)
        val dateRange = DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1))

        val result = repo.getAppUsageForRange(dateRange, NetworkType.WIFI).getOrThrow()
        val uids = result.map { it.uid }

        assertFalse(uids.contains(10001))
        assertFalse(uids.contains(10002))
        assertTrue(uids.contains(10003))
        assertTrue(uids.contains(10004))
    }

    // K. Permission/security failure is propagated.
    @Test
    fun `K - Permission failure is propagated as failure without fabricating data`() = runBlocking {
        val dataSource = FakeNetworkStatsDataSource(hasPermission = false)
        val repo = NetworkStatsRepositoryImpl(dataSource)
        val dateRange = DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1))

        val result = repo.getAppUsageForRange(dateRange, NetworkType.TOTAL)
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception is SecurityException)
    }

    // L. Cancellation is preserved.
    @Test
    fun `L - Cancellation is preserved and rethrown`() = runBlocking {
        val dataSource = object : NetworkStatsDataSource {
            override fun hasUsageStatsPermission(): Boolean = true
            override suspend fun getUsageForNetwork(networkType: NetworkType, startTimeMs: Long, endTimeMs: Long): NetworkUsage {
                throw CancellationException("Simulated coroutine cancellation")
            }
            override suspend fun getUidUsage(networkType: NetworkType, startTimeMs: Long, endTimeMs: Long): List<RawUidUsageBucket> {
                throw CancellationException("Simulated coroutine cancellation")
            }
            override suspend fun getDebugInfo(networkType: NetworkType, startTimeMs: Long, endTimeMs: Long): NetworkStatsDebugInfo {
                throw CancellationException("Simulated cancellation")
            }
        }
        val repo = NetworkStatsRepositoryImpl(dataSource)
        val dateRange = DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1))

        try {
            repo.getAppUsageForRange(dateRange, NetworkType.WIFI)
            fail("Expected CancellationException to be rethrown")
        } catch (e: CancellationException) {
            assertEquals("Simulated coroutine cancellation", e.message)
        }
    }

    // M. Reversed/invalid DateRange behavior remains governed by existing date-range protections.
    @Test
    fun `M - DateRange protections prevent inverted or future dates`() {
        val today = LocalDate.now()
        // Inverted range throws IllegalArgumentException in DateRange init
        try {
            DateRange(startDate = today, endDate = today.minusDays(5))
            fail("Expected IllegalArgumentException for reversed DateRange")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("cannot be after endDate") == true)
        }

        // Future date throws IllegalArgumentException in DateRange init
        try {
            DateRange(startDate = today.plusDays(1), endDate = today.plusDays(2))
            fail("Expected IllegalArgumentException for future DateRange")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("cannot be in the future") == true)
        }
    }

    // N. No INTERNET permission is introduced.
    @Test
    fun `N - No INTERNET permission in AndroidManifest`() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        val content = if (manifestFile.exists()) {
            manifestFile.readText()
        } else {
            File("app/src/main/AndroidManifest.xml").readText()
        }
        assertFalse(
            "INTERNET permission must NEVER be declared",
            content.contains("android.permission.INTERNET")
        )
        assertFalse(
            "QUERY_ALL_PACKAGES permission must NEVER be declared",
            content.contains("android.permission.QUERY_ALL_PACKAGES")
        )
    }

    // Overflow protection test
    @Test
    fun `Overflow protection saturates at Long MAX_VALUE without wrapping to negative`() = runBlocking {
        val dataSource = FakeNetworkStatsDataSource(
            wifiBuckets = mutableListOf(
                RawUidUsageBucket(uid = 10600, rxBytes = Long.MAX_VALUE - 100L, txBytes = Long.MAX_VALUE - 50L),
                RawUidUsageBucket(uid = 10600, rxBytes = 200L, txBytes = 100L)
            )
        )
        val repo = NetworkStatsRepositoryImpl(dataSource)
        val dateRange = DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1))

        val result = repo.getAppUsageForRange(dateRange, NetworkType.WIFI).getOrThrow()
        assertEquals(1, result.size)
        assertEquals(Long.MAX_VALUE, result[0].downloadBytes)
        assertEquals(Long.MAX_VALUE, result[0].uploadBytes)
        assertEquals(Long.MAX_VALUE, result[0].totalBytes)
    }

    // Sorting test: ordered by totalBytes descending, then uid ascending
    @Test
    fun `Output is deterministically sorted by totalBytes descending then uid ascending`() = runBlocking {
        val dataSource = FakeNetworkStatsDataSource(
            wifiBuckets = mutableListOf(
                RawUidUsageBucket(uid = 10002, rxBytes = 500L, txBytes = 500L), // total 1000
                RawUidUsageBucket(uid = 10001, rxBytes = 2000L, txBytes = 1000L), // total 3000
                RawUidUsageBucket(uid = 10003, rxBytes = 500L, txBytes = 500L)  // total 1000
            )
        )
        val repo = NetworkStatsRepositoryImpl(dataSource)
        val dateRange = DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1))

        val result = repo.getAppUsageForRange(dateRange, NetworkType.WIFI).getOrThrow()
        assertEquals(3, result.size)
        assertEquals(10001, result[0].uid) // 3000 bytes
        assertEquals(10002, result[1].uid) // 1000 bytes, lower uid
        assertEquals(10003, result[2].uid) // 1000 bytes, higher uid
    }

    // Special UIDs test: 0, SYSTEM_UID, UID_REMOVED, UID_TETHERING
    @Test
    fun `Special UIDs are resolved to appropriate names deterministically`() {
        val resolver = AppInfoResolverImpl(context)

        val root = resolver.resolveAppInfo(0)
        assertEquals("Root (Kernel)", root.displayName)

        val system = resolver.resolveAppInfo(Process.SYSTEM_UID)
        assertEquals("Android OS", system.displayName)

        val removed = resolver.resolveAppInfo(AppInfoResolverImpl.UID_REMOVED)
        assertEquals("Removed Apps", removed.displayName)

        val tethering = resolver.resolveAppInfo(AppInfoResolverImpl.UID_TETHERING)
        assertEquals("Tethering", tethering.displayName)
    }
}
