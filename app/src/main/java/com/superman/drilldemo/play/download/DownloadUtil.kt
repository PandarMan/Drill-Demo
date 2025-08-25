package com.superman.drilldemo.play.download

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.DefaultDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import java.io.File
import java.util.concurrent.Executors

// 确保导入你创建的 SQLiteOpenHelper，根据你的包名修改
// import com.yourpackage.Media3DatabaseHelper // 例如：com.example.myapp.Media3DatabaseHelper

/**
 * DownloadUtil 是一个单例对象 (object)，用于集中管理和初始化 Media3 下载功能所需的核心组件。
 * 它确保这些组件在整个应用中只被创建一次，并提供线程安全的访问方式。
 *
 * @UnstableApi 注解表明它使用了 Media3 中可能在未来版本发生变化的 API。
 */
@UnstableApi
object DownloadUtil {

    // --- 属性 (Properties) ---

    /**
     * [DOWNLOAD_CONTENT_DIRECTORY]
     * 类型: String (编译时常量)
     * 作用: 定义了存储下载内容的子目录名称。
     *       这个目录通常位于应用的外部私有存储空间。
     * 可见性: private (仅限 DownloadUtil 文件内部访问)
     */
    private const val DOWNLOAD_CONTENT_DIRECTORY = "downloads"

    /**
     * [TAG]
     * 类型: String (编译时常量)
     * 作用: 用于 Android 日志 (Logcat) 输出的标签，方便调试。
     * 可见性: private
     */
    private const val TAG = "DownloadUtil"

    /**
     * [USER_AGENT]
     * 类型: String (编译时常量)
     * 作用: 在进行 HTTP 网络请求 (如下载媒体) 时，发送给服务器的 User-Agent 字符串。
     *       设置 User-Agent 是一个良好的网络实践。
     * 可见性: private
     */
    private const val USER_AGENT = "ExoPlayerDemoApp"

    /**
     * [downloadManagerInstance]
     * 类型: DownloadManager? (可空的 DownloadManager)
     * 作用: 持有 DownloadManager 的单例实例。DownloadManager 是管理所有下载任务的核心组件。
     *       使用 @Volatile 注解确保在多线程环境中对该变量的修改对其他线程立即可见。
     *       初始值为 null，在首次需要时通过 initialize() 方法创建。
     * 可见性: private
     */
    @Volatile
    private var downloadManagerInstance: DownloadManager? = null

    /**
     * [downloadCacheInstance]
     * 类型: Cache? (可空的 Cache)
     * 作用: 持有 Cache (通常是 SimpleCache) 的单例实例。Cache 负责将下载的媒体数据实际存储到设备磁盘。
     *       使用 @Volatile 保证多线程可见性。
     *       初始值为 null，在首次需要时通过 initialize() 方法创建。
     * 可见性: private
     */
    @Volatile
    private var downloadCacheInstance: Cache? = null

    // --- 函数 (Functions) ---

