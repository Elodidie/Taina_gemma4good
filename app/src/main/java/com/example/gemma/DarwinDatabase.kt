package com.example.gemma

import android.content.Context
import androidx.room.*

@Dao
interface DarwinDao {
    @Insert
    suspend fun insert(record: DarwinRecord)

    @Query("SELECT * FROM darwin_records WHERE status = 'PENDING'")
    suspend fun getPending(): List<DarwinRecord>

    @Query("SELECT * FROM darwin_records ORDER BY createdAt DESC")
    suspend fun getAll(): List<DarwinRecord>

    @Query("UPDATE darwin_records SET status = 'SYNCED' WHERE occurrenceID = :id")
    suspend fun markSynced(id: String)
}

@Database(entities = [DarwinRecord::class], version = 1)
abstract class DarwinDatabase : RoomDatabase() {
    abstract fun darwinDao(): DarwinDao

    companion object {
        @Volatile private var instance: DarwinDatabase? = null

        fun getInstance(context: Context): DarwinDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    DarwinDatabase::class.java,
                    "darwin_db"
                ).build().also { instance = it }
            }
        }
    }
}