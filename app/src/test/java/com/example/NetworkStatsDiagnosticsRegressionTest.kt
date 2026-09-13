package com.example

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.NetworkStatsRepositoryImpl
import com.example.data.source.NetworkStatsDataSourceImpl
import com.example.model.NetworkType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import java.lang.reflect.Field

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [
    NetworkStatsDiagnosticsRegressionTest.ShadowTestNetworkStatsManager::class,
    NetworkStatsDiagnosticsRegressionTest.ShadowTestNetworkStats::class
])
class NetworkStatsDiagnosticsRegressionTest {

    private lateinit var context: Context
    private lateinit var manager: NetworkStatsManager
    private lateinit var shadowManager: ShadowTestNetworkStatsManager

    @Implements(NetworkStatsManager::class)
    class ShadowTestNetworkStatsManager {
        val summaryQueries = mutableListOf<Int>()
        val detailsQueries = mutableListOf<Int>()

        var wifiSummaryBucket: NetworkStats.Bucket? = null
        var mobileSummaryBucket: NetworkStats.Bucket? = null

        var wifiDetailsStats: NetworkStats? = null
        var mobileDetailsStats: NetworkStats? = null

        @Implementation
        fun querySummaryForDevice(networkType: Int, subscriberId: String?, startTime: Long, endTime: Long): NetworkStats.Bucket {
            summaryQueries.add(networkType)
            return when (networkType) {
                ConnectivityManager.TYPE_WIFI -> wifiSummaryBucket ?: createBucket()
                ConnectivityManager.TYPE_MOBILE -> mobileSummaryBucket ?: createBucket()
                else -> createBucket()
            }
        }

        @Implementation
        fun queryDetails(networkType: Int, subscriberId: String?, startTime: Long, endTime: Long): NetworkStats? {
            detailsQueries.add(networkType)
            return when (networkType) {
                ConnectivityManager.TYPE_WIFI -> wifiDetailsStats
                ConnectivityManager.TYPE_MOBILE -> mobileDetailsStats
                else -> null
            }
        }
    }

    @Implements(NetworkStats::class)
    class ShadowTestNetworkStats {
        val buckets = mutableListOf<NetworkStats.Bucket>()
        private var currentIndex = 0

        @Implementation
        fun hasNextBucket(): Boolean {
            return currentIndex < buckets.size
        }

        @Implementation
        fun getNextBucket(bucket: NetworkStats.Bucket): Boolean {
            if (currentIndex < buckets.size) {
                val source = buckets[currentIndex++]
                copyBucket(source, bucket)
                return true
            }
            return false
        }

        @Implementation
        fun close() {
            // no-op
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = Shadow.newInstanceOf(NetworkStatsManager::class.java)
        shadowManager = Shadow.extract(manager)
        shadowManager.summaryQueries.clear()
        shadowManager.detailsQueries.clear()
        shadowManager.wifiSummaryBucket = null
        shadowManager.mobileSummaryBucket = null
        shadowManager.wifiDetailsStats = null
        shadowManager.mobileDetailsStats = null
    }

    private fun createDataSource(permissionGranted: Boolean = true): NetworkStatsDataSourceImpl {
        return NetworkStatsDataSourceImpl(context, manager, permissionChecker = { permissionGranted })
    }

    @Test
    fun getDebugInfo_whenPermissionMissing_throwsSecurityException_andRepositoryFails() = runBlocking {
        val dataSource = createDataSource(permissionGranted = false)
        try {
            dataSource.getDebugInfo(NetworkType.WIFI, 1000L, 2000L)
            fail("Expected SecurityException when Usage Stats permission is not granted")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("PACKAGE_USAGE_STATS") == true)
        }

        // Verify repository returns explicit Result.failure with SecurityException, never fabricating 0-bytes
        val repository = NetworkStatsRepositoryImpl(dataSource)
        val repoResult = repository.getDebugInfo(NetworkType.WIFI, 1000L, 2000L)
        assertTrue(repoResult.isFailure)
        assertTrue(repoResult.exceptionOrNull() is SecurityException)
    }