    /**
     * [getDownloadManager]
     * 作用: 公共访问方法，用于获取 DownloadManager 的单例实例。
     *       如果实例尚未创建，则会调用 initialize() 方法进行初始化。
     *       使用 @Synchronized 注解确保此方法是线程安全的，防止 initialize() 被并发调用多次。
     * 参数:
     *   - context: Context - 通常传入 applicationContext，以避免内存泄漏并确保组件生命周期与应用一致。
     * 返回: DownloadManager - 初始化后的 DownloadManager 实例。
     * 可见性: public
     */
    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        if (downloadManagerInstance == null) {
            initialize(context.applicationContext) // 使用 applicationContext 进行初始化
        }
        return downloadManagerInstance!! // '!!' 表示我们确信此时实例已非空
    }

    /**
     * [getCache]
     * 作用: 公共访问方法，用于获取 Cache 的单例实例。
     *       如果实例尚未创建，则会调用 initialize() 方法进行初始化。
     *       使用 @Synchronized 注解确保线程安全。
     * 参数:
     *   - context: Context - 通常传入 applicationContext。
     * 返回: Cache - 初始化后的 Cache 实例。
     * 可见性: public
     */
    @Synchronized
    fun getCache(context: Context): Cache {
        if (downloadCacheInstance == null) {
            initialize(context.applicationContext)
        }
        return downloadCacheInstance!!
    }

    /**
     * [initialize]
     * 作用: 核心的私有初始化方法，负责创建和配置所有 Media3 下载相关的核心组件。
     *       这个方法被设计为只执行一次。
     *       使用 @Synchronized 注解确保初始化过程的原子性和线程安全性。
     * 参数:
     *   - appContext: Context - 必须是 applicationContext，以保证组件的正确生命周期。
     * 返回: Unit (无返回值)
     * 可见性: private
     */
    @Synchronized
    private fun initialize(appContext: Context) {
        // 防止重复初始化：如果核心实例已存在，则直接返回。
        if (downloadManagerInstance != null && downloadCacheInstance != null) {
            return
        }

        // 步骤 1: 初始化 SQLiteOpenHelper (例如 Media3DatabaseHelper)
        // 这是 DefaultDatabaseProvider 构造函数所必需的，用于管理底层的 SQLite 数据库。
        val media3DatabaseHelper = Media3DatabaseHelper(appContext)


        // 步骤 2: 初始化 DatabaseProvider
        // DatabaseProvider 接口的实现 (DefaultDatabaseProvider)，使用上一步的 SQLiteOpenHelper
        // 来与数据库交互，用于存储下载索引和缓存元数据。
        val databaseProvider: DatabaseProvider = DefaultDatabaseProvider(media3DatabaseHelper)

        // 步骤 3: 初始化 Cache (SimpleCache)
        // - downloadContentDirectory: 缓存文件在磁盘上的存储位置。
        // - NoOpCacheEvictor: 一个不做任何缓存淘汰的简单策略。对于下载内容，
        //   我们通常希望保留它们直到用户手动删除。
        // - databaseProvider: 用于存储 SimpleCache 的元数据。
        val downloadContentDirectory = File(appContext.getExternalFilesDir(null), DOWNLOAD_CONTENT_DIRECTORY)
        downloadCacheInstance = SimpleCache(
            downloadContentDirectory,
            NoOpCacheEvictor(), // 对于下载，通常不希望自动清除缓存内容
            databaseProvider
        )

        // 步骤 4: 创建用于网络数据源的工厂 (HttpDataSource.Factory)
        // DefaultHttpDataSource.Factory 用于创建从 HTTP/HTTPS URL 获取数据的 DataSource 实例。
        // 设置 User-Agent 是一个良好的网络实践。
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)

        // 步骤 5: 创建支持缓存的数据源工厂 (CacheDataSource.Factory)
        // CacheDataSource.Factory 是一个关键的组合工厂。它创建的 DataSource 实例会:
        // - 在下载时: 通过 httpDataSourceFactory 从网络读取，并将数据写入到 downloadCacheInstance。
        // - 在播放时: 优先尝试从 downloadCacheInstance 读取。如果缓存未命中，则通过 httpDataSourceFactory
        //   从网络读取，并且通常也会将新读取的数据写入缓存 (实现边播边存)。
        // - setUpstreamDataSourceFactory: 设置上游数据源工厂 (用于网络获取)。
        // - setCache: 关联到我们创建的缓存实例。
        // - setFlags: 可选的标志，例如 CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR 表示当从缓存读取发生错误时忽略缓存，直接从网络读取。
        val dataSourceFactory: DataSource.Factory = CacheDataSource.Factory()
            .setCache(downloadCacheInstance!!)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)


        // 步骤 6: 创建 DownloadManager
        // DownloadManager 是管理所有下载任务 (添加、移除、查询状态等) 的核心。
        // - appContext: 应用上下文。
        // - databaseProvider: 用于持久化存储下载任务的索引 (状态、进度等)。
        // - downloadCacheInstance: 下载内容的目标缓存。
        // - dataSourceFactory: 用于实际执行下载操作的数据源。
        // - Executors.newFixedThreadPool(3): 用于执行下载任务的线程池 (这里设置为3个线程)。
        downloadManagerInstance = DownloadManager(
            appContext,
            databaseProvider,
            downloadCacheInstance!!,
            dataSourceFactory,
            Executors.newFixedThreadPool(3) // 根据需求调整线程池大小
        ).apply {
            // 对 DownloadManager 进行额外配置，例如设置最大并行下载数。
            maxParallelDownloads = 2 // 例如，同时最多下载2个文件
        }
    }

    /**
     * [getDownloadNotificationHelper]
     * 作用: 一个辅助工厂方法，用于获取 DownloadNotificationHelper 的实例。
     *       DownloadNotificationHelper 是 Media3 提供的用于构建和管理下载服务前台通知的类。
     * 参数:
     *   - context: Context - 用于创建 DownloadNotificationHelper。
     *   - channelId: String - 通知渠道的 ID。这个 ID 必须与在 DownloadService 中
     *                      定义并用于创建通知渠道的 ID 一致。
     * 返回: DownloadNotificationHelper - DownloadNotificationHelper 的实例。
     * 可见性: public
     */
    fun getDownloadNotificationHelper(context: Context, channelId: String): DownloadNotificationHelper {
        return DownloadNotificationHelper(context, channelId)
    }
}






