package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val recipientNumber: String,
    val senderWalletNumber: String = "الافتراضية",
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = "",
    val isDeposit: Boolean = false
)

