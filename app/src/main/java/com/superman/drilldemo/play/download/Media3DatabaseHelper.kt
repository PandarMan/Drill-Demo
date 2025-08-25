package com.superman.drilldemo.play.download

// DatabaseHelper.kt
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// 数据库名称和版本，Media3 的表会创建在这个数据库里
// 你可以复用你应用已有的 SQLiteOpenHelper，或者为 Media3 单独创建一个
private const val DATABASE_NAME = "media3_downloads.db" // 或者你应用的总数据库名
private const val DATABASE_VERSION = 1 // 根据你的应用需求调整

class Media3DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    // Media3 的 DefaultDatabaseProvider 会负责在其获取到 SQLiteDatabase 实例后，
    // 调用内部方法来创建和管理它所需要的表 (例如下载索引表、缓存元数据表)。
    // 因此，在这个 onCreate 和 onUpgrade 中，你通常不需要为 Media3 手动创建表，
    // 除非你有非常特殊的与 Media3 内部表结构交互的需求或者复杂的迁移。
    // 你主要关注你自己应用可能也需要在这个数据库中创建的表。

    override fun onCreate(db: SQLiteDatabase) {
        // 如果你的应用也使用这个数据库，在这里创建你自己的表：
        // db.execSQL("CREATE TABLE my_app_table (...)")

        // Media3 的 DefaultDatabaseProvider 会在需要时通过调用内部的
        // เช่นandroidx.media3.database.VersionTable.setVersion()
        // androidx.media3.exoplayer.offline.DefaultDownloadIndex.createTableV2() (或类似方法)
        // androidx.media3.datasource.cache.CacheFileMetadataIndex.createTable()
        // 来创建它自己的表。
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 如果你的应用也使用这个数据库，在这里处理你自己的表升级：
        // if (oldVersion < 2) { /* upgrade logic for your tables */ }

        // 同样，Media3 的 DefaultDatabaseProvider 会处理它自己表的升级。
    }

    // 你可以添加 onDowngrade, onOpen 等回调如果需要
}