    @Test
    fun getDebugInfo_wifiDiagnostics_strictlyExcludesMobileData() = runBlocking {
        // Setup distinct values for both WiFi and Mobile
        shadowManager.wifiSummaryBucket = createBucket(rxBytes = 1000L, txBytes = 500L, rxPackets = 10L, txPackets = 5L)
        shadowManager.mobileSummaryBucket = createBucket(rxBytes = 999999L, txBytes = 888888L, rxPackets = 777L, txPackets = 666L)

        val dataSource = createDataSource(true)
        val result = dataSource.getDebugInfo(NetworkType.WIFI, 1000L, 2000L)

        // Only WiFi queries must be invoked
        assertEquals(listOf(ConnectivityManager.TYPE_WIFI), shadowManager.summaryQueries)
        assertEquals(listOf(ConnectivityManager.TYPE_WIFI), shadowManager.detailsQueries)

        // Values must reflect strictly WiFi, with no mobile contamination
        assertEquals(1000L, result.rawRxBytes)
        assertEquals(500L, result.rawTxBytes)
        assertEquals(1500L, result.rawTotalBytes)
        assertEquals(10L, result.rawRxPackets)
        assertEquals(5L, result.rawTxPackets)
        assertEquals(NetworkType.WIFI, result.networkType)
    }

    @Test
    fun getDebugInfo_mobileDiagnostics_strictlyExcludesWifiData() = runBlocking {
        // Setup distinct values for both Mobile and WiFi
        shadowManager.mobileSummaryBucket = createBucket(rxBytes = 2000L, txBytes = 800L, rxPackets = 20L, txPackets = 8L)
        shadowManager.wifiSummaryBucket = createBucket(rxBytes = 777777L, txBytes = 666666L, rxPackets = 555L, txPackets = 444L)

        val dataSource = createDataSource(true)
        val result = dataSource.getDebugInfo(NetworkType.MOBILE, 1000L, 2000L)

        // Only Mobile queries must be invoked
        assertEquals(listOf(ConnectivityManager.TYPE_MOBILE), shadowManager.summaryQueries)
        assertEquals(listOf(ConnectivityManager.TYPE_MOBILE), shadowManager.detailsQueries)

        // Values must reflect strictly Mobile, with no WiFi contamination
        assertEquals(2000L, result.rawRxBytes)
        assertEquals(800L, result.rawTxBytes)
        assertEquals(2800L, result.rawTotalBytes)
        assertEquals(20L, result.rawRxPackets)
        assertEquals(8L, result.rawTxPackets)
        assertEquals(NetworkType.MOBILE, result.networkType)
    }

    @Test
    fun getDebugInfo_packetCounters_preserveActualNetworkStatsBucketValues() = runBlocking {
        // Positive packet counts must be preserved exactly
        val wifiSummary = createBucket(rxBytes = 4000L, txBytes = 2000L, rxPackets = 123L, txPackets = 456L)
        shadowManager.wifiSummaryBucket = wifiSummary

        val wifiStats: NetworkStats = Shadow.newInstanceOf(NetworkStats::class.java)
        val shadowWifiStats = Shadow.extract<ShadowTestNetworkStats>(wifiStats)
        shadowWifiStats.buckets.add(
            createBucket(
                rxBytes = 400L,
                txBytes = 200L,
                rxPackets = 42L,
                txPackets = 17L,
                uid = 1003,
                state = NetworkStats.Bucket.STATE_FOREGROUND
            )
        )
        shadowManager.wifiDetailsStats = wifiStats

        val dataSource = createDataSource(true)
        val result = dataSource.getDebugInfo(NetworkType.WIFI, 1000L, 2000L)

        assertEquals(123L, result.rawRxPackets)
        assertEquals(456L, result.rawTxPackets)
        assertEquals(1, result.detailedBuckets.size)
        assertEquals(42L, result.detailedBuckets[0].rxPackets)
        assertEquals(17L, result.detailedBuckets[0].txPackets)
    }

