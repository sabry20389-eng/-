package com.example.data.dao

import androidx.room.*
import com.example.data.model.LimitSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface LimitSettingsDao {
    @Query("SELECT * FROM limit_settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<LimitSettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: LimitSettings)
}
