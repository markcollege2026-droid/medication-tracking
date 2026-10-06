package com.campmeds.app.data.dao

import androidx.room.*
import com.campmeds.app.data.entity.Patient
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {
    @Query("SELECT * FROM patients ORDER BY name ASC")
    fun observeAll(): Flow<List<Patient>>

    @Query("SELECT * FROM patients WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun search(query: String): Flow<List<Patient>>

    @Query("SELECT * FROM patients WHERE id = :id")
    suspend fun getById(id: String): Patient?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(patient: Patient)

    @Delete
    suspend fun delete(patient: Patient)
}
