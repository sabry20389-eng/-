package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "limit_settings")
data class LimitSettings(
    @PrimaryKey val id: Int = 1,
    val dailyLimit: Double = 60000.0,
    val monthlyLimit: Double = 200000.0,
    val dailyDepositLimit: Double = 60000.0,
    val monthlyDepositLimit: Double = 200000.0,
    val warningPercentage: Float = 0.8f, // 80% threshold
    val isPinLockEnabled: Boolean = false,
    val pinHash: String = "",
    val preventRooted: Boolean = false,
    val preventDebugger: Boolean = false
)
