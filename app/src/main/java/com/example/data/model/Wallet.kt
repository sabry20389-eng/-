package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallets")
data class Wallet(
    @PrimaryKey val phoneNumber: String,
    val label: String,
    val dailyLimit: Double = 60000.0,
    val monthlyLimit: Double = 200000.0,
    val dailyDepositLimit: Double = 60000.0,
    val monthlyDepositLimit: Double = 200000.0,
    val initialBalance: Double = 0.0
)

