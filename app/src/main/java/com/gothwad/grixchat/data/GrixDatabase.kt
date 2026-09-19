package com.gothwad.grixchat.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [OfflineDraft::class, NotificationItem::class],
    version = 1,
    exportSchema = false
)
abstract class GrixDatabase : RoomDatabase() {
    abstract fun grixDao(): GrixDao

    companion object {
        @Volatile
        private var INSTANCE: GrixDatabase? = null

        fun getDatabase(context: Context): GrixDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GrixDatabase::class.java,
                    "grixchat_database"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
