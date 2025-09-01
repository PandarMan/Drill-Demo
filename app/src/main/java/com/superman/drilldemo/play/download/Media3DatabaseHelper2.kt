package com.superman.drilldemo.play.download


import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

// 默认版本号，每个数据库实例将使用此版本或构造函数中指定的版本
private const val DEFAULT_DATABASE_VERSION = 1

class Media3DatabaseHelper2(
    context: Context,
    databaseName: String, // 数据库文件名作为参数
    version: Int = DEFAULT_DATABASE_VERSION // 可选的版本参数
) : SQLiteOpenHelper(context.applicationContext, databaseName, null, version) {

    /**
     * Media3 的 DefaultDatabaseProvider 会负责在其获取到 SQLiteDatabase 实例后，
     * 调用内部方法来创建和管理它所需要的表 (例如下载索引表、缓存元数据表)。
     * 因此，在这个 onCreate 中，你通常不需要为 Media3 手动创建表。
     * 你主要关注你自己应用可能也需要在这个数据库中创建的表（如果此 Helper 也被用于应用的其他数据）。
     */
    override fun onCreate(db: SQLiteDatabase) {
        Log.d("Media3DatabaseHelper", "onCreate called for database: ${db.path}")
        // 如果你的应用也使用这个数据库，并且表名与 Media3 的不冲突，可以在这里创建你自己的表。
        // 例如: db.execSQL("CREATE TABLE my_custom_app_data (...)")
        // 对于纯粹由 Media3 的 SimpleCache 和 DownloadManager 使用的数据库，此方法通常为空。
    }

    /**
     * Media3 的 DefaultDatabaseProvider 会处理它自己表的升级。
     * 如果你有在此数据库中创建的自定义表，你需要在这里处理它们的升级逻辑。
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.d("Media3DatabaseHelper", "onUpgrade called for database: ${db.path} from $oldVersion to $newVersion")
        // 如果你有自定义表:
        // if (oldVersion < YOUR_NEW_VERSION_FOR_CUSTOM_TABLE) {
        //     db.execSQL("ALTER TABLE my_custom_app_data ADD COLUMN new_column TEXT")
        // }
    }
}
