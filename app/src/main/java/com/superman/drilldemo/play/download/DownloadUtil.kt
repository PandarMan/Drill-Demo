package com.superman.drilldemo.play.download

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DefaultDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import java.io.File
import java.util.concurrent.Executors

/**
 *                         ┌───────────────────────┐
 *                         │    DownloadManager    │
 *                         │──────────────────────│
 *                         │ - 管理下载任务        │
 *                         │ - 使用线程池并发下载 │
 *                         └─────────┬────────────┘
 *                                   │ depends on
 *                                   ▼
 *                         ┌───────────────────────┐
 *                         │      SimpleCache      │
 *                         │──────────────────────│
 *                         │ - 缓存已下载文件      │
 *                         │ - 提供 CacheDataSource │
 *                         │ - 使用 DatabaseProvider│
 *                         └─────────┬────────────┘
 *                                   │ used by
 *                                   ▼
 *                         ┌───────────────────────────────┐
 *                         │     CacheDataSource.Factory    │
 *                         │──────────────────────────────│
 *                         │ - 提供给 ExoPlayer 播放器     │
 *                         │ - 从缓存读取或上游下载数据   │
 *                         │ - 上游数据源: HttpDataSource │
 *                         └─────────┬────────────────────┘
 *                                   │
 *                                   ▼
 *                        ┌─────────────────────────────┐
 *                        │  DefaultHttpDataSource.Factory│
 *                        │─────────────────────────────│
 *                        │ - 从网络下载数据             │
 *                        │ - 支持 User-Agent 设置       │
 *                        └─────────────────────────────┘
 *
 */
@UnstableApi
object DownloadUtil {

    // --- 目录常量 ---
    // private const val DOWNLOAD_CONTENT_DIRECTORY = "downloads" // 旧的内部缓存子目录名，如果不再使用可以移除
    private const val EXTERNAL_CACHE_SUBDIRECTORY_NAME = "media3_offline_cache" // 外部私有缓存的子目录名
    private const val INTERNAL_FALLBACK_CACHE_SUBDIRECTORY_NAME = "media3_internal_fallback_cache" // 内部回退缓存的子目录名


    private const val TAG = "DownloadUtil"
    private const val USER_AGENT = "ExoPlayerDemoApp"

    @Volatile
    private var downloadManagerInstance: DownloadManager? = null

    @Volatile
    private var downloadCacheInstance: SimpleCache? = null // 类型改为 SimpleCache 以便直接访问

    @Volatile
    private var databaseProviderInstance: DefaultDatabaseProvider? = null

    @Volatile
    private var httpDataSourceFactoryInstance: DefaultHttpDataSource.Factory? = null

