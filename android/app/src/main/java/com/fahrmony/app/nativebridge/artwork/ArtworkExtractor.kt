package com.fahrmony.app.nativebridge.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import com.fahrmony.app.nativebridge.FahrmonyLogBuffer
import java.net.HttpURLConnection
import java.net.URL

/**
 * 封面提取与多级缓存引擎 (Phase 10.1.12 模块化拆分)
 *
 * 优先级: Metadata 内嵌 Bitmap -> 通知栏大图标 -> content:// / file:// 本地 URI -> http/https 异步拉取。
 * 对所有解码结果强制 <=240px 等比约束与缩放后像素级去重，消除跨进程 Binder 反序列化引发的无效重绘与频闪。
 */
object ArtworkExtractor {

    private const val MAX_ARTWORK_DIM = 240
    private const val NETWORK_TIMEOUT_MS = 5000

    @Volatile private var lastRawSourceGenId: Int = -1
    @Volatile private var lastRawSourceUri: String? = null
    @Volatile private var cachedRawArtworkBitmap: Bitmap? = null
    @Volatile private var inFlightArtworkUri: String? = null
    @Volatile private var failedArtworkUri: String? = null

    fun invalidateGenId() {
        lastRawSourceGenId = -1
    }

    fun reset() {
        lastRawSourceGenId = -1
        lastRawSourceUri = null
        cachedRawArtworkBitmap = null
        failedArtworkUri = null
    }

    fun hasDirectBitmap(metadata: MediaMetadataCompat?, fallbackArtwork: Bitmap?): Boolean {
        val bmp = extractMetadataBitmap(metadata) ?: fallbackArtwork
        return bmp != null && !bmp.isRecycled
    }

