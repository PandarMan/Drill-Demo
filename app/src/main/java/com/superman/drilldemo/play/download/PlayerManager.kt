package com.superman.drilldemo.play.download

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DefaultDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.io.File

/**
 * 边播放边缓存
 */
@UnstableApi
object PlayerManager { // 或者可以是一个非 object 类，根据你的架构

    private const val TAG = "PlayerManager"
    private const val USER_AGENT_STREAMING = "YourApp/Streaming"

    // --- 配置常量 (用于边播边缓存) ---
    private const val STREAMING_DATABASE_NAME = "media3_streaming_cache.db"
    private const val STREAMING_CACHE_SUBDIR = "online_stream_cache" // 内部缓存子目录
    private const val STREAMING_CACHE_MAX_SIZE_BYTES = 100 * 1024 * 1024L // 100MB

    // --- 单例实例 (用于边播边缓存) ---
    @Volatile
    private var streamingCacheInstance: SimpleCache? = null

    @Volatile
    private var databaseProviderForStreaming: DefaultDatabaseProvider? = null

    @Volatile
    private var httpDataSourceFactoryForStreaming: DefaultHttpDataSource.Factory? = null

    @Volatile
    private var streamingCacheDataSourceFactory: CacheDataSource.Factory? = null

    @Volatile
    private var playerInstance: ExoPlayer? = null


    @Synchronized
    private fun getDatabaseProviderForStreaming(appContext: Context): DefaultDatabaseProvider {
        if (databaseProviderForStreaming == null) {
            databaseProviderForStreaming = DefaultDatabaseProvider(
                Media3DatabaseHelper2(appContext, STREAMING_DATABASE_NAME)
            )
        }
        return databaseProviderForStreaming!!
    }

    @Synchronized
    private fun getStreamingCache(context: Context): SimpleCache? {
        if (streamingCacheInstance == null) {
            val appContext = context.applicationContext
            val cacheDir = File(appContext.cacheDir, STREAMING_CACHE_SUBDIR) // 内部缓存
            if (!cacheDir.exists()) {
                if (!cacheDir.mkdirs()) {
                    Log.e(
                        TAG,
                        "Failed to create STREAMING cache directory: ${cacheDir.absolutePath}"
                    )
                    return null
                }
            }
            Log.d(TAG, "Using internal directory for STREAMING cache: ${cacheDir.absolutePath}")

            val lruEvictor = LeastRecentlyUsedCacheEvictor(STREAMING_CACHE_MAX_SIZE_BYTES)
            streamingCacheInstance = SimpleCache(
                cacheDir,
                lruEvictor,
                getDatabaseProviderForStreaming(appContext)
            )
        }
        return streamingCacheInstance
    }

    @Synchronized
    private fun getHttpDataSourceFactoryForStreaming(appContext: Context): DefaultHttpDataSource.Factory {
        if (httpDataSourceFactoryForStreaming == null) {
            httpDataSourceFactoryForStreaming =
                DefaultHttpDataSource.Factory().setUserAgent(USER_AGENT_STREAMING)
        }
        return httpDataSourceFactoryForStreaming!!
    }

    @Synchronized
    private fun getStreamingCacheDataSourceFactory(appContext: Context): CacheDataSource.Factory {
        if (streamingCacheDataSourceFactory == null) {
            val streamingCache = getStreamingCache(appContext)
            if (streamingCache == null) {
                Log.e(
                    TAG,
                    "Streaming cache is null, cannot create CacheDataSourceFactory for streaming."
                )
                throw IllegalStateException("Streaming cache could not be initialized.")
            }
            streamingCacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(streamingCache)
                .setUpstreamDataSourceFactory(getHttpDataSourceFactoryForStreaming(appContext))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }
        return streamingCacheDataSourceFactory!!
    }

    @Synchronized
    private fun ensureStreamingComponentsInitialized(appContext: Context) {
        getDatabaseProviderForStreaming(appContext)
        getHttpDataSourceFactoryForStreaming(appContext)
        getStreamingCache(appContext)
    }


    @Synchronized
    fun getPlayer(context: Context): ExoPlayer {
        if (playerInstance == null) {
            val appContext = context.applicationContext
            ensureStreamingComponentsInitialized(appContext)

            val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
                .setDataSourceFactory(getStreamingCacheDataSourceFactory(appContext))

            playerInstance = ExoPlayer.Builder(appContext)
                .setMediaSourceFactory(mediaSourceFactory)
                // .setSeekBackIncrementMs(10000) // 示例：自定义快退增量
                // .setSeekForwardIncrementMs(10000) // 示例：自定义快进增量
                .build()
            Log.d(TAG, "ExoPlayer instance created with streaming cache.")
        }
        return playerInstance!!
    }

    @Synchronized
    fun releasePlayer() {
        playerInstance?.release()
        playerInstance = null
        Log.d(TAG, "ExoPlayer instance released.")
    }

    @Synchronized
    fun releaseStreamingCache() { // 应用退出或不再需要流式缓存时调用
        streamingCacheInstance?.release()
        streamingCacheInstance = null
        databaseProviderForStreaming = null
        httpDataSourceFactoryForStreaming = null
        streamingCacheDataSourceFactory = null
        Log.d(TAG, "Streaming cache components released.")
    }

}
