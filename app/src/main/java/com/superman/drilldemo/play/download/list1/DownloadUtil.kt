package com.superman.drilldemo.play.download.list1

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DefaultDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import java.io.File
import java.util.concurrent.Executors

@UnstableApi
object DownloadUtil {

    private const val TAG = "DownloadUtil"
    private const val USER_AGENT_DOWNLOADS = "YourApp/OfflineDownloader" // 自定义 User Agent

    // --- 离线下载专用配置 ---
    private const val DOWNLOAD_DATABASE_NAME = "app_offline_downloads.db" // 下载元数据数据库名
    // 推荐使用外部私有目录，应用卸载时数据会一并删除，且通常不需要额外权限
    private const val EXTERNAL_DOWNLOAD_CACHE_SUBDIR = "my_app_offline_media"
    private const val INTERNAL_DOWNLOAD_FALLBACK_SUBDIR = "my_app_internal_offline_media_fallback"

    // --- 单例实例 (用于离线下载) ---
    @Volatile
    private var downloadManagerInstance: DownloadManager? = null
    @Volatile
    private var downloadCacheInstance: SimpleCache? = null
    @Volatile
    private var databaseProviderForDownloads: DefaultDatabaseProvider? = null
    @Volatile
    private var httpDataSourceFactoryForDownloads: DefaultHttpDataSource.Factory? = null
    @Volatile
    private var cacheDataSourceFactoryForDownloads: CacheDataSource.Factory? = null

    @Synchronized
    private fun getDatabaseProviderForDownloads(appContext: Context): DefaultDatabaseProvider {
        if (databaseProviderForDownloads == null) {
            Log.d(TAG, "Initializing DatabaseProvider for downloads with DB: $DOWNLOAD_DATABASE_NAME")
            databaseProviderForDownloads = DefaultDatabaseProvider(
                Media3DatabaseHelper(appContext, DOWNLOAD_DATABASE_NAME) // 传入特定数据库名
            )
        }
        return databaseProviderForDownloads!!
    }

    @Synchronized
    fun getDownloadCache(context: Context): SimpleCache? {
        if (downloadCacheInstance == null) {
            val appContext = context.applicationContext
            val baseExternalDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val cacheDir: File

            if (baseExternalDir != null && (baseExternalDir.exists() || baseExternalDir.mkdirs())) {
                cacheDir = File(baseExternalDir, EXTERNAL_DOWNLOAD_CACHE_SUBDIR)
                Log.d(TAG, "Using external private directory for DOWNLOAD cache: ${cacheDir.absolutePath}")
            } else {
                Log.w(TAG, "External storage not available or creatable for DOWNLOAD cache. Falling back to internal.")
                val internalCacheBaseDir = appContext.cacheDir
                cacheDir = File(internalCacheBaseDir, INTERNAL_DOWNLOAD_FALLBACK_SUBDIR)
                Log.d(TAG, "Using internal fallback directory for DOWNLOAD cache: ${cacheDir.absolutePath}")
            }

            if (!cacheDir.exists()) {
                if (!cacheDir.mkdirs()) {
                    Log.e(TAG, "Failed to create DOWNLOAD cache directory: ${cacheDir.absolutePath}")
                    return null // 目录创建失败，缓存无法初始化
                }
            }
            // NoOpCacheEvictor 用于离线下载，因为我们不希望下载的内容被自动清除
            downloadCacheInstance = SimpleCache(
                cacheDir,
                NoOpCacheEvictor(),
                getDatabaseProviderForDownloads(appContext)
            )
            Log.i(TAG, "SimpleCache for downloads initialized at: ${cacheDir.absolutePath}")
        }
        return downloadCacheInstance
    }

    @Synchronized
    private fun getHttpDataSourceFactoryForDownloads(appContext: Context): DefaultHttpDataSource.Factory {
        if (httpDataSourceFactoryForDownloads == null) {
            Log.d(TAG, "Initializing HttpDataSource.Factory for downloads.")
            httpDataSourceFactoryForDownloads = DefaultHttpDataSource.Factory().setUserAgent(USER_AGENT_DOWNLOADS)
        }
        return httpDataSourceFactoryForDownloads!!
    }

    /**
     * 为 DownloadManager 创建 CacheDataSource.Factory。
     * DownloadManager 使用它来下载数据，并将数据写入到 getDownloadCache() 返回的缓存中。
     */
    @Synchronized
    private fun getCacheDataSourceFactoryForDownloads(appContext: Context): CacheDataSource.Factory {
        if (cacheDataSourceFactoryForDownloads == null) {
            Log.d(TAG, "Initializing CacheDataSource.Factory for downloads.")
            val downloadCache = getDownloadCache(appContext)
                ?: throw IllegalStateException("Download cache is null, cannot create CacheDataSourceFactory for downloads.")

            cacheDataSourceFactoryForDownloads = CacheDataSource.Factory()
                .setCache(downloadCache)
                .setUpstreamDataSourceFactory(getHttpDataSourceFactoryForDownloads(appContext))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR) // 可选
            Log.i(TAG, "CacheDataSource.Factory for downloads initialized.")
        }
        return cacheDataSourceFactoryForDownloads!!
    }

    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        if (downloadManagerInstance == null) {
            Log.d(TAG, "Initializing DownloadManager.")
            val appContext = context.applicationContext

            // 确保所有依赖组件已准备好
            getDatabaseProviderForDownloads(appContext)
            getHttpDataSourceFactoryForDownloads(appContext)
            val dlCache = getDownloadCache(appContext)
            if (dlCache == null) {
                Log.e(TAG, "Download cache is null, cannot create DownloadManager.")
                // 抛出异常或返回一个不能工作的 Manager，取决于错误处理策略
                throw IllegalStateException("Download cache could not be initialized for DownloadManager.")
            }
            val dsFactory = getCacheDataSourceFactoryForDownloads(appContext)

            downloadManagerInstance = DownloadManager(
                appContext,
                getDatabaseProviderForDownloads(appContext), // 数据库提供者
                dlCache, // 缓存实例
                dsFactory, // 上游数据源工厂 (包装了缓存)
                Executors.newFixedThreadPool(3) // 下载执行器线程池 (数量可调)
            ).apply {
                maxParallelDownloads = 2 // 例如，同时下载的最大数量
                // requirements = Requirements(...) // 可选: 设置下载所需的条件 (如网络类型)
            }
            Log.i(TAG, "DownloadManager initialized.")
        }
        return downloadManagerInstance!!
    }

    /**
     * 获取 DownloadNotificationHelper 实例。
     * @param context Context
     * @param channelId 通知的渠道 ID，应与 MyDownloadService 中定义的一致。
     */
    fun getDownloadNotificationHelper(context: Context, channelId: String): DownloadNotificationHelper {
        return DownloadNotificationHelper(context.applicationContext, channelId)
    }

    /**
     * 释放 DownloadUtil 管理的资源。
     * 应该在应用退出或不再需要下载功能时调用。
     */
    @Synchronized
    fun release() {
        Log.d(TAG, "Releasing DownloadUtil resources...")
        downloadManagerInstance?.release() // 释放 DownloadManager 会停止所有下载并释放其内部资源
        downloadManagerInstance = null

        downloadCacheInstance?.release() // 释放缓存 (会关闭数据库等)
        downloadCacheInstance = null

        // 清理其他单例引用
        databaseProviderForDownloads = null
        httpDataSourceFactoryForDownloads = null
        cacheDataSourceFactoryForDownloads = null
        Log.i(TAG, "DownloadUtil resources released.")
    }
}
