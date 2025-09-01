package com.superman.drilldemo.play.download.list1

// (这个文件与前一个回答中的 Media3DatabaseHelper.kt 相同)
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

private const val DEFAULT_DATABASE_VERSION = 1

class Media3DatabaseHelper(
    context: Context,
    databaseName: String,
    version: Int = DEFAULT_DATABASE_VERSION
) : SQLiteOpenHelper(context.applicationContext, databaseName, null, version) {
    override fun onCreate(db: SQLiteDatabase) {
        Log.d("Media3DatabaseHelper", "onCreate called for database: ${db.path}")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.d("Media3DatabaseHelper", "onUpgrade for ${db.path} from $oldVersion to $newVersion")
    }
}