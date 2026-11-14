package com.example.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SecurityUpdateGood
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.repository.GitHubReleaseInfo
import com.example.data.repository.GitHubReleaseManager
import com.example.util.rememberHapticController

@Composable
fun GitHubUpdateDialog(
    installedVersion: String,
    githubOwner: String,
    githubRepo: String,
    githubToken: String = "",
    lastCheckTime: String,
    isCheckingUpdate: Boolean,
    updateStatusMessage: String?,
    latestRelease: GitHubReleaseInfo?,
    onDismiss: () -> Unit,
    onSaveRepoConfig: (owner: String, repo: String, token: String?) -> Unit,
    onCheckForUpdates: () -> Unit,
    onExecuteAtomicCheckpointAndDownload: (targetUrl: String, onReadyToOpenUrl: (String) -> Unit) -> Unit,
    onRunAtomicDbCheckpointOnly: () -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberHapticController()

    var ownerInput by remember(githubOwner) { mutableStateOf(githubOwner) }
    var repoInput by remember(githubRepo) { mutableStateOf(githubRepo) }
    var tokenInput by remember(githubToken) { mutableStateOf(githubToken) }
    var showToken by remember { mutableStateOf(false) }

    val isRepoConfigured = ownerInput.isNotBlank() && repoInput.isNotBlank()
    val hasNewerRelease = latestRelease?.isNewerThanInstalled == true

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(22.dp)
                .testTag("github_update_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdateAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Atomik Güncelleme & GitHub",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.3).sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Mevcut Sürüm: v$installedVersion · Otomatik APK CI/CD",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Status Pill
                    val badgeColor = when {
                        hasNewerRelease -> MaterialTheme.colorScheme.primary
                        latestRelease != null -> Color(0xFF10B981)
                        else -> Color(0xFFF59E0B)
                    }
                    val badgeText = when {
                        hasNewerRelease -> "Yeni: ${latestRelease?.tagName}"
                        latestRelease != null -> "Güncel"
                        isRepoConfigured -> "Hazır"
                        else -> "Repo Girin"
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .border(
                                width = 1.dp,
                                color = badgeColor.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(badgeColor)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // How it works banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Projeye özellik ekleyip GitHub'a push ettiğinizde (.github/workflows/android-release.yml) APK otomatik derlenip Releases altına yüklenir. Güncelleme öncesi Room veritabanı atomik (@Transaction) olarak güvenceye alınır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // GitHub Owner & Repo Configuration
                Text(
                    text = "GitHub Depo Bilgileri (Owner / Repo)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = ownerInput,
                        onValueChange = { ownerInput = it },
                        label = { Text("Kullanıcı (Owner)", fontSize = 11.sp) },
                        placeholder = { Text("ornek: kullanici-adi", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("github_owner_input"),
                        shape = RoundedCornerShape(10.dp),
                        colors = textFieldColors
                    )

                    OutlinedTextField(
                        value = repoInput,
                        onValueChange = { repoInput = it },
                        label = { Text("Repo Adı", fontSize = 11.sp) },
                        placeholder = { Text("ornek: planflow", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("github_repo_input"),
                        shape = RoundedCornerShape(10.dp),
                        colors = textFieldColors
                    )

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable {
                                haptics.performNavigationTap()
                                onSaveRepoConfig(ownerInput, repoInput, tokenInput)
                                Toast.makeText(
                                    context,
                                    "GitHub depo bilgisi kaydedildi ve denetleniyor",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .testTag("save_github_repo_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Depo Bilgisini Kaydet",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text("GitHub Token (Yalnızca Private/Gizli Repo için)", fontSize = 11.sp) },
                    placeholder = { Text("Public repoda boş bırakın (github_pat_...)", fontSize = 11.sp) },
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showToken) "Tokeni gizle" else "Tokeni göster",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_token_input"),
                    shape = RoundedCornerShape(10.dp),
                    colors = textFieldColors
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Son Denetim: $lastCheckTime",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Status message
                if (!updateStatusMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Text(
                            text = updateStatusMessage,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Release Info & Changelog Card (when a release is found)
                if (latestRelease != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = 1.dp,
                                color = if (hasNewerRelease) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${latestRelease.title} (${latestRelease.tagName})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (hasNewerRelease) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${latestRelease.publishedAt} · ${latestRelease.formattedApkSize}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = latestRelease.changelog.take(400),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                haptics.performTaskCompletion(isCompleting = true)
                                val targetUrl = latestRelease.preferredDownloadOrReleaseUrl
                                onExecuteAtomicCheckpointAndDownload(targetUrl) { verifiedUrl ->
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(verifiedUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(
                                            context,
                                            "Bağlantı açılamadı: $verifiedUrl",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("atomic_update_download_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (latestRelease.apkDownloadUrl != null) {
                                    "Atomik Yedekle & APK İndir (${latestRelease.tagName})"
                                } else {
                                    "Atomik Yedekle & GitHub Release'i Aç"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.SecurityUpdateGood,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 1.dp)
                                    .size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Play Protect uyarısı çıkarsa \"Diğer ayrıntılar → Yine de yükle\" seçin. Kalıcı çözüm için GitHub Secrets'a RELEASE_KEYSTORE_BASE64 ekleyip imzalı Release APK üretin.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Primary Check Button
                Button(
                    onClick = {
                        haptics.performNavigationTap()
                        if (ownerInput.trim() != githubOwner ||
                            repoInput.trim() != githubRepo ||
                            tokenInput.trim() != githubToken
                        ) {
                            onSaveRepoConfig(ownerInput, repoInput, tokenInput)
                        } else {
                            onCheckForUpdates()
                        }
                    },
                    enabled = !isCheckingUpdate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("check_github_update_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasNewerRelease) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                        contentColor = if (hasNewerRelease) MaterialTheme.colorScheme.surface else Color.White
                    )
                ) {
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("GitHub Releases Denetleniyor...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GitHub Releases Sürümünü Şimdi Denetle",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Secondary Row: Atomic DB Checkpoint + Copy CI/CD Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            haptics.performTaskCompletion(isCompleting = true)
                            onRunAtomicDbCheckpointOnly()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("atomic_db_checkpoint_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Atomik DB Koruma",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            haptics.performNavigationTap()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText(
                                "GitHub Actions Workflow",
                                GitHubReleaseManager.GITHUB_ACTIONS_WORKFLOW_SUMMARY
                            )
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(
                                context,
                                "GitHub Actions iş akışı bilgisi kopyalandı (.github/workflows/android-release.yml projeye eklendi)!",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("copy_github_actions_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CI/CD Bilgisi",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                TextButton(
                    onClick = {
                        haptics.performNavigationTap()
                        onDismiss()
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = "Kapat",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
