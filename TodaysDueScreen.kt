package com.campmeds.app.ui.duetoday

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.campmeds.app.auth.Session
import com.campmeds.app.data.entity.DoseStatus
import com.campmeds.app.data.entity.Medication
import com.campmeds.app.data.entity.Role
import com.campmeds.app.repository.CampMedsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Screen 5 (spec section 4/5): every dose due today across all patients, sorted by time.
 * Tap an item → scan QR (or select manually) → confirm NDC match → mark given/held/refused.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodaysDueScreen(
    repository: CampMedsRepository,
    onOpenPatients: () -> Unit,
    onOpenExport: () -> Unit,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    // BUG FIX (V1 bug 7): the date used to be captured once (remember { LocalDate.now() }), so a screen left
    // open across midnight kept showing yesterday. It is now state that is refreshed on
    //  (1) screen resume / app returning to foreground, (2) the system date/time/timezone-changed
    //  broadcasts, and (3) a timer that fires just after the next local midnight while in the foreground.
    var today by remember { mutableStateOf(LocalDate.now()) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) today = LocalDate.now()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) { today = LocalDate.now() }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(today) {
        val now = LocalDateTime.now()
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
        delay(Duration.between(now, nextMidnight).toMillis() + 500)
        today = LocalDate.now()
    }
    val currentUser by Session.currentUser.collectAsState()
    val isAdmin = Session.hasAtLeast(Role.ADMIN)

    var dueItems by remember { mutableStateOf<List<DueDoseItem>>(emptyList()) }
    var selectedItem by remember { mutableStateOf<DueDoseItem?>(null) }

    val todaysLogs by remember(today) { repository.observeDoseLogsForDay(today) }
        .collectAsState(initial = emptyList())
    val patients by repository.observePatients().collectAsState(initial = emptyList())

    // Rebuild the due list whenever today's logs or the patient/medication set changes.
    LaunchedEffect(todaysLogs, patients, today) {
        val activeMeds = repository.getActiveMedications(today)
        val patientsById = patients.associateBy { it.id }
        dueItems = buildTodaysDueList(today, activeMeds, patientsById, todaysLogs)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Today's Due — ${today.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}") },
                actions = {
                    IconButton(onClick = onOpenPatients) { Icon(Icons.Default.List, contentDescription = "Patients") }
                    if (isAdmin) {
                        IconButton(onClick = onOpenExport) { Icon(Icons.Default.Upload, contentDescription = "Export") }
                    }
                    IconButton(onClick = { Session.logout(); onLogout() }) {
                        Icon(Icons.Default.Logout, contentDescription = "Log out")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            currentUser?.let {
                Text(
                    "Logged in as ${it.name} (${it.role})",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (dueItems.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No doses due today.")
                }
            } else {
                LazyColumn {
                    items(dueItems, key = { "${it.medication.id}-${it.scheduledFor}" }) { item ->
                        val statusColor = when (item.status) {
                            DoseStatus.GIVEN -> MaterialTheme.colorScheme.primary
                            DoseStatus.REFUSED -> MaterialTheme.colorScheme.error
                            DoseStatus.HELD -> MaterialTheme.colorScheme.tertiary
                            DoseStatus.MISSED, null -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        ListItem(
                            headlineContent = { Text("${item.patient.name} — ${item.medication.name}") },
                            supportingContent = {
                                Text("${item.scheduledFor.toLocalTime()} · ${item.medication.dosageInstructions}")
                            },
                            trailingContent = {
                                Text(item.status?.name ?: "DUE", color = statusColor)
                            },
                            modifier = Modifier.clickable { selectedItem = item }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    selectedItem?.let { item ->
        DoseActionSheet(
            item = item,
            onDismiss = { selectedItem = null },
            onRecord = { status, wasOverride ->
                val user = currentUser ?: return@DoseActionSheet
                scope.launch {
                    repository.recordDose(
                        medicationId = item.medication.id,
                        scheduledFor = item.scheduledFor,
                        status = status,
                        loggedByUserId = user.id,
                        wasOverride = wasOverride
                    )
                    selectedItem = null
                }
            },
            repository = repository
        )
    }
}

/**
 * Bottom-sheet dosing flow: scan (or verify manually) → explicitly verify the physical medication →
 * mark outcome.
 *
 * Override rules (spec section 6): a scan that doesn't match the due medication, or an expired bottle,
 * blocks the outcome buttons unless the user is LEVEL1_OVERRIDE or ADMIN. Proceeding that way is
 * recorded on the DoseLog as wasOverride = true.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoseActionSheet(
    item: DueDoseItem,
    repository: CampMedsRepository,
    onDismiss: () -> Unit,
    onRecord: (DoseStatus, wasOverride: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(DoseFlowMode.CONFIRM_SCAN) }
    var scanned by remember { mutableStateOf<Medication?>(null) }
    var scannedUnknown by remember { mutableStateOf(false) }
    var verifiedPhysically by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

    val med = item.medication
    val canOverride = Session.hasAtLeast(Role.LEVEL1_OVERRIDE)
    val mismatch = scannedUnknown || (scanned != null && scanned!!.id != med.id)
    // BUG FIX (V1 bug 12): an expired bottle is never treated as normal.
    val expired = med.bottleExpiration?.isBefore(LocalDate.now()) == true
    val needsOverride = mismatch || expired
    val allowed = !needsOverride || canOverride

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${item.patient.name} — ${med.name}", style = MaterialTheme.typography.titleMedium)
            Text("Due ${item.scheduledFor.toLocalTime()} · ${med.dosageInstructions}")

            if (expired) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        "⚠️ EXPIRED: this bottle expired on ${med.bottleExpiration}. Do not use unless cleared by an authorized override.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            when (mode) {
                DoseFlowMode.CONFIRM_SCAN -> {
                    if (!hasCameraPermission) {
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Grant camera permission to scan")
                        }
                    } else {
                        Box(Modifier.fillMaxWidth().height(280.dp)) {
                            ScannerView(onScanned = { code ->
                                scope.launch {
                                    val found = repository.getMedicationByQr(code)
                                    scanned = found
                                    scannedUnknown = found == null
                                    mode = DoseFlowMode.MARK_OUTCOME
                                }
                            })
                        }
                    }
                    // BUG FIX (V1 bug 10): this used to say "select manually" but selected nothing. It now says
                    // what it really does: skip the scan and verify the due medication by eye.
                    TextButton(onClick = {
                        scanned = null; scannedUnknown = false
                        mode = DoseFlowMode.MARK_OUTCOME
                    }) { Text("Can't scan — verify manually") }
                }

                DoseFlowMode.MARK_OUTCOME -> {
                    when {
                        scannedUnknown -> Text(
                            "⚠️ Scanned code is not recognized. It does not match this dose.",
                            color = MaterialTheme.colorScheme.error
                        )
                        mismatch -> Text(
                            "⚠️ Scanned item is a different medication (${scanned?.name} — ${scanned?.strength}) than the one due for this dose.",
                            color = MaterialTheme.colorScheme.error
                        )
                        scanned != null -> Text("✓ Scan matches this dose.", color = MaterialTheme.colorScheme.primary)
                        else -> Text("Scan skipped — manual verification required.")
                    }
                    if (needsOverride && !canOverride) {
                        Text(
                            "Only a Level 1 override or Admin user can proceed past this warning.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    // Details the provider must compare with the physical medication in hand.
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text("Compare with the physical medication:", style = MaterialTheme.typography.labelLarge)
                            Text("Patient: ${item.patient.name}")
                            Text("Medication: ${med.name}")
                            Text("Strength: ${med.strength}")
                            Text("Form: ${med.form}")
                            Text("Dosage: ${med.dosageInstructions}")
                            med.bottleExpiration?.let { Text("Bottle expiration: $it") }
                        }
                    }

                    // BUG FIX (V1 bug 11): explicit confirmation before any outcome can be recorded.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = verifiedPhysically, onCheckedChange = { verifiedPhysically = it })
                        Text(
                            "I verified the medication name, strength, and form against the physical medication.",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    val enabled = verifiedPhysically && allowed
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = enabled, onClick = { onRecord(DoseStatus.GIVEN, needsOverride) }) { Text("Given") }
                        OutlinedButton(enabled = enabled, onClick = { onRecord(DoseStatus.HELD, needsOverride) }) { Text("Held") }
                        OutlinedButton(enabled = enabled, onClick = { onRecord(DoseStatus.REFUSED, needsOverride) }) { Text("Refused") }
                    }
                }
            }

            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    }
}

private enum class DoseFlowMode { CONFIRM_SCAN, MARK_OUTCOME }
