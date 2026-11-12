package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ActiveTab
import com.example.model.AppThemeMode
import com.example.ui.theme.CoralToday
import com.example.util.rememberHapticController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewSwitcherBottomSheet(
    currentTab: ActiveTab,
    themeMode: AppThemeMode,
    hasUpdateAvailable: Boolean = false,
    latestReleaseTag: String? = null,
    installedVersion: String = "1.0",
    onSelectTab: (ActiveTab) -> Unit,
    onToggleTheme: () -> Unit,
    onOpenCloudSync: () -> Unit,
    onOpenGitHubUpdate: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val haptics = rememberHapticController()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // Editorial Header with Coral Accent Dot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(CoralToday)
                )
                Text(
                    text = "Görünüm & Ayarlar",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Views section
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MenuRowItem(
                    title = "Günlük Akış (Minimal Ajanda)",
                    subtitle = "Tarih bazlı dikey sürekli ajanda görünümü",
                    icon = Icons.Default.ViewAgenda,
                    isSelected = currentTab == ActiveTab.FLOW,
                    onClick = {
                        haptics.performNavigationTap()
                        onSelectTab(ActiveTab.FLOW)
                        onDismiss()
                    }
                )

                MenuRowItem(
                    title = "Aylık Takvim",
                    subtitle = "Tüm ayı ızgara olarak görüntüle",
                    icon = Icons.Default.CalendarMonth,
                    isSelected = currentTab == ActiveTab.CALENDAR,
                    onClick = {
                        haptics.performNavigationTap()
                        onSelectTab(ActiveTab.CALENDAR)
                        onDismiss()
                    }
                )

                MenuRowItem(
                    title = "Görevler & Arama",
                    subtitle = "Kategori filtreleri ve tam görev listesi",
                    icon = Icons.AutoMirrored.Filled.List,
                    isSelected = currentTab == ActiveTab.TODO,
                    onClick = {
                        haptics.performNavigationTap()
                        onSelectTab(ActiveTab.TODO)
                        onDismiss()
                    }
                )

                MenuRowItem(
                    title = "Genel Bakış",
                    subtitle = "Günün zaman çizelgesi ve istatistik özeti",
                    icon = Icons.Default.Dashboard,
                    isSelected = currentTab == ActiveTab.OVERVIEW,
                    onClick = {
                        haptics.performNavigationTap()
                        onSelectTab(ActiveTab.OVERVIEW)
                        onDismiss()
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Actions Row 1: Cloud Sync & Theme
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Cloud Sync Card Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            haptics.performNavigationTap()
                            onDismiss()
                            onOpenCloudSync()
                        }
                        .padding(14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = CoralToday,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Bulut Yedek",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Theme Mode Card Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            haptics.performNavigationTap()
                            onToggleTheme()
                        }
                        .padding(14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val icon = when (themeMode) {
                            AppThemeMode.LIGHT -> Icons.Default.LightMode
                            AppThemeMode.DARK -> Icons.Default.DarkMode
                            AppThemeMode.SYSTEM -> Icons.Default.DarkMode
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = CoralToday,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = when (themeMode) {
                                AppThemeMode.LIGHT -> "Açık Tema"
                                AppThemeMode.DARK -> "Karanlık"
                                AppThemeMode.SYSTEM -> "Sistem"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Actions Row 2: GitHub Atomic Update & Release Check
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (hasUpdateAvailable) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .border(
                        width = 1.dp,
                        color = if (hasUpdateAvailable) CoralToday else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        haptics.performNavigationTap()
                        onDismiss()
                        onOpenGitHubUpdate()
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("open_github_update_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdateAlt,
                        contentDescription = null,
                        tint = CoralToday,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Atomik Güncelleme (GitHub Releases)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (hasUpdateAvailable && latestReleaseTag != null) {
                                "Yeni APK sürümü hazır: $latestReleaseTag"
                            } else {
                                "Sürüm v$installedVersion · Otomatik APK & Atomik DB"
                            },
                            fontSize = 11.sp,
                            color = if (hasUpdateAvailable) CoralToday else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (hasUpdateAvailable) CoralToday else MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (hasUpdateAvailable) "Güncelle" else "Denetle",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasUpdateAvailable) Color.White else CoralToday
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
fun MenuRowItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) CoralToday.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 1.dp,
                        color = CoralToday.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(14.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) CoralToday
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .border(
                    width = 1.dp,
                    color = if (isSelected) CoralToday else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) CoralToday else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(CoralToday)
            )
        }
    }
}
