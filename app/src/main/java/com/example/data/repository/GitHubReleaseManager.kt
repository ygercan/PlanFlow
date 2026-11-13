package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class GitHubReleaseInfo(
    val tagName: String,
    val title: String,
    val changelog: String,
    val publishedAt: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long,
    val isNewerThanInstalled: Boolean,
    val installedVersion: String
) {
    val preferredDownloadOrReleaseUrl: String
        get() = apkDownloadUrl?.takeIf { it.isNotBlank() } ?: htmlUrl

    val formattedApkSize: String
        get() = if (apkSizeBytes > 0) {
            String.format(Locale.US, "%.1f MB", apkSizeBytes / (1024.0 * 1024.0))
        } else {
            "GitHub Release"
        }
}

data class GitHubUpdateCheckResult(
    val success: Boolean,
    val message: String,
    val releaseInfo: GitHubReleaseInfo? = null,
    val checkedAt: String = SimpleDateFormat("dd MMM HH:mm", Locale("tr")).format(Date())
)

class GitHubReleaseManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("planflow_github_update_prefs", Context.MODE_PRIVATE)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    fun getGitHubOwner(): String {
        val saved = prefs.getString("github_owner", null)?.trim().orEmpty()
        if (saved.isNotBlank()) return saved
        val fromBuildConfig = try {
            BuildConfig.GITHUB_OWNER.trim()
        } catch (_: Exception) {
            ""
        }
        return if (fromBuildConfig == "your-github-username") "" else fromBuildConfig
    }

    fun getGitHubRepo(): String {
        val saved = prefs.getString("github_repo", null)?.trim().orEmpty()
        if (saved.isNotBlank()) return saved
        val fromBuildConfig = try {
            BuildConfig.GITHUB_REPO.trim()
        } catch (_: Exception) {
            ""
        }
        return if (fromBuildConfig == "your-github-repo") "" else fromBuildConfig
    }

    fun getGitHubToken(): String {
        return prefs.getString("github_token", null)?.trim().orEmpty()
    }

    fun setGitHubRepository(owner: String, repo: String, token: String? = null) {
        val cleanOwner = owner.trim().removePrefix("https://github.com/").split("/").firstOrNull()?.trim().orEmpty()
        val cleanRepo = repo.trim().removeSuffix(".git").split("/").lastOrNull()?.trim().orEmpty()
        val editor = prefs.edit()
            .putString("github_owner", cleanOwner)
            .putString("github_repo", cleanRepo)
        if (token != null) {
            editor.putString("github_token", token.trim())
        }
        editor.apply()
    }

    fun isRepositoryConfigured(): Boolean {
        val owner = getGitHubOwner()
        val repo = getGitHubRepo()
        return owner.isNotBlank() && repo.isNotBlank()
    }

    fun getLastCheckTimeString(): String {
        return prefs.getString("last_update_check_time", "Henüz denetlenmedi") ?: "Henüz denetlenmedi"
    }

    fun getInstalledVersionName(): String {
        return try {
            BuildConfig.VERSION_NAME
        } catch (_: Exception) {
            "1.0"
        }
    }

    private fun buildGitHubRequest(url: String, token: String): Request {
        val builder = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("User-Agent", "PlanFlow-Android-Updater")
            .get()
        if (token.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer $token")
        }
        return builder.build()
    }

    suspend fun checkLatestRelease(): GitHubUpdateCheckResult = withContext(Dispatchers.IO) {
        val owner = getGitHubOwner()
        val repo = getGitHubRepo()
        val token = getGitHubToken()
        val installedVer = getInstalledVersionName()

        if (owner.isBlank() || repo.isBlank()) {
            return@withContext GitHubUpdateCheckResult(
                success = false,
                message = "GitHub kullanıcı adı (owner) ve depo adı (repo) henüz tanımlanmadı."
            )
        }

        try {
            val latestUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"
            val response = httpClient.newCall(buildGitHubRequest(latestUrl, token)).execute()
            val bodyStr = response.body?.string().orEmpty()

            if (response.code == 404) {
                // Fallback 1: Check /releases list in case only pre-release exists
                val listUrl = "https://api.github.com/repos/$owner/$repo/releases?per_page=1"
                val listResp = httpClient.newCall(buildGitHubRequest(listUrl, token)).execute()
                val listBody = listResp.body?.string().orEmpty()
                if (listResp.isSuccessful) {
                    val arr = JSONArray(listBody)
                    if (arr.length() > 0) {
                        val parsed = parseReleaseJson(arr.getJSONObject(0), installedVer)
                        saveLastCheckTimestamp()
                        return@withContext buildResultFromRelease(parsed)
                    }
                    saveLastCheckTimestamp()
                    return@withContext GitHubUpdateCheckResult(
                        success = false,
                        message = "$owner/$repo deposunda henüz yayınlanmış bir GitHub Release (APK) bulunamadı. Kodu GitHub'a push ettiğinizde GitHub Actions otomatik oluşturacaktır."
                    )
                }

                // Fallback 2: Check whether the repository itself is Private or inaccessible (GitHub returns 404 for private repos without token)
                val repoMetaUrl = "https://api.github.com/repos/$owner/$repo"
                val repoResp = httpClient.newCall(buildGitHubRequest(repoMetaUrl, token)).execute()
                saveLastCheckTimestamp()

                val diagnosticMessage = if (repoResp.code == 404 || repoResp.code == 401 || repoResp.code == 403) {
                    if (token.isBlank()) {
                        "$owner/$repo deposuna erişilemedi (HTTP ${repoResp.code}). Deponuz 'Private' (Gizli) ise GitHub API sürüm listesini gizler: aşağıya GitHub Token (PAT) ekleyin veya depoyu Public yapın."
                    } else {
                        "$owner/$repo deposuna girilen Token ile erişilemedi (HTTP ${repoResp.code}). Token yetkisini (Contents: Read) ve depo adını kontrol edin."
                    }
                } else {
                    "$owner/$repo deposunda henüz yayınlanmış bir GitHub Release (APK) bulunamadı."
                }
                return@withContext GitHubUpdateCheckResult(
                    success = false,
                    message = diagnosticMessage
                )
            }

            if (response.code == 401 || response.code == 403) {
                saveLastCheckTimestamp()
                return@withContext GitHubUpdateCheckResult(
                    success = false,
                    message = "GitHub erişim/limit hatası (HTTP ${response.code}). Gizli depo kullanıyorsanız geçerli bir GitHub Token (PAT) girin."
                )
            }

            if (!response.isSuccessful) {
                saveLastCheckTimestamp()
                return@withContext GitHubUpdateCheckResult(
                    success = false,
                    message = "GitHub API yanıt hatası (HTTP ${response.code}). Depo adını kontrol edin."
                )
            }

            val releaseJson = JSONObject(bodyStr)
            val parsed = parseReleaseJson(releaseJson, installedVer)
            saveLastCheckTimestamp()
            return@withContext buildResultFromRelease(parsed)
        } catch (e: Exception) {
            return@withContext GitHubUpdateCheckResult(
                success = false,
                message = "GitHub bağlantı hatası: ${e.localizedMessage ?: "Ağ erişimi sağlanamadı"}"
            )
        }
    }

    private fun buildResultFromRelease(info: GitHubReleaseInfo): GitHubUpdateCheckResult {
        val msg = if (info.isNewerThanInstalled) {
            "Yeni sürüm bulundu: ${info.tagName} (Mevcut: v${info.installedVersion})"
        } else {
            "Uygulamanız güncel (v${info.installedVersion} / Son Release: ${info.tagName})."
        }
        return GitHubUpdateCheckResult(
            success = true,
            message = msg,
            releaseInfo = info
        )
    }

    private fun saveLastCheckTimestamp() {
        val nowStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("tr")).format(Date())
        prefs.edit().putString("last_update_check_time", nowStr).apply()
    }

    companion object {
        /**
         * Parses a GitHub Release JSON object and identifies any attached `.apk` binary asset.
         */
        fun parseReleaseJson(json: JSONObject, installedVersion: String): GitHubReleaseInfo {
            val tagName = json.optString("tag_name", "v1.0")
            val name = json.optString("name", tagName).ifBlank { tagName }
            val body = json.optString("body", "Bu sürüm için değişiklik notu girilmemiş.").ifBlank {
                "Otomatik GitHub Actions APK derlemesi."
            }
            val htmlUrl = json.optString("html_url", "")
            val publishedRaw = json.optString("published_at", "")
            val publishedFormatted = publishedRaw.take(10).ifBlank { "Yeni" }

            var apkUrl: String? = null
            var apkName: String? = null
            var apkSize = 0L

            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val assetName = asset.optString("name", "")
                    val downloadUrl = asset.optString("browser_download_url", "")
                    if (assetName.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                        apkUrl = downloadUrl
                        apkName = assetName
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            val isNewer = isRemoteVersionNewer(installedVersion, tagName)

            return GitHubReleaseInfo(
                tagName = tagName,
                title = name,
                changelog = body,
                publishedAt = publishedFormatted,
                htmlUrl = htmlUrl,
                apkDownloadUrl = apkUrl,
                apkFileName = apkName,
                apkSizeBytes = apkSize,
                isNewerThanInstalled = isNewer,
                installedVersion = installedVersion
            )
        }

        /**
         * Deterministic semantic version comparator.
         * Example: installed "1.0" vs remote "v1.0.1" -> true.
         * Example: installed "1.2.0" vs remote "v1.2.0" -> false.
         */
        fun isRemoteVersionNewer(installedVersion: String, remoteTag: String): Boolean {
            val cleanInstalled = installedVersion.trim().removePrefix("v").removePrefix("V")
                .substringBefore("-")
            val cleanRemote = remoteTag.trim().removePrefix("v").removePrefix("V")
                .substringBefore("-")

            val installedParts = cleanInstalled.split(".").map { it.toIntOrNull() ?: 0 }
            val remoteParts = cleanRemote.split(".").map { it.toIntOrNull() ?: 0 }

            val maxLen = maxOf(installedParts.size, remoteParts.size)
            for (i in 0 until maxLen) {
                val inst = installedParts.getOrElse(i) { 0 }
                val rem = remoteParts.getOrElse(i) { 0 }
                if (rem > inst) return true
                if (rem < inst) return false
            }
            return false
        }

        const val GITHUB_ACTIONS_WORKFLOW_SUMMARY = """# .github/workflows/android-release.yml
# Projeyi GitHub'a push ettiğinizde APK otomatik derlenir ve Releases altına yüklenir:
name: Android Atomic APK Release
on:
  push:
    branches: [ main, master ]
    tags: [ 'v*' ]
  workflow_dispatch:"""
    }
}
