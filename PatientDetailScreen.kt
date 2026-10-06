package com.campmeds.app.ui.patientdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.campmeds.app.auth.Session
import com.campmeds.app.data.entity.Role
import com.campmeds.app.repository.CampMedsRepository

/** Screen 3 (spec section 4): list of active medications, "Add medication" button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDetailScreen(
    repository: CampMedsRepository,
    patientId: String,
    onAddMedication: () -> Unit,
    onEditMedication: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onBack: () -> Unit
) {
    var patientName by remember { mutableStateOf("Patient") }
    var patientNotes by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(patientId) {
        repository.getPatient(patientId)?.let {
            patientName = it.name
            patientNotes = it.notes
        }
    }

    val medications by repository.observeMedicationsForPatient(patientId).collectAsState(initial = emptyList())
    val isAdmin = Session.hasAtLeast(Role.ADMIN)
    val today = java.time.LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(patientName) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(onClick = onAddMedication) {
                    Icon(Icons.Default.Add, contentDescription = "Add medication")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            patientNotes?.let {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Notes", style = MaterialTheme.typography.labelLarge)
                        Text(it)
                    }
                }
            }

            TextButton(onClick = onOpenHistory, modifier = Modifier.align(Alignment.End).padding(end = 16.dp)) {
                Text("View dose history →")
            }

            if (medications.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No medications on file.")
                }
            } else {
                LazyColumn {
                    items(medications, key = { it.id }) { med ->
                        val isActive = med.startDate <= today && (med.endDate == null || med.endDate >= today)
                        ListItem(
                            headlineContent = { Text("${med.name} — ${med.strength}") },
                            supportingContent = {
                                Text(
                                    buildString {
                                        append(med.form).append(" · ").append(med.dosageInstructions)
                                        if (!isActive) append(" · INACTIVE")
                                        if (med.isException) append(" · MANUAL ENTRY")
                                    }
                                )
                            },
                            modifier = Modifier.clickable(enabled = isAdmin) { onEditMedication(med.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
