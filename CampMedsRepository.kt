package com.campmeds.app.repository

import com.campmeds.app.auth.PinHasher
import com.campmeds.app.data.AppDatabase
import com.campmeds.app.data.entity.*
import com.campmeds.app.network.NdcApiClient
import com.campmeds.app.network.NdcLookupResult
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class CampMedsRepository(
    private val db: AppDatabase,
    private val ndcApiClient: NdcApiClient = NdcApiClient()
) {
    // ---------------- Auth ----------------

    suspend fun login(pin: String): User? {
        val users = db.userDao().getAll()
        return users.firstOrNull { PinHasher.verify(pin, it.pinSalt, it.pinHash) }
    }

    suspend fun createUser(name: String, role: Role, pin: String): User {
        val salt = PinHasher.newSalt()
        val user = User(name = name, role = role, pinHash = PinHasher.hash(pin, salt), pinSalt = salt)
        db.userDao().upsert(user)
        return user
    }

    fun observeUsers(): Flow<List<User>> = db.userDao().observeAll()

    // ---------------- Patients ----------------

    fun observePatients(): Flow<List<Patient>> = db.patientDao().observeAll()

    fun searchPatients(query: String): Flow<List<Patient>> = db.patientDao().search(query)

    suspend fun getPatient(id: String): Patient? = db.patientDao().getById(id)

    suspend fun savePatient(patient: Patient) = db.patientDao().upsert(patient)

    // ---------------- Medications ----------------

    fun observeMedicationsForPatient(patientId: String): Flow<List<Medication>> =
        db.medicationDao().observeForPatient(patientId)

    suspend fun getMedication(id: String): Medication? = db.medicationDao().getById(id)

    suspend fun getMedicationByQr(qrCode: String): Medication? = db.medicationDao().getByQrCode(qrCode)

    suspend fun saveMedication(medication: Medication) = db.medicationDao().upsert(medication)

    /**
     * Looks up an NDC via openFDA. Caches nothing itself — the caller (Add/Edit Medication
     * screen) persists the confirmed result onto the Medication entity, which is what makes
     * every subsequent read fully offline (spec section 7).
     */
    suspend fun lookupNdc(ndc: String): NdcLookupResult {
        val cached = db.medicationDao().getCachedByNdc(ndc)
        if (cached != null) {
            return NdcLookupResult.Found(cached.ndc, cached.name, cached.strength, cached.form)
        }
        return ndcApiClient.lookup(ndc)
    }

    /** All medications active (by date range) for a given day, across all patients. */
    suspend fun getActiveMedications(day: LocalDate): List<Medication> =
        db.medicationDao().getActiveOn(day.toString())

    // ---------------- Dose logs ----------------

    fun observeDoseLogsForDay(day: LocalDate): Flow<List<DoseLog>> {
        val start = day.atStartOfDay()
        val end = day.atTime(LocalTime.MAX)
        return db.doseLogDao().observeForDay(start.toString(), end.toString())
    }

    fun observeHistoryForPatient(patientId: String): Flow<List<DoseLog>> =
        db.doseLogDao().observeHistoryForPatient(patientId)

    suspend fun getHistoryForPatientOnce(patientId: String): List<DoseLog> =
        db.doseLogDao().getHistoryForPatientOnce(patientId)

    /**
     * Records (or overwrites) a dose outcome for a specific scheduled time.
     * Per spec section 8, edits simply overwrite — no correction/edit trail in this version.
     */
    suspend fun recordDose(
        medicationId: String,
        scheduledFor: LocalDateTime,
        status: DoseStatus,
        loggedByUserId: String,
        wasOverride: Boolean = false
    ) {
        val existing = db.doseLogDao().findExisting(medicationId, scheduledFor.toString())
        val entry = (existing ?: DoseLog(
            medicationId = medicationId,
            scheduledFor = scheduledFor,
            status = status,
            loggedAt = LocalDateTime.now(),
            loggedByUserId = loggedByUserId,
            wasOverride = wasOverride
        )).copy(
            status = status,
            loggedAt = LocalDateTime.now(),
            loggedByUserId = loggedByUserId,
            wasOverride = wasOverride
        )
        db.doseLogDao().upsert(entry)
    }

    companion object {
        @Volatile private var instance: CampMedsRepository? = null

        fun getInstance(db: AppDatabase): CampMedsRepository =
            instance ?: synchronized(this) {
                instance ?: CampMedsRepository(db).also { instance = it }
            }
    }
}