    fun extractUriString(metadata: MediaMetadataCompat?): String? {
        return metadata?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata?.getString(MediaMetadataCompat.METADATA_KEY_ART_URI)
            ?: metadata?.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI)
            ?: metadata?.description?.iconUri?.toString()
    }

    private fun extractMetadataBitmap(metadata: MediaMetadataCompat?): Bitmap? {
        return metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON)
            ?: metadata?.description?.iconBitmap
    }

    fun extract(
        controller: MediaControllerCompat,
        fallbackArtwork: Bitmap?,
        appContext: Context?,
        mainHandler: Handler,
        isActiveController: Boolean,
        canCommitNetworkArtwork: (String) -> Boolean,
        onNetworkArtworkReady: () -> Unit
    ): Bitmap? {
        val metadata = controller.metadata
        val bmp = extractMetadataBitmap(metadata) ?: fallbackArtwork

        if (bmp != null && !bmp.isRecycled) {
            val srcGenId = bmp.generationId
            val prevCached = cachedRawArtworkBitmap
            if (prevCached != null && !prevCached.isRecycled && lastRawSourceGenId == srcGenId) {
                return prevCached
            }
            val scaled = scaleBitmapSafely(bmp, MAX_ARTWORK_DIM)
            // 缩放后执行像素级比对：解决源图 >240px 且 Binder 每次反序列化产生新 genId 导致的缓存失效与 AA 频闪
            if (prevCached != null && !prevCached.isRecycled &&
                scaled.width == prevCached.width && scaled.height == prevCached.height &&
                scaled.sameAs(prevCached)
            ) {
                if (scaled != bmp && !scaled.isRecycled) {
                    scaled.recycle()
                }
                lastRawSourceGenId = srcGenId
                return prevCached
            }
            lastRawSourceGenId = srcGenId
            lastRawSourceUri = null
            cachedRawArtworkBitmap = scaled
            FahrmonyLogBuffer.addLog(
                "ARTWORK_IN",
                "MediaSession封面提取",
                "来源: MetadataBitmap, 尺寸: ${scaled.width}x${scaled.height}, genId: ${scaled.generationId}",
                controller.packageName
            )
            return scaled
        }

        val uriStr = extractUriString(metadata)
        if (uriStr.isNullOrBlank()) {
            return null
        }

        val prevCached = cachedRawArtworkBitmap
        if (prevCached != null && !prevCached.isRecycled && lastRawSourceUri == uriStr) {
            return prevCached
        }

        if (uriStr.startsWith("content://") || uriStr.startsWith("file://")) {
            try {
                val uri = Uri.parse(uriStr)
                appContext?.contentResolver?.openInputStream(uri)?.use { stream ->
                    val decoded = BitmapFactory.decodeStream(stream)
                    if (decoded != null && !decoded.isRecycled) {
                        val scaled = scaleBitmapSafely(decoded, MAX_ARTWORK_DIM)
                        if (scaled != decoded && !decoded.isRecycled) {
                            decoded.recycle()
                        }
                        lastRawSourceGenId = -1
                        lastRawSourceUri = uriStr
                        cachedRawArtworkBitmap = scaled
                        FahrmonyLogBuffer.addLog(
                            "ARTWORK_IN",
                            "MediaSession封面提取",
                            "来源: UriStream, 尺寸: ${scaled.width}x${scaled.height}, genId: ${scaled.generationId}",
                            controller.packageName
                        )
                        return scaled
                    }
                }
            } catch (ignored: Exception) {}
            return null
        }

        if ((uriStr.startsWith("https://", ignoreCase = true) || uriStr.startsWith("http://", ignoreCase = true)) &&
            isActiveController &&
            uriStr != inFlightArtworkUri &&
            uriStr != failedArtworkUri
        ) {
            fetchNetworkArtworkAsync(uriStr, controller.packageName, mainHandler, canCommitNetworkArtwork, onNetworkArtworkReady)
        }

        return null
    }

    private fun fetchNetworkArtworkAsync(
        uriStr: String,
        packageName: String,
        mainHandler: Handler,
        canCommitNetworkArtwork: (String) -> Boolean,
        onNetworkArtworkReady: () -> Unit
    ) {
        inFlightArtworkUri = uriStr
        Thread({
            var connection: HttpURLConnection? = null
            try {
                // 绕过 Android 9+ 默认明文 HTTP 拦截（无需放宽 Manifest），以原始 uriStr 作为缓存标识
                val fetchUrl = if (uriStr.startsWith("http://", ignoreCase = true)) {
                    "https://" + uriStr.substring(7)
                } else {
                    uriStr
                }
                connection = (URL(fetchUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = NETWORK_TIMEOUT_MS
                    readTimeout = NETWORK_TIMEOUT_MS
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Fahrmony/1.3")
                }
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val decoded = connection.inputStream.use { BitmapFactory.decodeStream(it) }
                    if (decoded != null && !decoded.isRecycled) {
                        val scaled = scaleBitmapSafely(decoded, MAX_ARTWORK_DIM)
                        if (scaled != decoded && !decoded.isRecycled) {
                            decoded.recycle()
                        }
                        mainHandler.post {
                            if (canCommitNetworkArtwork(uriStr)) {
                                lastRawSourceGenId = -1
                                lastRawSourceUri = uriStr
                                cachedRawArtworkBitmap = scaled
                                failedArtworkUri = null
                                FahrmonyLogBuffer.addLog(
                                    "ARTWORK_IN",
                                    "MediaSession封面提取",
                                    "来源: NetworkUri, 尺寸: ${scaled.width}x${scaled.height}, genId: ${scaled.generationId}",
                                    packageName
                                )
                                onNetworkArtworkReady()
                            } else if (!scaled.isRecycled) {
                                scaled.recycle()
                            }
                        }
                        return@Thread
                    }
                }
                failedArtworkUri = uriStr
            } catch (_: Throwable) {
                failedArtworkUri = uriStr
            } finally {
                try { connection?.disconnect() } catch (_: Throwable) {}
                if (inFlightArtworkUri == uriStr) {
                    inFlightArtworkUri = null
                }
            }
        }, "FahrmonyArtworkFetch").start()
    }

    /**
     * 等比安全缩放位图至指定最大尺寸，杜绝 Binder 1MB 缓冲区溢出与 IPC 掉帧
     */
    private fun scaleBitmapSafely(source: Bitmap, maxDim: Int = MAX_ARTWORK_DIM): Bitmap {
        if (source.isRecycled) return source
        val width = source.width
        val height = source.height
        if (width <= maxDim && height <= maxDim) {
            return source
        }
        val ratio = Math.min(maxDim.toFloat() / width, maxDim.toFloat() / height)
        val targetWidth = Math.max(1, (width * ratio).toInt())
        val targetHeight = Math.max(1, (height * ratio).toInt())
        return try {
            Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        } catch (e: Throwable) {
            source
        }
    }
}

