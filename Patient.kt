package com.campmeds.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

@Entity(tableName = "patients")
data class Patient(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val dob: LocalDate? = null,
    val notes: String? = null // allergies, etc. — free text
)
