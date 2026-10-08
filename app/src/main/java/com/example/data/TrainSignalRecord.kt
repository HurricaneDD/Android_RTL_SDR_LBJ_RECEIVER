package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一次 LBJ 解码回调对应的一条原始业务记录。
 *
 * 不做内容去重：即使相邻两次信号的所有字段完全相同，也必须分别保存。
 */
@Entity(
    tableName = "train_signal_records",
    indices = [
        Index(value = ["trainRecordId", "timestamp"]),
        Index(value = ["timestamp"])
    ]
)
data class TrainSignalRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val trainRecordId: Long,
    val trainNo: String,
    val direction: String,
    val speed: String,
    val locoModel: String,
    val locoCode: String,
    val route: String,
    val positionKm: String,
    val category: String,
    val longitude: String = "",
    val latitude: String = "",
    val timestamp: Long
)
