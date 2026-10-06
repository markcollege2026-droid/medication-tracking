package com.campmeds.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Entity(
    tableName = "medications",
    foreignKeys = [
        ForeignKey(
            entity = Patient::class,
            parentColumns = ["id"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("patientId"), Index("qrCode", unique = true)]
)
data class Medication(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patientId: String,

    // NDC lookup fields (section 3)
    val ndc: String,                 // 10 or 11-digit NDC, as entered
    val name: String,                // from openFDA lookup (or manual, if isException)
    val strength: String,            // from openFDA lookup
    val form: String,                // tablet/liquid/etc., from openFDA lookup

    val dosageInstructions: String,  // free text, e.g. "1 tablet, twice daily"
    val scheduleTimes: List<LocalTime>, // e.g. [08:00, 20:00] — stored via Converters
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val bottleExpiration: LocalDate? = null,
    val pillCountEntered: Int? = null,  // manual, not auto-tracked

    val isException: Boolean = false,   // true if manual override used instead of a clean NDC match
    val qrCode: String = UUID.randomUUID().toString() // printed/attached to the bag or bottle
)
