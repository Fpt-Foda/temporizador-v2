package com.magcubic.restlembrete

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    data class Update(val version: String, val downloadUrl: String)

    sealed class CheckResult {
        data class Available(val update: Update) : CheckResult()
        data object UpToDate : CheckResult()
        data object NotConfigured : CheckResult()
        data class Failed(val message: String) : CheckResult()
    }

    sealed class DownloadResult {
        data object StartedInstaller : DownloadResult()
        data class Failed(val message: String) : DownloadResult()
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    fun check(context: Context, callback: (CheckResult) -> Unit) {
        val repository = context.getString(R.string.update_repository).trim()
        if (repository.isBlank() || repository.startsWith("COLOQUE_")) {
            callback(CheckResult.NotConfigured)
            return
        }

        Thread {
            val result = try {
                val connection = (URL("https://api.github.com/repos/$repository/releases/latest").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "Temporizador-V2")
                }
                if (connection.responseCode !in 200..299) {
                    CheckResult.Failed("Não foi possível consultar a atualização.")
                } else {
                    val release = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(release)
                    val version = json.getString("tag_name").removePrefix("v")
                    val assets = json.getJSONArray("assets")
                    var apkUrl: String? = null
                    for (index in 0 until assets.length()) {
                        val asset = assets.getJSONObject(index)
                        if (asset.getString("name").endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.getString("browser_download_url")
                            break
                        }
                    }
                    val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
                    when {
                        apkUrl == null -> CheckResult.Failed("A versão publicada não tem um arquivo APK.")
                        isNewer(version, currentVersion) -> CheckResult.Available(Update(version, apkUrl))
                        else -> CheckResult.UpToDate
                    }
                }
            } catch (_: Exception) {
                CheckResult.Failed("Não foi possível verificar agora. Confira a internet.")
            }
            mainHandler.post { callback(result) }
        }.start()
    }

    fun downloadAndInstall(context: Context, update: Update, callback: (DownloadResult) -> Unit) {
        Thread {
            try {
                val updatesDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates").apply { mkdirs() }
                val apkFile = File(updatesDir, "TemporizadorV2-${update.version}.apk")
                val connection = (URL(update.downloadUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    setRequestProperty("User-Agent", "Temporizador-V2")
                }
                connection.inputStream.use { input ->
                    apkFile.outputStream().use { output -> input.copyTo(output) }
                }
                mainHandler.post {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                        val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(settingsIntent)
                        callback(DownloadResult.Failed("Autorize instalações para o Temporizador V2 e tente novamente."))
                    } else {
                        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
                        val installIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(apkUri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(installIntent)
                        callback(DownloadResult.StartedInstaller)
                    }
                }
            } catch (_: Exception) {
                mainHandler.post { callback(DownloadResult.Failed("Não foi possível baixar a atualização. Tente novamente.")) }
            }
        }.start()
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val remoteParts = remote.split(Regex("[^0-9]+"))
            .filter { it.isNotBlank() }.map { it.toIntOrNull() ?: 0 }
        val localParts = local.split(Regex("[^0-9]+"))
            .filter { it.isNotBlank() }.map { it.toIntOrNull() ?: 0 }
        val size = maxOf(remoteParts.size, localParts.size)
        for (index in 0 until size) {
            val remotePart = remoteParts.getOrElse(index) { 0 }
            val localPart = localParts.getOrElse(index) { 0 }
            if (remotePart != localPart) return remotePart > localPart
        }
        return false
    }
}
