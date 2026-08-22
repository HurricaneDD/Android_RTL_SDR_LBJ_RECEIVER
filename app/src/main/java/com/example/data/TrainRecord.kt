package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "train_records")
data class TrainRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val trainNo: String,
    val direction: String,
    val locoModel: String = "----",
    val locoCode: String = "---",
    val route: String = "----",
    val category: String = "列车",
    val firstSeenTime: Long = System.currentTimeMillis(),
    val lastSeenTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "route_station_kms")
data class RouteStationKmEntity(
    @PrimaryKey
    val routeName: String,
    val stationKm: Double,
    val updatedTimestamp: Long
)
