package com.campmeds.app.ui.export

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.campmeds.app.auth.Session
import com.campmeds.app.data.entity.Role
import com.campmeds.app.export.XlsxExporter
import com.campmeds.app.repository.CampMedsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Screen 7 (spec section 4): generates one XLSX workbook, one worksheet per patient. Admin only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    repository: CampMedsRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isAdmin = Session.hasAtLeast(Role.ADMIN)

    var status by remember { mutableStateOf<String?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val fileName = remember {
        "campmeds_export_${LocalDate.now().format(DateTimeFormatter.ISO_DATE)}.xlsx"
    }

    val createDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri: Uri? ->
        if (uri == null) {
            status = "Export cancelled."
            return@rememberLauncherForActivityResult
        }
        status = "Exporting..."
        scope.launch {
            val snapshot = repository.observePatients().first() // one-shot read of current patient set
            val users = repository.observeUsers().first().associateBy { it.id }

            val bundles = snapshot.map { patient ->
                val meds = repository.observeMedicationsForPatient(patient.id).first()
                val medsById = meds.associateBy { it.id }
                val logs = repository.getHistoryForPatientOnce(patient.id)
                XlsxExporter.ExportBundle(
                    patient = patient,
                    medicationsById = medsById,
                    doseLogs = logs,
                    usersById = users
                )
            }

            runCatching {
                XlsxExporter().export(context, uri, bundles)
            }.onSuccess {
                status = "Export complete: saved to the selected location."
            }.onFailure {
                status = "Export failed: ${it.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!isAdmin) {
                Text("Only Admin users can export data.")
            } else {
                Text("Exports every patient's dose log to one XLSX workbook — one worksheet per patient.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = { createDocLauncher.launch(fileName) }) {
                    Text("Choose location & export")
                }
                status?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it)
                }
            }
        }
    }
}
