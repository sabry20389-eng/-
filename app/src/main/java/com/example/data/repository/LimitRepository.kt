package com.example.data.repository

import com.example.data.dao.LimitSettingsDao
import com.example.data.dao.TransactionDao
import com.example.data.dao.WalletDao
import com.example.data.model.LimitSettings
import com.example.data.model.Transaction
import com.example.data.model.Wallet
import kotlinx.coroutines.flow.Flow

class LimitRepository(
    private val transactionDao: TransactionDao,
    private val limitSettingsDao: LimitSettingsDao,
    private val walletDao: WalletDao
) {
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()
    val settings: Flow<LimitSettings?> = limitSettingsDao.getSettings()
    val allWallets: Flow<List<Wallet>> = walletDao.getAllWallets()

    suspend fun insertTransaction(transaction: Transaction) {
        transactionDao.insertTransaction(transaction)
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.deleteTransaction(transaction)
    }

    suspend fun clearAllTransactions() {
        transactionDao.clearAllTransactions()
    }

    suspend fun updateSettings(settings: LimitSettings) {
        limitSettingsDao.insertOrUpdateSettings(settings)
    }

    fun getTransactionsSince(timestamp: Long): Flow<List<Transaction>> {
        return transactionDao.getTransactionsSince(timestamp)
    }

    suspend fun insertWallet(wallet: Wallet) {
        walletDao.insertWallet(wallet)
    }

    suspend fun deleteWallet(wallet: Wallet) {
        walletDao.deleteWallet(wallet)
    }

    suspend fun clearAllWallets() {
        walletDao.clearAllWallets()
    }
}
