package com.campmeds.app.ui.duetoday

import com.campmeds.app.data.entity.DoseLog
import com.campmeds.app.data.entity.DoseStatus
import com.campmeds.app.data.entity.Medication
import com.campmeds.app.data.entity.Patient
import java.time.LocalDate
import java.time.LocalDateTime

/** One row on the "Today's due" screen (spec section 4, screen 5). */
data class DueDoseItem(
    val medication: Medication,
    val patient: Patient,
    val scheduledFor: LocalDateTime,
    val status: DoseStatus? // null = not yet logged
)

/**
 * Builds every dose due today across all patients, sorted by time, cross-referencing each
 * medication's scheduleTimes against any DoseLog entries already recorded for today.
 */
fun buildTodaysDueList(
    day: LocalDate,
    activeMedications: List<Medication>,
    patientsById: Map<String, Patient>,
    todaysLogs: List<DoseLog>
): List<DueDoseItem> {
    val logsByMedAndTime = todaysLogs.associateBy { it.medicationId to it.scheduledFor }

    return activeMedications.flatMap { med ->
        val patient = patientsById[med.patientId] ?: return@flatMap emptyList<DueDoseItem>()
        med.scheduleTimes.map { time ->
            val scheduledFor = day.atTime(time)
            val log = logsByMedAndTime[med.id to scheduledFor]
            DueDoseItem(
                medication = med,
                patient = patient,
                scheduledFor = scheduledFor,
                status = log?.status
            )
        }
    }.sortedBy { it.scheduledFor }
}
