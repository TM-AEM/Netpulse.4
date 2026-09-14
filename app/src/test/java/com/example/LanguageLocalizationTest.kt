package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.AppDataUnit
import com.example.data.preferences.AppLanguage
import com.example.data.preferences.AppRefreshMode
import com.example.data.preferences.AppSettingsPreferences
import com.example.data.preferences.AppThemeMode
import com.example.model.DateRange
import com.example.model.PeriodPreset
import com.example.util.ByteFormatter
import com.example.util.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class LanguageLocalizationTest {

    private lateinit var context: Context
    private lateinit var preferences: AppSettingsPreferences

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val sharedPrefs = context.getSharedPreferences("netpulse_settings_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().commit()
        preferences = AppSettingsPreferences(context)
    }

    @Test
    fun app_language_enum_supports_ar_en_fr() {
        assertEquals(3, AppLanguage.entries.size)

        val ar = AppLanguage.AR
        assertEquals("ar", ar.code)
        assertTrue(ar.isRtl)
        assertEquals("العربية", ar.getLabel(AppLanguage.AR))
        assertEquals("Arabic", ar.getLabel(AppLanguage.EN))
        assertEquals("Arabe", ar.getLabel(AppLanguage.FR))

        val en = AppLanguage.EN
        assertEquals("en", en.code)
        assertFalse(en.isRtl)
        assertEquals("English", en.getLabel(AppLanguage.AR))
        assertEquals("English", en.getLabel(AppLanguage.EN))
        assertEquals("Anglais", en.getLabel(AppLanguage.FR))

        val fr = AppLanguage.FR
        assertEquals("fr", fr.code)
        assertFalse(fr.isRtl)
        assertEquals("الفرنسية (Français)", fr.getLabel(AppLanguage.AR))
        assertEquals("French (Français)", fr.getLabel(AppLanguage.EN))
        assertEquals("Français", fr.getLabel(AppLanguage.FR))
    }

    @Test
    fun byte_formatter_french_labels() {
        val bytes = 1024L * 1024L * 500L // 500 MB
        val formatted = ByteFormatter.format(bytes)

        val displayFr = formatted.getDisplay(AppLanguage.FR)
        assertTrue("French display should contain Mo: $displayFr", displayFr.contains("Mo"))

        val gbBytes = 1024L * 1024L * 1024L * 3L // 3 GB
        val formattedGb = ByteFormatter.format(gbBytes)
        val displayGbFr = formattedGb.getDisplay(AppLanguage.FR)
        assertTrue("French display should contain Go: $displayGbFr", displayGbFr.contains("Go"))
    }

    @Test
    fun date_time_utils_french_formatting() {
        val date = LocalDate.of(2025, 3, 15)
        val formattedFr = DateTimeUtils.formatDate(date, AppLanguage.FR)
        assertTrue("French date should contain day 15: $formattedFr", formattedFr.contains("15"))
        assertTrue("French date should contain year 2025: $formattedFr", formattedFr.contains("2025"))

        val dayFr = DateTimeUtils.formatDayName(date, AppLanguage.FR)
        assertFalse("French day name should not be empty", dayFr.isEmpty())
    }

    @Test
    fun date_range_format_display_french() {
        val zone = ZoneId.of("UTC")
        val dateRange = DateRange(
            startDate = LocalDate.of(2025, 1, 1),
            endDate = LocalDate.of(2025, 1, 7),
            preset = PeriodPreset.LAST_7_DAYS,
            zoneId = zone
        )

        val displayFr = dateRange.formatDisplay(AppLanguage.FR)
        assertTrue("Formatted display in French should contain 2025: $displayFr", displayFr.contains("2025"))
    }

    @Test
    fun preferences_persists_and_restores_french_language() {
        assertEquals(AppLanguage.AR, preferences.getLanguage())

        preferences.setLanguage(AppLanguage.FR)
        assertEquals(AppLanguage.FR, preferences.getLanguage())
        assertEquals(AppLanguage.FR, preferences.language.value)

        // Reload via fresh preferences instance
        val reloaded = AppSettingsPreferences(context)
        assertEquals(AppLanguage.FR, reloaded.getLanguage())
    }

    @Test
    fun settings_options_have_french_labels() {
        // AppThemeMode
        for (theme in AppThemeMode.entries) {
            assertFalse("Theme label for ${theme.name} in French should not be blank", theme.getLabel(AppLanguage.FR).isBlank())
        }

        // AppDataUnit
        for (unit in AppDataUnit.entries) {
            assertFalse("DataUnit label for ${unit.name} in French should not be blank", unit.getLabel(AppLanguage.FR).isBlank())
        }

        // AppRefreshMode
        for (mode in AppRefreshMode.entries) {
            assertFalse("RefreshMode label for ${mode.name} in French should not be blank", mode.getLabel(AppLanguage.FR).isBlank())
        }
    }
}
