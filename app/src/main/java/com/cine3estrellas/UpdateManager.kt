package com.cine3estrellas

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    object Installing : DownloadState()
    object Error : DownloadState()
}

object UpdateManager {
    private const val APK_FILENAME = "cine3estrellas-update.apk"

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openUnknownSourcesSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e("UpdateManager", "Error opening settings", e)
            }
        }
    }

    /**
     * Descarga el APK desde [apkUrl] y emite el progreso como un Flow<DownloadState>.
     * Al completarse la descarga, lanza automáticamente el instalador del sistema.
     * Implementación basada en OkHttp para mayor fiabilidad, idéntica al sistema del instalador de referencia.
     */
    fun downloadAndInstall(context: Context, apkUrl: String): Flow<DownloadState> = channelFlow {
        send(DownloadState.Downloading(0f))

        val targetFile = File(context.cacheDir, APK_FILENAME)
        if (targetFile.exists()) {
            targetFile.delete()
        }

        try {
            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val request = Request.Builder()
                .url(apkUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Respuesta fallida del servidor: Código ${response.code}")
                    }
                    val body = response.body ?: throw IOException("El contenido de la respuesta está vacío")
                    val contentLength = body.contentLength()

                    body.byteStream().use { inputStream ->
                        FileOutputStream(targetFile).use { outputStream ->
                            val buffer = ByteArray(128 * 1024) // 128KB buffer for high performance
                            var bytesRead: Int
                            var totalBytesRead = 0L
                            var lastEmitTime = 0L

                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead

                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastEmitTime > 100) { // Limitar la frecuencia de emisión
                                    if (contentLength > 0) {
                                        val currentProgress = totalBytesRead.toFloat() / contentLength
                                        send(DownloadState.Downloading(currentProgress.coerceIn(0f, 1f)))
                                    } else {
                                        send(DownloadState.Downloading(-1f)) // Indeterminado
                                    }
                                    lastEmitTime = currentTime
                                }
                            }
                        }
                    }
                }
            }

            // Descarga completada con éxito
            send(DownloadState.Downloading(1.0f))
            delay(1000)
            send(DownloadState.Installing)
            delay(500)

            // Ejecutar el instalador en el hilo principal
            withContext(Dispatchers.Main) {
                installExistingApk(context)
            }

        } catch (e: Exception) {
            Log.e("UpdateManager", "Error during download process", e)
            send(DownloadState.Error)
        }
    }

    fun getUpdateFile(context: Context): File {
        return File(context.cacheDir, APK_FILENAME)
    }

    fun installExistingApk(context: Context) {
        val apkFile = getUpdateFile(context)
        if (!apkFile.exists()) {
            Log.e("UpdateManager", "APK file does not exist: ${apkFile.absolutePath}")
            return
        }

        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            Log.d("UpdateManager", "Starting installation intent for URI: $uri")
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Error starting APK installation intent", e)
        }
    }
}
