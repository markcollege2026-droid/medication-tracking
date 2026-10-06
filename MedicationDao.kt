package com.campmeds.app.data.dao

import androidx.room.*
import com.campmeds.app.data.entity.Medication
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications WHERE patientId = :patientId ORDER BY name ASC")
    fun observeForPatient(patientId: String): Flow<List<Medication>>

    @Query(
        """
        SELECT * FROM medications
        WHERE startDate <= :today AND (endDate IS NULL OR endDate >= :today)
        """
    )
    suspend fun getActiveOn(today: String): List<Medication>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getById(id: String): Medication?

    @Query("SELECT * FROM medications WHERE qrCode = :qrCode LIMIT 1")
    suspend fun getByQrCode(qrCode: String): Medication?

    // BUG FIX (V1 bug 9): only medications that came from a real openFDA lookup may act as the local
    // NDC cache. Manually entered (isException = 1) records must never be replayed later as if they
    // were FDA data.
    @Query("SELECT * FROM medications WHERE ndc = :ndc AND isException = 0 LIMIT 1")
    suspend fun getCachedByNdc(ndc: String): Medication?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(medication: Medication)

    @Delete
    suspend fun delete(medication: Medication)
}
