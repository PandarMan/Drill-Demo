package com.superman.drilldemo.play.download.list1



import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DefaultDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@UnstableApi
object PlayerManager { // 如果你希望它可以被实例化，可以移除 object 并将其改为 class

    private const val TAG = "PlayerManager"
    private const val USER_AGENT_STREAMING = "SongDownloadApp/Streaming" // User agent for streaming requests

    // --- 配置常量 (专门用于边播边缓存/在线流) ---
    private const val STREAMING_DATABASE_NAME = "app_streaming_cache.db"    // 独立的数据库文件名
    private const val STREAMING_CACHE_SUBDIR = "online_audio_stream_cache" // 内部缓存子目录
    private const val STREAMING_CACHE_MAX_SIZE_BYTES = 100 * 1024 * 1024L // 例如: 100MB

    // --- 单例实例 (用于边播边缓存) ---
    @Volatile
    private var streamingCacheInstance: SimpleCache? = null

    @Volatile
    private var databaseProviderForStreaming: DefaultDatabaseProvider? = null

    @Volatile
    private var httpDataSourceFactoryForStreaming: DefaultHttpDataSource.Factory? = null

    @Volatile
    private var cacheDataSourceFactoryForStreaming: CacheDataSource.Factory? = null
    // ExoPlayer 实例不再由此类管理

    /**
     * 获取或创建用于流式播放缓存的 DatabaseProvider。
     * 使用与离线下载不同的数据库文件。
     */
    @Synchronized
    private fun getDbProviderForStreaming(appContext: Context): DefaultDatabaseProvider {
        if (databaseProviderForStreaming == null) {
            Log.d(TAG, "Initializing DatabaseProvider for streaming with DB: $STREAMING_DATABASE_NAME")
            databaseProviderForStreaming = DefaultDatabaseProvider(
                Media3DatabaseHelper(appContext, STREAMING_DATABASE_NAME) // 传入特定数据库名
            )
        }
        return databaseProviderForStreaming!!
    }

    /**
     * 获取或创建用于流式播放的 SimpleCache 实例。
     * - 使用应用的内部缓存目录。
     * - 使用 LRU (LeastRecentlyUsedCacheEvictor) 淘汰策略。
     */
    @Synchronized
    private fun getStreamingCache(context: Context): SimpleCache? {
        if (streamingCacheInstance == null) {
            val appContext = context.applicationContext
            val cacheDir = File(appContext.cacheDir, STREAMING_CACHE_SUBDIR)
            if (!cacheDir.exists()) {
                if (!cacheDir.mkdirs()) {
                    Log.e(TAG, "Failed to create STREAMING cache directory: ${cacheDir.absolutePath}")
                    return null // 目录创建失败，无法初始化缓存
                }
            }
            Log.d(TAG, "Using internal directory for STREAMING cache: ${cacheDir.absolutePath}")

            val lruEvictor = LeastRecentlyUsedCacheEvictor(STREAMING_CACHE_MAX_SIZE_BYTES)
            streamingCacheInstance = SimpleCache(
                cacheDir,
                lruEvictor,
                getDbProviderForStreaming(appContext) // 使用流式缓存专用的 DatabaseProvider
            )
            Log.i(TAG, "SimpleCache for streaming initialized. Max size: ${STREAMING_CACHE_MAX_SIZE_BYTES / (1024 * 1024)} MB")
        }
        return streamingCacheInstance
    }

    /**
     * 获取或创建用于流式播放的 HttpDataSource.Factory。
     */
    @Synchronized
    private fun getHttpDataSourceFactoryForStreaming(appContext: Context): DefaultHttpDataSource.Factory {
        if (httpDataSourceFactoryForStreaming == null) {
            Log.d(TAG, "Initializing HttpDataSource.Factory for streaming.")
            httpDataSourceFactoryForStreaming = DefaultHttpDataSource.Factory()
                .setUserAgent(USER_AGENT_STREAMING)
            // 你可以在这里添加其他配置，例如 setConnectTimeoutMillis, setReadTimeoutMillis
            // .setTransferListener(...) // 如果你需要监听网络传输
        }
        return httpDataSourceFactoryForStreaming!!
    }

    /**
     * 公共方法，获取或创建配置好的 CacheDataSource.Factory 实例，专门用于在线流式播放。
     * PlaybackService 将调用此方法来获取其 ExoPlayer 所需的数据源工厂。
     *
     * @param appContext Application context.
     * @return 配置好的 CacheDataSource.Factory。
     * @throws IllegalStateException 如果其依赖的 SimpleCache 无法初始化。
     */
    @Synchronized
    fun getStreamingCacheDataSourceFactory(appContext: Context): CacheDataSource.Factory {
        if (cacheDataSourceFactoryForStreaming == null) {
            Log.d(TAG, "Initializing CacheDataSource.Factory for streaming.")
            // 1. 获取 (或创建) SimpleCache 实例
            val sCache = getStreamingCache(appContext)
                ?: throw IllegalStateException("Streaming SimpleCache could not be initialized, cannot create CacheDataSource.Factory.")

            // 2. 获取 (或创建) 上游的 HttpDataSource.Factory
            val upstreamFactory = getHttpDataSourceFactoryForStreaming(appContext)

            // 3. 创建并配置 CacheDataSource.Factory
            cacheDataSourceFactoryForStreaming = CacheDataSource.Factory()
                .setCache(sCache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR) // 当缓存读取发生错误时，直接从上游读取
            // .setEventListener(...) // 如果你需要监听缓存事件
            Log.i(TAG, "CacheDataSource.Factory for streaming initialized.")
        }
        return cacheDataSourceFactoryForStreaming!!
    }

    /**
     * 释放与流式播放缓存相关的资源。
     * 这个方法应该在应用退出或不再需要流式缓存功能时被调用。
     * 注意：ExoPlayer 实例本身由 PlaybackService 管理和释放。
     */
    @Synchronized
    fun releaseStreamingResources() {
        Log.d(TAG, "Releasing streaming resources in PlayerManager...")
        // 释放 SimpleCache (这将关闭数据库等)
        streamingCacheInstance?.release()
        streamingCacheInstance = null

        // 清理其他单例引用，以便它们可以在下次需要时重新初始化
        databaseProviderForStreaming = null
        httpDataSourceFactoryForStreaming = null
        cacheDataSourceFactoryForStreaming = null
        Log.i(TAG, "Streaming resources in PlayerManager released.")
    }

    // 可选: 一个辅助方法确保所有流式组件都被初始化（如果需要在获取工厂前一次性确保）
    // @Synchronized
    // fun ensureStreamingComponentsInitialized(appContext: Context) {
    //     getDbProviderForStreaming(appContext)
    //     getHttpDataSourceFactoryForStreaming(appContext)
    //     getStreamingCache(appContext)
    //     // CacheDataSourceFactory 会在其 getter 中被初始化
    //     Log.d(TAG, "Ensured all streaming components are initialized on demand.")
    // }
}


