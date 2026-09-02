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

    @Query("SELECT * FROM train_records WHERE (trainNo = :trainNo OR trainNo = :baseTrainNo OR :trainNo LIKE '%' || trainNo) AND lastSeenTime >= :minTime ORDER BY lastSeenTime DESC LIMIT 1")
    suspend fun findRecentTrainSession(trainNo: String, baseTrainNo: String, minTime: Long): TrainRecord?

    @Query("UPDATE train_records SET trainNo = :trainNo, direction = :direction, locoModel = :locoModel, locoCode = :locoCode, route = :route, category = :category, lastSeenTime = :lastSeenTime WHERE id = :id")
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

    // Route station kilometers
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRouteStationKm(entity: RouteStationKmEntity)

    @Query("SELECT * FROM route_station_kms ORDER BY updatedTimestamp DESC")
    fun getAllRouteStationKms(): Flow<List<RouteStationKmEntity>>

    @Query("SELECT * FROM route_station_kms")
    suspend fun getAllRouteStationKmsList(): List<RouteStationKmEntity>

    @Query("DELETE FROM route_station_kms WHERE routeName = :routeName")
    suspend fun deleteRouteStationKm(routeName: String)
}

@Database(
    entities = [TrainRecord::class, RouteStationKmEntity::class],
    version = 3,
    exportSchema = false
)
abstract class LbjDatabase : RoomDatabase() {
    abstract fun lbjDao(): LbjDao

    companion object {
        @Volatile
        private var INSTANCE: LbjDatabase? = null

        fun getDatabase(context: Context): LbjDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LbjDatabase::class.java,
                    "lbj_receiver_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
