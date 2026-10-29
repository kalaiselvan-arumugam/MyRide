package com.kalaiselvan.ride.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.kalaiselvan.ride.maintenance.MaintenanceStatus
import com.kalaiselvan.ride.maintenance.MaintenanceStore
import com.kalaiselvan.ride.maintenance.ServiceRecord
import com.kalaiselvan.ride.telemetry.TelemetryState
import com.kalaiselvan.ride.theme.*
import com.kalaiselvan.ride.ui.components.CustomDatePickerDialog
import com.kalaiselvan.ride.ui.components.FlatIcon
import com.kalaiselvan.ride.ui.components.FlatIconType
import com.kalaiselvan.ride.ui.theme.neumorphicInner
import com.kalaiselvan.ride.ui.theme.neumorphicOuter
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MaintenanceScreen(
    telemetry: TelemetryState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember { MaintenanceStore.getInstance(context) }
    val records by store.records.collectAsState()

    val currentOdo = telemetry.currentOdo
    val status = remember(records, currentOdo) { store.getStatus(currentOdo) }

    var editingRecord by remember { mutableStateOf<ServiceRecord?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var viewingReceiptPath by remember { mutableStateOf<String?>(null) }
    var recordToDelete by remember { mutableStateOf<ServiceRecord?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. Next Service Status Banner ──────────────────────────────
            item {
                NextServiceStatusCard(status = status, currentOdo = currentOdo)
            }

            // ── 2. Service History Section Header ───────────────────────────
            item {
                Text(
                    text = "SERVICE HISTORY (${records.size})",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }

            // ── 3. Service History Cards ────────────────────────────────────
            if (records.isEmpty()) {
                item {
                    EmptyHistoryPlaceholder(onAddClick = { isCreatingNew = true })
                }
            } else {
                items(records, key = { it.id }) { record ->
                    ServiceRecordCard(
                        record = record,
                        onViewReceipt = { viewingReceiptPath = it },
                        onEdit = { editingRecord = record },
                        onDelete = { recordToDelete = record }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }

        // ── Floating Circular Button to Log Service Record ───────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(AccentGreen)
                .clickable { isCreatingNew = true },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = Color.Black,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(y = (-1).dp)
            )
        }

        // ── Dialog: Add / Edit Service Record ───────────────────────────────
        if (isCreatingNew || editingRecord != null) {
            ServiceRecordFormDialog(
                initialRecord = editingRecord,
                existingRecords = records,
                currentBikeOdo = currentOdo,
                onDismiss = {
                    isCreatingNew = false
                    editingRecord = null
                },
                onSave = { record ->
                    if (editingRecord != null) {
                        store.updateRecord(record)
                        Toast.makeText(context, "Service record updated", Toast.LENGTH_SHORT).show()
                    } else {
                        store.addRecord(record)
                        Toast.makeText(context, "Service record logged", Toast.LENGTH_SHORT).show()
                    }
                    isCreatingNew = false
                    editingRecord = null
                }
            )
        }

        // ── Dialog: Full Screen Receipt Viewer ──────────────────────────────
        if (viewingReceiptPath != null) {
            FullScreenReceiptDialog(
                imagePath = viewingReceiptPath!!,
                onDismiss = { viewingReceiptPath = null }
            )
        }

        // ── Dialog: Confirm Delete ──────────────────────────────────────────
        if (recordToDelete != null) {
            AlertDialog(
                onDismissRequest = { recordToDelete = null },
                containerColor = BgCard,
                title = { Text("Delete Service Record?", color = TextMain, fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to delete this maintenance entry? Any attached receipt photo will also be removed.", color = TextMuted) },
                confirmButton = {
                    TextButton(onClick = {
                        recordToDelete?.let { store.deleteRecord(it.id) }
                        recordToDelete = null
                        Toast.makeText(context, "Record deleted", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Delete", color = RpmRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { recordToDelete = null }) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            )
        }
    }
}

@Composable
private fun NextServiceStatusCard(
    status: MaintenanceStatus,
    currentOdo: Double
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphicOuter(cornerRadius = 20.dp, blurRadius = 10.dp)
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NEXT SERVICE STATUS",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                if (status.hasActiveReminder) {
                    Text(
                        text = "Service scheduled",
                        color = AccentGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = status.statusText,
                color = TextMain,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (status.targetOdoKm != null && status.targetOdoKm > 0) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TARGET ODO", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${String.format(Locale.US, "%.0f", status.targetOdoKm)} KM",
                            color = TextMain,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (status.targetDateFormatted != null) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TARGET DATE", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = status.targetDateFormatted,
                            color = TextMain,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (currentOdo > 0) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("CURRENT ODO", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${String.format(Locale.US, "%.0f", currentOdo)} KM",
                            color = AccentGreen,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceRecordCard(
    record: ServiceRecord,
    onViewReceipt: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val receiptBitmap = remember(record.receiptImagePath) {
        loadReceiptBitmap(record.receiptImagePath)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphicOuter(cornerRadius = 18.dp, blurRadius = 8.dp)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row: Date & Cost Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FlatIcon(FlatIconType.CALENDAR, tint = TextMuted, size = 13.dp)
                        Text(
                            text = record.formattedServiceDate,
                            color = TextMain,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        text = "•  ${String.format(Locale.US, "%.0f", record.odoKm)} KM",
                        color = AccentGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1F242C))
                        .border(1.dp, Color(0xFF2E3540), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${record.currency}${String.format(Locale.US, "%.0f", record.totalCost)}",
                        color = TextMain,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Receipt Attachment Thumbnail & Targets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Receipt / Document Preview Button
                if (record.receiptImagePath != null && File(record.receiptImagePath).exists()) {
                    val isPdf = record.receiptImagePath.endsWith(".pdf", ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF121518))
                            .border(1.dp, Color(0xFF2C3440), RoundedCornerShape(10.dp))
                            .clickable { onViewReceipt(record.receiptImagePath) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (receiptBitmap != null) {
                            Image(
                                bitmap = receiptBitmap,
                                contentDescription = "Receipt Thumbnail",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isPdf) Color(0xFF3B1E22) else Color(0xFF1E242C)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isPdf) "PDF" else "DOC",
                                    color = if (isPdf) RpmRed else BluetoothBlue,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FlatIcon(FlatIconType.FOLDER, tint = if (isPdf) RpmRed else BluetoothBlue, size = 12.dp)
                            Text(
                                text = if (isPdf) "View PDF" else "View Receipt",
                                color = if (isPdf) RpmRed else BluetoothBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Text(
                        text = "No receipt attached",
                        color = Color(0xFF555E68),
                        fontSize = 11.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }

                // Edit / Delete Actions
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Edit",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onEdit() }
                            .padding(4.dp)
                    )
                    Text(
                        text = "Delete",
                        color = RpmRed.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onDelete() }
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryPlaceholder(onAddClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphicOuter(cornerRadius = 20.dp, blurRadius = 8.dp)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FlatIcon(FlatIconType.WRENCH, tint = AccentGreen, size = 42.dp)
            Text(
                text = "No Service Records Yet",
                color = TextMain,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Keep track of your bike's maintenance, attach receipts, and get notified before your next service.",
                color = TextMuted,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onAddClick,
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("+ Log First Service Record", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceRecordFormDialog(
    initialRecord: ServiceRecord?,
    existingRecords: List<ServiceRecord>,
    currentBikeOdo: Double,
    onDismiss: () -> Unit,
    onSave: (ServiceRecord) -> Unit
) {
    val context = LocalContext.current
    val store = remember { MaintenanceStore.getInstance(context) }
    val appPrefs = remember { com.kalaiselvan.ride.preferences.AppPreferences.getInstance(context) }
    val userPrefs by appPrefs.userPreferences.collectAsState()

    var serviceDateMs by remember { mutableStateOf(initialRecord?.serviceDateMs ?: System.currentTimeMillis()) }
    var odoText by remember {
        mutableStateOf(
            if (initialRecord != null) String.format(Locale.US, "%.0f", initialRecord.odoKm)
            else if (currentBikeOdo > 0) String.format(Locale.US, "%.0f", currentBikeOdo)
            else ""
        )
    }
    var costText by remember {
        mutableStateOf(if (initialRecord != null) String.format(Locale.US, "%.0f", initialRecord.totalCost) else "")
    }
    var currency by remember { mutableStateOf(initialRecord?.currency ?: userPrefs.preferredCurrency) }
    var notes by remember { mutableStateOf(initialRecord?.notes ?: "") }
    var receiptPath by remember { mutableStateOf(initialRecord?.receiptImagePath) }

    // Next service reminder targets
    var nextOdoText by remember {
        mutableStateOf(
            if (initialRecord?.nextServiceOdoKm != null) String.format(Locale.US, "%.0f", initialRecord.nextServiceOdoKm)
            else {
                val base = (odoText.toDoubleOrNull() ?: currentBikeOdo).takeIf { it > 0 } ?: 0.0
                if (base > 0) String.format(Locale.US, "%.0f", base + 6000.0) else ""
            }
        )
    }
    var nextDateMs by remember {
        mutableStateOf(
            initialRecord?.nextServiceDateMs ?: Calendar.getInstance().apply { add(Calendar.MONTH, 6) }.timeInMillis
        )
    }
    var reminderEnabled by remember { mutableStateOf(initialRecord?.reminderEnabled ?: true) }

    // Validation against other service records
    val otherRecords = remember(existingRecords, initialRecord) {
        existingRecords.filter { it.id != initialRecord?.id }
    }
    val maxPastOdo = remember(otherRecords, serviceDateMs) {
        otherRecords.filter { it.serviceDateMs <= serviceDateMs }.maxOfOrNull { it.odoKm } ?: 0.0
    }
    val minFutureOdo = remember(otherRecords, serviceDateMs) {
        otherRecords.filter { it.serviceDateMs > serviceDateMs }.minOfOrNull { it.odoKm }
    }

    val currentOdoValue = odoText.toDoubleOrNull() ?: 0.0
    val odoError: String? = when {
        odoText.isNotBlank() && currentOdoValue <= 0.0 ->
            "ODO must be greater than 0 KM"
        odoText.isNotBlank() && maxPastOdo > 0.0 && currentOdoValue < maxPastOdo ->
            "ODO cannot be less than previous record (${maxPastOdo.toInt()} KM)"
        odoText.isNotBlank() && minFutureOdo != null && currentOdoValue > minFutureOdo ->
            "ODO cannot be greater than subsequent record (${minFutureOdo.toInt()} KM)"
        else -> null
    }

    val nextOdoValue = nextOdoText.toDoubleOrNull() ?: 0.0
    val nextOdoError: String? = if (!reminderEnabled) null else when {
        nextOdoText.isNotBlank() && nextOdoValue <= 0.0 ->
            "Target ODO must be greater than 0 KM"
        nextOdoText.isNotBlank() && currentOdoValue > 0.0 && nextOdoValue <= currentOdoValue ->
            "Target ODO must be greater than current ODO (${currentOdoValue.toInt()} KM)"
        else -> null
    }

    val nextDateError: String? = if (!reminderEnabled) null else when {
        nextDateMs <= serviceDateMs ->
            "Target date must be after service date"
        else -> null
    }

    // Camera Capture setup
    var tempCameraFile by remember { mutableStateOf<File?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraFile != null) {
            receiptPath = tempCameraFile!!.absolutePath
            Toast.makeText(context, "Receipt photo captured", Toast.LENGTH_SHORT).show()
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val ext = getFileExtension(context, uri)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val saved = store.saveReceiptImage(stream, extension = ext)
                    receiptPath = saved
                    Toast.makeText(context, if (ext == "pdf") "PDF document attached" else "Receipt attached", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load document: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun launchCamera() {
        try {
            val file = store.createReceiptTempFile()
            tempCameraFile = file
            val uri = FileProvider.getUriForFile(context, "com.kalaiselvan.ride.fileprovider", file)
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Camera launch error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, "Camera permission is required to capture receipts", Toast.LENGTH_SHORT).show()
        }
    }

    var showServiceDatePicker by remember { mutableStateOf(false) }
    var showTargetDatePicker by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(BgCard)
                    .border(1.dp, Color(0xFF2A313C), RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                // Modal Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialRecord != null) "EDIT SERVICE RECORD" else "LOG SERVICE RECORD",
                        color = TextMain,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "✕",
                        color = TextMuted,
                        fontSize = 18.sp,
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Form Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Service Date Picker Row
                    FormSectionLabel("SERVICE DATE")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F1215))
                            .border(1.dp, Color(0xFF262E38), RoundedCornerShape(12.dp))
                            .clickable { showServiceDatePicker = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = SimpleDateFormat("dd MMMM, yyyy", Locale.getDefault()).format(Date(serviceDateMs)),
                            color = TextMain,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "Change", color = BluetoothBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            FlatIcon(FlatIconType.CALENDAR, tint = BluetoothBlue, size = 13.dp)
                        }
                    }

                    // Odometer & Total Cost in 2 columns
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            FormSectionLabel("KM DRIVEN (ODO)")
                            OutlinedTextField(
                                value = odoText,
                                onValueChange = { odoText = it },
                                placeholder = { Text("e.g. 6200", color = TextMuted) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = odoError != null && odoText.isNotBlank(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (odoError != null && odoText.isNotBlank()) RpmRed else AccentGreen,
                                    unfocusedBorderColor = if (odoError != null && odoText.isNotBlank()) RpmRed else Color(0xFF2A313C),
                                    errorBorderColor = RpmRed,
                                    focusedTextColor = TextMain,
                                    unfocusedTextColor = TextMain
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (odoError != null && odoText.isNotBlank()) {
                                Text(
                                    text = odoError,
                                    color = RpmRed,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 2.dp, top = 3.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            FormSectionLabel("TOTAL COST")
                            OutlinedTextField(
                                value = costText,
                                onValueChange = { costText = it },
                                placeholder = { Text("e.g. 4850", color = TextMuted) },
                                leadingIcon = { Text(currency, color = TextMain, fontWeight = FontWeight.Bold) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentGreen,
                                    unfocusedBorderColor = Color(0xFF2A313C),
                                    focusedTextColor = TextMain,
                                    unfocusedTextColor = TextMain
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Service Notes / Work description
                    FormSectionLabel("WORK PERFORMED & PARTS")
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = { Text("e.g. Motul 300V oil, OEM filter, chain cleaning & lube", color = TextMuted) },
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGreen,
                            unfocusedBorderColor = Color(0xFF2A313C),
                            focusedTextColor = TextMain,
                            unfocusedTextColor = TextMain
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // ── RECEIPT ATTACHMENT SECTION ──────────────────────────
                    FormSectionLabel("RECEIPT ATTACHMENT")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0F1215))
                            .border(1.dp, Color(0xFF262E38), RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        if (receiptPath != null && File(receiptPath!!).exists()) {
                            val isPdf = receiptPath!!.endsWith(".pdf", ignoreCase = true)
                            val bitmap = remember(receiptPath) {
                                loadReceiptBitmap(receiptPath)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = "Receipt",
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isPdf) Color(0xFF3B1E22) else Color(0xFF1E242C)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (isPdf) "PDF" else "DOC",
                                                color = if (isPdf) RpmRed else BluetoothBlue,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = if (isPdf) "PDF Document Attached" else "Receipt Attached",
                                            color = TextMain,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text("Tap remove to replace", color = TextMuted, fontSize = 11.sp)
                                    }
                                }

                                TextButton(onClick = { receiptPath = null }) {
                                    Text("Remove", color = RpmRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                            launchCamera()
                                        } else {
                                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMain),
                                    border = BorderStroke(1.dp, Color(0xFF323B47)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        FlatIcon(FlatIconType.CAMERA, tint = TextMain, size = 14.dp)
                                        Text("Camera / Scan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                OutlinedButton(
                                    onClick = { documentLauncher.launch(arrayOf("image/*", "application/pdf")) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMain),
                                    border = BorderStroke(1.dp, Color(0xFF323B47)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        FlatIcon(FlatIconType.FOLDER, tint = TextMain, size = 14.dp)
                                        Text("Upload Doc", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // ── NEXT SERVICE TARGETS & REMINDER SETUP ────────────────
                    HorizontalDivider(color = Color(0x1AFFFFFF), thickness = 1.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            FormSectionLabel("NEXT SERVICE REMINDERS")
                            Text("Alerts 500 km or 15 days before due", color = TextMuted, fontSize = 11.sp)
                        }

                        NeumorphicSwitch(
                            checked = reminderEnabled,
                            onCheckedChange = { reminderEnabled = it }
                        )
                    }

                    if (reminderEnabled) {
                        // Next Service Odometer
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("TARGET KM", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = nextOdoText,
                                    onValueChange = { nextOdoText = it },
                                    placeholder = { Text("e.g. 12000", color = TextMuted) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    isError = nextOdoError != null && nextOdoText.isNotBlank(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = if (nextOdoError != null && nextOdoText.isNotBlank()) RpmRed else AccentGreen,
                                        unfocusedBorderColor = if (nextOdoError != null && nextOdoText.isNotBlank()) RpmRed else Color(0xFF2A313C),
                                        errorBorderColor = RpmRed,
                                        focusedTextColor = TextMain,
                                        unfocusedTextColor = TextMain
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (nextOdoError != null && nextOdoText.isNotBlank()) {
                                    Text(
                                        text = nextOdoError,
                                        color = RpmRed,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 2.dp, top = 3.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text("TARGET DUE DATE", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF0F1215))
                                        .border(
                                            1.dp,
                                            if (nextDateError != null) RpmRed else Color(0xFF2A313C),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { showTargetDatePicker = true }
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(Date(nextDateMs)),
                                            color = TextMain,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        FlatIcon(FlatIconType.CALENDAR, tint = TextMuted, size = 14.dp)
                                    }
                                }
                                if (nextDateError != null) {
                                    Text(
                                        text = nextDateError,
                                        color = RpmRed,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 2.dp, top = 3.dp)
                                    )
                                }
                            }
                        }

                        // Quick target presets (+6000 km, +12000 km)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PresetPill("+6,000 KM") {
                                val base = odoText.toDoubleOrNull() ?: currentBikeOdo
                                if (base > 0) nextOdoText = String.format(Locale.US, "%.0f", base + 6000.0)
                            }
                            PresetPill("+12,000 KM") {
                                val base = odoText.toDoubleOrNull() ?: currentBikeOdo
                                if (base > 0) nextOdoText = String.format(Locale.US, "%.0f", base + 12000.0)
                            }
                            PresetPill("+6 Months") {
                                nextDateMs = Calendar.getInstance().apply { timeInMillis = serviceDateMs; add(Calendar.MONTH, 6) }.timeInMillis
                            }
                            PresetPill("+1 Year") {
                                nextDateMs = Calendar.getInstance().apply { timeInMillis = serviceDateMs; add(Calendar.YEAR, 1) }.timeInMillis
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                        border = BorderStroke(1.dp, Color(0xFF333A44)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val odo = odoText.toDoubleOrNull() ?: 0.0
                            val cost = costText.toDoubleOrNull() ?: 0.0
                            val nextOdo = nextOdoText.toDoubleOrNull()

                            if (odo <= 0.0) {
                                Toast.makeText(context, "Please enter current odometer reading (> 0 KM)", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (odoError != null) {
                                Toast.makeText(context, odoError, Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (reminderEnabled) {
                                if (nextOdo == null || nextOdo <= 0.0) {
                                    Toast.makeText(context, "Please enter next target KM", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (nextOdoError != null) {
                                    Toast.makeText(context, nextOdoError, Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (nextDateError != null) {
                                    Toast.makeText(context, nextDateError, Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                            }

                            val newRecord = ServiceRecord(
                                id = initialRecord?.id ?: UUID.randomUUID().toString(),
                                serviceDateMs = serviceDateMs,
                                odoKm = odo,
                                totalCost = cost,
                                currency = currency,
                                notes = notes.trim(),
                                receiptImagePath = receiptPath,
                                nextServiceDateMs = if (reminderEnabled) nextDateMs else null,
                                nextServiceOdoKm = if (reminderEnabled) nextOdo else null,
                                reminderEnabled = reminderEnabled
                            )
                            onSave(newRecord)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Text("Save Record", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }

    if (showServiceDatePicker) {
        CustomDatePickerDialog(
            initialDateMs = serviceDateMs,
            maxDateMs = System.currentTimeMillis(),
            title = "Select Service Date",
            onDismiss = { showServiceDatePicker = false },
            onDateSelected = { picked ->
                serviceDateMs = picked
                if (nextDateMs <= picked) {
                    nextDateMs = Calendar.getInstance().apply {
                        timeInMillis = picked
                        add(Calendar.MONTH, 6)
                    }.timeInMillis
                }
                showServiceDatePicker = false
            }
        )
    }

    if (showTargetDatePicker) {
        CustomDatePickerDialog(
            initialDateMs = if (nextDateMs > serviceDateMs) nextDateMs else serviceDateMs + 86400000L,
            minDateMs = serviceDateMs + 86400000L,
            title = "Select Target Due Date",
            onDismiss = { showTargetDatePicker = false },
            onDateSelected = { picked ->
                nextDateMs = picked
                showTargetDatePicker = false
            }
        )
    }
}

@Composable
private fun FormSectionLabel(title: String) {
    Text(
        text = title,
        color = TextMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

@Composable
private fun PresetPill(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E232B))
            .border(1.dp, Color(0xFF2E3744), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(text = text, color = BluetoothBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FullScreenReceiptDialog(
    imagePath: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isPdf = imagePath.endsWith(".pdf", ignoreCase = true)
    val bitmap = remember(imagePath) {
        loadReceiptBitmap(imagePath)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header with Close Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPdf) "DOCUMENT PREVIEW (PDF)" else "RECEIPT PREVIEW",
                        color = TextMain,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Text("✕", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Full Resolution Image / Rendered PDF Page
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Full Receipt",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FlatIcon(FlatIconType.FOLDER, tint = if (isPdf) RpmRed else TextMuted, size = 36.dp)
                            Text(
                                text = if (isPdf) "PDF Document Attached" else "Could not load image file",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isPdf) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val file = File(imagePath)
                                    val uri = FileProvider.getUriForFile(context, "com.kalaiselvan.ride.fileprovider", file)
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/pdf")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Open PDF"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot open PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMain),
                            border = BorderStroke(1.dp, Color(0xFF323B47)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Open in PDF App")
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222933), contentColor = TextMain),
                        shape = RoundedCornerShape(12.dp),
                        modifier = if (isPdf) Modifier.weight(1f) else Modifier.fillMaxWidth(0.6f)
                    ) {
                        Text("Close Preview")
                    }
                }
            }
        }
    }
}

private fun getFileExtension(context: Context, uri: Uri): String {
    val mimeType = context.contentResolver.getType(uri)
    if (mimeType == "application/pdf") return "pdf"
    if (mimeType?.startsWith("image/png") == true) return "png"
    if (mimeType?.startsWith("image/webp") == true) return "webp"
    if (mimeType?.startsWith("image/") == true) return "jpg"

    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                val name = cursor.getString(nameIndex)
                if (name != null && name.contains(".")) {
                    val ext = name.substringAfterLast(".").lowercase()
                    if (ext in listOf("pdf", "jpg", "jpeg", "png", "webp")) {
                        return if (ext == "jpeg") "jpg" else ext
                    }
                }
            }
        }
    } catch (_: Exception) {}

    return "jpg"
}

private fun loadReceiptBitmap(path: String?): ImageBitmap? {
    if (path == null) return null
    val file = File(path)
    if (!file.exists()) return null
    return try {
        if (path.endsWith(".pdf", ignoreCase = true)) {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            if (renderer.pageCount > 0) {
                val page = renderer.openPage(0)
                val scale = when {
                    page.width > 2000 || page.height > 2000 -> 1f
                    page.width > 1000 || page.height > 1000 -> 1.5f
                    else -> 2f
                }
                val width = (page.width * scale).toInt().coerceAtLeast(1)
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                pfd.close()
                bmp.asImageBitmap()
            } else {
                renderer.close()
                pfd.close()
                null
            }
        } else {
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        }
    } catch (_: Exception) {
        null
    }
}
