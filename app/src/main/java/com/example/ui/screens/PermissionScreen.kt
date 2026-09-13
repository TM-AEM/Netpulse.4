package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.preferences.AppLanguage
import com.example.ui.theme.DesignTokens

@Composable
fun PermissionScreen(
    onCheckPermission: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier,
    language: AppLanguage = AppLanguage.AR
) {
    val context = LocalContext.current
    val isArabic = language == AppLanguage.AR

    val title = if (isArabic) "مطلوب إذن الوصول لبيانات الاستخدام" else "Usage Access Permission Required"
    val description = if (isArabic) {
        "لقراءة إحصائيات استهلاك الشبكة الدقيقة المسجلة بواسطة نظام أندرويد (NetworkStatsManager)، يحتاج تطبيق NetPulse إلى إذن 'الوصول لبيانات الاستخدام'.\n\nجميع البيانات تُعالج محلياً 100% على جهازك دون إرسال أي شيء خارجياً."
    } else {
        "To read accurate network statistics recorded directly by Android system (NetworkStatsManager), NetPulse requires 'Usage Access' permission.\n\nAll data is processed 100% locally on your device without transmitting anything."
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("permission_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(DesignTokens.SpacingLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(modifier = Modifier.height(DesignTokens.SpacingXLarge))

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(DesignTokens.SpacingMedium))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(DesignTokens.SpacingMedium)
                )
            }

            Spacer(modifier = Modifier.height(DesignTokens.SpacingXLarge))

            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Fallback
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("grant_permission_button")
            ) {
                Text(if (isArabic) "فتح إعدادات الوصول للاستخدام" else "Open Usage Access Settings")
            }

            Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall))

            Button(
                onClick = onCheckPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("check_permission_button")
            ) {
                Text(if (isArabic) "التحقق من الإذن مجدداً" else "Check Permission Again")
            }

            Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall))

            OutlinedButton(
                onClick = onOpenPrivacyPolicy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("privacy_policy_button")
            ) {
                Text(if (isArabic) "سياسة الخصوصية" else "Privacy Policy")
            }
        }
    }
}
