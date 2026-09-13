package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.preferences.AppLanguage
import com.example.ui.theme.DesignTokens
import com.example.util.DateTimeUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDateRangeDialog(
    initialStartDate: LocalDate,
    initialEndDate: LocalDate,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
    language: AppLanguage = AppLanguage.AR
) {
    val isArabic = language == AppLanguage.AR
    val zoneId = DateTimeUtils.getLocalZoneId()
    val today = DateTimeUtils.today(zoneId)

    var startDate by remember { mutableStateOf(if (initialStartDate.isAfter(today)) today else initialStartDate) }
    var endDate by remember { mutableStateOf(if (initialEndDate.isAfter(today)) today else initialEndDate) }
    var pickingStart by remember { mutableStateOf<Boolean?>(null) }

    if (pickingStart != null) {
        val isStart = pickingStart == true
        val currentPickDate = if (isStart) startDate else endDate
        val initialEpochMs = currentPickDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()

        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialEpochMs,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                    return !date.isAfter(today)
                }
            }
        )

        DatePickerDialog(
            onDismissRequest = { pickingStart = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { ms ->
                            val selectedLocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.of("UTC")).toLocalDate()
                            // Clamp to today to never allow future dates
                            val boundedDate = minOf(selectedLocalDate, today)
                            if (isStart) {
                                startDate = boundedDate
                                if (endDate.isBefore(startDate)) {
                                    endDate = startDate
                                }
                            } else {
                                endDate = boundedDate
                                if (startDate.isAfter(endDate)) {
                                    startDate = endDate
                                }
                            }
                        }
                        pickingStart = null
                    }
                ) {
                    Text(if (isArabic) "موافق" else "OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { pickingStart = null }) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(DesignTokens.DialogCornerRadius),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().testTag("custom_date_range_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DesignTokens.SpacingLarge)
            ) {
                Text(
                    text = if (isArabic) "تحديد فترة مخصصة" else "Select Custom Period",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(DesignTokens.SpacingMedium))

                OutlinedButton(
                    onClick = { pickingStart = true },
                    modifier = Modifier.fillMaxWidth().testTag("start_date_picker_button")
                ) {
                    val label = if (isArabic) "من: ${DateTimeUtils.formatDateArabic(startDate)}" else "From: ${DateTimeUtils.formatDateEnglish(startDate)}"
                    Text(text = label)
                }

                Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall))

                OutlinedButton(
                    onClick = { pickingStart = false },
                    modifier = Modifier.fillMaxWidth().testTag("end_date_picker_button")
                ) {
                    val label = if (isArabic) "إلى: ${DateTimeUtils.formatDateArabic(endDate)}" else "To: ${DateTimeUtils.formatDateEnglish(endDate)}"
                    Text(text = label)
                }

                Spacer(modifier = Modifier.height(DesignTokens.SpacingLarge))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(if (isArabic) "إلغاء" else "Cancel")
                    }
                    Button(
                        onClick = {
                            val s = minOf(startDate, endDate)
                            val e = minOf(maxOf(startDate, endDate), today)
                            onConfirm(s, e)
                        },
                        modifier = Modifier.testTag("confirm_custom_range_button")
                    ) {
                        Text(if (isArabic) "تطبيق" else "Apply")
                    }
                }
            }
        }
    }
}
