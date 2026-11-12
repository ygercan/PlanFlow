package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ActiveTab
import com.example.model.AppThemeMode
import com.example.model.DailyProgressState
import com.example.ui.theme.CoralToday

@Composable
fun TopNavigationBar(
    currentTab: ActiveTab,
    pendingTaskCount: Int,
    dailyProgress: DailyProgressState,
    themeMode: AppThemeMode,
    isSyncing: Boolean,
    onTabSelected: (ActiveTab) -> Unit,
    onThemeToggle: () -> Unit,
    onCloudSyncClick: () -> Unit,
    onOpenMenu: () -> Unit
) {
    // Calculate top inset directly from the device status/notification bar
    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(
                top = statusBarTopPadding + 8.dp,
                bottom = 10.dp,
                start = 20.dp,
                end = 20.dp
            )
    ) {
        // Top Row: Daily Progress Bar on left, Uniform Circular Action Buttons on right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            DailyTopProgressBar(
                dailyProgress = dailyProgress,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Cloud Sync Circular Button
                TopBarCircleButton(
                    onClick = onCloudSyncClick,
                    testTag = "cloud_sync_button"
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "Bulut Senkronizasyonu",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 2. Theme Mode Circular Button
                TopBarCircleButton(
                    onClick = onThemeToggle,
                    testTag = "theme_toggle_button"
                ) {
                    val icon = when (themeMode) {
                        AppThemeMode.LIGHT -> Icons.Default.LightMode
                        AppThemeMode.DARK -> Icons.Default.DarkMode
                        AppThemeMode.SYSTEM -> Icons.Default.DarkMode
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Tema Değiştir",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 3. View Settings Menu Circular Button (moved next to Theme button)
                TopBarCircleButton(
                    onClick = onOpenMenu,
                    testTag = "top_menu_button"
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Görünüm Menüsü",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Standardized Segmented Tab Bar
        val trackBackgroundColor = MaterialTheme.colorScheme.surfaceVariant

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(trackBackgroundColor)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActiveTab.entries.forEach { tab ->
                val isSelected = currentTab == tab

                val pillBgColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    animationSpec = tween(durationMillis = 200),
                    label = "tab_bg_${tab.name}"
                )

                val textColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        CoralToday
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(durationMillis = 200),
                    label = "tab_text_${tab.name}"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp))
                        .background(pillBgColor)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = CoralToday.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(9.dp)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 6.dp)
                        .testTag("tab_${tab.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = when (tab) {
                                ActiveTab.FLOW -> "Akış"
                                ActiveTab.CALENDAR -> "Takvim"
                                ActiveTab.TODO -> "Görevler"
                                ActiveTab.OVERVIEW -> "Özet"
                            },
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = textColor
                        )

                        if (tab == ActiveTab.TODO && pendingTaskCount > 0) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) CoralToday
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$pendingTaskCount",
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) {
                                        Color.White
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBarCircleButton(
    onClick: () -> Unit,
    testTag: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                shape = CircleShape
            )
            .clickable { onClick() }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun DailyTopProgressBar(
    dailyProgress: DailyProgressState,
    modifier: Modifier = Modifier
) {
    val animatedFraction by animateFloatAsState(
        targetValue = dailyProgress.progressFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "top_daily_progress_fraction"
    )

    val isAllDone = dailyProgress.totalCount > 0 && dailyProgress.completedCount == dailyProgress.totalCount
    val activeBarColor by animateColorAsState(
        targetValue = if (isAllDone) Color(0xFF10B981) else CoralToday,
        animationSpec = tween(durationMillis = 350),
        label = "top_daily_bar_color"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("top_daily_progress_bar")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "BUGÜN",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (dailyProgress.totalCount > 0) {
                        "${dailyProgress.completedCount}/${dailyProgress.totalCount} tamamlandı"
                    } else {
                        "Görev yok"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
            }

            Text(
                text = "%${dailyProgress.percentage}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = activeBarColor,
                modifier = Modifier.testTag("top_daily_progress_percentage")
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Hairline 4dp progress track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(activeBarColor)
            )
        }
    }
}
