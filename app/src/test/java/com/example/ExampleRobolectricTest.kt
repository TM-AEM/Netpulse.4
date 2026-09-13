package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.AppDataUnit
import com.example.model.ByteUnit
import com.example.model.DateRange
import com.example.model.NetworkType
import com.example.model.NetworkUsage
import com.example.model.PeriodPreset
import com.example.util.ByteFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("NetPulse", appName)
  }

  @Test
  fun `byte formatter formats correctly`() {
    val zero = ByteFormatter.format(0L)
    assertEquals("0 B", zero.displayDefault)

    val kb = ByteFormatter.format(1024L)
    assertEquals("1.0 KB", kb.displayDefault)

    val mb = ByteFormatter.format(1024L * 1024L * 5L)
    assertEquals("5.00 MB", mb.displayDefault)
    assertEquals("MB", mb.unitSymbolEn)

    val forcedGb = ByteFormatter.format(1024L * 1024L * 1024L * 2L, AppDataUnit.GB)
    assertEquals("2.00 GB", forcedGb.displayDefault)
  }

  @Test
  fun `date range logic bounds to today`() {
    val range = DateRange.fromPreset(PeriodPreset.TODAY)
    assertEquals(LocalDate.now(), range.startDate)
    assertEquals(LocalDate.now(), range.endDate)
    assertTrue(range.isSingleDay)

    val usage1 = NetworkUsage(100L, 200L, 0L, 1000L, NetworkType.WIFI)
    val usage2 = NetworkUsage(300L, 400L, 0L, 1000L, NetworkType.MOBILE)
    val total = usage1 + usage2
    assertEquals(400L, total.downloadBytes)
    assertEquals(600L, total.uploadBytes)
    assertEquals(1000L, total.totalBytes)
  }
}