    @Volatile
    private var cacheDataSourceFactoryInstance: CacheDataSource.Factory? = null


    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        if (downloadManagerInstance == null) {
            ensureComponentsInitialized(context.applicationContext)
            downloadManagerInstance = DownloadManager(
                context.applicationContext,
                getDatabaseProvider(context.applicationContext), // 确保 DatabaseProvider 已初始化
                getDownloadCache(context.applicationContext)!!,   // 确保 Cache 已初始化
                getCacheDataSourceFactory(context.applicationContext), // 确保 DataSource.Factory 已初始化
                Executors.newFixedThreadPool(3) // 根据需求调整线程池大小
            ).apply {
                maxParallelDownloads = 2 // 例如，同时最多下载2个文件
            }
        }
        return downloadManagerInstance!!
    }

    @Synchronized
    fun getDownloadCache(context: Context): Cache? { // 返回类型改为 SimpleCache?
        if (downloadCacheInstance == null) {
            val appContext = context.applicationContext

            // 尝试使用外部私有目录
            // 你可以选择一个类型，如 Environment.DIRECTORY_DOWNLOADS, Environment.DIRECTORY_MUSIC,
            // 或者传 null 将其放在 /Android/data/<package>/files/ 目录下
            val externalFilesDirType = Environment.DIRECTORY_DOWNLOADS  //下载目录
            val baseExternalDir = appContext.getExternalFilesDir(externalFilesDirType)
            val cacheDir: File

            if (baseExternalDir != null) {
                // 外部存储可用
                cacheDir = File(baseExternalDir, EXTERNAL_CACHE_SUBDIRECTORY_NAME)
                Log.d(TAG, "Using external private cache directory: ${cacheDir.absolutePath}")
            } else {
                // 外部存储不可用，回退到内部存储
                Log.w(TAG, "External storage not available. Falling back to internal cache.")
                val internalCacheBaseDir = appContext.cacheDir
                cacheDir = File(internalCacheBaseDir, INTERNAL_FALLBACK_CACHE_SUBDIRECTORY_NAME)
                Log.d(TAG, "Using internal fallback cache directory: ${cacheDir.absolutePath}")
            }

            // 确保目录存在
            if (!cacheDir.exists()) {
                if (!cacheDir.mkdirs()) {
                    Log.e(TAG, "Failed to create cache directory: ${cacheDir.absolutePath}")
                    // 如果目录创建失败，可能无法初始化缓存，这里返回null或抛出异常
                    return null
                }
            }

            downloadCacheInstance = SimpleCache(
                cacheDir,
                NoOpCacheEvictor(), // 对于下载，通常不希望自动清除缓存内容
                getDatabaseProvider(appContext) // 数据库提供者
            )
        }
        return downloadCacheInstance
    }


    @Synchronized
    private fun getDatabaseProvider(appContext: Context): DefaultDatabaseProvider {
        if (databaseProviderInstance == null) {
            // Media3DatabaseHelper 通常使用 context.getDatabasePath()，这是内部存储
            databaseProviderInstance = DefaultDatabaseProvider(Media3DatabaseHelper(appContext))
        }
        return databaseProviderInstance!!
    }

    @Synchronized
    fun getHttpDataSourceFactory(appContext: Context): DefaultHttpDataSource.Factory {
        if (httpDataSourceFactoryInstance == null) {
            httpDataSourceFactoryInstance = DefaultHttpDataSource.Factory().setUserAgent(USER_AGENT)
        }
        return httpDataSourceFactoryInstance!!
    }


    @Synchronized
    fun getCacheDataSourceFactory(appContext: Context): CacheDataSource.Factory {
        if (cacheDataSourceFactoryInstance == null) {
            ensureComponentsInitialized(appContext) // 确保 Cache 和 HttpFactory 已初始化
            cacheDataSourceFactoryInstance = CacheDataSource.Factory()
                .setCache(getDownloadCache(appContext)!!)
                .setUpstreamDataSourceFactory(getHttpDataSourceFactory(appContext))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }
        return cacheDataSourceFactoryInstance!!
    }

    /**
     * 确保核心的非 DownloadManager 组件已初始化。
     * DownloadManager 依赖这些组件，所以在创建 DM 之前调用此方法。
     */
    @Synchronized
    private fun ensureComponentsInitialized(appContext: Context) {
        // 调用 getter 方法会触发它们的按需初始化
        getDatabaseProvider(appContext)
        getHttpDataSourceFactory(appContext)
        getDownloadCache(appContext) // 这会初始化 downloadCacheInstance
        // CacheDataSourceFactory 依赖 Cache 和 HttpFactory，它会在其自己的 getter 中处理
    }


    fun getDownloadNotificationHelper(context: Context, channelId: String): DownloadNotificationHelper {
        return DownloadNotificationHelper(context, channelId)
    }

    // 你可能需要一个方法来清除缓存或下载管理器，如果应用需要这种功能
    // 例如，在测试或特定用户操作后。
    @Synchronized
    fun release() {
        downloadManagerInstance?.release() // 如果 DownloadManager 有自己的释放逻辑
        downloadManagerInstance = null

        downloadCacheInstance?.release() // SimpleCache 有 release 方法
        downloadCacheInstance = null

        databaseProviderInstance = null // DefaultDatabaseProvider 没有显式的 release，它依赖 SQLiteOpenHelper
        httpDataSourceFactoryInstance = null
        cacheDataSourceFactoryInstance = null
        Log.d(TAG, "DownloadUtil components released")
    }
}
