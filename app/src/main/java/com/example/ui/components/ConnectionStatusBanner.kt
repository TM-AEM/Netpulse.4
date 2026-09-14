package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.preferences.AppLanguage
import com.example.model.NetworkType
import com.example.ui.theme.DesignTokens
import com.example.ui.theme.MobileAccent
import com.example.ui.theme.WifiAccent
import com.example.util.ConnectionStatus

@Composable
fun ConnectionStatusBanner(
    connectionStatus: ConnectionStatus,
    modifier: Modifier = Modifier,
    language: AppLanguage = AppLanguage.AR
) {
    val isConnected = connectionStatus.isConnected
    val disconnectedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val disconnectedIndicator = MaterialTheme.colorScheme.error

    val (icon, tintColor, statusText) = when {
        !isConnected -> Triple(
            Icons.Default.WifiOff,
            disconnectedColor,
            when (language) {
                AppLanguage.AR -> "لا يوجد اتصال بالإنترنت"
                AppLanguage.FR -> "Aucune connexion Internet"
                AppLanguage.EN -> "No Internet Connection"
            }
        )
        connectionStatus.networkType == NetworkType.WIFI -> Triple(
            Icons.Default.Wifi,
            WifiAccent,
            when (language) {
                AppLanguage.AR -> "متصل عبر شبكة Wi-Fi"
                AppLanguage.FR -> "Connecté au Wi-Fi"
                AppLanguage.EN -> "Connected to Wi-Fi"
            }
        )
        connectionStatus.networkType == NetworkType.MOBILE -> Triple(
            Icons.Default.SignalCellularAlt,
            MobileAccent,
            when (language) {
                AppLanguage.AR -> "متصل عبر بيانات الجوال"
                AppLanguage.FR -> "Connecté aux données mobiles"
                AppLanguage.EN -> "Connected to Mobile Data"
            }
        )
        else -> Triple(
            Icons.Default.Wifi,
            MaterialTheme.colorScheme.primary,
            when (language) {
                AppLanguage.AR -> "متصل (${connectionStatus.labelAr})"
                AppLanguage.FR -> "Connecté (${connectionStatus.labelEn})"
                AppLanguage.EN -> "Connected (${connectionStatus.labelEn})"
            }
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("connection_status_banner"),
        shape = RoundedCornerShape(DesignTokens.CardCornerRadius),
        color = tintColor.copy(alpha = 0.1f),
        tonalElevation = DesignTokens.CardElevation
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.SpacingMedium, vertical = DesignTokens.SpacingSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) tintColor else disconnectedIndicator)
            )
            Spacer(modifier = Modifier.width(DesignTokens.SpacingSmall))
            Icon(
                imageVector = icon,
                contentDescription = statusText,
                tint = tintColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(DesignTokens.SpacingSmall))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (connectionStatus.isMetered && isConnected) {
                Spacer(modifier = Modifier.width(DesignTokens.SpacingSmall))
                Text(
                    text = when (language) {
                        AppLanguage.AR -> "(شبكة محدودة)"
                        AppLanguage.FR -> "(Réseau mesuré)"
                        AppLanguage.EN -> "(Metered)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