    @Test
    fun getDebugInfo_forWifi_queriesOnlyWifi() = runBlocking {
        val wifiSummary = createBucket(rxBytes = 1000L, txBytes = 500L, rxPackets = 10L, txPackets = 5L)
        shadowManager.wifiSummaryBucket = wifiSummary

        val wifiStats: NetworkStats = Shadow.newInstanceOf(NetworkStats::class.java)
        val shadowWifiStats = Shadow.extract<ShadowTestNetworkStats>(wifiStats)
        shadowWifiStats.buckets.add(
            createBucket(
                rxBytes = 300L,
                txBytes = 150L,
                rxPackets = 4L,
                txPackets = 2L,
                uid = 1001,
                state = NetworkStats.Bucket.STATE_FOREGROUND
            )
        )
        shadowManager.wifiDetailsStats = wifiStats

        val dataSource = createDataSource(true)
        val result = dataSource.getDebugInfo(NetworkType.WIFI, 1000L, 2000L)

        // Verify summary was queried for WIFI only
        assertEquals(listOf(ConnectivityManager.TYPE_WIFI), shadowManager.summaryQueries)
        // Verify details were queried for WIFI only
        assertEquals(listOf(ConnectivityManager.TYPE_WIFI), shadowManager.detailsQueries)

        assertEquals(1000L, result.rawRxBytes)
        assertEquals(500L, result.rawTxBytes)
        assertEquals(1500L, result.rawTotalBytes)
        assertEquals(10L, result.rawRxPackets)
        assertEquals(5L, result.rawTxPackets)
        assertEquals(NetworkType.WIFI, result.networkType)

        // Verify detailed buckets have packet counts from bucket
        assertEquals(1, result.detailedBuckets.size)
        val bucket = result.detailedBuckets[0]
        assertEquals(1001, bucket.uid)
        assertEquals("FOREGROUND", bucket.state)
        assertEquals(300L, bucket.rxBytes)
        assertEquals(150L, bucket.txBytes)
        assertEquals(4L, bucket.rxPackets)
        assertEquals(2L, bucket.txPackets)
    }

    @Test
    fun getDebugInfo_forMobile_queriesOnlyMobile() = runBlocking {
        val mobileSummary = createBucket(rxBytes = 2000L, txBytes = 800L, rxPackets = 20L, txPackets = 8L)
        shadowManager.mobileSummaryBucket = mobileSummary

        val mobileStats: NetworkStats = Shadow.newInstanceOf(NetworkStats::class.java)
        val shadowMobileStats = Shadow.extract<ShadowTestNetworkStats>(mobileStats)
        shadowMobileStats.buckets.add(
            createBucket(
                rxBytes = 600L,
                txBytes = 200L,
                rxPackets = 6L,
                txPackets = 2L,
                uid = 1002,
                state = NetworkStats.Bucket.STATE_DEFAULT
            )
        )
        shadowManager.mobileDetailsStats = mobileStats

        val dataSource = createDataSource(true)
        val result = dataSource.getDebugInfo(NetworkType.MOBILE, 1000L, 2000L)

        // Verify summary was queried for MOBILE only
        assertEquals(listOf(ConnectivityManager.TYPE_MOBILE), shadowManager.summaryQueries)
        // Verify details were queried for MOBILE only
        assertEquals(listOf(ConnectivityManager.TYPE_MOBILE), shadowManager.detailsQueries)

        assertEquals(2000L, result.rawRxBytes)
        assertEquals(800L, result.rawTxBytes)
        assertEquals(2800L, result.rawTotalBytes)
        assertEquals(20L, result.rawRxPackets)
        assertEquals(8L, result.rawTxPackets)
        assertEquals(NetworkType.MOBILE, result.networkType)

        assertEquals(1, result.detailedBuckets.size)
        val bucket = result.detailedBuckets[0]
        assertEquals(1002, bucket.uid)
        assertEquals("DEFAULT", bucket.state)
        assertEquals(600L, bucket.rxBytes)
        assertEquals(200L, bucket.txBytes)
        assertEquals(6L, bucket.rxPackets)
        assertEquals(2L, bucket.txPackets)
    }

