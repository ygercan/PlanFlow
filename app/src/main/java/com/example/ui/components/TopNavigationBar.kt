package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ActiveTab
import com.example.model.AppThemeMode
import com.example.model.DailyProgressState
import com.example.ui.theme.CoralToday
import com.example.util.rememberHapticController

@Composable
fun TopNavigationBar(
    currentTab: ActiveTab,
    pendingTaskCount: Int,
    dailyProgress: DailyProgressState,
    themeMode: AppThemeMode,
    isSyncing: Boolean,
    statusBarTopPadding: Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
    onTabSelected: (ActiveTab) -> Unit,
    onThemeToggle: () -> Unit,
    onCloudSyncClick: () -> Unit,
    onOpenMenu: () -> Unit
) {
    val haptics = rememberHapticController()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
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
                .wrapContentHeight()
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
                    onClick = {
                        haptics.performNavigationTap()
                        onCloudSyncClick()
                    },
                    testTag = "cloud_sync_button"
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = CoralToday
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "Bulut Senkronizasyonu",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 2. Theme Mode Circular Button
                TopBarCircleButton(
                    onClick = {
                        haptics.performNavigationTap()
                        onThemeToggle()
                    },
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
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 3. View Settings Menu Circular Button (next to Theme button)
                TopBarCircleButton(
                    onClick = {
                        haptics.performNavigationTap()
                        onOpenMenu()
                    },
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

        // Standardized Segmented Tab Bar — wraps content bounds dynamically via IntrinsicSize.Min
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActiveTab.entries.forEach { tab ->
                val isSelected = currentTab == tab

                // Distinct pill background that wraps the tab header content bounds
                val pillBgColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
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

                val interactionSource = remember { MutableInteractionSource() }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(pillBgColor)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = CoralToday.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            haptics.performNavigationTap()
                            onTabSelected(tab)
                        }
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                        .testTag("tab_${tab.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = textColor
                    )

                    if (tab == ActiveTab.TODO && pendingTaskCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isSelected) CoralToday
                                    else MaterialTheme.colorScheme.surface
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
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

@Composable
private fun TopBarCircleButton(
    onClick: () -> Unit,
    testTag: String,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 38.dp, minHeight = 38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .padding(9.dp)
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
            .wrapContentHeight()
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
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

        // Progress track
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
