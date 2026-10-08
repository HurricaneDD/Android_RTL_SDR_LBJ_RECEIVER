package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LbjDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrainRecord(record: TrainRecord): Long

    @Update
    suspend fun updateTrainRecord(record: TrainRecord)

    @Query("UPDATE train_records SET locoModel = :locoModel, locoCode = :locoCode, route = :route, category = :category, lastSeenTime = :lastSeenTime WHERE id = :id")
    suspend fun updateTrainSession(
        id: Long,
        locoModel: String,
        locoCode: String,
        route: String,
        category: String,
        lastSeenTime: Long
    )

    @Query("UPDATE train_records SET lastSeenTime = :lastSeenTime WHERE id = :id")
    suspend fun updateLastSeenTime(id: Long, lastSeenTime: Long)

    @Query("SELECT * FROM train_records WHERE (trainNo = :trainNo OR trainNo = :baseTrainNo OR :trainNo LIKE '%' || trainNo OR trainNo LIKE '%' || :baseTrainNo OR :baseTrainNo LIKE '%' || trainNo) AND lastSeenTime >= :minTime ORDER BY lastSeenTime DESC LIMIT 1")
    suspend fun findRecentTrainSession(trainNo: String, baseTrainNo: String, minTime: Long): TrainRecord?

    @Query("""
        UPDATE train_records 
        SET trainNo = CASE WHEN :trainNo != '----' AND :trainNo != '' THEN :trainNo ELSE trainNo END,
            direction = CASE WHEN :direction != '未知' AND :direction NOT LIKE '未知%' THEN :direction ELSE direction END,
            locoModel = CASE WHEN :locoModel != '----' AND :locoModel != '' THEN :locoModel ELSE locoModel END,
            locoCode = CASE WHEN :locoCode != '---' AND :locoCode != '' THEN :locoCode ELSE locoCode END,
            route = CASE WHEN :route != '----' AND :route != '' THEN :route ELSE route END,
            category = CASE WHEN :category != '等待信号...' AND :category != '' THEN :category ELSE category END,
            lastSeenTime = :lastSeenTime 
        WHERE id = :id
    """)
    suspend fun updateFullTrainRecord(
        id: Long,
        trainNo: String,
        direction: String,
        locoModel: String,
        locoCode: String,
        route: String,
        category: String,
        lastSeenTime: Long
    )

    @Query("SELECT * FROM train_records ORDER BY firstSeenTime DESC LIMIT 200")
    fun getAllTrainRecords(): Flow<List<TrainRecord>>

    @Query("DELETE FROM train_records")
    suspend fun clearAllTrainRecords()

    @Query("DELETE FROM train_records WHERE id = :id")
    suspend fun deleteTrainRecord(id: Long)

    @Insert
    suspend fun insertTrainSignalRecord(record: TrainSignalRecord): Long

    @Query("SELECT * FROM train_signal_records WHERE trainRecordId = :trainRecordId ORDER BY timestamp ASC, id ASC")
    fun getTrainSignalRecords(trainRecordId: Long): Flow<List<TrainSignalRecord>>

    @Query("SELECT * FROM train_signal_records ORDER BY timestamp ASC, id ASC")
    suspend fun getAllTrainSignalRecordsList(): List<TrainSignalRecord>

    @Query("DELETE FROM train_signal_records WHERE trainRecordId = :trainRecordId")
    suspend fun deleteTrainSignalRecords(trainRecordId: Long)

    @Query("DELETE FROM train_signal_records")
    suspend fun clearAllTrainSignalRecords()

    // Route station kilometers
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRouteStationKm(entity: RouteStationKmEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRouteStationKms(entities: List<RouteStationKmEntity>)

    @Query("SELECT * FROM route_station_kms ORDER BY updatedTimestamp DESC")
    fun getAllRouteStationKms(): Flow<List<RouteStationKmEntity>>

    @Query("SELECT * FROM route_station_kms")
    suspend fun getAllRouteStationKmsList(): List<RouteStationKmEntity>

    @Query("DELETE FROM route_station_kms WHERE routeName = :routeName")
    suspend fun deleteRouteStationKm(routeName: String)
}

@Database(
    entities = [TrainRecord::class, TrainSignalRecord::class, RouteStationKmEntity::class],
    version = 4,
    exportSchema = false
)
abstract class LbjDatabase : RoomDatabase() {

    companion object {
        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS train_signal_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        trainRecordId INTEGER NOT NULL,
                        trainNo TEXT NOT NULL,
                        direction TEXT NOT NULL,
                        speed TEXT NOT NULL,
                        locoModel TEXT NOT NULL,
                        locoCode TEXT NOT NULL,
                        route TEXT NOT NULL,
                        positionKm TEXT NOT NULL,
                        category TEXT NOT NULL,
                        longitude TEXT NOT NULL,
                        latitude TEXT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_train_signal_records_trainRecordId_timestamp ON train_signal_records(trainRecordId, timestamp)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_train_signal_records_timestamp ON train_signal_records(timestamp)"
                )
            }
        }

        @Volatile
        private var INSTANCE: LbjDatabase? = null

        fun getDatabase(context: Context): LbjDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LbjDatabase::class.java,
                    "lbj_receiver_db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    abstract fun lbjDao(): LbjDao
}