    @Test
    fun getDebugInfo_whenPacketsUnavailable_doesNotFabricateZero() = runBlocking {
        // -1L indicates unavailable metric in Android NetworkStats.Bucket
        val wifiSummary = createBucket(rxBytes = 500L, txBytes = 200L, rxPackets = -1L, txPackets = -1L)
        shadowManager.wifiSummaryBucket = wifiSummary

        val dataSource = createDataSource(true)
        val result = dataSource.getDebugInfo(NetworkType.WIFI, 1000L, 2000L)

        // Must report -1L (unavailable), not fabricated 0L
        assertEquals(-1L, result.rawRxPackets)
        assertEquals(-1L, result.rawTxPackets)
        assertEquals(500L, result.rawRxBytes)
        assertEquals(200L, result.rawTxBytes)
    }

    @Test
    fun getDebugInfo_forTotal_queriesBothWifiAndMobile() = runBlocking {
        val wifiSummary = createBucket(rxBytes = 1000L, txBytes = 400L, rxPackets = 10L, txPackets = 4L)
        val mobileSummary = createBucket(rxBytes = 2000L, txBytes = 600L, rxPackets = 20L, txPackets = 6L)
        shadowManager.wifiSummaryBucket = wifiSummary
        shadowManager.mobileSummaryBucket = mobileSummary

        val dataSource = createDataSource(true)
        val result = dataSource.getDebugInfo(NetworkType.TOTAL, 1000L, 2000L)

        assertTrue(shadowManager.summaryQueries.contains(ConnectivityManager.TYPE_WIFI))
        assertTrue(shadowManager.summaryQueries.contains(ConnectivityManager.TYPE_MOBILE))
        assertTrue(shadowManager.detailsQueries.contains(ConnectivityManager.TYPE_WIFI))
        assertTrue(shadowManager.detailsQueries.contains(ConnectivityManager.TYPE_MOBILE))

        assertEquals(3000L, result.rawRxBytes)
        assertEquals(1000L, result.rawTxBytes)
        assertEquals(4000L, result.rawTotalBytes)
        assertEquals(30L, result.rawRxPackets)
        assertEquals(10L, result.rawTxPackets)
        assertEquals(NetworkType.TOTAL, result.networkType)
    }

    companion object {
        fun createBucket(
            rxBytes: Long = 0L,
            txBytes: Long = 0L,
            rxPackets: Long = 0L,
            txPackets: Long = 0L,
            uid: Int = 1000,
            state: Int = NetworkStats.Bucket.STATE_DEFAULT
        ): NetworkStats.Bucket {
            val bucket = NetworkStats.Bucket()
            setBucketField(bucket, "mRxBytes", rxBytes)
            setBucketField(bucket, "mTxBytes", txBytes)
            setBucketField(bucket, "mRxPackets", rxPackets)
            setBucketField(bucket, "mTxPackets", txPackets)
            setBucketField(bucket, "mUid", uid)
            setBucketField(bucket, "mState", state)
            return bucket
        }

        private fun copyBucket(from: NetworkStats.Bucket, to: NetworkStats.Bucket) {
            setBucketField(to, "mRxBytes", from.rxBytes)
            setBucketField(to, "mTxBytes", from.txBytes)
            setBucketField(to, "mRxPackets", from.rxPackets)
            setBucketField(to, "mTxPackets", from.txPackets)
            setBucketField(to, "mUid", from.uid)
            setBucketField(to, "mState", from.state)
            setBucketField(to, "mBeginTimeStamp", from.startTimeStamp)
            setBucketField(to, "mEndTimeStamp", from.endTimeStamp)
        }

        private fun setBucketField(bucket: NetworkStats.Bucket, fieldName: String, value: Any) {
            val clazz = NetworkStats.Bucket::class.java
            val field: Field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(bucket, value)
        }
    }
}
