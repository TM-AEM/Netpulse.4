package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.preferences.AppLanguage
import com.example.ui.theme.DesignTokens

@Composable
fun PrivacyPolicyDialog(
    onDismiss: () -> Unit,
    language: AppLanguage = AppLanguage.AR
) {
    val title = when (language) {
        AppLanguage.AR -> "سياسة الخصوصية والشفافية"
        AppLanguage.FR -> "Politique de confidentialité et transparence"
        AppLanguage.EN -> "Privacy Policy & Transparency"
    }
    val content = when (language) {
        AppLanguage.AR -> """
        تطبيق NetPulse مبني على مبدأ الخصوصية الكاملة:
        
        1. محلي 100%: جميع إحصائيات الاستهلاك يتم الاستعلام عنها محلياً ومباشرة من نظام أندرويد عبر NetworkStatsManager.
        2. لا خوادم ولا تتبع: التطبيق لا يتصل بأي خوادم خارجية ولا يجمع أي بيانات شخصية أو إحصائية.
        3. إذن الوصول للاستخدام: مطلوب حصرياً لقراءة جداول استهلاك الشبكة الرسمية من أندرويد.
        4. إعداداتك محفوظة على جهازك فقط عبر التخزين المحلي الآمن.
        """.trimIndent()
        AppLanguage.FR -> """
        NetPulse est conçu selon un principe de confidentialité absolue :
        
        1. 100% Local : Toutes les statistiques réseau sont interrogées directement et localement depuis Android via NetworkStatsManager.
        2. Aucun serveur, aucun suivi : Zéro télémétrie, analyse ou serveur tiers.
        3. Autorisation d'accès : Requise uniquement pour lire les tables officielles de consommation réseau d'Android.
        4. Préférences locales : Vos réglages restent exclusivement sur votre appareil grâce au stockage local sécurisé.
        """.trimIndent()
        AppLanguage.EN -> """
        NetPulse is built with absolute privacy:
        
        1. 100% Local: All data statistics are queried directly and locally from Android NetworkStatsManager.
        2. No Servers, No Tracking: Zero telemetry, analytics, or external API servers.
        3. Permissions: PACKAGE_USAGE_STATS is requested solely to query Android system usage tables.
        4. Local Preferences: Your settings (theme, language, unit) remain exclusively on your device.
        """.trimIndent()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(DesignTokens.DialogCornerRadius),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().testTag("privacy_policy_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DesignTokens.SpacingLarge)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(DesignTokens.SpacingMedium))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(DesignTokens.SpacingLarge))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().testTag("close_privacy_dialog_button")
                ) {
                    Text(
                        when (language) {
                            AppLanguage.AR -> "حسناً، فهمت"
                            AppLanguage.FR -> "J'ai compris"
                            AppLanguage.EN -> "Got It"
                        }
                    )
                }
            }
        }
    }
}
