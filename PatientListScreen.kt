package com.campmeds.app.ui.patientlist

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
import com.campmeds.app.data.entity.Patient
import com.campmeds.app.data.entity.Role
import com.campmeds.app.repository.CampMedsRepository
import kotlinx.coroutines.launch

/** Screen 2 (spec section 4): search/filter, tap to open a patient. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientListScreen(
    repository: CampMedsRepository,
    onOpenPatient: (String) -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val patients by remember(query) {
        if (query.isBlank()) repository.observePatients() else repository.searchPatients(query)
    }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    val isAdmin = Session.hasAtLeast(Role.ADMIN)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Patients") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add patient")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search patients") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )

            if (patients.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No patients found.")
                }
            } else {
                LazyColumn {
                    items(patients, key = { it.id }) { patient ->
                        ListItem(
                            headlineContent = { Text(patient.name) },
                            supportingContent = { patient.dob?.let { Text("DOB: $it") } },
                            modifier = Modifier.clickable { onOpenPatient(patient.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddPatientDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, dob, notes ->
                showAddDialog = false
                scope.launch { repository.savePatient(Patient(name = name, dob = dob, notes = notes)) }
            }
        )
    }
}

@Composable
private fun AddPatientDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, dob: java.time.LocalDate?, notes: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add patient") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes / allergies (optional)") })
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), null, notes.ifBlank { null }) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

