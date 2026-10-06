package com.campmeds.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.util.UUID

@Entity(
    tableName = "dose_logs",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["loggedByUserId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("medicationId"), Index("loggedByUserId"), Index("scheduledFor")]
)
data class DoseLog(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val medicationId: String,
    val scheduledFor: LocalDateTime,     // due time this entry corresponds to
    val status: DoseStatus,
    val loggedAt: LocalDateTime,         // actual timestamp of the action
    val loggedByUserId: String?,         // nullable to survive user deletion (FK SET_NULL)
    val wasOverride: Boolean = false     // true if a LEVEL1_OVERRIDE user bypassed a scan/NDC mismatch
)
